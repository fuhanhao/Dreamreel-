package com.dreamreel.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "dreamreel.security")
public record SecurityProperties(
        String jwtSecret,
        long jwtExpirationMs,
        List<String> corsAllowedOrigins
) {
    public List<String> resolvedCorsOrigins() {
        if (corsAllowedOrigins != null && !corsAllowedOrigins.isEmpty()) {
            return corsAllowedOrigins;
        }
        return List.of(
                "http://localhost:7050",
                "http://127.0.0.1:7050",
                "https://www.dreamreel.com",
                "https://dreamreel.com");
    }
}
