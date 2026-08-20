package com.dreamreel.api.dto;

import com.dreamreel.api.domain.GenerationJob;
import com.dreamreel.api.domain.GenerationStatus;

import java.time.Instant;
import java.util.UUID;

public record TextGenerationResponse(
        UUID id,
        UUID projectId,
        String nodeId,
        String model,
        String prompt,
        String nodeType,
        GenerationStatus status,
        String outputText,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt
) {
    public static TextGenerationResponse from(GenerationJob job, String nodeType) {
        return new TextGenerationResponse(
                job.getId(),
                job.getProjectId(),
                job.getNodeId(),
                job.getModel(),
                job.getPrompt(),
                nodeType,
                job.getStatus(),
                job.getOutputText(),
                job.getErrorMessage(),
                job.getCreatedAt(),
                job.getUpdatedAt()
        );
    }
}
