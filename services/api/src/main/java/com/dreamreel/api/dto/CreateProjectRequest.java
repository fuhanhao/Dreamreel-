package com.dreamreel.api.dto;

import com.dreamreel.api.domain.ProjectType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(
        @NotBlank @Size(max = 200) String name,
        @NotNull ProjectType type,
        @Size(max = 1000) String description
) {}
