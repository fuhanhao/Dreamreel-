package com.dreamreel.api.service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.CannedAccessControlList;
import com.aliyun.oss.model.ObjectMetadata;
import com.dreamreel.api.config.OssProperties;
import com.dreamreel.api.domain.GenerationMediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Locale;
import java.util.UUID;

@Service
public class OssStorageService {

    private static final Logger log = LoggerFactory.getLogger(OssStorageService.class);

    private final OssProperties properties;
    private final OSS ossClient;
    private final RestClient mediaDownloadRestClient;

    public OssStorageService(
            OssProperties properties,
            ObjectProvider<OSS> ossClientProvider,
            RestClient mediaDownloadRestClient) {
        this.properties = properties;
        this.ossClient = ossClientProvider.getIfAvailable();
        this.mediaDownloadRestClient = mediaDownloadRestClient;
    }

    public boolean isEnabled() {
        return properties.enabled() && ossClient != null;
    }

    public boolean isOurUrl(String url) {
        if (!isEnabled() || url == null || url.isBlank()) {
            return false;
        }
        return url.contains(properties.bucket() + "." + properties.normalizedEndpoint())
                || url.contains("/" + properties.folderPrefix() + "/");
    }

    public String uploadGeneration(UUID jobId, GenerationMediaType mediaType, InputStream inputStream, String ext, String contentType) {
        var folder = mediaType == GenerationMediaType.VIDEO ? "video" : "image";
        try {
            return upload("generations/" + folder, jobId, inputStream.readAllBytes(), ext, contentType);
        } catch (IOException ex) {
            throw new IllegalStateException("读取上传流失败: " + ex.getMessage(), ex);
        }
    }

    public String uploadReference(UUID uploadId, InputStream inputStream, String ext, String contentType) {
        try {
            return upload("uploads", uploadId, inputStream.readAllBytes(), ext, contentType);
        } catch (IOException ex) {
            throw new IllegalStateException("读取上传流失败: " + ex.getMessage(), ex);
        }
    }

    /** 按自定义文件名上传（文件名可含标记，便于幂等判断） */
    public String uploadNamed(String category, String fileName, byte[] bytes, String contentType) {
        ensureEnabled();
        if (bytes == null || bytes.length == 0) {
            throw new IllegalStateException("上传内容为空");
        }
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        var key = properties.folderPrefix() + "/" + category + "/" + fileName;
        putPublicObject(key, bytes, contentType);
        var url = buildPublicUrl(key);
        log.info("已上传到 OSS key={} bytes={} url={}", key, bytes.length, url);
        return url;
    }

