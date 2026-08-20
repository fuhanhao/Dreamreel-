package com.dreamreel.api.client;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.dreamreel.api.config.SpeechProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Locale;

/**
 * 火山引擎豆包语音合成 V3：POST /api/v3/tts/unidirectional
 */
@Component
public class VolcengineTtsClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final SpeechProperties properties;

    public VolcengineTtsClient(
            @Qualifier("speechRestClient") RestClient speechRestClient,
            ObjectMapper objectMapper,
            SpeechProperties properties) {
        this.restClient = speechRestClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /** 合成 mp3，返回完整音频字节 */
    public byte[] synthesize(String text, String speaker, String emotionHint) {
        requireConfigured();
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("TTS 文本不能为空");
        }
        var trimmed = text.length() > 800 ? text.substring(0, 800) : text;
        var resolvedSpeaker = speaker != null && !speaker.isBlank()
                ? speaker.trim()
                : properties.defaultSpeakerOrFallback();
        var resourceId = resolveResourceId(resolvedSpeaker);

        ObjectNode body = objectMapper.createObjectNode();
        body.putObject("user").put("uid", "dreamreel");
        var req = body.putObject("req_params");
        req.put("text", trimmed);
        req.put("speaker", resolvedSpeaker);
        var audio = req.putObject("audio_params");
        audio.put("format", "mp3");
        audio.put("sample_rate", properties.sampleRate() > 0 ? properties.sampleRate() : 24000);

        var additions = buildAdditions(resolvedSpeaker, emotionHint);
        if (additions != null) {
            req.put("additions", additions);
        }

        var raw = postRaw("/api/v3/tts/unidirectional", resourceId, body);
        return parseAudioChunks(raw);
    }

    private String buildAdditions(String speaker, String emotionHint) {
        try {
            var additions = objectMapper.createObjectNode();
            if (speaker.startsWith("S_")) {
                additions.put("model_type", 4);
            }
            if (emotionHint != null && !emotionHint.isBlank()) {
                var arr = additions.putArray("context_texts");
                arr.add("用「" + emotionHint.trim() + "」的语气朗读");
            }
            if (additions.isEmpty()) {
                return null;
            }
            return objectMapper.writeValueAsString(additions);
        } catch (Exception ex) {
            return null;
        }
    }

    private String resolveResourceId(String speaker) {
        if (properties.resourceId() != null && !properties.resourceId().isBlank()) {
            return properties.resourceId().trim();
        }
        if (speaker.startsWith("S_")) {
            return "seed-icl-2.0";
        }
        var lower = speaker.toLowerCase(Locale.ROOT);
        if (lower.contains("_uranus_") || lower.startsWith("saturn_")) {
            return "seed-tts-2.0";
        }
        return "seed-tts-1.0";
    }

    private String postRaw(String uri, String resourceId, ObjectNode body) {
        ResourceAccessException lastIo = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                var request = restClient.post()
                        .uri(uri)
                        .header("X-Api-Resource-Id", resourceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body);
                // 新版控制台：仅需 X-Api-Key；旧版：X-Api-App-Id + X-Api-Access-Key
                if (properties.hasApiKey()) {
                    request = request.header("X-Api-Key", properties.apiKey().trim());
                } else {
                    request = request
                            .header("X-Api-App-Id", properties.appId().trim())
                            .header("X-Api-Access-Key", properties.accessKey().trim());
                }
                return request.exchange((req, response) -> {
                            var status = response.getStatusCode().value();
                            var stream = response.getBody();
                            if (stream == null) {
                                throw new IllegalStateException("豆包 TTS 返回空响应 (HTTP " + status + ")");
                            }
                            var raw = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                            if (status >= 400) {
                                throw new IllegalStateException(
                                        "豆包 TTS 失败 (HTTP " + status + "): " + summarizeError(raw));
                            }
                            return raw;
                        });
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
                "豆包 TTS 网络请求失败（已重试 3 次）: " + (lastIo != null ? lastIo.getMessage() : "unknown"));
    }

    private byte[] parseAudioChunks(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("豆包 TTS 返回空内容");
        }
        var chunks = new ArrayList<byte[]>();
        try (JsonParser parser = objectMapper.getFactory().createParser(raw)) {
            JsonNode node;
            while ((node = objectMapper.readTree(parser)) != null) {
                if (!node.has("code")) {
                    continue;
                }
                var code = node.get("code").asInt();
                if (code == 0 && node.has("data") && !node.get("data").isNull()) {
                    var data = node.get("data").asText("");
                    if (!data.isBlank()) {
                        chunks.add(Base64.getDecoder().decode(data));
                    }
                } else if (code == 20000000) {
                    break;
                } else if (code != 0) {
                    var msg = node.has("message") ? node.get("message").asText() : ("code=" + code);
                    throw new IllegalStateException("豆包 TTS 合成失败: " + msg);
                }
            }
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("豆包 TTS 响应解析失败: " + ex.getMessage(), ex);
        }

        if (chunks.isEmpty()) {
            throw new IllegalStateException("豆包 TTS 未返回音频数据: " + summarizeError(raw));
        }

        var total = chunks.stream().mapToInt(b -> b.length).sum();
        var merged = new byte[total];
        var offset = 0;
        for (var chunk : chunks) {
            System.arraycopy(chunk, 0, merged, offset, chunk.length);
            offset += chunk.length;
        }
        if (merged.length < 64) {
            throw new IllegalStateException("豆包 TTS 返回音频过短");
        }
        return merged;
    }

    private String summarizeError(String raw) {
        if (raw == null || raw.isBlank()) {
            return "empty";
        }
        return raw.length() > 300 ? raw.substring(0, 300) + "..." : raw;
    }

    private void requireConfigured() {
        if (!properties.isConfigured()) {
            throw new IllegalStateException(
                    "请配置豆包语音：新版 API Key（dreamreel.speech.api-key / SPEECH_API_KEY）"
                            + "，或旧版 app-id + access-key（SPEECH_APP_ID / SPEECH_ACCESS_KEY）");
        }
    }
}
