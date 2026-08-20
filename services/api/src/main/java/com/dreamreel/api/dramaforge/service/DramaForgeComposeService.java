package com.dreamreel.api.dramaforge.service;

import com.dreamreel.api.dramaforge.domain.*;
import com.dreamreel.api.dramaforge.repository.*;
import com.dreamreel.api.config.MediaStorageProperties;
import com.dreamreel.api.domain.GenerationJob;
import com.dreamreel.api.domain.GenerationMediaType;
import com.dreamreel.api.domain.GenerationStatus;
import com.dreamreel.api.repository.GenerationJobRepository;
import com.dreamreel.api.service.MediaStorageService;
import com.dreamreel.api.service.OssStorageService;
import com.dreamreel.api.service.UploadStorageService;
import com.dreamreel.api.service.VideoGenerationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional
public class DramaForgeComposeService {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final DramaForgeShotRepository shotRepository;
    private final DramaForgeEpisodeRepository episodeRepository;
    private final DramaForgeCompositionRepository compositionRepository;
    private final DramaForgeConfigRepository configRepository;
    private final GenerationJobRepository generationJobRepository;
    private final MediaStorageService mediaStorageService;
    private final MediaStorageProperties mediaStorageProperties;
    private final OssStorageService ossStorageService;
    private final UploadStorageService uploadStorageService;
    private final VideoGenerationService videoGenerationService;
    private final DramaForgeEventHub eventHub;
    private final DramaForgeLipSyncService lipSyncService;
    private final ObjectMapper objectMapper;

