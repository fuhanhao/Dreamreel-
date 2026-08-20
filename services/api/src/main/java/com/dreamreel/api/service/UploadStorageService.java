package com.dreamreel.api.service;

import com.dreamreel.api.config.MediaStorageProperties;
import com.dreamreel.api.domain.GenerationJob;
import com.dreamreel.api.domain.GenerationMediaType;
import com.dreamreel.api.exception.ResourceNotFoundException;
import com.dreamreel.api.repository.GenerationJobRepository;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class UploadStorageService {

    private static final Pattern UPLOAD_ID_PATTERN = Pattern.compile("/api/v1/uploads/([0-9a-fA-F-]{36})");
    private static final Pattern MEDIA_ID_PATTERN = Pattern.compile("/api/v1/media/([0-9a-fA-F-]{36})");
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );
    private static final Set<String> ALLOWED_VIDEO_TYPES = Set.of(
            "video/mp4", "video/webm", "video/quicktime"
    );
    private static final Set<String> ALLOWED_AUDIO_TYPES = Set.of(
            "audio/mpeg", "audio/mp3", "audio/wav", "audio/x-wav", "audio/webm", "audio/ogg"
    );

    private final MediaStorageProperties properties;
    private final GenerationJobRepository generationJobRepository;
    private final MediaStorageService mediaStorageService;
    private final OssStorageService ossStorageService;

    public UploadStorageService(
            MediaStorageProperties properties,
            GenerationJobRepository generationJobRepository,
            MediaStorageService mediaStorageService,
            OssStorageService ossStorageService) {
        this.properties = properties;
        this.generationJobRepository = generationJobRepository;
        this.mediaStorageService = mediaStorageService;
        this.ossStorageService = ossStorageService;
    }

    public UploadResult store(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的文件");
        }

        var contentType = normalizeContentType(file.getContentType());
        if (!ALLOWED_IMAGE_TYPES.contains(contentType)
                && !ALLOWED_VIDEO_TYPES.contains(contentType)
                && !ALLOWED_AUDIO_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("仅支持 JPG / PNG / WebP / GIF / MP4 / WebM / MP3 / WAV 文件");
        }
        if (file.getSize() > 100 * 1024 * 1024) {
            throw new IllegalArgumentException("文件大小不能超过 100MB");
        }

        var id = UUID.randomUUID();
        var ext = extensionFor(contentType, file.getOriginalFilename());

        if (ossStorageService.isEnabled()) {
            var ossUrl = ossStorageService.uploadReference(id, file.getInputStream(), ext, contentType);
            return new UploadResult(id, ossUrl, contentType, file.getOriginalFilename());
        }

        ensureUploadRoot();
        var target = uploadRoot().resolve(id + "." + ext);
        file.transferTo(target);
        return new UploadResult(id, buildLocalPublicUrl(id), contentType, file.getOriginalFilename());
    }

    /**
     * 持久化字节（对白 TTS / 成片尾帧 jpg / 重混成片等）。
     * 支持图片、音频、视频 MIME；未知类型时按文件名推断，不再默认成 audio/mpeg。
     */
    public UploadResult storeBytes(byte[] bytes, String contentType, String originalFilename) throws IOException {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("上传内容为空");
        }
        var normalized = resolveStoreBytesContentType(contentType, originalFilename);
        if (!ALLOWED_IMAGE_TYPES.contains(normalized)
                && !ALLOWED_VIDEO_TYPES.contains(normalized)
                && !ALLOWED_AUDIO_TYPES.contains(normalized)) {
            throw new IllegalArgumentException(
                    "storeBytes 仅支持 JPG / PNG / WebP / GIF / MP4 / WebM / MP3 / WAV，收到: " + normalized);
        }
        if (bytes.length > 100 * 1024 * 1024) {
            throw new IllegalArgumentException("文件大小不能超过 100MB");
        }
        var id = UUID.randomUUID();
        var ext = extensionFor(normalized, originalFilename);
        if (ossStorageService.isEnabled()) {
            var ossUrl = ossStorageService.uploadReference(
                    id, new java.io.ByteArrayInputStream(bytes), ext, normalized);
            return new UploadResult(id, ossUrl, normalized, originalFilename);
        }
        ensureUploadRoot();
        var target = uploadRoot().resolve(id + "." + ext);
        Files.write(target, bytes);
        return new UploadResult(id, buildLocalPublicUrl(id), normalized, originalFilename);
    }

    /** 规范化 MIME；缺省或 octet-stream 时按扩展名推断（避免尾帧 jpg 被误标成 mp3）。 */
    private String resolveStoreBytesContentType(String contentType, String originalFilename) {
        var normalized = normalizeContentType(contentType);
        if (ALLOWED_IMAGE_TYPES.contains(normalized)
                || ALLOWED_VIDEO_TYPES.contains(normalized)
                || ALLOWED_AUDIO_TYPES.contains(normalized)) {
            return normalized;
        }
        var name = originalFilename != null ? originalFilename.toLowerCase(Locale.ROOT) : "";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (name.endsWith(".png")) {
            return "image/png";
        }
        if (name.endsWith(".webp")) {
            return "image/webp";
        }
        if (name.endsWith(".gif")) {
            return "image/gif";
        }
        if (name.endsWith(".mp4") || name.endsWith(".mov")) {
            return name.endsWith(".mov") ? "video/quicktime" : "video/mp4";
        }
        if (name.endsWith(".webm")) {
            return "video/webm";
        }
        if (name.endsWith(".mp3")) {
            return "audio/mpeg";
        }
        if (name.endsWith(".wav")) {
            return "audio/wav";
        }
        if (name.endsWith(".ogg")) {
            return "audio/ogg";
        }
        // 无可靠线索时保持原值，由上层校验拦截
        return normalized;
    }

    public Optional<StoredUpload> load(UUID id) {
        if (ossStorageService.isEnabled()) {
            return Optional.empty();
        }
        var path = findUploadPath(id);
        if (path == null) {
            return Optional.empty();
        }
        return Optional.of(new StoredUpload(new FileSystemResource(path), contentTypeFor(path)));
    }

    public Optional<String> findOssPublicUrl(UUID id) {
        if (!ossStorageService.isEnabled()) {
            return Optional.empty();
        }
        return Optional.ofNullable(ossStorageService.findUploadPublicUrl(id));
    }

    public String resolveForProvider(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }

        if (ossStorageService.isOurUrl(url)) {
            return url;
        }

        if (isRemoteProviderUrl(url)) {
            return url;
        }

        var uploadPath = resolveUploadPath(url);
        if (uploadPath != null && Files.exists(uploadPath)) {
            return toDataUrl(uploadPath);
        }

        var mediaJobId = extractId(url, MEDIA_ID_PATTERN);
        if (mediaJobId != null) {
            return generationJobRepository.findById(mediaJobId)
                    .map(this::resolveGenerationJobReference)
                    .orElse(url);
        }

        return url;
    }

    public boolean isLocalUploadUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        if (ossStorageService.isOurUrl(url)) {
            return true;
        }
        return url.startsWith(properties.uploadPublicBaseUrl()) || UPLOAD_ID_PATTERN.matcher(url).find();
    }

    public String buildLocalPublicUrl(UUID id) {
        var base = properties.uploadPublicBaseUrl();
        if (base.endsWith("/")) {
            return base + id;
        }
        return base + "/" + id;
    }

    private String resolveGenerationJobReference(GenerationJob job) {
        if (job.getProviderOutputUrl() != null && !job.getProviderOutputUrl().isBlank()) {
            return job.getProviderOutputUrl();
        }
        if (job.getOutputUrl() != null && ossStorageService.isOurUrl(job.getOutputUrl())) {
            return job.getOutputUrl();
        }
        if (mediaStorageService.hasStoredFile(job.getId(), job.getMediaType())) {
            var ossUrl = ossStorageService.findGenerationPublicUrl(job.getId(), job.getMediaType());
            if (ossUrl != null) {
                return ossUrl;
            }
            var path = localGenerationPath(job);
            if (path != null && Files.exists(path)) {
                return toDataUrl(path);
            }
        }
        var outputUrl = job.getOutputUrl();
        if (outputUrl != null && !outputUrl.isBlank() && isRemoteProviderUrl(outputUrl)) {
            return outputUrl;
        }
        return job.getOutputUrl();
    }

    private Path localGenerationPath(GenerationJob job) {
        var root = Path.of(properties.storagePath()).toAbsolutePath().normalize();
        var folder = job.getMediaType().name().toLowerCase(Locale.ROOT);
        var dir = root.resolve(folder);
        if (!Files.isDirectory(dir)) {
            return null;
        }
        try (var stream = Files.list(dir)) {
            return stream
                    .filter(path -> path.getFileName().toString().startsWith(job.getId().toString() + "."))
                    .findFirst()
                    .orElse(null);
        } catch (IOException ex) {
            return null;
        }
    }

    private Path resolveUploadPath(String url) {
        var id = extractId(url, UPLOAD_ID_PATTERN);
        if (id == null) {
            return null;
        }
        return findUploadPath(id);
    }

    private Path findUploadPath(UUID id) {
        ensureUploadRoot();
        try (var stream = Files.list(uploadRoot())) {
            return stream
                    .filter(path -> path.getFileName().toString().startsWith(id.toString() + "."))
                    .findFirst()
                    .orElse(null);
        } catch (IOException ex) {
            return null;
        }
    }

    private Path uploadRoot() {
        return Path.of(properties.uploadPath()).toAbsolutePath().normalize();
    }

    private void ensureUploadRoot() {
        try {
            Files.createDirectories(uploadRoot());
        } catch (IOException ex) {
            throw new IllegalStateException("无法创建上传目录", ex);
        }
    }

    private boolean isRemoteProviderUrl(String url) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return false;
        }
        return !url.contains("localhost") && !url.contains("127.0.0.1");
    }

    private String toDataUrl(Path path) {
        try {
            var bytes = Files.readAllBytes(path);
            var mime = contentTypeFor(path).toString();
            return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (IOException ex) {
            throw new IllegalStateException("读取参考文件失败: " + path, ex);
        }
    }

    private UUID extractId(String url, Pattern pattern) {
        var matcher = pattern.matcher(url);
        if (!matcher.find()) {
            return null;
        }
        try {
            return UUID.fromString(matcher.group(1));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        return contentType.toLowerCase(Locale.ROOT);
    }

    private String extensionFor(String contentType, String originalName) {
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            case "video/mp4" -> "mp4";
            case "video/webm" -> "webm";
            case "video/quicktime" -> "mov";
            case "audio/mpeg", "audio/mp3" -> "mp3";
            case "audio/wav", "audio/x-wav" -> "wav";
            case "audio/webm" -> "webm";
            case "audio/ogg" -> "ogg";
            default -> {
                if (originalName != null && originalName.contains(".")) {
                    yield originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
                }
                yield "bin";
            }
        };
    }

    private MediaType contentTypeFor(Path path) {
        var fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        }
        if (fileName.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (fileName.endsWith(".webp")) {
            return MediaType.valueOf("image/webp");
        }
        if (fileName.endsWith(".gif")) {
            return MediaType.IMAGE_GIF;
        }
        if (fileName.endsWith(".mp4")) {
            return MediaType.valueOf("video/mp4");
        }
        if (fileName.endsWith(".webm")) {
            return MediaType.valueOf("video/webm");
        }
        if (fileName.endsWith(".mov")) {
            return MediaType.valueOf("video/quicktime");
        }
        if (fileName.endsWith(".mp3")) {
            return MediaType.valueOf("audio/mpeg");
        }
        if (fileName.endsWith(".wav")) {
            return MediaType.valueOf("audio/wav");
        }
        if (fileName.endsWith(".ogg")) {
            return MediaType.valueOf("audio/ogg");
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    public StoredUpload require(UUID id) {
        return load(id).orElseThrow(() -> new ResourceNotFoundException("上传文件不存在: " + id));
    }

    public record UploadResult(UUID id, String url, String contentType, String originalFilename) {
    }

    public record StoredUpload(Resource resource, MediaType contentType) {
    }
}
