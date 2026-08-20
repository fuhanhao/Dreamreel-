package com.dreamreel.api.client;

import com.dreamreel.api.util.MediaSizeHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

@Component
public class TokenFreeClient {

    private final RestClient restClient;
    private final RestClient downloadClient;
    private final ObjectMapper objectMapper;

    public TokenFreeClient(RestClient tokenFreeRestClient, ObjectMapper objectMapper) {
        this.restClient = tokenFreeRestClient;
        this.downloadClient = RestClient.create();
        this.objectMapper = objectMapper;
    }

    public List<VideoModel> listVideoModels(String apiKey) {
        var response = restClient.get()
                .uri("/v1/models")
                .header("Authorization", bearer(apiKey))
                .retrieve()
                .body(JsonNode.class);

        var models = new ArrayList<VideoModel>();
        if (response == null || !response.has("data")) {
            return models;
        }

        for (var node : response.get("data")) {
            var endpointTypes = node.path("supported_endpoint_types");
            boolean isVideo = false;
            if (endpointTypes.isArray()) {
                for (var type : endpointTypes) {
                    if ("openai-video".equals(type.asText())) {
                        isVideo = true;
                        break;
                    }
                }
            }
            if (isVideo) {
                models.add(new VideoModel(node.path("id").asText(), node.path("owned_by").asText("video")));
            }
        }
        return models;
    }

    public List<ChatModel> listChatModels(String apiKey) {
        var response = restClient.get()
                .uri("/v1/models")
                .header("Authorization", bearer(apiKey))
                .retrieve()
                .body(JsonNode.class);

        var models = new ArrayList<ChatModel>();
        if (response == null || !response.has("data")) {
            return models;
        }

        for (var node : response.get("data")) {
            var endpointTypes = node.path("supported_endpoint_types");
            boolean isVideo = false;
            boolean isImage = false;
            boolean isChat = false;
            if (endpointTypes.isArray()) {
                for (var type : endpointTypes) {
                    var t = type.asText();
                    if ("openai-video".equals(t)) {
                        isVideo = true;
                    } else if ("openai-image".equals(t)) {
                        isImage = true;
                    } else if ("openai".equals(t) || t.contains("chat")) {
                        isChat = true;
                    }
                }
            }
            if (isChat && !isVideo && !isImage) {
                models.add(new ChatModel(node.path("id").asText(), node.path("owned_by").asText("chat")));
            }
        }
        return models;
    }

    public List<ImageModel> listImageModels(String apiKey) {
        var response = restClient.get()
                .uri("/v1/models")
                .header("Authorization", bearer(apiKey))
                .retrieve()
                .body(JsonNode.class);

        var models = new ArrayList<ImageModel>();
        if (response == null || !response.has("data")) {
            return models;
        }

        for (var node : response.get("data")) {
            var endpointTypes = node.path("supported_endpoint_types");
            boolean isImage = false;
            if (endpointTypes.isArray()) {
                for (var type : endpointTypes) {
                    if ("openai-image".equals(type.asText())) {
                        isImage = true;
                        break;
                    }
                }
            }
            if (isImage) {
                models.add(new ImageModel(node.path("id").asText(), node.path("owned_by").asText("image")));
            }
        }
        return models;
    }

    public TokenFreeImageResult createImage(String apiKey, CreateImagePayload payload) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", payload.model());
        body.put("prompt", payload.prompt());
        body.put("n", 1);
        body.put("response_format", "url");

