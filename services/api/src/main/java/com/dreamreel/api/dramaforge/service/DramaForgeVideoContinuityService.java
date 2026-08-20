package com.dreamreel.api.dramaforge.service;

import com.dreamreel.api.domain.GenerationStatus;
import com.dreamreel.api.dramaforge.domain.DramaForgeShot;
import com.dreamreel.api.dramaforge.domain.DramaForgeShotStatus;
import com.dreamreel.api.dramaforge.repository.DramaForgeShotRepository;
import com.dreamreel.api.exception.ResourceNotFoundException;
import com.dreamreel.api.service.MediaStorageService;
import com.dreamreel.api.service.OssStorageService;
import com.dreamreel.api.service.UploadStorageService;
import com.dreamreel.api.service.VideoGenerationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * 镜头视频连续性：等待上一镜成片，并抽取尾帧供下一镜 image-to-video 参考。
 */
@Component
public class DramaForgeVideoContinuityService {

    private static final Logger log = LoggerFactory.getLogger(DramaForgeVideoContinuityService.class);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(5);
    private static final Duration MAX_WAIT = Duration.ofMinutes(25);

    private final VideoGenerationService videoGenerationService;
    private final DramaForgeShotRepository shotRepository;
    private final DramaForgeStatusCalculator statusCalculator;
    private final UploadStorageService uploadStorageService;
    private final MediaStorageService mediaStorageService;
    private final OssStorageService ossStorageService;
    private final DramaForgeShotAudioRemasterService shotAudioRemasterService;

    public DramaForgeVideoContinuityService(
            VideoGenerationService videoGenerationService,
            DramaForgeShotRepository shotRepository,
            DramaForgeStatusCalculator statusCalculator,
            UploadStorageService uploadStorageService,
            MediaStorageService mediaStorageService,
            OssStorageService ossStorageService,
            DramaForgeShotAudioRemasterService shotAudioRemasterService) {
        this.videoGenerationService = videoGenerationService;
        this.shotRepository = shotRepository;
        this.statusCalculator = statusCalculator;
        this.uploadStorageService = uploadStorageService;
        this.mediaStorageService = mediaStorageService;
        this.ossStorageService = ossStorageService;
        this.shotAudioRemasterService = shotAudioRemasterService;
    }

    /** 轮询直至镜头视频完成或失败。 */
    public DramaForgeShot waitForShotVideo(UUID projectId, UUID shotId, String apiKeyHeader)
            throws InterruptedException {
        var shot = shotRepository.findById(shotId)
                .orElseThrow(() -> new ResourceNotFoundException("镜头不存在: " + shotId));
        if (shot.getVideoJobId() == null) {
            throw new IllegalStateException("镜头 " + shot.getShotNumber() + " 未提交视频任务");
        }

        var deadline = Instant.now().plus(MAX_WAIT);
        while (Instant.now().isBefore(deadline)) {
            var result = videoGenerationService.getForProject(
                    projectId, shot.getVideoJobId(), apiKeyHeader);
            if (result.status() == GenerationStatus.COMPLETED) {
                shot.setStatus(DramaForgeShotStatus.VIDEO_DONE);
                shot.setErrorMessage(null);
                shot = shotRepository.save(shot);
                shotAudioRemasterService.remasterIfNeeded(shot);
                return shotRepository.findById(shotId).orElse(shot);
            }
            if (result.status() == GenerationStatus.FAILED) {
                var err = result.errorMessage() != null ? result.errorMessage() : "unknown";
                shot.setStatus(DramaForgeShotStatus.FAILED);
                var detail = err.trim().isEmpty() ? "视频生成失败" : err.trim();
                shot.setErrorMessage(detail.length() > 2000 ? detail.substring(0, 2000) : detail);
                shotRepository.save(shot);
                throw new IllegalStateException("镜头 " + shot.getShotNumber() + " 视频生成失败: " + err);
            }
            Thread.sleep(POLL_INTERVAL.toMillis());
        }
        // 超时不改镜头为 FAILED：上游任务往往仍在跑，标失败会让 SYNC_VIDEOS 不再轮询，UI 也显示失败。
        throw new IllegalStateException(
                "镜头 " + shot.getShotNumber() + " 视频仍在生成中（等待超时），已转后台同步，请稍后刷新");
    }

