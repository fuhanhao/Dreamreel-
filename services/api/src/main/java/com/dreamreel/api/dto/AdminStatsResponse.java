package com.dreamreel.api.dto;

public record AdminStatsResponse(
        long totalUsers,
        long activeUsers,
        long totalProjects,
        long totalGenerations,
        long completedGenerations,
        long failedGenerations
) {
}
