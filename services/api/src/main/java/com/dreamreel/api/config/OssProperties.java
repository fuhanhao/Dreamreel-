package com.dreamreel.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dreamreel.oss")
public record OssProperties(
        boolean enabled,
        String endpoint,
        String region,
        String bucket,
        String envFolder,
        String accessKeyId,
        String accessKeySecret
) {
    public String normalizedEndpoint() {
        if (endpoint == null) {
            return "";
        }
        return endpoint
                .replace("https://", "")
                .replace("http://", "")
                .trim();
    }

    public String folderPrefix() {
        if (envFolder == null || envFolder.isBlank()) {
            return "dev";
        }
        var trimmed = envFolder.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
