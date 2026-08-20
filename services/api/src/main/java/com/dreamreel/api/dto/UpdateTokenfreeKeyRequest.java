package com.dreamreel.api.dto;

import jakarta.validation.constraints.Size;

public record UpdateTokenfreeKeyRequest(
        @Size(max = 512) String apiKey
) {
}
