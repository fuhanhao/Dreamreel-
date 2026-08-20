package com.dreamreel.api.dramaforge.service;

import com.dreamreel.api.config.ArkApiKeyContext;
import com.dreamreel.api.config.ArkApiKeyResolver;
import com.dreamreel.api.config.DramaForgeProperties;
import com.dreamreel.api.dramaforge.domain.DramaForgeJob;
import com.dreamreel.api.dramaforge.domain.DramaForgeJobType;
import com.dreamreel.api.dramaforge.dto.DramaForgeDtos.JobResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.Executor;

@Service
public class DramaForgeEnqueueService {

    private final DramaForgeJobService jobService;
    private final DramaForgeJobProcessor jobProcessor;
    private final DramaForgeProperties dramaForgeProperties;
    private final ObjectMapper objectMapper;
    private final Executor jobExecutor;

    public DramaForgeEnqueueService(
            DramaForgeJobService jobService,
            DramaForgeJobProcessor jobProcessor,
            DramaForgeProperties dramaForgeProperties,
            ObjectMapper objectMapper,
            @Qualifier("dramaforgeJobExecutor") Executor jobExecutor) {
        this.jobService = jobService;
        this.jobProcessor = jobProcessor;
        this.dramaForgeProperties = dramaForgeProperties;
        this.objectMapper = objectMapper;
        this.jobExecutor = jobExecutor;
    }

    public JobResponse enqueue(UUID projectId, DramaForgeJobType type, UUID episodeId, String apiKey) {
        return enqueueAndMaybeRunInline(projectId, type, null, episodeId, apiKey);
    }

    public JobResponse enqueueShotStoryboard(UUID projectId, UUID episodeId, UUID shotId, String apiKey) {
        return enqueueAndMaybeRunInline(projectId, DramaForgeJobType.SHOT_STORYBOARD, shotId, episodeId, apiKey);
    }

    public JobResponse enqueueShotVideo(UUID projectId, UUID episodeId, UUID shotId, String apiKey) {
        return enqueueAndMaybeRunInline(projectId, DramaForgeJobType.SHOT_VIDEO, shotId, episodeId, apiKey);
    }

    public JobResponse enqueueAssetDesign(UUID projectId, UUID assetId, String apiKey) {
        return enqueueAssetDesign(projectId, assetId, apiKey, false);
    }

    public JobResponse enqueueAssetDesign(UUID projectId, UUID assetId, String apiKey, boolean privacySafe) {
        return enqueueAndMaybeRunInline(
                projectId, DramaForgeJobType.ASSET_DESIGN_SINGLE, assetId, null, apiKey, privacySafe);
    }

    private JobResponse enqueueAndMaybeRunInline(
            UUID projectId,
            DramaForgeJobType type,
            UUID targetId,
            UUID episodeId,
            String apiKey) {
        return enqueueAndMaybeRunInline(projectId, type, targetId, episodeId, apiKey, false);
    }

    private JobResponse enqueueAndMaybeRunInline(
            UUID projectId,
            DramaForgeJobType type,
            UUID targetId,
            UUID episodeId,
            String apiKey,
            boolean privacySafe) {
        if (type == DramaForgeJobType.SYNC_VIDEOS) {
            var active = jobService.findActiveSyncJob(projectId, episodeId);
            if (active != null) {
                return jobService.toResponse(active);
            }
        }
        var inline = dramaForgeProperties.processJobsInline();
        // inline 且项目空闲时直接 RUNNING，避免被其它实例抢走。
        // 若已有 RUNNING，则落 QUEUED（排队等待），避免撞 uq_dramaforge_jobs_running_project。
        var startRunning = inline && !jobService.hasRunningJob(projectId);
        var job = jobService.enqueue(
                projectId, type, targetId, episodeId, writePayload(apiKey, privacySafe), startRunning);
        if (startRunning) {
            jobExecutor.execute(() -> jobProcessor.process(job));
        }
        return jobService.toResponse(job);
    }

    private String writePayload(String apiKey) {
        return writePayload(apiKey, false);
    }

    private String writePayload(String apiKey, boolean privacySafe) {
        try {
            var payload = new java.util.LinkedHashMap<String, String>();
            payload.put("apiKey", apiKey != null ? apiKey : "");
            var arkApiKey = ArkApiKeyResolver.sanitize(ArkApiKeyContext.get());
            if (arkApiKey != null) {
                payload.put("arkApiKey", arkApiKey);
            }
            if (privacySafe) {
                payload.put("privacySafe", "true");
            }
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
