package com.dreamreel.api.dto;

import com.dreamreel.api.domain.User;
import com.dreamreel.api.domain.UserRole;
import com.dreamreel.api.domain.UserStatus;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String displayName,
        UserRole role,
        UserStatus status,
        boolean hasTokenfreeApiKey,
        boolean hasArkApiKey,
        Instant createdAt,
        Instant updatedAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getRole(),
                user.getStatus(),
                user.getTokenfreeApiKey() != null && !user.getTokenfreeApiKey().isBlank(),
                user.getArkApiKey() != null && !user.getArkApiKey().isBlank(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
