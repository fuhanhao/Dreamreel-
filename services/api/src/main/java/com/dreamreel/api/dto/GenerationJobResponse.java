package com.dreamreel.api.dto;

import com.dreamreel.api.domain.GenerationJob;
import com.dreamreel.api.domain.GenerationMediaType;
import com.dreamreel.api.domain.GenerationStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record GenerationJobResponse(
        UUID id,
        UUID userId,
        UUID projectId,
        String nodeId,
        String providerTaskId,
        String model,
        GenerationMediaType mediaType,
        String prompt,
        GenerationStatus status,
        Integer progress,
        String outputUrl,
        String outputText,
        String errorMessage,
        String generationMode,
        String referenceImageUrl,
        List<String> referenceImageUrls,
        String referenceVideoUrl,
        String ratio,
        Double strength,
        Instant createdAt,
        Instant updatedAt
) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static GenerationJobResponse from(GenerationJob job) {
        return from(job, resolveReferenceImageUrls(job));
    }

    public static GenerationJobResponse from(GenerationJob job, List<String> referenceImageUrls) {
        var urls = referenceImageUrls != null ? referenceImageUrls : List.<String>of();
        var primary = !urls.isEmpty()
                ? urls.getFirst()
                : job.getReferenceImageUrl();
        return new GenerationJobResponse(
                job.getId(),
                job.getUserId(),
                job.getProjectId(),
                job.getNodeId(),
                job.getProviderTaskId(),
                job.getModel(),
                job.getMediaType(),
                job.getPrompt(),
                job.getStatus(),
                job.getProgress(),
                job.getOutputUrl(),
                job.getOutputText(),
                job.getErrorMessage(),
                job.getGenerationMode(),
                primary,
                urls,
                job.getReferenceVideoUrl(),
                job.getRatio(),
                job.getStrength(),
                job.getCreatedAt(),
                job.getUpdatedAt()
        );
    }

    public static List<String> resolveReferenceImageUrls(GenerationJob job) {
        var urls = new ArrayList<String>();
        if (job.getReferenceImageUrls() != null && !job.getReferenceImageUrls().isBlank()) {
            try {
                var parsed = MAPPER.readValue(job.getReferenceImageUrls(), new TypeReference<List<String>>() {});
                if (parsed != null) {
                    for (var url : parsed) {
                        if (url != null && !url.isBlank() && !urls.contains(url)) {
                            urls.add(url.trim());
                        }
                    }
                }
            } catch (Exception ignored) {
                // fall through to single URL
            }
        }
        if (urls.isEmpty() && job.getReferenceImageUrl() != null && !job.getReferenceImageUrl().isBlank()) {
            urls.add(job.getReferenceImageUrl().trim());
        }
        return urls;
    }
}
