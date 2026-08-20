package com.dreamreel.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dreamreel.ark")
public record ArkProperties(
        String baseUrl,
        String apiKey,
        /** 默认 Seedance 模型，如 doubao-seedance-2-5-260628 */
        String defaultModel,
        /** 快速版模型，可选 */
        String fastModel,
        /** 分镜图片使用的 Seedream 模型 */
        String imageModel,
        boolean watermark
) {
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
