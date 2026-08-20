package com.dreamreel.api.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.dreamreel.api.config.ArkApiKeyContext;
import com.dreamreel.api.config.ArkApiKeyResolver;
import com.dreamreel.api.config.ArkProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 火山方舟 Seedance 视频生成：POST/GET /api/v3/contents/generations/tasks
 */
@Component
public class ArkSeedanceClient {

    private static final Map<String, String> MODEL_ALIASES = Map.ofEntries(
            Map.entry("bytedance/seedance-2.5", "doubao-seedance-2-5-260628"),
            Map.entry("seedance-2.5", "doubao-seedance-2-5-260628"),
            Map.entry("seedance-2-5", "doubao-seedance-2-5-260628"),
            Map.entry("bytedance/seedance-2", "doubao-seedance-2-0-260128"),
            Map.entry("seedance-2", "doubao-seedance-2-0-260128"),
            Map.entry("seedance-2.0", "doubao-seedance-2-0-260128"),
            Map.entry("bytedance/seedance-2-fast", "doubao-seedance-2-0-fast-260128"),
            Map.entry("seedance-2-fast", "doubao-seedance-2-0-fast-260128")
    );

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ArkProperties properties;
    private final ArkApiKeyResolver apiKeyResolver;

    public ArkSeedanceClient(
            @Qualifier("arkRestClient") RestClient arkRestClient,
            ObjectMapper objectMapper,
            ArkProperties properties,
            ArkApiKeyResolver apiKeyResolver) {
        this.restClient = arkRestClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.apiKeyResolver = apiKeyResolver;
    }

    public ArkVideoTask createVideoTask(CreateVideoPayload payload) {
        requireApiKey();
        var body = buildCreateBody(payload);
        var response = postForJson("/api/v3/contents/generations/tasks", body);
        return parseTask(response);
    }

    public ArkVideoTask getVideoTask(String taskId) {
        requireApiKey();
        var response = restClient.get()
                .uri("/api/v3/contents/generations/tasks/{id}", taskId)
                .header("Authorization", bearer())
                .exchange((request, httpResponse) ->
                        readResponse(httpResponse.getBody(), httpResponse.getStatusCode().value()));
        return parseTask(response);
    }

    public List<VideoModel> listModels() {
        var models = new ArrayList<VideoModel>();
        var seen = new java.util.LinkedHashSet<String>();
        for (var id : List.of(
                resolveModel(null),
                properties.fastModel(),
                "doubao-seedance-2-5-260628",
                "doubao-seedance-2-0-260128",
                "doubao-seedance-2-0-fast-260128",
                "bytedance/seedance-2.5",
                "bytedance/seedance-2",
                "bytedance/seedance-2-fast")) {
            if (id == null || id.isBlank() || !seen.add(id.trim())) {
                continue;
            }
            models.add(new VideoModel(id.trim(), "bytedance"));
        }
        return models;
    }

    private ObjectNode buildCreateBody(CreateVideoPayload payload) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", resolveModel(payload.model()));

        ArrayNode content = body.putArray("content");
        ObjectNode textItem = content.addObject();
        textItem.put("type", "text");
        textItem.put("text", buildPrompt(payload));

        var imageUrls = normalizeUrls(payload.imageUrls(), payload.imageUrl());
        for (var url : imageUrls) {
            ObjectNode item = content.addObject();
            item.put("type", "image_url");
            item.putObject("image_url").put("url", url);
            item.put("role", "reference_image");
        }

        if (payload.videoUrl() != null && !payload.videoUrl().isBlank()) {
            ObjectNode item = content.addObject();
            item.put("type", "video_url");
            item.putObject("video_url").put("url", payload.videoUrl().trim());
            item.put("role", "reference_video");
        }

        var audioUrls = normalizeUrls(payload.audioUrls(), null);
        // Seedance r2v：参考音频合计 ≤15.2s、最多 3 条；保守只传 1 条音色样本
        if (audioUrls.size() > 1) {
            audioUrls = audioUrls.subList(0, 1);
        }
        for (var url : audioUrls) {
            ObjectNode item = content.addObject();
            item.put("type", "audio_url");
            item.putObject("audio_url").put("url", url);
            item.put("role", "reference_audio");
        }

