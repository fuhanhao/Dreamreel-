package com.dreamreel.api.dramaforge.service;

import com.dreamreel.api.domain.GenerationStatus;
import com.dreamreel.api.dramaforge.domain.DramaForgeShot;
import com.dreamreel.api.dramaforge.domain.DramaForgeShotStatus;
import com.dreamreel.api.repository.GenerationJobRepository;
import com.dreamreel.api.service.MediaStorageService;
import com.dreamreel.api.service.OssStorageService;
import com.dreamreel.api.service.UploadStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
import java.util.Locale;
import java.util.UUID;

/**
 * 镜头成片音频后处理：保留 Seedance 原声，对人声频段 EQ/压限/轻度增益并 duck 环境音；
 * 不叠后期对白 TTS。字幕由 Seedance 生成时按提示词自绘，此处不再硬烧。
 */
@Service
public class DramaForgeShotAudioRemasterService {

    private static final Logger log = LoggerFactory.getLogger(DramaForgeShotAudioRemasterService.class);
    /** v13：Seedance 原声增强（字幕改由生成提示词交给模型自绘） */
    private static final String REMARKER = "seedance-voice-v13";
    private static final String LEGACY_REMARKER = "dialogue-clear";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private final GenerationJobRepository generationJobRepository;
    private final MediaStorageService mediaStorageService;
    private final UploadStorageService uploadStorageService;
    private final OssStorageService ossStorageService;

    public DramaForgeShotAudioRemasterService(
            GenerationJobRepository generationJobRepository,
            MediaStorageService mediaStorageService,
            UploadStorageService uploadStorageService,
            OssStorageService ossStorageService) {
        this.generationJobRepository = generationJobRepository;
        this.mediaStorageService = mediaStorageService;
        this.uploadStorageService = uploadStorageService;
        this.ossStorageService = ossStorageService;
    }

    public boolean remasterIfNeeded(DramaForgeShot shot) {
        return remasterIfNeeded(shot, false);
    }