    /** 从已完成镜头成片中抽取最后一帧，上传后返回可访问 URL。 */
    public String extractLastFrameUrl(UUID projectId, DramaForgeShot shot) {
        var videoUrl = statusCalculator.resolveVideoUrl(shot);
        if (videoUrl == null || videoUrl.isBlank()) {
            return null;
        }

        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("df-tail-frame-");
            var input = resolveLocalVideoPath(shot.getVideoJobId());
            Path source = input;
            if (source == null || !Files.exists(source)) {
                source = workDir.resolve("source.mp4");
                downloadUrl(videoUrl, source);
            }
            validateVideoSource(source);
            var frame = workDir.resolve("tail.jpg");
            extractLastFrame(source, frame);
            var bytes = Files.readAllBytes(frame);
            if (bytes.length < 64) {
                throw new IllegalStateException("尾帧图片过小，抽取可能失败");
            }
            var upload = uploadStorageService.storeBytes(
                    bytes,
                    "image/jpeg",
                    "shot-" + shot.getShotNumber() + "-tail.jpg");
            log.info("Extracted tail frame for shot {} -> {}", shot.getShotNumber(), upload.url());
            return upload.url();
        } catch (IOException | InterruptedException | IllegalStateException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Failed to extract tail frame for shot {}: {}", shot.getShotNumber(), ex.getMessage());
            return null;
        } finally {
            if (workDir != null) {
                deleteRecursive(workDir);
            }
        }
    }

    /**
     * 严格取「上一镜号」(shotNumber - 1) 的成片，用于镜间尾帧衔接。
     * 不跳过未完成的中间镜（例如镜头 4 未出片时，镜头 5 不能改用镜头 3）。
     */
    public Optional<DramaForgeShot> findPreviousCompletedShot(DramaForgeShot shot) {
        if (shot == null || shot.getShotNumber() <= 1) {
            return Optional.empty();
        }
        var previousNumber = shot.getShotNumber() - 1;
        return shotRepository.findByEpisodeIdOrderByShotNumberAsc(shot.getEpisodeId()).stream()
                .filter(candidate -> candidate.getShotNumber() == previousNumber)
                .findFirst()
                .filter(candidate -> candidate.getVideoJobId() != null)
                .filter(candidate -> {
                    var url = statusCalculator.resolveVideoUrl(candidate);
                    return url != null && !url.isBlank();
                });
    }

    /** 上一镜号实体（不论是否已成片），用于报错指明应先完成哪一镜。 */
    public Optional<DramaForgeShot> findImmediatePreviousShot(DramaForgeShot shot) {
        if (shot == null || shot.getShotNumber() <= 1) {
            return Optional.empty();
        }
        var previousNumber = shot.getShotNumber() - 1;
        return shotRepository.findByEpisodeIdOrderByShotNumberAsc(shot.getEpisodeId()).stream()
                .filter(candidate -> candidate.getShotNumber() == previousNumber)
                .findFirst();
    }

    private Path resolveLocalVideoPath(UUID videoJobId) {
        if (videoJobId == null) {
            return null;
        }
        return mediaStorageService.loadStoredMedia(videoJobId)
                .map(stored -> {
                    try {
                        if (stored.resource().isFile()) {
                            return stored.resource().getFile().toPath();
                        }
                    } catch (IOException ignored) {
                    }
                    return null;
                })
                .orElse(null);
    }

    private void validateVideoSource(Path video) throws IOException {
        if (video == null || !Files.exists(video)) {
            throw new IllegalStateException("成片文件不存在，无法抽尾帧");
        }
        var size = Files.size(video);
        if (size < 1024) {
            throw new IllegalStateException("成片文件过小（" + size + " bytes），可能下载失败");
        }
        // 常见 HTML 错误页误存为 mp4
        try (var in = Files.newInputStream(video)) {
            var head = in.readNBytes(16);
            var asText = new String(head, java.nio.charset.StandardCharsets.US_ASCII).toLowerCase(Locale.ROOT);
            if (asText.startsWith("<!doctype") || asText.startsWith("<html") || asText.startsWith("{")) {
                throw new IllegalStateException("成片 URL 返回的不是视频内容（疑似 HTML/JSON 错误页）");
            }
        }
    }

    /** 公共抽帧尾参；withImage2=true 时强制 -f image2（完整 ffmpeg）；残缺版可关掉再试。 */
    private static java.util.List<String> frameExtractArgs(boolean withImage2, String... headAndOutput) {
        if (headAndOutput.length < 2) {
            return java.util.List.of();
        }
        var out = headAndOutput[headAndOutput.length - 1];
        var args = new java.util.ArrayList<String>();
        args.add("-hide_banner");
        args.add("-loglevel");
        args.add("error");
        for (int i = 0; i < headAndOutput.length - 1; i++) {
            args.add(headAndOutput[i]);
        }
        args.add("-frames:v");
        args.add("1");
        args.add("-q:v");
        args.add("2");
        if (withImage2) {
            args.add("-f");
            args.add("image2");
        }
        args.add("-y");
        args.add(out);
        return args;
    }

    /**
     * 抽取视频尾帧。多 seek 策略 + 有/无 -f image2 各试一轮。
     */
    private void extractLastFrame(Path video, Path output) throws IOException, InterruptedException {
        var ffmpeg = findFfmpeg();
        var heads = new java.util.ArrayList<String[]>();
        heads.add(new String[]{"-sseof", "-0.05", "-i", video.toString(), output.toString()});
        heads.add(new String[]{"-sseof", "-1", "-i", video.toString(), output.toString()});
        try {
            var duration = probeDurationSeconds(ffmpeg, video);
            if (duration != null && duration > 0) {
                var seek = Math.max(0, duration - 0.1);
                heads.add(new String[]{
                        "-ss", String.format(Locale.ROOT, "%.3f", seek),
                        "-i", video.toString(),
                        output.toString()});
            }
        } catch (Exception ex) {
            log.debug("duration probe skipped: {}", ex.getMessage());
        }
        heads.add(new String[]{"-i", video.toString(), "-vf", "reverse", output.toString()});

        IllegalStateException last = null;
        for (boolean withImage2 : new boolean[]{true, false}) {
            for (var head : heads) {
                Files.deleteIfExists(output);
                try {
                    runFfmpeg(ffmpeg, frameExtractArgs(withImage2, head));
                    if (Files.exists(output) && Files.size(output) > 0) {
                        return;
                    }
                    last = new IllegalStateException("FFmpeg 未产出尾帧文件");
                } catch (IllegalStateException ex) {
                    last = ex;
                    log.warn("Tail-frame strategy failed for {} via {} (image2={}): {}",
                            video.getFileName(), ffmpeg, withImage2, ex.getMessage());
                }
            }
        }
        throw last != null ? last : new IllegalStateException("尾帧抽取失败");
    }

    private Double probeDurationSeconds(String ffmpeg, Path video) throws IOException, InterruptedException {
        var ffprobe = resolveFfprobe(ffmpeg);
        if (ffprobe != null) {
            try {
                var out = runProcessCapture(java.util.List.of(
                        ffprobe,
                        "-v", "error",
                        "-show_entries", "format=duration",
                        "-of", "default=noprint_wrappers=1:nokey=1",
                        video.toString()), true);
                return Double.parseDouble(out.trim());
            } catch (Exception ignored) {
                // fall through
            }
        }
        var err = runProcessCapture(java.util.List.of(
                ffmpeg, "-hide_banner", "-i", video.toString()), false);
        var matcher = java.util.regex.Pattern
                .compile("Duration:\\s*(\\d+):(\\d+):(\\d+(?:\\.\\d+)?)")
                .matcher(err);
        if (!matcher.find()) {
            return null;
        }
        var h = Double.parseDouble(matcher.group(1));
        var m = Double.parseDouble(matcher.group(2));
        var s = Double.parseDouble(matcher.group(3));
        return h * 3600 + m * 60 + s;
    }

    private String resolveFfprobe(String ffmpeg) {
        try {
            var ffmpegPath = Path.of(ffmpeg);
            if (ffmpegPath.getParent() == null) {
                return null;
            }
            var name = ffmpegPath.getFileName().toString().toLowerCase(Locale.ROOT);
            var probeName = name.replace("ffmpeg", "ffprobe");
            var sibling = ffmpegPath.getParent().resolve(probeName);
            if (Files.exists(sibling)) {
                return sibling.toString();
            }
        } catch (Exception ignored) {
            // ignore
        }
        return null;
    }

    private void runFfmpeg(String ffmpeg, java.util.List<String> args) throws IOException, InterruptedException {
        var command = new java.util.ArrayList<String>();
        command.add(ffmpeg);
        command.addAll(args);
        runProcessCapture(command, true);
    }

    /**
     * @param requireZeroExit true 时非 0 抛错；false 时仅返回合并后的 stdout/stderr（用于 ffmpeg -i 探时长）
     */
    private String runProcessCapture(java.util.List<String> command, boolean requireZeroExit)
            throws IOException, InterruptedException {
        var process = new ProcessBuilder(command).redirectErrorStream(true).start();
        var output = new StringBuilder();
        try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (output.length() < 4000) {
                    output.append(line).append('\n');
                }
            }
        }
        var code = process.waitFor();
        if (requireZeroExit && code != 0) {
            var detail = output.toString().trim();
            if (detail.length() > 500) {
                detail = detail.substring(detail.length() - 500);
            }
            throw new IllegalStateException(
                    "FFmpeg 执行失败 (exit=" + code + ")"
                            + (detail.isBlank() ? ": " + String.join(" ", command) : ": " + detail));
        }
        return output.toString();
    }

    private void downloadUrl(String url, Path target) throws IOException, InterruptedException {
        var resolved = url;
        if (resolved.startsWith("/")) {
            resolved = "http://127.0.0.1:7051" + resolved;
        }
        if (resolved.startsWith("data:")) {
            var comma = resolved.indexOf(',');
            if (comma > 0) {
                var bytes = java.util.Base64.getDecoder().decode(resolved.substring(comma + 1));
                Files.write(target, bytes);
            }
            return;
        }
        if (ossStorageService.isOurUrl(resolved)) {
            // OSS URL 直接下载
        }
        var request = HttpRequest.newBuilder()
                .uri(URI.create(resolved))
                .timeout(Duration.ofSeconds(180))
                .GET()
                .build();
        var response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() >= 400) {
            throw new IOException("HTTP " + response.statusCode() + " for " + resolved);
        }
        try (InputStream in = response.body()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * 解析可用的完整 ffmpeg。禁止落到 Trae/Cursor 等 IDE 自带残缺二进制；
     * 优先 FFMPEG_PATH → WinGet 绝对路径 → where 结果（过滤 IDE）。
     */
    private String findFfmpeg() {
        var fromEnv = System.getenv("FFMPEG_PATH");
        if (fromEnv != null && !fromEnv.isBlank()) {
            var envPath = fromEnv.trim();
            if (isCompleteFfmpeg(envPath)) {
                log.info("Using ffmpeg from FFMPEG_PATH: {}", envPath);
                return envPath;
            }
            log.warn("FFMPEG_PATH is set but not a complete ffmpeg: {}", envPath);
        }

        for (var candidate : listFfmpegCandidatesAbsolute()) {
            if (isEditorBundledFfmpeg(candidate)) {
                continue;
            }
            if (isCompleteFfmpeg(candidate)) {
                log.info("Using ffmpeg: {}", candidate);
                return candidate;
            }
        }

        throw new IllegalStateException(
                "未找到可用的完整 ffmpeg（需支持 image2/mjpeg 抽帧）。"
                        + "请安装 ffmpeg 或设置环境变量 FFMPEG_PATH 指向完整版，"
                        + "例如 WinGet: %LOCALAPPDATA%\\Microsoft\\WinGet\\Links\\ffmpeg.exe。"
                        + "当前 PATH 中的 Trae/Cursor 自带 ffmpeg 会被跳过。");
    }

    /** 绝对路径候选，不包含裸 "ffmpeg"（避免 PATH 解析到 Trae）。 */
    private static java.util.List<String> listFfmpegCandidatesAbsolute() {
        var list = new java.util.ArrayList<String>();
        var localApp = System.getenv("LOCALAPPDATA");
        if (localApp != null && !localApp.isBlank()) {
            list.add(localApp + "\\Microsoft\\WinGet\\Links\\ffmpeg.exe");
        }
        var programFiles = System.getenv("ProgramFiles");
        if (programFiles != null && !programFiles.isBlank()) {
            list.add(programFiles + "\\ffmpeg\\bin\\ffmpeg.exe");
            list.add(programFiles + "\\Gyan\\FFmpeg\\bin\\ffmpeg.exe");
        }
        try {
            var isWin = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
            var pb = new ProcessBuilder(isWin
                    ? java.util.List.of("where.exe", "ffmpeg")
                    : java.util.List.of("which", "-a", "ffmpeg"));
            pb.redirectErrorStream(true);
            var p = pb.start();
            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    var trimmed = line.trim();
                    if (!trimmed.isBlank() && Path.of(trimmed).isAbsolute()) {
                        list.add(trimmed);
                    }
                }
            }
            p.waitFor();
        } catch (Exception ignored) {
            // keep defaults
        }
        try {
            if (localApp != null) {
                var packages = Path.of(localApp, "Microsoft", "WinGet", "Packages");
                if (Files.isDirectory(packages)) {
                    try (var stream = Files.walk(packages, 5)) {
                        stream.filter(p -> p.getFileName().toString().equalsIgnoreCase("ffmpeg.exe"))
                                .limit(8)
                                .forEach(p -> list.add(p.toString()));
                    }
                }
            }
        } catch (Exception ignored) {
            // keep defaults
        }
        // 去重保序
        var seen = new java.util.LinkedHashSet<String>();
        for (var c : list) {
            seen.add(c);
        }
        return new java.util.ArrayList<>(seen);
    }

    private static boolean isEditorBundledFfmpeg(String path) {
        var lower = path.toLowerCase(Locale.ROOT).replace('\\', '/');
        return lower.contains("/trae")
                || lower.contains("trae cn")
                || lower.contains("/cursor")
                || lower.contains("/code/")
                || lower.contains("resources/app/bin");
    }

    /** 能跑 -version，且支持写出 jpeg（image2 或 mjpeg）。 */
    private static boolean isCompleteFfmpeg(String path) {
        try {
            var file = Path.of(path);
            if (!file.isAbsolute() || !Files.exists(file)) {
                return false;
            }
            var version = runQuick(java.util.List.of(path, "-version"));
            if (version == null || !version.toLowerCase(Locale.ROOT).contains("ffmpeg version")) {
                return false;
            }
            var formats = runQuick(java.util.List.of(path, "-hide_banner", "-formats"));
            if (formats == null) {
                return false;
            }
            var lower = formats.toLowerCase(Locale.ROOT);
            // 完整版通常含 image2；至少要有 mjpeg/单帧写出能力
            return lower.contains("image2") || lower.contains("mjpeg");
        } catch (Exception ex) {
            return false;
        }
    }

    private static String runQuick(java.util.List<String> command) {
        try {
            var process = new ProcessBuilder(command).redirectErrorStream(true).start();
            var out = new StringBuilder();
            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null && out.length() < 200_000) {
                    out.append(line).append('\n');
                }
            }
            process.waitFor();
            return out.toString();
        } catch (Exception ex) {
            return null;
        }
    }

    private void deleteRecursive(Path dir) {
        try {
            if (!Files.exists(dir)) {
                return;
            }
            try (var stream = Files.walk(dir)) {
                stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                });
            }
        } catch (IOException ignored) {
        }
    }
}
