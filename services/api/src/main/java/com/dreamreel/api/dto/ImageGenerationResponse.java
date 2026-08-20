package com.dreamreel.api.dto;

import com.dreamreel.api.domain.GenerationJob;
import com.dreamreel.api.domain.GenerationMediaType;
import com.dreamreel.api.domain.GenerationStatus;

import java.time.Instant;
import java.util.UUID;

public record ImageGenerationResponse(
        UUID id,
        UUID projectId,
        String nodeId,
        String model,
        String prompt,
        GenerationStatus status,
        String outputUrl,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt
) {
    public static ImageGenerationResponse from(GenerationJob job) {
        return new ImageGenerationResponse(
                job.getId(),
                job.getProjectId(),
                job.getNodeId(),
                job.getModel(),
                job.getPrompt(),
                job.getStatus(),
                job.getOutputUrl(),
                job.getErrorMessage(),
                job.getCreatedAt(),
                job.getUpdatedAt()
        );
    }
}
