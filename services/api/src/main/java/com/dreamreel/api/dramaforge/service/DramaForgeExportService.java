package com.dreamreel.api.dramaforge.service;

import com.dreamreel.api.dramaforge.domain.DramaForgeShot;
import com.dreamreel.api.dramaforge.domain.DramaForgeShotStatus;
import com.dreamreel.api.dramaforge.dto.DramaForgeDtos.ExportResponse;
import com.dreamreel.api.dramaforge.repository.*;
import com.dreamreel.api.config.MediaStorageProperties;
import com.dreamreel.api.domain.GenerationJob;
import com.dreamreel.api.domain.GenerationStatus;
import com.dreamreel.api.repository.GenerationJobRepository;
import com.dreamreel.api.repository.ProjectRepository;
import com.dreamreel.api.service.MediaStorageService;
import com.dreamreel.api.service.UploadStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@Transactional(readOnly = true)
public class DramaForgeExportService {

    private static final long SHOT_DURATION_US = 5_000_000L;

    private final DramaForgeConfigRepository configRepository;
    private final DramaForgeAssetRepository assetRepository;
    private final DramaForgeEpisodeRepository episodeRepository;
    private final DramaForgeShotRepository shotRepository;
    private final DramaForgeCompositionRepository compositionRepository;
    private final ProjectRepository projectRepository;
    private final GenerationJobRepository generationJobRepository;
    private final DramaForgeStatusCalculator statusCalculator;
    private final MediaStorageService mediaStorageService;
    private final MediaStorageProperties mediaStorageProperties;
    private final UploadStorageService uploadStorageService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public DramaForgeExportService(
            DramaForgeConfigRepository configRepository,
            DramaForgeAssetRepository assetRepository,
            DramaForgeEpisodeRepository episodeRepository,
            DramaForgeShotRepository shotRepository,
            DramaForgeCompositionRepository compositionRepository,
            ProjectRepository projectRepository,
            GenerationJobRepository generationJobRepository,
            DramaForgeStatusCalculator statusCalculator,
            MediaStorageService mediaStorageService,
            MediaStorageProperties mediaStorageProperties,
            UploadStorageService uploadStorageService,
            ObjectMapper objectMapper) {
        this.configRepository = configRepository;
        this.assetRepository = assetRepository;
        this.episodeRepository = episodeRepository;
        this.shotRepository = shotRepository;
        this.compositionRepository = compositionRepository;
        this.projectRepository = projectRepository;
        this.generationJobRepository = generationJobRepository;
        this.statusCalculator = statusCalculator;
        this.mediaStorageService = mediaStorageService;
        this.mediaStorageProperties = mediaStorageProperties;
        this.uploadStorageService = uploadStorageService;
        this.objectMapper = objectMapper;
    }

    public ExportResponse exportProjectZip(UUID projectId) throws IOException, InterruptedException {
        var project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalStateException("项目不存在"));
        var config = configRepository.findByProjectId(projectId).orElse(null);
        var assets = assetRepository.findByProjectIdOrderBySortOrderAscNameAsc(projectId);
        var episodes = episodeRepository.findByProjectIdOrderByEpisodeNumberAsc(projectId);
        var compositions = compositionRepository.findByProjectIdOrderByCreatedAtDesc(projectId);

        var manifest = objectMapper.createObjectNode();
        manifest.put("projectId", projectId.toString());
        manifest.put("projectName", project.getName());
        if (config != null) {
            manifest.set("config", objectMapper.valueToTree(config));
        }
        manifest.set("assets", objectMapper.valueToTree(assets));
        manifest.set("episodes", objectMapper.valueToTree(episodes));
        manifest.set("compositions", objectMapper.valueToTree(compositions));

        var shotsNode = objectMapper.createArrayNode();
        for (var episode : episodes) {
            var episodeShots = objectMapper.createObjectNode();
            episodeShots.put("episodeId", episode.getId().toString());
            episodeShots.put("title", episode.getTitle());
            var list = objectMapper.createArrayNode();
            for (var shot : shotRepository.findByEpisodeIdOrderByShotNumberAsc(episode.getId())) {
                list.add(objectMapper.valueToTree(toShotExport(shot)));
            }
            episodeShots.set("shots", list);
            shotsNode.add(episodeShots);
        }
        manifest.set("shotsByEpisode", shotsNode);

