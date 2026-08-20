package com.dreamreel.api.dto;

public record CreationStatsResponse(
        long projectCount,
        int projectDeltaPercent,
        double renderHours,
        int renderDeltaPercent,
        long videoCount,
        int videoDeltaPercent,
        long credits,
        int creditsDeltaPercent
) {
}