    /**
     * @param force 为 true 时即使已重混也会从原片（providerOutputUrl）再处理一次
     * @return true 若本次完成了处理并更新了 outputUrl
     */
    public boolean remasterIfNeeded(DramaForgeShot shot, boolean force) {
        if (shot == null || shot.getStatus() != DramaForgeShotStatus.VIDEO_DONE) {
            return false;
        }
        if (shot.getVideoJobId() == null) {
            return false;
        }
        // 有对白才做原声增强；无对白保持 Seedance 原片
        // 字幕由生成提示词交给 Seedance 自绘，后处理不再硬烧
        var dialogue = shot.getDialogue();
        if (dialogue == null || dialogue.isBlank()) {
            return false;
        }

        var job = generationJobRepository.findById(shot.getVideoJobId()).orElse(null);
        if (job == null || job.getStatus() != GenerationStatus.COMPLETED) {
            return false;
        }

        var currentUrl = job.getOutputUrl();
        var providerUrl = job.getProviderOutputUrl();
        var currentCorrupt = isAudioOnlyUrl(currentUrl);
        if (!force && !currentCorrupt && currentUrl != null && currentUrl.contains(REMARKER)) {
            return false;
        }

        Path workDir = null;
        try {
            if (currentCorrupt && providerUrl != null && !providerUrl.isBlank()) {
                job.setOutputUrl(providerUrl);
                generationJobRepository.save(job);
            }

            // 先锁定 Seedance 原片到 providerOutputUrl，再解析源片；勿让 ensureStored 盖掉后处理结果
            var sourceUrl = resolveSourceVideoUrl(job);
            if (sourceUrl == null || sourceUrl.isBlank()) {
                return false;
            }
            if ((job.getProviderOutputUrl() == null || job.getProviderOutputUrl().isBlank())
                    && !isRemasteredUrl(sourceUrl)
                    && !isAudioOnlyUrl(sourceUrl)) {
                job.setProviderOutputUrl(sourceUrl);
                generationJobRepository.save(job);
            }

            workDir = Files.createTempDirectory("df-audio-remaster-");
            var videoPath = workDir.resolve("source.mp4");
            var enhancedPath = workDir.resolve("enhanced-audio.mp4");
            var outputPath = workDir.resolve("remastered.mp4");

            downloadUrl(sourceUrl, videoPath);
            DramaForgeDialogueAudioMixer.enhanceOriginalVoice(videoPath, enhancedPath);
            Files.copy(enhancedPath, outputPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("Seedance voice enhance shot {} (no hardsub burn)", shot.getShotNumber());

            var bytes = Files.readAllBytes(outputPath);
            if (bytes.length == 0) {
                throw new IllegalStateException("重混结果为空");
            }

            var fileName = "shot-" + shot.getShotNumber() + "-" + REMARKER + "-" + UUID.randomUUID() + ".mp4";
            String newUrl;
            if (ossStorageService.isEnabled()) {
                newUrl = ossStorageService.uploadNamed("generations/video", fileName, bytes, "video/mp4");
            } else {
                var uploaded = uploadStorageService.storeBytes(bytes, "video/mp4", fileName);
                newUrl = uploaded.url();
            }

            if (job.getProviderOutputUrl() == null || job.getProviderOutputUrl().isBlank()) {
                var original = sourceUrl;
                if (isRemasteredUrl(original)) {
                    original = null;
                }
                job.setProviderOutputUrl(isAudioOnlyUrl(original) ? null : original);
            }
            job.setOutputUrl(newUrl);
            generationJobRepository.save(job);
            log.info("Enhanced Seedance voice for shot {} -> {}", shot.getShotNumber(), newUrl);
            return true;
        } catch (Exception ex) {
            log.warn("Seedance voice enhance skipped for shot {}: {}", shot.getShotNumber(), ex.getMessage());
            return false;
        } finally {
            if (workDir != null) {
                deleteRecursive(workDir);
            }
        }
    }

    private String resolveSourceVideoUrl(com.dreamreel.api.domain.GenerationJob job) {
        var provider = job.getProviderOutputUrl();
        if (provider != null && !provider.isBlank() && !isAudioOnlyUrl(provider)
                && !isRemasteredUrl(provider)) {
            return provider;
        }
        var out = job.getOutputUrl();
        // 已后处理文件不能再当源（会叠加重增强）
        if (out != null && !out.isBlank() && !isAudioOnlyUrl(out) && !isRemasteredUrl(out)) {
            return out;
        }
        if (provider != null && !provider.isBlank() && !isAudioOnlyUrl(provider)) {
            return provider;
        }
        return mediaStorageService.findOssPublicUrl(job.getId(), job.getMediaType()).orElse(out);
    }

    private static boolean isRemasteredUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        return url.contains(LEGACY_REMARKER)
                || url.contains("seedance-voice")
                || url.contains(REMARKER);
    }

    private static boolean isAudioOnlyUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        var lower = url.toLowerCase(Locale.ROOT);
        return lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".ogg")
                || lower.contains(".mp3?") || lower.contains("/audio/");
    }

    private void downloadUrl(String url, Path target) throws IOException, InterruptedException {
        var resolved = url;
        if (resolved.startsWith("/")) {
            resolved = "http://127.0.0.1:7051" + resolved;
        }
        if (resolved.startsWith("data:")) {
            var comma = resolved.indexOf(',');
            if (comma > 0) {
                Files.write(target, java.util.Base64.getDecoder().decode(resolved.substring(comma + 1)));
            }
            return;
        }
        var request = HttpRequest.newBuilder()
                .uri(URI.create(resolved))
                .timeout(Duration.ofSeconds(180))
                .GET()
                .build();
        var response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() >= 400) {
            throw new IOException("HTTP " + response.statusCode() + " for " + resolved);
        }
        try (InputStream in = response.body()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteRecursive(Path root) {
        try (var walk = Files.walk(root)) {
            walk.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }
}