        var refUrls = collectImageUrls(payload);
        if (isSeedreamModel(payload.model())) {
            body.put("aspect_ratio", MediaSizeHelper.normalizeSeedreamAspectRatio(payload.ratio()));
            if (isSeedream5Model(payload.model())) {
                // Seedream 5.x：只用 size=2K/4K，勿传 basic/high（会报 quality not allowed）
                body.put("size", MediaSizeHelper.toSeedreamSize(payload.quality()));
            } else {
                // Seedream 4.5：quality=basic/high；size=2K/4K
                body.put("quality", MediaSizeHelper.toSeedreamQuality(payload.quality()));
                body.put("size", MediaSizeHelper.toSeedreamSize(payload.quality()));
            }
            if (!refUrls.isEmpty()) {
                var imageUrls = objectMapper.createArrayNode();
                for (var url : refUrls) {
                    imageUrls.add(url);
                }
                body.set("image_urls", imageUrls);
                // 官方 Seedream 参考图字段为 image（URL 或数组）
                body.set("image", imageUrls);
            }
        } else {
            body.put("size", payload.size());
            if (!refUrls.isEmpty()) {
                var images = objectMapper.createArrayNode();
                for (var url : refUrls) {
                    images.add(url);
                }
                body.set("image", images);
                body.put("image_url", refUrls.getFirst());
                if (refUrls.size() > 1) {
                    var imageUrls = objectMapper.createArrayNode();
                    for (var url : refUrls) {
                        imageUrls.add(url);
                    }
                    body.set("image_urls", imageUrls);
                }
            }
        }

        if (payload.strength() != null) {
            body.put("strength", payload.strength());
            body.put("image_strength", payload.strength());
        }

        var response = postForJson(apiKey, "/v1/images/generations", body);

