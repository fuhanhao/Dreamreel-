package com.dreamreel.api.service;

import com.dreamreel.api.config.MediaStorageProperties;
import com.dreamreel.api.domain.GenerationJob;
import com.dreamreel.api.domain.GenerationMediaType;
import com.dreamreel.api.repository.GenerationJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class MediaStorageService {

    private static final Logger log = LoggerFactory.getLogger(MediaStorageService.class);
    private static final Pattern MEDIA_ID_PATTERN = Pattern.compile("/api/v1/media/([0-9a-fA-F-]{36})");

    private final MediaStorageProperties properties;
    private final RestClient mediaDownloadRestClient;
    private final GenerationJobRepository generationJobRepository;
    private final OssStorageService ossStorageService;

    public MediaStorageService(
            MediaStorageProperties properties,
            RestClient mediaDownloadRestClient,
            GenerationJobRepository generationJobRepository,
            OssStorageService ossStorageService) {
        this.properties = properties;
        this.mediaDownloadRestClient = mediaDownloadRestClient;
        this.generationJobRepository = generationJobRepository;
        this.ossStorageService = ossStorageService;
    }

    public void persistRemoteOutput(GenerationJob job, String remoteUrl) {
        if (remoteUrl == null || remoteUrl.isBlank()) {
            return;
        }
        if (job.getId() == null) {
            throw new IllegalStateException("生成任务必须先保存后再持久化输出文件");
        }

        job.setProviderOutputUrl(remoteUrl);

        // 已对白重混的成片不要被上游原片/jobId 规范文件盖回去
        if (isDialogueRemasteredUrl(job.getOutputUrl())) {
            clearTransferError(job);
            return;
        }

        if (hasStoredFile(job.getId(), job.getMediaType())) {
            job.setOutputUrl(resolveStoredPublicUrl(job.getId(), job.getMediaType(), job.getOutputUrl()));
            clearTransferError(job);
            return;
        }

        try {
            if (ossStorageService.isEnabled()) {
                var ossUrl = ossStorageService.transferRemoteGeneration(job.getId(), job.getMediaType(), remoteUrl);
                job.setOutputUrl(ossUrl);
            } else {
                download(job.getId(), job.getMediaType(), remoteUrl);
                job.setOutputUrl(buildLocalPublicUrl(job.getId()));
            }
            clearTransferError(job);
        } catch (Exception ex) {
            log.error("保存生成文件到 OSS/本地失败，暂保留远程地址 jobId={} url={} err={}",
                    job.getId(), remoteUrl, ex.toString(), ex);
            job.setOutputUrl(remoteUrl);
            // 标记错误但不改任务状态，便于下次 get/轮询时 ensureStoredOutput 再试转存
            if (job.getErrorMessage() == null || job.getErrorMessage().isBlank()) {
                job.setErrorMessage("媒体转存未完成: " + ex.getMessage());
            }
        }
    }

    private void clearTransferError(GenerationJob job) {
        var err = job.getErrorMessage();
        if (err != null && err.startsWith("媒体转存未完成")) {
            job.setErrorMessage(null);
        }
    }

    public boolean hasStoredFile(UUID jobId, GenerationMediaType mediaType) {
        if (ossStorageService.isEnabled()) {
            return ossStorageService.existsGeneration(jobId, mediaType);
        }
        return Files.exists(resolveLocalPath(jobId, mediaType));
    }

    public boolean hasLocalFile(UUID jobId, GenerationMediaType mediaType) {
        return hasStoredFile(jobId, mediaType);
    }

    public String buildPublicUrl(UUID jobId) {
        if (ossStorageService.isEnabled()) {
            var imageUrl = ossStorageService.findGenerationPublicUrl(jobId, GenerationMediaType.IMAGE);
            if (imageUrl != null) {
                return imageUrl;
            }
            var videoUrl = ossStorageService.findGenerationPublicUrl(jobId, GenerationMediaType.VIDEO);
            if (videoUrl != null) {
                return videoUrl;
            }
        }
        return buildLocalPublicUrl(jobId);
    }

    public String buildLocalPublicUrl(UUID jobId) {
        var base = properties.publicBaseUrl();
        if (base.endsWith("/")) {
            return base + jobId;
        }
        return base + "/" + jobId;
    }

    public boolean isLocalMediaUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        if (ossStorageService.isOurUrl(url)) {
            return true;
        }
        return url.startsWith(properties.publicBaseUrl()) || MEDIA_ID_PATTERN.matcher(url).find();
    }

    public String resolveProviderAccessibleUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return imageUrl;
        }
        if (ossStorageService.isOurUrl(imageUrl)) {
            return imageUrl;
        }
        if (!isLocalMediaUrl(imageUrl)) {
            return imageUrl;
        }

        var jobId = extractJobId(imageUrl);
        if (jobId == null) {
            return imageUrl;
        }

        return generationJobRepository.findById(jobId)
                .map(job -> {
                    if (job.getProviderOutputUrl() != null && !job.getProviderOutputUrl().isBlank()) {
                        return job.getProviderOutputUrl();
                    }
                    var outputUrl = job.getOutputUrl();
                    if (outputUrl != null && !isLocalMediaUrl(outputUrl)) {
                        return outputUrl;
                    }
                    if (outputUrl != null && ossStorageService.isOurUrl(outputUrl)) {
                        return outputUrl;
                    }
                    return imageUrl;
                })
                .orElse(imageUrl);
    }

    public Optional<StoredMedia> loadStoredMedia(UUID jobId) {
        if (ossStorageService.isEnabled()) {
            return Optional.empty();
        }

        for (var mediaType : GenerationMediaType.values()) {
            if (mediaType == GenerationMediaType.TEXT) {
                continue;
            }
            var path = resolveLocalPath(jobId, mediaType);
            if (Files.exists(path)) {
                return Optional.of(new StoredMedia(new FileSystemResource(path), contentTypeFor(path)));
            }
        }
        return Optional.empty();
    }

    public void deleteStoredOutput(GenerationJob job) {
        if (job.getId() == null || job.getMediaType() == GenerationMediaType.TEXT) {
            return;
        }

        if (ossStorageService.isEnabled()) {
            ossStorageService.deleteGeneration(job.getId(), job.getMediaType());
            return;
        }

        for (var ext : new String[] {"mp4", "webm", "mov", "png", "jpg", "jpeg", "webp", "gif"}) {
            var path = resolveStorageRoot()
                    .resolve(job.getMediaType().name().toLowerCase(Locale.ROOT))
                    .resolve(job.getId() + "." + ext);
            try {
                Files.deleteIfExists(path);
            } catch (IOException ex) {
                log.warn("删除本地生成文件失败 jobId={} path={}", job.getId(), path, ex);
            }
        }
    }

    public void ensureStoredOutput(GenerationJob job) {
        if (job.getId() == null || job.getMediaType() == GenerationMediaType.TEXT) {
            return;
        }

        var outputUrl = job.getOutputUrl();
        var corruptedOutput = outputUrl != null && outputUrl.contains("/null.");
        // 对白清晰化后的成片 URL 不在 jobId 规范路径上；不可被转存回写覆盖
        if (!corruptedOutput && isDialogueRemasteredUrl(outputUrl)) {
            return;
        }

        if (!corruptedOutput && hasStoredFile(job.getId(), job.getMediaType())) {
            job.setOutputUrl(resolveStoredPublicUrl(job.getId(), job.getMediaType(), outputUrl));
            return;
        }

        var remoteUrl = job.getProviderOutputUrl();
        if (remoteUrl == null || remoteUrl.isBlank()) {
            if (outputUrl != null && !outputUrl.isBlank() && !isLocalMediaUrl(outputUrl)) {
                remoteUrl = outputUrl;
            }
        }
        if (remoteUrl != null && !remoteUrl.isBlank()) {
            persistRemoteOutput(job, remoteUrl);
        }
    }

    public Optional<String> findOssPublicUrl(UUID jobId, GenerationMediaType mediaType) {
        if (!ossStorageService.isEnabled()) {
            return Optional.empty();
        }
        return Optional.ofNullable(ossStorageService.findGenerationPublicUrl(jobId, mediaType));
    }

    /** DramaForge 成片后处理产物（dialogue-clear* / seedance-voice*），勿用 jobId 规范转存 URL 覆盖。 */
    public static boolean isDialogueRemasteredUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        var lower = url.toLowerCase(Locale.ROOT);
        return lower.contains("dialogue-clear") || lower.contains("seedance-voice");
    }

    private String resolveStoredPublicUrl(UUID jobId, GenerationMediaType mediaType, String currentOutputUrl) {
        if (ossStorageService.isEnabled()) {
            var ossUrl = ossStorageService.findGenerationPublicUrl(jobId, mediaType);
            if (ossUrl != null) {
                return ossUrl;
            }
        }
        if (currentOutputUrl != null && !currentOutputUrl.isBlank()) {
            return currentOutputUrl;
        }
        return buildLocalPublicUrl(jobId);
    }

    private void download(UUID jobId, GenerationMediaType mediaType, String remoteUrl) throws IOException {
        ensureStorageRoot();
        var target = resolveLocalPath(jobId, mediaType, remoteUrl);

        mediaDownloadRestClient.get()
                .uri(URI.create(remoteUrl))
                .exchange((request, response) -> {
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        throw new IllegalStateException("下载失败，HTTP " + response.getStatusCode().value());
                    }
                    try (InputStream body = response.getBody()) {
                        if (body == null) {
                            throw new IllegalStateException("下载失败，响应体为空");
                        }
                        Files.copy(body, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                    return null;
                });

        log.info("已保存生成文件到本地 jobId={} path={}", jobId, target);
    }

    private Path resolveLocalPath(UUID jobId, GenerationMediaType mediaType) {
        var ext = mediaType == GenerationMediaType.VIDEO ? "mp4" : "png";
        return resolveStorageRoot()
                .resolve(mediaType.name().toLowerCase(Locale.ROOT))
                .resolve(jobId + "." + ext);
    }

    private Path resolveLocalPath(UUID jobId, GenerationMediaType mediaType, String remoteUrl) {
        var ext = extensionFromUrl(remoteUrl, mediaType);
        return resolveStorageRoot()
                .resolve(mediaType.name().toLowerCase(Locale.ROOT))
                .resolve(jobId + "." + ext);
    }

    private Path resolveStorageRoot() {
        return Path.of(properties.storagePath()).toAbsolutePath().normalize();
    }

    private void ensureStorageRoot() throws IOException {
        var root = resolveStorageRoot();
        Files.createDirectories(root.resolve("image"));
        Files.createDirectories(root.resolve("video"));
    }

    private String extensionFromUrl(String remoteUrl, GenerationMediaType mediaType) {
        try {
            var path = URI.create(remoteUrl).getPath();
            if (path != null) {
                var dot = path.lastIndexOf('.');
                if (dot >= 0 && dot < path.length() - 1) {
                    var ext = path.substring(dot + 1).toLowerCase(Locale.ROOT);
                    if (ext.matches("[a-z0-9]{2,5}")) {
                        return ext;
                    }
                }
            }
        } catch (Exception ignored) {
            // fall through
        }
        return mediaType == GenerationMediaType.VIDEO ? "mp4" : "png";
    }

    private MediaType contentTypeFor(Path path) {
        var fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".mp4")) {
            return MediaType.valueOf("video/mp4");
        }
        if (fileName.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        }
        if (fileName.endsWith(".webp")) {
            return MediaType.valueOf("image/webp");
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    private UUID extractJobId(String url) {
        var matcher = MEDIA_ID_PATTERN.matcher(url);
        if (!matcher.find()) {
            return null;
        }
        try {
            return UUID.fromString(matcher.group(1));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public record StoredMedia(Resource resource, MediaType contentType) {
    }
}
