package com.dreamreel.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dreamreel.speech")
public record SpeechProperties(
        String baseUrl,
        String appId,
        /** 旧版控制台 Access Token，请求头 X-Api-Access-Key */
        String accessKey,
        String secretKey,
        /**
         * 新版控制台 API Key（API Key 管理），请求头 X-Api-Key。
         * 配置后优先使用，无需再配 app-id / access-key。
         */
        String apiKey,
        String defaultSpeaker,
        /** 可选；留空则按 speaker 自动选择 seed-tts-2.0 / seed-icl-2.0 等 */
        String resourceId,
        int sampleRate
) {
    public boolean isConfigured() {
        return hasApiKey() || hasLegacyCredentials();
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    public boolean hasLegacyCredentials() {
        return appId != null && !appId.isBlank()
                && accessKey != null && !accessKey.isBlank();
    }

    public String defaultSpeakerOrFallback() {
        return defaultSpeaker != null && !defaultSpeaker.isBlank()
                ? defaultSpeaker
                : "zh_female_xiaohe_uranus_bigtts";
    }
}
