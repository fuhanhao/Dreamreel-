package com.dreamreel.api.dto;

import com.dreamreel.api.domain.ProjectType;
import jakarta.validation.constraints.Size;

public record UpdateProjectRequest(
        @Size(max = 200) String name,
        ProjectType type,
        @Size(max = 1000) String description
) {}
