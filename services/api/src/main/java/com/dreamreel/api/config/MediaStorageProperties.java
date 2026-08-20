package com.dreamreel.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dreamreel.media")
public record MediaStorageProperties(
        String storagePath,
        String publicBaseUrl,
        String uploadPath,
        String uploadPublicBaseUrl
) {
}