        return parseImageResult(response);
    }

    private static java.util.List<String> collectImageUrls(CreateImagePayload payload) {
        var urls = new ArrayList<String>();
        if (payload.imageUrls() != null) {
            for (var url : payload.imageUrls()) {
                if (url != null && !url.isBlank() && !urls.contains(url.trim())) {
                    urls.add(url.trim());
                }
            }
        }
        if (payload.imageUrl() != null && !payload.imageUrl().isBlank()) {
            var primary = payload.imageUrl().trim();
            if (!urls.contains(primary)) {
                urls.add(0, primary);
            }
        }
        return urls;
    }

    private boolean isSeedreamModel(String model) {
        if (model == null || model.isBlank()) {
            return false;
        }
        return model.toLowerCase(Locale.ROOT).contains("seedream");
    }

    /** Seedream 5.x（含 Lite / Pro）：网关 quality 不接受 basic/high。 */
    private boolean isSeedream5Model(String model) {
        if (!isSeedreamModel(model)) {
            return false;
        }
        var m = model.toLowerCase(Locale.ROOT);
        return m.contains("seedream-5") || m.contains("seedream/5") || m.contains("5-0") || m.contains("5.0");
    }

    public TokenFreeChatResult createChatCompletion(String apiKey, CreateChatPayload payload) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", payload.model());
        body.put("temperature", 0.7);

        var messages = objectMapper.createArrayNode();

        var systemMsg = objectMapper.createObjectNode();
        systemMsg.put("role", "system");
        systemMsg.put("content", payload.systemPrompt());
        messages.add(systemMsg);

        var userMsg = objectMapper.createObjectNode();
        userMsg.put("role", "user");
        userMsg.put("content", payload.userPrompt());
        messages.add(userMsg);

        body.set("messages", messages);

        var response = postForJson(apiKey, "/v1/chat/completions", body);

        return parseChatResult(response);
    }

    public TokenFreeChatResult createChatCompletion(String apiKey, String model, List<ChatMessage> messages) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("temperature", 0.4);

        var messageArray = objectMapper.createArrayNode();
        for (var message : messages) {
            var node = objectMapper.createObjectNode();
            node.put("role", message.role());
            node.put("content", message.content());
            messageArray.add(node);
        }
        body.set("messages", messageArray);

        var response = postForJson(apiKey, "/v1/chat/completions", body);
        return parseChatResult(response);
    }

    /** OpenAI 兼容 TTS：返回 mp3 字节；用于角色音色试听样本 */
    public byte[] createSpeech(String apiKey, String model, String voice, String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("TTS 文本不能为空");
        }
        var modelId = model != null && !model.isBlank() ? model.trim() : "gpt-4o-mini-tts";
        var text = input.length() > 800 ? input.substring(0, 800) : input;
        var body = objectMapper.createObjectNode();
        body.put("model", modelId);
        body.put("response_format", "mp3");

        if (isElevenLabsModel(modelId)) {
            if (isElevenLabsDialogueModel(modelId)) {
                throw new IllegalArgumentException("text-to-dialogue-v3 需使用 createDialogueSpeech");
            }
            if (isElevenLabsIsolationModel(modelId)) {
                throw new IllegalArgumentException("audio-isolation 需使用 isolateAudio");
            }
            var resolvedVoice = resolveElevenLabsVoice(voice);
            body.put("input", text);
            body.put("voice", resolvedVoice);
            var metadata = objectMapper.createObjectNode();
            metadata.put("text", text);
            metadata.put("voice", resolvedVoice);
            metadata.put("stability", 0.5);
            metadata.put("similarity_boost", 0.75);
            metadata.put("speed", 1);
            if (modelId.contains("turbo")) {
                metadata.put("language_code", containsCjk(text) ? "zh" : "en");
            }
            body.set("metadata", metadata);
        } else {
            body.put("voice", voice != null && !voice.isBlank() ? voice : "alloy");
            body.put("input", text);
        }

        ResourceAccessException lastIo = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                return restClient.post()
                        .uri("/v1/audio/speech")
                        .header("Authorization", bearer(apiKey))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .exchange((request, response) -> {
                            var status = response.getStatusCode().value();
                            var stream = response.getBody();
                            if (stream == null) {
                                throw new IllegalStateException("TokenFree TTS 返回空响应 (HTTP " + status + ")");
                            }
                            var bytes = stream.readAllBytes();
                            if (status >= 400) {
                                var err = "TokenFree TTS 失败 (HTTP " + status + ")";
                                try {
                                    var tree = objectMapper.readTree(bytes);
                                    var extracted = extractError(tree);
                                    if (extracted != null && !extracted.isBlank()) {
                                        err = extracted;
                                    }
                                } catch (Exception ignored) {
                                    // not JSON
                                }
                                throw new IllegalStateException(err);
                            }
                            if (bytes.length < 64) {
                                throw new IllegalStateException("TokenFree TTS 返回音频过短，请检查模型权限");
                            }
                            return bytes;
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
                "TokenFree TTS 网络请求失败（已重试 3 次）: " + lastIo.getMessage());
    }

    /** ElevenLabs text-to-dialogue-v3：多角色对白 */
    public byte[] createDialogueSpeech(String apiKey, String model, List<DialogueLine> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("对白不能为空");
        }
        var modelId = model != null && !model.isBlank() ? model.trim() : "elevenlabs/text-to-dialogue-v3";
        var body = objectMapper.createObjectNode();
        body.put("model", modelId);
        body.put("input", lines.get(0).text());
        body.put("response_format", "mp3");
        var metadata = objectMapper.createObjectNode();
        var dialogue = objectMapper.createArrayNode();
        for (var line : lines) {
            var node = objectMapper.createObjectNode();
            node.put("text", line.text());
            node.put("voice", resolveElevenLabsVoice(line.voice()));
            dialogue.add(node);
        }
        metadata.set("dialogue", dialogue);
        body.set("metadata", metadata);
        return postSpeechBytes(apiKey, body);
    }

    /** ElevenLabs audio-isolation：人声分离 */
    public byte[] isolateAudio(String apiKey, String audioUrl) {
        if (audioUrl == null || audioUrl.isBlank()) {
            throw new IllegalArgumentException("audio_url 不能为空");
        }
        var body = objectMapper.createObjectNode();
        body.put("model", "elevenlabs/audio-isolation");
        body.put("input", audioUrl);
        body.put("response_format", "mp3");
        var metadata = objectMapper.createObjectNode();
        metadata.put("audio_url", audioUrl);
        body.set("metadata", metadata);
        return postSpeechBytes(apiKey, body);
    }

    private byte[] postSpeechBytes(String apiKey, ObjectNode body) {
        ResourceAccessException lastIo = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                return restClient.post()
                        .uri("/v1/audio/speech")
                        .header("Authorization", bearer(apiKey))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .exchange((request, response) -> {
                            var status = response.getStatusCode().value();
                            var stream = response.getBody();
                            if (stream == null) {
                                throw new IllegalStateException("TokenFree TTS 返回空响应 (HTTP " + status + ")");
                            }
                            var bytes = stream.readAllBytes();
                            if (status >= 400) {
                                var err = "TokenFree TTS 失败 (HTTP " + status + ")";
                                try {
                                    var tree = objectMapper.readTree(bytes);
                                    var extracted = extractError(tree);
                                    if (extracted != null && !extracted.isBlank()) {
                                        err = extracted;
                                    }
                                } catch (Exception ignored) {
                                    // not JSON
                                }
                                throw new IllegalStateException(err);
                            }
                            if (bytes.length < 64) {
                                throw new IllegalStateException("TokenFree TTS 返回音频过短，请检查模型权限");
                            }
                            return bytes;
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
                "TokenFree TTS 网络请求失败（已重试 3 次）: " + lastIo.getMessage());
    }

    private static boolean isElevenLabsModel(String model) {
        return model != null && model.toLowerCase(Locale.ROOT).contains("elevenlabs/");
    }

    private static boolean isElevenLabsDialogueModel(String model) {
        return model != null && model.toLowerCase(Locale.ROOT).contains("text-to-dialogue");
    }

    private static boolean isElevenLabsIsolationModel(String model) {
        return model != null && model.toLowerCase(Locale.ROOT).contains("audio-isolation");
    }

    private static String resolveElevenLabsVoice(String voice) {
        if (voice == null || voice.isBlank()) {
            return "Roger";
        }
        var v = voice.trim();
        // TokenFree/Kie 仅接受预设名，不接受 ElevenLabs voice_id
        var allowed = java.util.Set.of(
                "Rachel", "Roger", "George", "Bill", "Sarah", "Aria", "Charlotte", "Daniel", "Eric", "Jessica");
        if (allowed.contains(v)) {
            return v;
        }
        var lower = v.toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "shimmer", "nova", "sarah" -> "Sarah";
            case "onyx", "echo", "bill" -> "Bill";
            case "alloy", "fable" -> "Roger";
            case "rachel" -> "Rachel";
            case "george" -> "George";
            case "aria" -> "Aria";
            default -> "Roger";
        };
    }

    private static boolean containsCjk(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            var ch = text.charAt(i);
            if (Character.UnicodeScript.of(ch) == Character.UnicodeScript.HAN) {
                return true;
            }
        }
        return false;
    }

    public record DialogueLine(String text, String voice) {}

    public TokenFreeVideoTask createVideoTask(String apiKey, CreateVideoPayload payload) {
        var imageUrls = new ArrayList<String>();
        if (payload.imageUrls() != null) {
            for (var url : payload.imageUrls()) {
                if (url != null && !url.isBlank()) {
                    imageUrls.add(url);
                }
            }
        }
        if (imageUrls.isEmpty() && payload.imageUrl() != null && !payload.imageUrl().isBlank()) {
            imageUrls.add(payload.imageUrl());
        }
        var audioUrls = new ArrayList<String>();
        if (payload.audioUrls() != null) {
            for (var url : payload.audioUrls()) {
                if (url != null && !url.isBlank()) {
                    audioUrls.add(url);
                }
            }
        }
        var hasVideo = payload.videoUrl() != null && !payload.videoUrl().isBlank();

        if (!imageUrls.isEmpty() || hasVideo || !audioUrls.isEmpty()) {
            var urlOnlyRefs = !imageUrls.isEmpty()
                    && imageUrls.stream().allMatch(u -> u.startsWith("http://") || u.startsWith("https://"));
            var references = new ArrayList<ReferenceFile>();
            if (!urlOnlyRefs) {
                for (var url : imageUrls) {
                    references.add(loadReferenceFile(url));
                }
                if (hasVideo && references.isEmpty()) {
                    references.add(loadReferenceFile(payload.videoUrl()));
                }
            }
            var response = postVideoMultipart(apiKey, payload, references, imageUrls, audioUrls);
            return parseTask(response);
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", payload.model());
        body.put("prompt", payload.prompt());
        body.put("seconds", String.valueOf(payload.seconds()));
        body.put("size", resolvePixelSize(payload));
        if (payload.resolution() != null && !payload.resolution().isBlank()) {
            body.put("resolution", payload.resolution());
        }
        if (payload.ratio() != null && !payload.ratio().isBlank()) {
            body.put("aspect_ratio", payload.ratio());
        }
        body.put("generate_audio", true);

        var response = postForJson(apiKey, "/v1/videos", body);
        return parseTask(response);
    }

    public TokenFreeVideoTask getVideoTask(String apiKey, String taskId) {
        var response = restClient.get()
                .uri("/v1/videos/{id}", taskId)
                .header("Authorization", bearer(apiKey))
                .retrieve()
                .body(JsonNode.class);

        return parseTask(response);
    }

    private JsonNode postVideoMultipart(
            String apiKey,
            CreateVideoPayload payload,
            List<ReferenceFile> references,
            List<String> imageUrls,
            List<String> audioUrls) {
        var prompt = buildReferencePrompt(payload, imageUrls.size(), audioUrls.size());
        var size = resolvePixelSize(payload);
        var resolution = payload.resolution() != null && !payload.resolution().isBlank()
                ? payload.resolution()
                : "720p";

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("prompt", prompt);
        body.add("model", payload.model());
        body.add("seconds", String.valueOf(payload.seconds()));
        body.add("size", size);
        body.add("resolution", resolution);
        body.add("generate_audio", "true");
        if (payload.ratio() != null && !payload.ratio().isBlank()) {
            body.add("aspect_ratio", payload.ratio());
        }
        if (payload.mode() != null && !payload.mode().isBlank()) {
            body.add("mode", payload.mode());
        }
        // 多图 reference-to-video：仅用 image_urls 绑定 @ImageN，避免与 input_reference 重复导致序号错位
        var urlOnlyRefs = !imageUrls.isEmpty();
        if (!urlOnlyRefs) {
            for (int i = 0; i < references.size(); i++) {
                var reference = references.get(i);
                var index = i;
                body.add("input_reference", new ByteArrayResource(reference.bytes()) {
                    @Override
                    public String getFilename() {
                        return index == 0 ? reference.filename() : "reference-" + (index + 1) + "-" + reference.filename();
                    }
                });
            }
        }
        // Seedance reference-to-video：公网 URL 列表 + prompt 中 @ImageN
        for (var url : imageUrls) {
            if (url != null && !url.isBlank() && (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("data:"))) {
                body.add("image_urls", url);
            }
        }
        for (var url : audioUrls) {
            if (url != null && !url.isBlank() && (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("data:"))) {
                body.add("audio_urls", url);
            }
        }

        return restClient.post()
                .uri("/v1/videos")
                .header("Authorization", bearer(apiKey))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .exchange((request, response) -> readResponse(response.getBody(), response.getStatusCode().value()));
    }

    private String buildReferencePrompt(CreateVideoPayload payload, int imageCount, int audioCount) {
        var prompt = payload.prompt() != null ? payload.prompt() : "";
        if (prompt.contains("@Image1") || prompt.contains("@Audio1")) {
            return prompt;
        }
        if (imageCount > 0) {
            var lead = imageCount == 1
                    ? "Strictly match @Image1 appearance. "
                    : "Strictly match @Image1..@Image" + imageCount + " for character/scene/prop consistency. ";
            return lead + prompt;
        }
        if (payload.videoUrl() != null && !payload.videoUrl().isBlank()) {
            return "Use @Image1 as the primary visual reference. " + prompt;
        }
        if (audioCount > 0) {
            return "Match @Audio1 voice tone. " + prompt;
        }
        return prompt;
    }

    /** 像素尺寸 WxH；切勿把 aspect_ratio 当作 size */
    private String resolvePixelSize(CreateVideoPayload payload) {
        if (payload.size() != null && !payload.size().isBlank() && payload.size().contains("x")) {
            return payload.size();
        }
        return com.dreamreel.api.util.MediaSizeHelper.toVideoSize(payload.ratio(), payload.resolution());
    }

    private ReferenceFile loadReferenceFile(String url) {
        if (url.startsWith("data:")) {
            var comma = url.indexOf(',');
            if (comma < 0) {
                throw new IllegalStateException("参考文件 data URL 格式无效");
            }
            var meta = url.substring(5, comma);
            var mime = meta.contains(";") ? meta.substring(0, meta.indexOf(';')) : meta;
            var bytes = Base64.getDecoder().decode(url.substring(comma + 1));
            return new ReferenceFile(bytes, filenameForMime(mime), mime);
        }

        if (url.startsWith("http://") || url.startsWith("https://")) {
            var entity = downloadClient.get()
                    .uri(URI.create(url))
                    .retrieve()
                    .toEntity(byte[].class);
            var bytes = entity.getBody();
            if (bytes == null || bytes.length == 0) {
                throw new IllegalStateException("参考文件下载失败: 空响应");
            }
            var contentType = entity.getHeaders().getContentType();
            var mime = contentType != null ? contentType.toString() : guessMimeFromUrl(url);
            return new ReferenceFile(bytes, filenameFromUrl(url, mime), mime);
        }

        throw new IllegalStateException("不支持的参考文件地址");
    }

    private String filenameFromUrl(String url, String mime) {
        var path = URI.create(url).getPath();
        if (path != null) {
            var name = path.substring(path.lastIndexOf('/') + 1);
            if (!name.isBlank() && name.contains(".")) {
                return name;
            }
        }
        return filenameForMime(mime);
    }

    private String filenameForMime(String mime) {
        return switch (mime.toLowerCase(Locale.ROOT)) {
            case "image/jpeg", "image/jpg" -> "reference.jpg";
            case "image/png" -> "reference.png";
            case "image/webp" -> "reference.webp";
            case "image/gif" -> "reference.gif";
            case "video/mp4" -> "reference.mp4";
            case "video/webm" -> "reference.webm";
            case "video/quicktime" -> "reference.mov";
            default -> "reference.bin";
        };
    }

    private String guessMimeFromUrl(String url) {
        var lower = url.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".mp4")) {
            return "video/mp4";
        }
        if (lower.endsWith(".webm")) {
            return "video/webm";
        }
        if (lower.endsWith(".mov")) {
            return "video/quicktime";
        }
        return "image/jpeg";
    }

    private String bearer(String apiKey) {
        return "Bearer " + apiKey;
    }

    private JsonNode postForJson(String apiKey, String uri, ObjectNode body) {
        ResourceAccessException lastIo = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                return restClient.post()
                        .uri(uri)
                        .header("Authorization", bearer(apiKey))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .exchange((request, response) -> readResponse(response.getBody(), response.getStatusCode().value()));
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
                "TokenFree 网络请求失败（已重试 3 次），请检查网络或代理: " + lastIo.getMessage());
    }

    private JsonNode readResponse(InputStream bodyStream, int statusCode) throws IOException {
        if (bodyStream == null) {
            return errorNode("TokenFree 返回空响应 (HTTP " + statusCode + ")");
        }

        var tree = objectMapper.readTree(bodyStream);
        if (statusCode >= 400) {
            var error = extractError(tree);
            if (error == null) {
                error = "TokenFree 请求失败 (HTTP " + statusCode + ")";
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

    private TokenFreeImageResult parseImageResult(JsonNode response) {
        if (response == null) {
            throw new IllegalStateException("TokenFree 返回空响应");
        }

        var error = extractError(response);
        if (error != null) {
            return new TokenFreeImageResult(null, "failed", error);
        }

        if (response.has("data") && response.get("data").isArray() && !response.get("data").isEmpty()) {
            var first = response.get("data").get(0);
            var url = firstText(first, "url");
            if (url != null) {
                return new TokenFreeImageResult(url, "completed", null);
            }
        }

        return new TokenFreeImageResult(null, "failed", "未返回图片 URL");
    }

    private TokenFreeChatResult parseChatResult(JsonNode response) {
        if (response == null) {
            throw new IllegalStateException("TokenFree 返回空响应");
        }

        var error = extractError(response);
        if (error != null) {
            return new TokenFreeChatResult(null, "failed", error);
        }

        if (response.has("choices") && response.get("choices").isArray() && !response.get("choices").isEmpty()) {
            var message = response.get("choices").get(0).path("message");
            var content = extractMessageContent(message.path("content"));
            if (content != null && !content.isBlank()) {
                return new TokenFreeChatResult(content.trim(), "completed", null);
            }
        }

        return new TokenFreeChatResult(null, "failed", "未返回文本内容");
    }

    private String extractMessageContent(JsonNode contentNode) {
        if (contentNode == null || contentNode.isNull() || contentNode.isMissingNode()) {
            return null;
        }
        if (contentNode.isTextual()) {
            return contentNode.asText();
        }
        if (contentNode.isArray()) {
            var sb = new StringBuilder();
            for (var part : contentNode) {
                if (part.has("text")) {
                    sb.append(part.get("text").asText(""));
                } else if (part.isTextual()) {
                    sb.append(part.asText());
                }
            }
            return sb.toString();
        }
        return contentNode.asText(null);
    }

    private TokenFreeVideoTask parseTask(JsonNode response) {
        if (response == null) {
            throw new IllegalStateException("TokenFree 返回空响应");
        }

        var id = firstText(response, "id", "task_id");
        var status = response.path("status").asText("queued");
        var progress = response.path("progress").isNumber() ? response.path("progress").asInt() : null;
        var outputUrl = extractOutputUrl(response);
        var error = extractError(response);

        return new TokenFreeVideoTask(id, status, progress, outputUrl, error);
    }

    private String extractOutputUrl(JsonNode response) {
        var direct = firstText(response, "video_url", "url", "output_url");
        if (direct != null) {
            return direct;
        }
        if (response.has("metadata") && response.get("metadata").isObject()) {
            var metadataUrl = firstText(response.get("metadata"), "url", "video_url", "output_url");
            if (metadataUrl != null) {
                return metadataUrl;
            }
        }
        if (response.has("output") && response.get("output").has("url")) {
            return response.get("output").get("url").asText(null);
        }
        if (response.has("data") && response.get("data").isArray() && !response.get("data").isEmpty()) {
            var first = response.get("data").get(0);
            var dataUrl = firstText(first, "url", "video_url", "output_url");
            if (dataUrl != null) {
                return dataUrl;
            }
        }
        return null;
    }

    private String extractError(JsonNode response) {
        if (response.has("message") && response.get("message").isTextual()) {
            var message = response.get("message").asText();
            if (!message.isBlank()) {
                return message;
            }
        }
        if (response.has("error")) {
            var error = response.get("error");
            if (error.isTextual()) {
                return error.asText();
            }
            if (error.has("message")) {
                return error.get("message").asText();
            }
        }
        return null;
    }

    private String firstText(JsonNode node, String... fields) {
        for (var field : fields) {
            if (node.has(field) && !node.get(field).isNull()) {
                var value = node.get(field).asText();
                if (!value.isBlank()) {
                    return value;
                }
            }
        }
        return null;
    }

    public record VideoModel(String id, String provider) {}

    public record ChatModel(String id, String provider) {}

    public record ImageModel(String id, String provider) {}

    public record CreateImagePayload(
            String model,
            String prompt,
            String size,
            String ratio,
            String quality,
            String imageUrl,
            Double strength,
            java.util.List<String> imageUrls
    ) {
        public CreateImagePayload(
                String model,
                String prompt,
                String size,
                String ratio,
                String quality,
                String imageUrl,
                Double strength) {
            this(model, prompt, size, ratio, quality, imageUrl, strength, null);
        }
    }

    public record CreateChatPayload(String model, String systemPrompt, String userPrompt) {}

    public record ChatMessage(String role, String content) {}

    public record TokenFreeImageResult(String outputUrl, String status, String errorMessage) {}

    public record TokenFreeChatResult(String outputText, String status, String errorMessage) {}

    public record CreateVideoPayload(
            String model,
            String prompt,
            int seconds,
            String size,
            String ratio,
            String mode,
            String imageUrl,
            String videoUrl,
            java.util.List<String> imageUrls,
            java.util.List<String> audioUrls,
            String resolution
    ) {}

    public record TokenFreeVideoTask(
            String taskId,
            String status,
            Integer progress,
            String outputUrl,
            String errorMessage
    ) {}

    private record ReferenceFile(byte[] bytes, String filename, String contentType) {}
}
