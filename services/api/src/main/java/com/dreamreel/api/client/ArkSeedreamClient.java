package com.dreamreel.api.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.dreamreel.api.config.ArkApiKeyContext;
import com.dreamreel.api.config.ArkApiKeyResolver;
import com.dreamreel.api.config.ArkProperties;
import com.dreamreel.api.util.MediaSizeHelper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 火山方舟 Seedream 图片生成：POST /api/v3/images/generations。
 */
@Component
public class ArkSeedreamClient {

    private static final String DEFAULT_MODEL = "doubao-seedream-5-0-260128";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ArkProperties properties;
    private final ArkApiKeyResolver apiKeyResolver;

    public ArkSeedreamClient(
            @Qualifier("arkRestClient") RestClient arkRestClient,
            ObjectMapper objectMapper,
            ArkProperties properties,
            ArkApiKeyResolver apiKeyResolver) {
        this.restClient = arkRestClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.apiKeyResolver = apiKeyResolver;
    }

    public ArkImageResult createImage(CreateImagePayload payload) {
        var body = buildBody(payload);
        var response = restClient.post()
                .uri("/api/v3/images/generations")
                .header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((request, httpResponse) ->
                        readResponse(httpResponse.getBody(), httpResponse.getStatusCode().value()));
        return parseResult(response);
    }

    private ObjectNode buildBody(CreateImagePayload payload) {
        var body = objectMapper.createObjectNode();
        body.put("model", resolveModel(payload.model()));
        body.put("prompt", payload.prompt());
        body.put("size", "2K");
        body.put("aspect_ratio", MediaSizeHelper.normalizeSeedreamAspectRatio(payload.ratio()));
        body.put("response_format", "url");
        body.put("output_format", "jpeg");
        body.put("watermark", properties.watermark());

        var references = normalizeUrls(payload.imageUrls(), payload.imageUrl());
        if (!references.isEmpty()) {
            var images = body.putArray("image");
            references.stream().limit(10).forEach(images::add);
        }
        return body;
    }

    private String resolveModel(String requested) {
        if (requested != null && !requested.isBlank()) {
            return requested.trim();
        }
        var configured = properties.imageModel();
        return configured != null && !configured.isBlank() ? configured.trim() : DEFAULT_MODEL;
    }

    private List<String> normalizeUrls(List<String> urls, String primary) {
        var result = new ArrayList<String>();
        if (primary != null && !primary.isBlank()) {
            result.add(primary.trim());
        }
        if (urls != null) {
            for (var url : urls) {
                if (url != null && !url.isBlank() && !result.contains(url.trim())) {
                    result.add(url.trim());
                }
            }
        }
        return result;
    }

    private String bearer() {
        var apiKey = apiKeyResolver.resolve(ArkApiKeyContext.get(), null);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "请配置火山方舟 Seedream API Key（个人设置或环境变量 ARK_API_KEY）");
        }
        return "Bearer " + apiKey;
    }

    private JsonNode readResponse(InputStream bodyStream, int statusCode) throws IOException {
        if (bodyStream == null) {
            throw new IllegalStateException("火山方舟 Seedream 返回空响应 (HTTP " + statusCode + ")");
        }
        var body = objectMapper.readTree(bodyStream);
        if (statusCode >= 400) {
            throw new IllegalStateException(extractError(body, statusCode));
        }
        return body;
    }

    private ArkImageResult parseResult(JsonNode response) {
        var data = response.path("data");
        if (data.isArray() && !data.isEmpty()) {
            var outputUrl = text(data.get(0), "url");
            if (outputUrl != null) {
                return new ArkImageResult(outputUrl, null);
            }
        }
        var directUrl = text(response, "url");
        if (directUrl != null) {
            return new ArkImageResult(directUrl, null);
        }
        throw new IllegalStateException(extractError(response, 200));
    }

    private String extractError(JsonNode node, int statusCode) {
        var error = node.path("error");
        if (error.isTextual() && !error.asText().isBlank()) {
            return error.asText();
        }
        if (error.isObject()) {
            var code = text(error, "code");
            var message = text(error, "message");
            if (message != null) {
                return code != null ? code + ": " + message : message;
            }
        }
        var message = text(node, "message");
        return message != null
                ? message
                : "火山方舟 Seedream 请求失败 (HTTP " + statusCode + ")";
    }

    private String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        var value = node.get(field).asText();
        return value != null && !value.isBlank() ? value : null;
    }

    public record CreateImagePayload(
            String model,
            String prompt,
            String ratio,
            String imageUrl,
            List<String> imageUrls
    ) {}

    public record ArkImageResult(String outputUrl, String errorMessage) {}
}
