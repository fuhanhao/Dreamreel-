package com.dreamreel.api.common;

import java.time.Instant;
import java.util.Map;

public record ApiResponse<T>(
        boolean success,
        T data,
        String message,
        Instant timestamp
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, Instant.now());
    }

    public static ApiResponse<Map<String, String>> okMessage(String message) {
        return new ApiResponse<>(true, Map.of("message", message), null, Instant.now());
    }
}
