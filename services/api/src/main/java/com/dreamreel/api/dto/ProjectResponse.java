package com.dreamreel.api.dto;

import com.dreamreel.api.domain.Project;
import com.dreamreel.api.domain.ProjectType;

import java.time.Instant;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        String name,
        ProjectType type,
        String description,
        String canvasData,
        Instant createdAt,
        Instant updatedAt
) {
    public static ProjectResponse from(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getType(),
                project.getDescription(),
                project.getCanvasData(),
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }

    public static ProjectResponse summary(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getType(),
                project.getDescription(),
                null,
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }
}