    public DramaForgeComposeService(
            DramaForgeShotRepository shotRepository,
            DramaForgeEpisodeRepository episodeRepository,
            DramaForgeCompositionRepository compositionRepository,
            DramaForgeConfigRepository configRepository,
            GenerationJobRepository generationJobRepository,
            MediaStorageService mediaStorageService,
            MediaStorageProperties mediaStorageProperties,
            OssStorageService ossStorageService,
            UploadStorageService uploadStorageService,
            VideoGenerationService videoGenerationService,
            DramaForgeEventHub eventHub,
            DramaForgeLipSyncService lipSyncService,
            ObjectMapper objectMapper) {
        this.shotRepository = shotRepository;
        this.episodeRepository = episodeRepository;
        this.compositionRepository = compositionRepository;
        this.configRepository = configRepository;
        this.generationJobRepository = generationJobRepository;
        this.mediaStorageService = mediaStorageService;
        this.mediaStorageProperties = mediaStorageProperties;
        this.ossStorageService = ossStorageService;
        this.uploadStorageService = uploadStorageService;
        this.videoGenerationService = videoGenerationService;
        this.eventHub = eventHub;
        this.lipSyncService = lipSyncService;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public DramaForgeComposition composeEpisode(UUID projectId, UUID episodeId)
            throws IOException, InterruptedException {
        return composeEpisode(projectId, episodeId, null);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public DramaForgeComposition composeEpisode(UUID projectId, UUID episodeId, String apiKey)
            throws IOException, InterruptedException {
        var episode = episodeRepository.findByIdAndProjectId(episodeId, projectId)
                .orElseThrow(() -> new IllegalStateException("剧集不存在"));
        var shots = resolveTimelineOrderedShots(episode);
        if (shots.isEmpty()) {
            throw new IllegalStateException("没有可合成的镜头");
        }

        // 合成前先把方舟任务状态/成片 URL 同步下来（不要求全部镜头成功）
        syncShotVideos(projectId, shots, apiKey);

        var composition = new DramaForgeComposition();
        composition.setProjectId(projectId);
        composition.setEpisodeId(episodeId);
        composition.setStatus("running");
        composition = compositionRepository.save(composition);

        var tempDir = Files.createTempDirectory("dramaforge-compose-" + episodeId);
        var listFile = tempDir.resolve("inputs.txt");
        var outputFile = tempDir.resolve("output.mp4");
        var inputLines = new ArrayList<String>();
        var skippedReasons = new ArrayList<String>();

        try {
            int included = 0;
            for (var shot : shots) {
                var part = tempDir.resolve("part-" + shot.getShotNumber() + ".mp4");
                var reason = downloadShotVideo(shot, part);
                if (reason != null) {
                    skippedReasons.add("镜头" + shot.getShotNumber() + ":" + reason);
                    continue;
                }
                included++;
                inputLines.add("file '" + part.toAbsolutePath().toString().replace("\\", "/") + "'");
            }
            if (inputLines.isEmpty()) {
                var detail = skippedReasons.isEmpty()
                        ? "无 videoJobId"
                        : String.join("；", skippedReasons.subList(0, Math.min(5, skippedReasons.size())));
                throw new IllegalStateException(
                        "可合成片段为 0/" + shots.size() + "。"
                                + "已跳过原因（前几项）：" + detail
                                + "。请先确认镜头卡片能播放视频，或点「同步视频」后再合成。");
            }
            Files.writeString(listFile, String.join("\n", inputLines));

            var ffmpeg = findFfmpeg();
            runFfmpeg(ffmpeg, List.of(
                    "-y", "-f", "concat", "-safe", "0",
                    "-i", listFile.toString(),
                    "-c", "copy",
                    outputFile.toString()));

            if (!Files.exists(outputFile)) {
                throw new IllegalStateException("FFmpeg 合成失败，请确认已安装 ffmpeg 并在 PATH 中");
            }

            var config = configRepository.findByProjectId(projectId).orElse(null);
            if (config != null && config.getBgmUrl() != null && !config.getBgmUrl().isBlank()) {
                var bgmMixed = tempDir.resolve("bgm-mixed.mp4");
                double vol = config.getBgmVolume() != null ? config.getBgmVolume() : 0.18;
                mixBgmIntoComposition(ffmpeg, outputFile, config.getBgmUrl(), vol, bgmMixed);
                Files.move(bgmMixed, outputFile, StandardCopyOption.REPLACE_EXISTING);
            }

            var upload = uploadStorageService.store(new InMemoryMultipartFile(
                    "composition.mp4",
                    "video/mp4",
                    Files.readAllBytes(outputFile)
            ));
            composition.setOutputUrl(upload.url());
            composition.setStatus("completed");
            var skipped = shots.size() - included;
            if (skipped > 0) {
                composition.setErrorMessage("部分合成：已拼接 " + included + "/" + shots.size()
                        + " 镜（跳过 " + skipped + "）");
            } else {
                composition.setErrorMessage(null);
            }
            composition = compositionRepository.save(composition);
            eventHub.publish(projectId, "composition_completed", java.util.Map.of(
                    "compositionId", composition.getId().toString(),
                    "outputUrl", composition.getOutputUrl(),
                    "includedShots", included,
                    "totalShots", shots.size(),
                    "skippedShots", skipped
            ));
            return composition;
        } catch (Exception ex) {
            composition.setStatus("failed");
            composition.setErrorMessage(ex.getMessage());
            compositionRepository.save(composition);
            if (ex instanceof IllegalStateException ise) {
                throw ise;
            }
            throw new IllegalStateException(ex.getMessage() != null ? ex.getMessage() : "合成失败", ex);
        } finally {
            deleteRecursive(tempDir);
        }
    }

    private void syncShotVideos(UUID projectId, List<DramaForgeShot> shots, String apiKey) {
        for (var shot : shots) {
            if (shot.getVideoJobId() == null) {
                continue;
            }
            try {
                var result = videoGenerationService.getForProject(
                        projectId, shot.getVideoJobId(), com.dreamreel.api.config.ArkApiKeyContext.get());
                if (result.status() == GenerationStatus.COMPLETED) {
                    if (shot.getStatus() != DramaForgeShotStatus.VIDEO_DONE) {
                        shot.setStatus(DramaForgeShotStatus.VIDEO_DONE);
                        shotRepository.save(shot);
                    }
                }
            } catch (Exception ignored) {
                // 单镜同步失败不阻断；后续 materialize 再给具体原因
            }
        }
    }

    /** @return null 成功；否则跳过原因。仅下载原始视频，不做对白混音/口型/调色等任何处理。 */
    private String downloadShotVideo(DramaForgeShot shot, Path target) {
        if (shot.getVideoJobId() == null) {
            return "未提交视频任务";
        }
        var job = generationJobRepository.findById(shot.getVideoJobId()).orElse(null);
        if (job == null) {
            return "generationJob 不存在";
        }
        try {
            mediaStorageService.ensureStoredOutput(job);
            generationJobRepository.save(job);
        } catch (Exception ignored) {
        }
        job = generationJobRepository.findById(shot.getVideoJobId()).orElse(job);

        if (job.getStatus() != GenerationStatus.COMPLETED) {
            return "任务状态=" + job.getStatus();
        }

        try {
            if (copyLocalIfPresent(job, target)) {
                return null;
            }
            var url = resolveDownloadableUrl(job);
            if (url == null || url.isBlank()) {
                return "无 outputUrl/providerOutputUrl";
            }
            downloadUrl(url, target);
            if (!Files.exists(target) || Files.size(target) <= 0) {
                return "下载结果为空";
            }
            return null;
        } catch (Exception ex) {
            var msg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            return "下载失败:" + msg;
        }
    }

    /** @return null 成功；否则跳过原因 */
    private String materializeShotVideo(
            DramaForgeShot shot,
            Path target,
            boolean mixDialogue,
            boolean lipSyncOn,
            String lipSyncEndpoint) {
        if (shot.getVideoJobId() == null) {
            return "未提交视频任务";
        }
        var job = generationJobRepository.findById(shot.getVideoJobId()).orElse(null);
        if (job == null) {
            return "generationJob 不存在";
        }
        try {
            mediaStorageService.ensureStoredOutput(job);
            generationJobRepository.save(job);
        } catch (Exception ignored) {
        }
        job = generationJobRepository.findById(shot.getVideoJobId()).orElse(job);

        if (job.getStatus() != GenerationStatus.COMPLETED) {
            return "任务状态=" + job.getStatus();
        }

        try {
            var materialized = false;
            if (copyLocalIfPresent(job, target)) {
                materialized = true;
            } else {
                var url = resolveDownloadableUrl(job);
                if (url == null || url.isBlank()) {
                    return "无 outputUrl/providerOutputUrl";
                }
                downloadUrl(url, target);
                if (!Files.exists(target) || Files.size(target) <= 0) {
                    return "下载结果为空";
                }
                materialized = true;
            }
            var hasDialogueAudio = shot.getDialogueAudioUrl() != null
                    && !shot.getDialogueAudioUrl().isBlank();
            var lipsMatched = false;
            // 先口型（按 TTS 改嘴），再混清晰对白
            if (materialized && lipSyncOn && hasDialogueAudio) {
                var videoUrl = resolveDownloadableUrl(job);
                if (videoUrl != null && !videoUrl.isBlank()) {
                    var synced = lipSyncService.maybeApplyLipSync(
                            true,
                            lipSyncEndpoint,
                            videoUrl,
                            shot.getDialogueAudioUrl(),
                            target.getParent());
                    if (synced != null && Files.exists(synced) && Files.size(synced) > 0) {
                        Files.move(synced, target, StandardCopyOption.REPLACE_EXISTING);
                        lipsMatched = true;
                    }
                }
            }
            if (materialized && mixDialogue && hasDialogueAudio) {
                mixDialogueIntoVideo(target, shot.getDialogueAudioUrl(), lipsMatched);
            }
            return null;
        } catch (Exception ex) {
            var msg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            return "下载失败:" + msg;
        }
    }

    private void mixBgmIntoComposition(
            String ffmpeg,
            Path videoPath,
            String bgmUrl,
            double volume,
            Path output) throws IOException, InterruptedException {
        var bgmPath = videoPath.getParent().resolve("bgm-track.mp3");
        downloadUrl(bgmUrl, bgmPath);
        var vol = Math.max(0.05, Math.min(0.5, volume));
        try {
            // 有对白/原声：低音量循环 BGM 与原声混合
            runFfmpeg(ffmpeg, List.of(
                    "-y",
                    "-i", videoPath.toString(),
                    "-stream_loop", "-1",
                    "-i", bgmPath.toString(),
                    "-filter_complex",
                    "[1:a]volume=" + vol + "[bg];[0:a][bg]amix=inputs=2:duration=first:dropout_transition=2[a]",
                    "-map", "0:v",
                    "-map", "[a]",
                    "-c:v", "copy",
                    "-c:a", "aac",
                    "-shortest",
                    output.toString()));
        } catch (IllegalStateException first) {
            // 无音轨：仅挂 BGM
            runFfmpeg(ffmpeg, List.of(
                    "-y",
                    "-i", videoPath.toString(),
                    "-stream_loop", "-1",
                    "-i", bgmPath.toString(),
                    "-filter_complex", "[1:a]volume=" + vol + "[a]",
                    "-map", "0:v",
                    "-map", "[a]",
                    "-c:v", "copy",
                    "-c:a", "aac",
                    "-shortest",
                    output.toString()));
        }
        Files.deleteIfExists(bgmPath);
    }

    private void mixDialogueIntoVideo(Path videoPath, String dialogueAudioUrl) throws IOException, InterruptedException {
        mixDialogueIntoVideo(videoPath, dialogueAudioUrl, false);
    }

    private void mixDialogueIntoVideo(Path videoPath, String dialogueAudioUrl, boolean lipsAlreadyMatchedToTts)
            throws IOException, InterruptedException {
        var audioPath = videoPath.getParent().resolve("dialogue-" + videoPath.getFileName());
        downloadUrl(dialogueAudioUrl, audioPath);
        var merged = videoPath.getParent().resolve("merged-" + videoPath.getFileName());
        if (lipsAlreadyMatchedToTts) {
            DramaForgeDialogueAudioMixer.mixAlignedZeroDelay(videoPath, audioPath, merged, 0.025, 3.0);
        } else {
            // 无口型服务时：按原片人声时刻对齐 TTS，让「听到的话」和「张嘴时刻」一致
            DramaForgeDialogueAudioMixer.mixAligned(videoPath, audioPath, merged, 0.025, 3.0);
        }
        Files.move(merged, videoPath, StandardCopyOption.REPLACE_EXISTING);
        Files.deleteIfExists(audioPath);
    }

    private static String resolveColorGradeFilter(DramaForgeConfig config) {
        if (config == null || config.getColorGradePreset() == null) {
            return null;
        }
        return switch (config.getColorGradePreset().trim().toLowerCase(Locale.ROOT)) {
            case "warm" -> "eq=saturation=1.08:gamma=1.04:contrast=1.02";
            case "cool" -> "eq=saturation=0.95:gamma=0.98:contrast=1.03";
            case "cinematic" -> "eq=contrast=1.1:brightness=0.02:saturation=1.05:gamma=1.02";
            case "neutral" -> "eq=contrast=1.04:saturation=1.02";
            default -> null;
        };
    }

    private void runFfmpeg(String ffmpeg, List<String> args) throws IOException, InterruptedException {
        var command = new ArrayList<String>();
        command.add(ffmpeg);
        command.addAll(args);
        var process = new ProcessBuilder(command).redirectErrorStream(true).start();
        try (var reader = new BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
            while (reader.readLine() != null) {
                // drain
            }
        }
        if (process.waitFor() != 0) {
            throw new IllegalStateException("FFmpeg 执行失败: " + String.join(" ", command));
        }
    }

    private boolean copyLocalIfPresent(GenerationJob job, Path target) throws IOException {
        if (ossStorageService.isEnabled()) {
            return false;
        }
        if (!mediaStorageService.hasLocalFile(job.getId(), job.getMediaType())) {
            return false;
        }
        var root = Path.of(mediaStorageProperties.storagePath()).toAbsolutePath().normalize();
        var folder = job.getMediaType().name().toLowerCase(Locale.ROOT);
        var dir = root.resolve(folder);
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (var stream = Files.list(dir)) {
            var source = stream
                    .filter(path -> path.getFileName().toString().startsWith(job.getId().toString()))
                    .findFirst()
                    .orElse(null);
            if (source == null) {
                return false;
            }
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            return Files.size(target) > 0;
        }
    }

    private String resolveDownloadableUrl(GenerationJob job) {
        if (ossStorageService.isEnabled()) {
            var oss = ossStorageService.findGenerationPublicUrl(job.getId(), GenerationMediaType.VIDEO);
            if (oss != null && !oss.isBlank()) {
                return oss;
            }
        }
        var output = job.getOutputUrl();
        if (output != null && !output.isBlank() && !mediaStorageService.isLocalMediaUrl(output)) {
            return output;
        }
        if (output != null && !output.isBlank() && ossStorageService.isOurUrl(output)) {
            return output;
        }
        var provider = job.getProviderOutputUrl();
        if (provider != null && !provider.isBlank()) {
            return provider;
        }
        if (output != null && !output.isBlank()) {
            // 本地 /api/v1/media/{id}：拼接本机绝对地址
            if (output.startsWith("/")) {
                return "http://127.0.0.1:7051" + output;
            }
            return output;
        }
        return null;
    }

    private void downloadUrl(String url, Path target) throws IOException, InterruptedException {
        if (url.startsWith("data:")) {
            var comma = url.indexOf(',');
            if (comma > 0) {
                var bytes = java.util.Base64.getDecoder().decode(url.substring(comma + 1));
                Files.write(target, bytes);
            }
            return;
        }
        var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(180))
                .GET()
                .build();
        var response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() >= 400) {
            throw new IOException("HTTP " + response.statusCode());
        }
        try (InputStream in = response.body()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String findFfmpeg() {
        var fromPath = System.getenv("FFMPEG_PATH");
        if (fromPath != null && !fromPath.isBlank()) {
            return fromPath;
        }
        return "ffmpeg";
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

    private List<DramaForgeShot> resolveTimelineOrderedShots(DramaForgeEpisode episode) {
        var defaultOrder = shotRepository.findByEpisodeIdOrderByShotNumberAsc(episode.getId());
        var byId = new java.util.HashMap<UUID, DramaForgeShot>();
        for (var shot : defaultOrder) {
            byId.put(shot.getId(), shot);
        }
        var timelineJson = episode.getTimelineJson();
        if (timelineJson == null || timelineJson.isBlank()) {
            return defaultOrder;
        }
        try {
            JsonNode root = objectMapper.readTree(timelineJson);
            var clips = root.path("clips");
            if (!clips.isArray() || clips.isEmpty()) {
                return defaultOrder;
            }
            var ordered = new ArrayList<DramaForgeShot>();
            var seen = new java.util.HashSet<UUID>();
            var clipList = new ArrayList<JsonNode>();
            clips.forEach(clipList::add);
            clipList.sort(Comparator.comparingInt(n -> n.path("order").asInt(Integer.MAX_VALUE)));
            for (var clip : clipList) {
                var shotIdText = clip.path("shotId").asText(null);
                if (shotIdText == null || shotIdText.isBlank()) {
                    continue;
                }
                try {
                    var shotId = UUID.fromString(shotIdText);
                    var shot = byId.get(shotId);
                    if (shot != null && seen.add(shotId)) {
                        ordered.add(shot);
                    }
                } catch (Exception ignored) {
                }
            }
            for (var shot : defaultOrder) {
                if (!seen.contains(shot.getId())) {
                    ordered.add(shot);
                }
            }
            return ordered;
        } catch (Exception ex) {
            return defaultOrder;
        }
    }

    private void writeDialogueSrt(Path srtFile, List<DramaForgeShot> shots) throws IOException {
        var sb = new StringBuilder();
        int index = 1;
        double cursor = 0;
        for (var shot : shots) {
            var dialogue = shot.getDialogue();
            if (dialogue == null || dialogue.isBlank()) {
                cursor += Math.max(2, shot.getDurationSeconds() != null ? shot.getDurationSeconds() : 4);
                continue;
            }
            var duration = Math.max(2, shot.getDurationSeconds() != null ? shot.getDurationSeconds() : 4);
            sb.append(index++).append('\n');
            sb.append(formatSrtTime(cursor)).append(" --> ").append(formatSrtTime(cursor + duration)).append('\n');
            sb.append(dialogue.trim()).append("\n\n");
            cursor += duration;
        }
        if (!sb.isEmpty()) {
            Files.writeString(srtFile, sb.toString());
        }
    }

    private static String formatSrtTime(double seconds) {
        var totalMs = (long) (seconds * 1000);
        var h = totalMs / 3_600_000;
        var m = (totalMs % 3_600_000) / 60_000;
        var s = (totalMs % 60_000) / 1000;
        var ms = totalMs % 1000;
        return String.format(Locale.ROOT, "%02d:%02d:%02d,%03d", h, m, s, ms);
    }

    private static final class InMemoryMultipartFile implements org.springframework.web.multipart.MultipartFile {
        private final String name;
        private final String contentType;
        private final byte[] bytes;

        private InMemoryMultipartFile(String name, String contentType, byte[] bytes) {
            this.name = name;
            this.contentType = contentType;
            this.bytes = bytes;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getOriginalFilename() {
            return name;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return bytes.length == 0;
        }

        @Override
        public long getSize() {
            return bytes.length;
        }

        @Override
        public byte[] getBytes() {
            return bytes;
        }

        @Override
        public java.io.InputStream getInputStream() {
            return new java.io.ByteArrayInputStream(bytes);
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException {
            Files.write(dest.toPath(), bytes);
        }
    }
}
