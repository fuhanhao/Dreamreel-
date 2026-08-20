package com.dreamreel.api.dramaforge.service;

import com.dreamreel.api.dramaforge.domain.DramaForgeJobType;
import com.dreamreel.api.dramaforge.dto.DramaForgeDtos.PipelineOverviewResponse;
import com.dreamreel.api.dramaforge.repository.DramaForgeConfigRepository;
import com.dreamreel.api.dramaforge.repository.DramaForgeEpisodeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class DramaForgeWorkflowService {

    private final DramaForgeService dramaForgeService;
    private final DramaForgeJobService jobService;
    private final DramaForgeConfigRepository configRepository;
    private final DramaForgeEpisodeRepository episodeRepository;
    private final ObjectMapper objectMapper;

    public DramaForgeWorkflowService(
            DramaForgeService dramaForgeService,
            DramaForgeJobService jobService,
            DramaForgeConfigRepository configRepository,
            DramaForgeEpisodeRepository episodeRepository,
            ObjectMapper objectMapper) {
        this.dramaForgeService = dramaForgeService;
        this.jobService = jobService;
        this.configRepository = configRepository;
        this.episodeRepository = episodeRepository;
        this.objectMapper = objectMapper;
    }

    public PipelineOverviewResponse runPipeline(UUID projectId, String apiKey) {
        var payload = writePayload(apiKey, null);
        jobService.enqueue(projectId, DramaForgeJobType.WORKFLOW_RUN, null, null, payload);
        return dramaForgeService.getOverview(projectId);
    }

    /** WORKFLOW_RUN 任务处理器：按当前阶段 enqueue 下一步 */
    public void advancePipeline(UUID projectId, String apiKey, UUID episodeIdHint) {
        var overview = dramaForgeService.getOverview(projectId);
        var payload = writePayload(apiKey, episodeIdHint);
        var episodeId = episodeIdHint != null ? episodeIdHint : firstEpisodeId(projectId);

        switch (overview.stage()) {
            case "story_input" -> {
                requireSourceText(projectId);
                jobService.enqueue(projectId, DramaForgeJobType.EXTRACT_ASSETS, null, null, payload);
            }
            case "script_locked" -> {
                jobService.enqueue(projectId, DramaForgeJobType.GENERATE_SCRIPT, null, null, payload);
            }
            case "assets_locked" -> {
                jobService.enqueue(projectId, DramaForgeJobType.ASSET_DESIGN, null, null, payload);
            }
            case "video_done" -> {
                if (overview.videoDoneCount() < overview.shotCount()) {
                    jobService.enqueue(projectId, DramaForgeJobType.VIDEO, null, episodeId, payload);
                    jobService.enqueue(projectId, DramaForgeJobType.SYNC_VIDEOS, null, episodeId, payload);
                } else {
                    jobService.enqueue(projectId, DramaForgeJobType.COMPOSE, null, episodeId, payload);
                }
            }
            default -> {
                // composed or unknown — no-op
            }
        }
    }

    private UUID firstEpisodeId(UUID projectId) {
        return episodeRepository.findByProjectIdOrderByEpisodeNumberAsc(projectId).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("请先创建剧集"))
                .getId();
    }

    private void requireSourceText(UUID projectId) {
        var config = configRepository.findByProjectId(projectId)
                .orElseThrow(() -> new IllegalStateException("DramaForge 配置不存在"));
        if (config.getSourceText() == null || config.getSourceText().isBlank()) {
            throw new IllegalStateException("请先在 Step ① 粘贴故事正文并保存");
        }
    }

    private String writePayload(String apiKey, UUID episodeId) {
        try {
            var map = new java.util.LinkedHashMap<String, Object>();
            map.put("apiKey", apiKey != null ? apiKey : "");
            if (episodeId != null) {
                map.put("episodeId", episodeId.toString());
            }
            return objectMapper.writeValueAsString(map);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
