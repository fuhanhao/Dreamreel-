package com.dreamreel.api.dto;

import java.util.UUID;

public record UploadResponse(
        UUID id,
        String url,
        String contentType,
        String originalFilename
) {
}
