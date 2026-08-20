package com.dreamreel.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateVideoGenerationRequest(
        UUID projectId,
        @Size(max = 100) String nodeId,
        @NotBlank @Size(max = 128) String model,
        @NotBlank @Size(max = 4000) String prompt,
        @Min(2) @Max(15) Integer seconds,
        @Size(max = 16) String ratio,
        @Size(max = 16) String quality,
        @Size(max = 32) String mode,
        @Size(max = 2000) String imageUrl,
        @Size(max = 2000) String videoUrl,
        java.util.List<@Size(max = 2000) String> imageUrls,
        java.util.List<@Size(max = 2000) String> audioUrls
) {}