        body.put("generate_audio", true);
        body.put("watermark", properties.watermark());
        body.put("duration", Math.max(2, Math.min(15, payload.seconds())));

        if (payload.ratio() != null && !payload.ratio().isBlank()) {
            body.put("ratio", payload.ratio().trim());
        }
        if (payload.resolution() != null && !payload.resolution().isBlank()) {
            body.put("resolution", payload.resolution().trim());
        }

        return body;
    }

    private String buildPrompt(CreateVideoPayload payload) {
        var prompt = payload.prompt() != null ? payload.prompt().trim() : "";
        if (prompt.contains("@Image1") || prompt.contains("@Audio1") || prompt.contains("图片1")) {
            return prompt;
        }
        var imageCount = normalizeUrls(payload.imageUrls(), payload.imageUrl()).size();
        var audioCount = normalizeUrls(payload.audioUrls(), null).size();
        if (imageCount > 0) {
            var lead = imageCount == 1
                    ? "Strictly match @Image1 appearance. "
                    : "Strictly match @Image1..@Image" + imageCount + " for character/scene/prop consistency. ";
            return lead + prompt;
        }
        if (audioCount > 0) {
            return "Match @Audio1 voice tone. " + prompt;
        }
        return prompt;
    }

    private String resolveModel(String requested) {
        if (requested != null && !requested.isBlank()) {
            var key = requested.trim().toLowerCase(Locale.ROOT);
            if (MODEL_ALIASES.containsKey(key)) {
                return MODEL_ALIASES.get(key);
            }
            if (MODEL_ALIASES.containsKey(requested.trim())) {
                return MODEL_ALIASES.get(requested.trim());
            }
            // 已是方舟模型 ID / 接入点 ID
            if (requested.contains("seedance") || requested.startsWith("ep-") || requested.startsWith("doubao-")) {
                return requested.trim();
            }
            var aliased = MODEL_ALIASES.get(key);
            if (aliased != null) {
                return aliased;
            }
            return requested.trim();
        }
        var configured = properties.defaultModel();
        return configured != null && !configured.isBlank()
                ? configured
                : "doubao-seedance-2-5-260628";
    }

    private List<String> normalizeUrls(List<String> urls, String single) {
        var out = new ArrayList<String>();
        if (urls != null) {
            for (var url : urls) {
                if (url != null && !url.isBlank() && !out.contains(url.trim())) {
                    out.add(url.trim());
                }
            }
        }
        if (single != null && !single.isBlank()) {
            var trimmed = single.trim();
            out.remove(trimmed);
            out.add(0, trimmed);
        }
        return out;
    }

    private void requireApiKey() {
        if (!apiKeyResolver.isConfigured(ArkApiKeyContext.get(), null)) {
            throw new IllegalStateException(
                    "请配置火山方舟 / Seedance API Key（前端设置，或环境变量 ARK_API_KEY）");
        }
    }

    private String bearer() {
        var key = apiKeyResolver.resolve(ArkApiKeyContext.get(), null);
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                    "请配置火山方舟 / Seedance API Key（前端设置，或环境变量 ARK_API_KEY）");
        }
        return "Bearer " + key;
    }

    private JsonNode postForJson(String uri, ObjectNode body) {
        ResourceAccessException lastIo = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                return restClient.post()
                        .uri(uri)
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .exchange((request, response) ->
                                readResponse(response.getBody(), response.getStatusCode().value()));
            } catch (ResourceAccessException ex) {
                lastIo = ex;
                if (attempt < 3) {
                    try {
                        Thread.sleep(2000L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw ex;
                    }
                }
            }
        }
        throw new IllegalStateException(
                "火山方舟网络请求失败（已重试 3 次）: " + (lastIo != null ? lastIo.getMessage() : "unknown"));
    }

    private JsonNode readResponse(InputStream bodyStream, int statusCode) throws IOException {
        if (bodyStream == null) {
            return errorNode("火山方舟返回空响应 (HTTP " + statusCode + ")");
        }
        var tree = objectMapper.readTree(bodyStream);
        if (statusCode >= 400) {
            var error = extractError(tree);
            if (error == null) {
                error = "火山方舟请求失败 (HTTP " + statusCode + ")";
            }
            return errorNode(error);
        }
        return tree;
    }

    private JsonNode errorNode(String message) {
        var wrapper = objectMapper.createObjectNode();
        var error = objectMapper.createObjectNode();
        error.put("message", message);
        wrapper.set("error", error);
        return wrapper;
    }

    private ArkVideoTask parseTask(JsonNode response) {
        if (response == null) {
            throw new IllegalStateException("火山方舟返回空响应");
        }
        var error = extractError(response);
        if (error != null) {
            return new ArkVideoTask(firstText(response, "id"), "failed", 0, null, error);
        }

        var id = firstText(response, "id");
        var status = firstText(response, "status");
        if (status == null || status.isBlank()) {
            // 创建任务成功时通常只有 id
            status = id != null ? "queued" : "failed";
        }

        String outputUrl = null;
        var content = response.get("content");
        if (content != null && !content.isNull()) {
            outputUrl = firstText(content, "video_url");
        }
        if (outputUrl == null) {
            outputUrl = firstText(response, "video_url");
        }

        Integer progress = switch (status.toLowerCase(Locale.ROOT)) {
            case "succeeded", "success", "completed" -> 100;
            case "running", "processing", "in_progress" -> 55;
            case "failed", "error", "cancelled", "expired" -> 0;
            default -> 10;
        };

        String errMsg = extractError(response);
        if ((errMsg == null || errMsg.isBlank())
                && status != null
                && List.of("failed", "error", "cancelled", "expired").contains(status.toLowerCase(Locale.ROOT))) {
            errMsg = firstText(response, "failure_reason");
            if (errMsg == null) {
                errMsg = firstText(response, "fail_reason");
            }
            if (errMsg == null) {
                errMsg = firstText(response, "reason");
            }
            if (errMsg == null && response.get("content") != null && response.get("content").isObject()) {
                errMsg = firstText(response.get("content"), "failure_reason");
                if (errMsg == null) {
                    errMsg = firstText(response.get("content"), "message");
                }
            }
            if (errMsg == null || errMsg.isBlank()) {
                errMsg = "视频生成失败（上游状态: " + status + "，未返回详细原因）";
            }
        }

        return new ArkVideoTask(id, status, progress, outputUrl, errMsg);
    }

    private String extractError(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (node.has("error")) {
            var err = node.get("error");
            if (err.isTextual()) {
                return err.asText();
            }
            if (err.isObject()) {
                var msg = firstText(err, "message");
                if (msg == null) {
                    msg = firstText(err, "msg");
                }
                if (msg == null) {
                    msg = firstText(err, "detail");
                }
                if (msg == null) {
                    msg = firstText(err, "description");
                }
                var code = firstText(err, "code");
                if (code == null) {
                    code = firstText(err, "type");
                }
                if (msg != null && code != null) {
                    return code + ": " + msg;
                }
                return msg != null ? msg : err.toString();
            }
        }
        var content = node.get("content");
        if (content != null && content.isObject() && content.has("error")) {
            var nested = extractError(content);
            if (nested != null && !nested.isBlank()) {
                return nested;
            }
        }
        // 仅在明确失败态时读取顶层 message，避免误伤排队中的正常字段
        var status = firstText(node, "status");
        if (status != null && List.of("failed", "error", "cancelled", "expired")
                .contains(status.toLowerCase(Locale.ROOT))) {
            var msg = firstText(node, "message");
            if (msg == null) {
                msg = firstText(node, "msg");
            }
            if (msg == null) {
                msg = firstText(node, "failure_reason");
            }
            if (msg == null) {
                msg = firstText(node, "fail_reason");
            }
            return msg;
        }
        return null;
    }

    private String firstText(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        var v = node.get(field).asText();
        return v != null && !v.isBlank() ? v : null;
    }

    public record CreateVideoPayload(
            String model,
            String prompt,
            int seconds,
            String ratio,
            String mode,
            String imageUrl,
            String videoUrl,
            List<String> imageUrls,
            List<String> audioUrls,
            String resolution
    ) {}

    public record ArkVideoTask(
            String taskId,
            String status,
            Integer progress,
            String outputUrl,
            String errorMessage
    ) {}

    public record VideoModel(String id, String provider) {}
}