        var tempDir = Files.createTempDirectory("dramaforge-export-" + projectId);
        try {
            Files.writeString(tempDir.resolve("manifest.json"), objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(manifest));
            if (config != null && config.getSourceText() != null) {
                Files.writeString(tempDir.resolve("source.txt"), config.getSourceText());
            }
            var mediaDir = Files.createDirectory(tempDir.resolve("media"));
            copyMediaReference(mediaDir, "assets", assets.stream()
                    .map(a -> a.getReferenceImageUrl())
                    .filter(Objects::nonNull)
                    .toList());
            for (var episode : episodes) {
                var urls = shotRepository.findByEpisodeIdOrderByShotNumberAsc(episode.getId()).stream()
                        .flatMap(shot -> {
                            var list = new ArrayList<String>();
                            if (shot.getStoryboardUrl() != null) list.add(shot.getStoryboardUrl());
                            var videoUrl = statusCalculator.resolveVideoUrl(shot);
                            if (videoUrl != null) list.add(videoUrl);
                            return list.stream();
                        })
                        .toList();
                copyMediaReference(mediaDir, "episode-" + episode.getEpisodeNumber(), urls);
            }
            var zipBytes = zipDirectory(tempDir);
            var upload = uploadStorageService.store(new InMemoryMultipartFile(
                    sanitize(project.getName()) + "-export.zip",
                    "application/zip",
                    zipBytes
            ));
            return new ExportResponse("project_zip", upload.url(), "项目导出包已生成");
        } finally {
            deleteRecursive(tempDir);
        }
    }

    public ExportResponse exportJianyingDraft(UUID projectId, UUID episodeId) throws IOException, InterruptedException {
        var episode = episodeRepository.findByIdAndProjectId(episodeId, projectId)
                .orElseThrow(() -> new IllegalStateException("剧集不存在"));
        var shots = shotRepository.findByEpisodeIdOrderByShotNumberAsc(episodeId).stream()
                .filter(shot -> shot.getStatus() == DramaForgeShotStatus.VIDEO_DONE
                        || statusCalculator.resolveVideoUrl(shot) != null)
                .toList();
        if (shots.isEmpty()) {
            throw new IllegalStateException("没有可导出的视频镜头，请先生成视频");
        }

        var tempDir = Files.createTempDirectory("dramaforge-jianying-" + episodeId);
        try {
            var materialsDir = Files.createDirectory(tempDir.resolve("materials"));
            var videos = objectMapper.createArrayNode();
            var segments = objectMapper.createArrayNode();
            long timeline = 0;

            int index = 0;
            for (var shot : shots) {
                var materialId = UUID.randomUUID().toString();
                var fileName = "shot-" + shot.getShotNumber() + ".mp4";
                var localFile = materialsDir.resolve(fileName);
                if (!downloadShotVideo(shot, localFile)) {
                    continue;
                }
                var durationUs = resolveShotDurationUs(shot);
                var videoNode = objectMapper.createObjectNode();
                videoNode.put("id", materialId);
                videoNode.put("type", "video");
                videoNode.put("material_name", fileName);
                videoNode.put("path", "materials/" + fileName);
                videoNode.put("duration", durationUs);
                videos.add(videoNode);

                var segment = objectMapper.createObjectNode();
                segment.put("id", UUID.randomUUID().toString());
                segment.put("material_id", materialId);
                var target = objectMapper.createObjectNode();
                target.put("start", timeline);
                target.put("duration", durationUs);
                segment.set("target_timerange", target);
                var source = objectMapper.createObjectNode();
                source.put("start", 0);
                source.put("duration", durationUs);
                segment.set("source_timerange", source);
                segments.add(segment);
                timeline += durationUs;
                index++;
            }
            if (index == 0) {
                throw new IllegalStateException("无法下载视频文件，请确认视频已生成完成");
            }

            var draftContent = objectMapper.createObjectNode();
            var canvas = objectMapper.createObjectNode();
            canvas.put("width", 1920);
            canvas.put("height", 1080);
            canvas.put("ratio", "16:9");
            draftContent.set("canvas_config", canvas);
            draftContent.put("duration", timeline);
            var materials = objectMapper.createObjectNode();
            materials.set("videos", videos);
            materials.set("audios", objectMapper.createArrayNode());
            draftContent.set("materials", materials);
            var track = objectMapper.createObjectNode();
            track.put("id", UUID.randomUUID().toString());
            track.put("type", "video");
            track.set("segments", segments);
            var tracks = objectMapper.createArrayNode();
            tracks.add(track);
            draftContent.set("tracks", tracks);

            var meta = objectMapper.createObjectNode();
            meta.put("draft_name", episode.getTitle());
            meta.put("draft_id", UUID.randomUUID().toString());
            meta.put("tm_duration", timeline);

            Files.writeString(tempDir.resolve("draft_content.json"),
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(draftContent));
            Files.writeString(tempDir.resolve("draft_meta_info.json"),
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(meta));
            Files.writeString(tempDir.resolve("README.txt"), """
                    剪映草稿导出包
                    1. 解压到剪映草稿目录（或手动导入素材后按时间线排列）
                    2. materials/ 目录包含各镜头视频
                    3. draft_content.json 为简化时间线描述
                    """);

            var zipBytes = zipDirectory(tempDir);
            var upload = uploadStorageService.store(new InMemoryMultipartFile(
                    sanitize(episode.getTitle()) + "-jianying.zip",
                    "application/zip",
                    zipBytes
            ));
            return new ExportResponse("jianying_draft", upload.url(), "剪映草稿包已生成");
        } finally {
            deleteRecursive(tempDir);
        }
    }

    private Map<String, Object> toShotExport(DramaForgeShot shot) {
        var map = new LinkedHashMap<String, Object>();
        map.put("id", shot.getId().toString());
        map.put("shotNumber", shot.getShotNumber());
        map.put("description", shot.getDescription());
        map.put("storyboardUrl", shot.getStoryboardUrl());
        map.put("videoUrl", statusCalculator.resolveVideoUrl(shot));
        map.put("status", shot.getStatus().name().toLowerCase());
        return map;
    }

    private void copyMediaReference(Path mediaDir, String prefix, List<String> urls) throws IOException, InterruptedException {
        int i = 0;
        for (var url : urls) {
            if (url == null || url.isBlank()) continue;
            var ext = url.contains(".png") ? "png" : url.contains(".webp") ? "webp" : "mp4";
            var target = mediaDir.resolve(prefix + "-" + (++i) + "." + ext);
            downloadUrl(url, target);
        }
    }

    private long resolveShotDurationUs(DramaForgeShot shot) {
        if (shot.getDurationSeconds() != null && shot.getDurationSeconds() >= 2) {
            return shot.getDurationSeconds() * 1_000_000L;
        }
        return SHOT_DURATION_US;
    }

    private boolean downloadShotVideo(DramaForgeShot shot, Path target) throws IOException, InterruptedException {
        if (shot.getVideoJobId() != null) {
            var job = generationJobRepository.findById(shot.getVideoJobId()).orElse(null);
            if (job != null && copyLocalGeneration(job, target)) {
                return true;
            }
        }
        var url = statusCalculator.resolveVideoUrl(shot);
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            downloadUrl(url, target);
            return Files.exists(target) && Files.size(target) > 0;
        } catch (IOException ex) {
            return false;
        }
    }

    private boolean copyLocalGeneration(GenerationJob job, Path target) throws IOException, InterruptedException {
        if (job.getStatus() != GenerationStatus.COMPLETED) {
            return false;
        }
        if (mediaStorageService.hasLocalFile(job.getId(), job.getMediaType())) {
            var root = Path.of(mediaStorageProperties.storagePath()).toAbsolutePath().normalize();
            var folder = job.getMediaType().name().toLowerCase();
            try (var stream = Files.list(root.resolve(folder))) {
                var source = stream
                        .filter(path -> path.getFileName().toString().startsWith(job.getId().toString()))
                        .findFirst()
                        .orElse(null);
                if (source != null) {
                    Files.copy(source, target);
                    return true;
                }
            }
        }
        if (job.getOutputUrl() != null && !job.getOutputUrl().isBlank()) {
            downloadUrl(job.getOutputUrl(), target);
            return Files.exists(target);
        }
        return false;
    }

    private void downloadUrl(String url, Path target) throws IOException, InterruptedException {
        if (url.startsWith("data:")) {
            var comma = url.indexOf(',');
            if (comma > 0) {
                var bytes = Base64.getDecoder().decode(url.substring(comma + 1));
                Files.write(target, bytes);
            }
            return;
        }
        var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() >= 400) {
            throw new IOException("下载失败 HTTP " + response.statusCode());
        }
        try (InputStream in = response.body()) {
            Files.copy(in, target);
        }
    }

    private byte[] zipDirectory(Path dir) throws IOException {
        var buffer = new ByteArrayOutputStream();
        try (var zos = new ZipOutputStream(buffer)) {
            try (var stream = Files.walk(dir)) {
                for (var path : stream.filter(Files::isRegularFile).toList()) {
                    var entryName = dir.relativize(path).toString().replace("\\", "/");
                    zos.putNextEntry(new ZipEntry(entryName));
                    Files.copy(path, zos);
                    zos.closeEntry();
                }
            }
        }
        return buffer.toByteArray();
    }

    private void deleteRecursive(Path dir) {
        try {
            if (!Files.exists(dir)) return;
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

    private String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5_-]", "_");
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
        public InputStream getInputStream() {
            return new java.io.ByteArrayInputStream(bytes);
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException {
            Files.write(dest.toPath(), bytes);
        }
    }
}
