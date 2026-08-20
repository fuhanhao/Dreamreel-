package com.dreamreel.api.dto;

import com.dreamreel.api.domain.UserStatus;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        UserStatus status,
        @Size(max = 100) String displayName
) {
}
