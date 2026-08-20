package com.dreamreel.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateTextGenerationRequest(
        UUID projectId,
        @Size(max = 100) String nodeId,
        @NotBlank @Size(max = 128) String model,
        @NotBlank @Size(max = 2000) String prompt,
        @NotBlank @Pattern(regexp = "text|script|prompt") String nodeType,
        @Size(max = 32000) String context
) {}
