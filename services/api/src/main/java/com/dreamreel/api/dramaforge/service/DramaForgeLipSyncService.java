package com.dreamreel.api.dramaforge.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Map;

/**
 * 可选口型同步：若配置了 lipSyncEndpoint，则把视频+对白音频 POST 到外部服务（如 LatentSync/MuseTalk 网关）。
 * 未配置时跳过，不阻断合成。
 */
@Service
public class DramaForgeLipSyncService {

    private static final Logger log = LoggerFactory.getLogger(DramaForgeLipSyncService.class);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private final RestClient restClient = RestClient.create();

    /**
     * @param videoUrl 可公网访问的视频 URL（非本地路径）
     * @return 处理后的本地视频路径；失败或未启用时返回 null
     */
    public Path maybeApplyLipSync(
            boolean enabled,
            String endpoint,
            String videoUrl,
            String audioUrl,
            Path workDir) {
        if (!enabled || endpoint == null || endpoint.isBlank()) {
            return null;
        }
        if (videoUrl == null || videoUrl.isBlank()) {
            return null;
        }
        if (audioUrl == null || audioUrl.isBlank()) {
            return null;
        }
        try {
            var body = Map.of(
                    "videoUrl", videoUrl,
                    "audioUrl", audioUrl,
                    "outputHint", workDir.resolve("lipsync-output.mp4").toString()
            );
            var response = restClient.post()
                    .uri(endpoint.trim())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            if (response == null) {
                return null;
            }
            var outUrl = response.get("outputUrl");
            if (outUrl == null) {
                outUrl = response.get("url");
            }
            if (outUrl == null || outUrl.toString().isBlank()) {
                log.warn("LipSync endpoint returned no outputUrl");
                return null;
            }
            var outStr = outUrl.toString();
            if (outStr.startsWith("http://") || outStr.startsWith("https://")) {
                var dest = workDir.resolve("lipsync-" + System.currentTimeMillis() + ".mp4");
                downloadHttp(outStr, dest);
                if (Files.exists(dest) && Files.size(dest) > 0) {
                    return dest;
                }
                log.warn("LipSync HTTP output download failed: {}", outStr);
                return null;
            }
            var out = Path.of(outStr);
            if (Files.exists(out) && Files.size(out) > 0) {
                return out;
            }
            log.warn("LipSync output not found on disk: {}", outUrl);
            return null;
        } catch (Exception ex) {
            log.warn("LipSync skipped: {}", ex.getMessage());
            return null;
        }
    }

    private void downloadHttp(String url, Path target) throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(180))
                .GET()
                .build();
        var response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        try (InputStream in = response.body()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
