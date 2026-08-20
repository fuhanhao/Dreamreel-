package com.dreamreel.api.dto;

public record AuthResponse(
        String token,
        UserResponse user
) {
}
