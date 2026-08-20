package com.dreamreel.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateImageGenerationRequest(
        UUID projectId,
        @Size(max = 100) String nodeId,
        @NotBlank @Size(max = 128) String model,
        @NotBlank @Size(max = 8000) String prompt,
        @Size(max = 16) String ratio,
        @Size(max = 16) String quality,
        @Size(max = 32) String mode,
        @Size(max = 2000) String imageUrl,
        @DecimalMin("0.0") @DecimalMax("1.0") Double strength,
        /** 多参考图（角色定妆等）；与 imageUrl 合并后发给上游 */
        List<@Size(max = 2000) String> imageUrls
) {
    public CreateImageGenerationRequest(
            UUID projectId,
            String nodeId,
            String model,
            String prompt,
            String ratio,
            String quality,
            String mode,
            String imageUrl,
            Double strength) {
        this(projectId, nodeId, model, prompt, ratio, quality, mode, imageUrl, strength, null);
    }
}