    public String transferRemoteGeneration(UUID jobId, GenerationMediaType mediaType, String remoteUrl) {
        var folder = mediaType == GenerationMediaType.VIDEO ? "video" : "image";
        var ext = extensionFromUrl(remoteUrl, mediaType == GenerationMediaType.VIDEO ? "mp4" : "png");

        return mediaDownloadRestClient.get()
                .uri(URI.create(remoteUrl))
                .exchange((request, response) -> {
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        throw new IllegalStateException("下载失败，HTTP " + response.getStatusCode().value());
                    }
                    try (InputStream body = response.getBody()) {
                        if (body == null) {
                            throw new IllegalStateException("下载失败，响应体为空");
                        }
                        // 必须先读到内存并设置 Content-Length；流式 putObject 对 TOS/OSS 常失败并静默回退成临时链
                        var bytes = body.readAllBytes();
                        if (bytes.length == 0) {
                            throw new IllegalStateException("下载失败，文件为空");
                        }
                        var contentType = response.getHeaders().getContentType() != null
                                ? response.getHeaders().getContentType().toString()
                                : guessContentType(ext);
                        return upload("generations/" + folder, jobId, bytes, ext, contentType);
                    }
                });
    }

    public boolean existsGeneration(UUID jobId, GenerationMediaType mediaType) {
        if (!isEnabled()) {
            return false;
        }
        var folder = mediaType == GenerationMediaType.VIDEO ? "video" : "image";
        for (var ext : generationExtensions(mediaType)) {
            if (ossClient.doesObjectExist(properties.bucket(), objectKey("generations/" + folder, jobId, ext))) {
                return true;
            }
        }
        return false;
    }

    public String findGenerationPublicUrl(UUID jobId, GenerationMediaType mediaType) {
        if (!isEnabled()) {
            return null;
        }
        var folder = mediaType == GenerationMediaType.VIDEO ? "video" : "image";
        for (var ext : generationExtensions(mediaType)) {
            var key = objectKey("generations/" + folder, jobId, ext);
            if (ossClient.doesObjectExist(properties.bucket(), key)) {
                return buildPublicUrl(key);
            }
        }
        return null;
    }

    public void deleteGeneration(UUID jobId, GenerationMediaType mediaType) {
        if (!isEnabled()) {
            return;
        }
        var folder = mediaType == GenerationMediaType.VIDEO ? "video" : "image";
        for (var ext : generationExtensions(mediaType)) {
            var key = objectKey("generations/" + folder, jobId, ext);
            if (ossClient.doesObjectExist(properties.bucket(), key)) {
                ossClient.deleteObject(properties.bucket(), key);
                log.info("已删除 OSS 对象 key={}", key);
            }
        }
    }

    public String findUploadPublicUrl(UUID uploadId) {
        if (!isEnabled()) {
            return null;
        }
        var prefix = properties.folderPrefix() + "/uploads/" + uploadId + ".";
        var listing = ossClient.listObjects(properties.bucket(), prefix);
        for (var summary : listing.getObjectSummaries()) {
            return buildPublicUrl(summary.getKey());
        }
        return null;
    }

    private String upload(String category, UUID id, byte[] bytes, String ext, String contentType) {
        ensureEnabled();
        if (bytes == null || bytes.length == 0) {
            throw new IllegalStateException("上传内容为空");
        }
        var key = objectKey(category, id, ext);
        putPublicObject(key, bytes, contentType);
        var url = buildPublicUrl(key);
        log.info("已上传到 OSS key={} bytes={} url={}", key, bytes.length, url);
        return url;
    }

    /** 上传并以 PublicRead 可读；元数据 ACL + 二次 setObjectAcl，避免浏览器 403。 */
    private void putPublicObject(String key, byte[] bytes, String contentType) {
        var metadata = new ObjectMetadata();
        metadata.setContentType(contentType);
        metadata.setContentLength(bytes.length);
        metadata.setObjectAcl(CannedAccessControlList.PublicRead);
        ossClient.putObject(properties.bucket(), key, new java.io.ByteArrayInputStream(bytes), metadata);
        try {
            ossClient.setObjectAcl(properties.bucket(), key, CannedAccessControlList.PublicRead);
        } catch (Exception ex) {
            // 元数据已带 ACL 时二次设置偶发失败可忽略；若对象仍私有则播放会 403
            log.warn("二次设置 OSS 对象 ACL 失败 key={}（put 时已声明 PublicRead）", key, ex);
        }
    }

    private String upload(String category, UUID id, InputStream inputStream, String ext, String contentType) {
        try {
            return upload(category, id, inputStream.readAllBytes(), ext, contentType);
        } catch (IOException ex) {
            throw new IllegalStateException("读取上传流失败: " + ex.getMessage(), ex);
        }
    }

    private String objectKey(String category, UUID id, String ext) {
        return properties.folderPrefix() + "/" + category + "/" + id + "." + ext;
    }

    public String buildPublicUrl(String objectKey) {
        return "https://" + properties.bucket() + "." + properties.normalizedEndpoint() + "/" + objectKey;
    }

    private String[] generationExtensions(GenerationMediaType mediaType) {
        return mediaType == GenerationMediaType.VIDEO
                ? new String[] {"mp4", "webm", "mov"}
                : new String[] {"png", "jpg", "jpeg", "webp", "gif"};
    }

    private String extensionFromUrl(String remoteUrl, String fallback) {
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
        return fallback;
    }

    private String guessContentType(String ext) {
        return switch (ext) {
            case "mp4" -> "video/mp4";
            case "webm" -> "video/webm";
            case "mov" -> "video/quicktime";
            case "jpg", "jpeg" -> "image/jpeg";
            case "webp" -> "image/webp";
            case "gif" -> "image/gif";
            default -> "image/png";
        };
    }

    private void ensureEnabled() {
        if (!isEnabled()) {
            throw new IllegalStateException("OSS 未启用");
        }
    }
}
