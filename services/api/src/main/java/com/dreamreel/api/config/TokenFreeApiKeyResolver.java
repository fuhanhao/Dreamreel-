package com.dreamreel.api.config;

import org.springframework.stereotype.Component;

@Component
public class TokenFreeApiKeyResolver {

    private final TokenFreeProperties properties;

    public TokenFreeApiKeyResolver(TokenFreeProperties properties) {
        this.properties = properties;
    }

    public String resolve(String headerApiKey, String userApiKey) {
        if (headerApiKey != null && !headerApiKey.isBlank()) {
            return headerApiKey.trim();
        }
        if (userApiKey != null && !userApiKey.isBlank()) {
            return userApiKey.trim();
        }
        var configured = properties.apiKey();
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        return null;
    }

    public boolean isConfigured(String headerApiKey, String userApiKey) {
        var key = resolve(headerApiKey, userApiKey);
        return key != null && !key.isBlank();
    }
}
