package com.dreamreel.api.dto;

import jakarta.validation.constraints.Size;

public record UpdateArkKeyRequest(
        @Size(max = 512) String apiKey
) {
}
