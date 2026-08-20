package com.dreamreel.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dreamreel.admin")
public record AdminProperties(
        String email,
        String password,
        String displayName,
        boolean autoCreate
) {
}
