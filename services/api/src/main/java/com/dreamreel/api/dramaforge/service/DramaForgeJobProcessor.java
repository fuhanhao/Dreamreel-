package com.dreamreel.api.dramaforge.service;

import com.dreamreel.api.config.ArkApiKeyContext;
import com.dreamreel.api.config.ArkApiKeyResolver;
import com.dreamreel.api.dramaforge.domain.DramaForgeJob;
import com.dreamreel.api.dramaforge.domain.DramaForgeJobStatus;
import com.dreamreel.api.dramaforge.domain.DramaForgeJobType;
import com.dreamreel.api.dramaforge.domain.DramaForgeShotStatus;
import com.dreamreel.api.dramaforge.repository.DramaForgeJobRepository;
import com.dreamreel.api.dramaforge.repository.DramaForgeShotRepository;
import com.dreamreel.api.service.ProjectApiKeyResolver;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class DramaForgeJobProcessor {

    private final DramaForgeService dramaForgeService;
    private final DramaForgeImportService importService;
    private final DramaForgeComposeService composeService;
    private final DramaForgeExportService exportService;
    private final DramaForgeShotRepository shotRepository;
    private final DramaForgeJobRepository jobRepository;
    private final DramaForgeJobService jobService;
    private final DramaForgeEventHub eventHub;
    private final DramaForgeWorkflowService workflowService;
    private final ObjectMapper objectMapper;
    private final ProjectApiKeyResolver projectApiKeyResolver;
    private final ArkApiKeyResolver arkApiKeyResolver;

    public DramaForgeJobProcessor(
            DramaForgeService dramaForgeService,
            DramaForgeImportService importService,
            DramaForgeComposeService composeService,
            DramaForgeExportService exportService,
            DramaForgeShotRepository shotRepository,
            DramaForgeJobRepository jobRepository,
            DramaForgeJobService jobService,
            DramaForgeEventHub eventHub,
            DramaForgeWorkflowService workflowService,
            ObjectMapper objectMapper,
            ProjectApiKeyResolver projectApiKeyResolver,
            ArkApiKeyResolver arkApiKeyResolver) {
        this.dramaForgeService = dramaForgeService;
        this.importService = importService;
        this.composeService = composeService;
        this.exportService = exportService;
        this.shotRepository = shotRepository;
        this.jobRepository = jobRepository;
        this.jobService = jobService;
        this.eventHub = eventHub;
        this.workflowService = workflowService;
        this.objectMapper = objectMapper;
        this.projectApiKeyResolver = projectApiKeyResolver;
        this.arkApiKeyResolver = arkApiKeyResolver;
    }

    public void process(DramaForgeJob job) {
        try {
            jobService.extendLease(job);
            var apiKey = resolveApiKey(job);
            var arkApiKey = resolveArkApiKey(job);
            if (arkApiKey != null && !arkApiKey.isBlank()) {
                ArkApiKeyContext.set(arkApiKey);
            }
            try {
                processWithKeys(job, apiKey);
            } finally {
                ArkApiKeyContext.clear();
            }
            jobService.complete(job);
            eventHub.publish(job.getProjectId(), "pipeline_updated", dramaForgeService.getOverview(job.getProjectId()));
        } catch (Exception ex) {
            var message = ex.getMessage() != null && !ex.getMessage().isBlank()
                    ? ex.getMessage()
                    : "任务执行失败";
            jobService.fail(job, message);
            // 仅终态 FAILED 才回写镜头；可重试入队时不把镜头标成失败
            if (shouldMarkShotFailedAfterJobFail(job)) {
                try {
                    dramaForgeService.markShotFailedById(job.getTargetId(), message);
                } catch (Exception ignored) {
                    // 任务失败信息已写入 job，镜头回写失败不阻断
                }
            }
        }
    }

    /** 任务已终态失败且关联镜头时才回写镜头失败态。 */
    static boolean shouldMarkShotFailedAfterJobFail(DramaForgeJob job) {
        return job.getStatus() == DramaForgeJobStatus.FAILED
                && (job.getJobType() == DramaForgeJobType.SHOT_VIDEO
                || job.getJobType() == DramaForgeJobType.SHOT_STORYBOARD)
                && job.getTargetId() != null;
    }

    private void processWithKeys(DramaForgeJob job, String apiKey) throws Exception {
            switch (job.getJobType()) {
                case EXTRACT_ASSETS -> {
                    jobService.reportProgress(job, 0, 1, "正在提取角色/场景/道具…");
                    importService.extractAssets(job.getProjectId(), apiKey);
                    jobService.reportProgress(job, 1, 1, "资产提取完成");
                }
                case GENERATE_SCRIPT -> {
                    jobService.reportProgress(job, 0, 2, "正在生成剧本…");
                    var episode = importService.generateEpisodeScript(job.getProjectId(), apiKey);
                    job.setEpisodeId(episode.getId());
                    jobRepository.save(job);
                    jobService.extendLease(job);
                    jobService.reportProgress(job, 1, 2, "正在解析镜头…");
                    dramaForgeService.parseShotsFromScript(job.getProjectId(), episode.getId());
                    jobService.reportProgress(job, 2, 2, "剧本与镜头已就绪");
                }
                case ASSET_DESIGN -> {
                    jobService.extendLease(job);
                    dramaForgeService.generateAssetDesigns(job.getProjectId(), apiKey,
                            (current, total, message) -> jobService.reportProgress(job, current, total, message));
                }
                case ASSET_DESIGN_SINGLE -> {
                    if (job.getTargetId() == null) {
                        throw new IllegalStateException("任务缺少 assetId");
                    }
                    var privacySafe = "true".equalsIgnoreCase(
                            readPayloadField(job.getPayloadJson(), "privacySafe"));
                    jobService.reportProgress(job, 0, 1,
                            privacySafe ? "正在合规重生资产设计图（纯文生图）…" : "正在重新生成资产设计图…");
                    dramaForgeService.regenerateAssetDesign(
                            job.getProjectId(), job.getTargetId(), apiKey, privacySafe);
                    jobService.reportProgress(job, 1, 1, "资产设计图已更新");
                }
                case STORYBOARD -> {
                    jobService.extendLease(job);
                    dramaForgeService.generateStoryboards(job.getProjectId(), requireEpisode(job), apiKey,
                            (current, total, message) -> jobService.reportProgress(job, current, total, message));
                }
                case SHOT_STORYBOARD -> {
                    if (job.getTargetId() == null) {
                        throw new IllegalStateException("任务缺少 shotId");
                    }
                    jobService.reportProgress(job, 0, 1, "正在重新生成镜头分镜…");
                    dramaForgeService.regenerateShotStoryboard(
                            job.getProjectId(), requireEpisode(job), job.getTargetId(), apiKey);
                    jobService.reportProgress(job, 1, 1, "镜头分镜已更新");
                }
                case SHOT_VIDEO -> {
                    if (job.getTargetId() == null) {
                        throw new IllegalStateException("任务缺少 shotId");
                    }
                    jobService.reportProgress(job, 0, 1, "正在生成镜头视频…");
                    dramaForgeService.generateShotVideo(
                            job.getProjectId(), requireEpisode(job), job.getTargetId(), apiKey);
                    jobService.reportProgress(job, 1, 1, "镜头视频任务已提交");
                    tryEnqueueSync(job);
                }
                case GRID_STORYBOARD -> dramaForgeService.generateGridStoryboards(job.getProjectId(), requireEpisode(job), apiKey);
                case VIDEO -> {
                    dramaForgeService.generateVideos(job.getProjectId(), requireEpisode(job), apiKey,
                            (current, total, message) -> jobService.reportProgress(job, current, total, message));
                    tryEnqueueSync(job);
                }
                case SYNC_VIDEOS -> {
                    var synced = dramaForgeService.syncPendingVideos(job.getProjectId(), requireEpisode(job), apiKey);
                    jobService.reportProgress(
                            job,
                            synced,
                            Math.max(synced, 1),
                            synced > 0 ? "本轮已同步 " + synced + " 个视频" : "等待上游视频完成…");
                    // 只要还有未完成镜头就继续轮询（此前 synced>0 时会停链，剩余镜头无人再查）
                    if (hasPendingVideos(job.getEpisodeId())) {
                        // 降低对方舟状态查询的频率；完成后当前行会被 complete() 删除，不堆积
                        Thread.sleep(10_000L);
                        jobService.extendLease(job);
                        tryEnqueueSync(job);
                    }
                }
                case COMPOSE -> composeService.composeEpisode(job.getProjectId(), requireEpisode(job), apiKey);
                case EXPORT_PROJECT -> {
                    var export = exportService.exportProjectZip(job.getProjectId());
                    eventHub.publish(job.getProjectId(), "export_completed", export);
                }
                case EXPORT_JIANYING -> {
                    var export = exportService.exportJianyingDraft(job.getProjectId(), requireEpisode(job));
                    eventHub.publish(job.getProjectId(), "export_completed", export);
                }
                case WORKFLOW_RUN -> {
                    jobService.reportProgress(job, 0, 1, "正在推进流水线…");
                    UUID episodeHint = readPayloadUuid(job.getPayloadJson(), "episodeId");
                    workflowService.advancePipeline(job.getProjectId(), apiKey, episodeHint);
                    jobService.reportProgress(job, 1, 1, "已提交下一步任务");
                }
                default -> throw new IllegalStateException("未知任务类型: " + job.getJobType());
            }
    }

    private UUID requireEpisode(DramaForgeJob job) {
        if (job.getEpisodeId() == null) {
            throw new IllegalStateException("任务缺少 episodeId");
        }
        return job.getEpisodeId();
    }

    private String resolveApiKey(DramaForgeJob job) {
        var headerApiKey = readPayloadField(job.getPayloadJson(), "apiKey");
        return projectApiKeyResolver.resolve(job.getProjectId(), headerApiKey);
    }

    private String resolveArkApiKey(DramaForgeJob job) {
        var fromPayload = readPayloadField(job.getPayloadJson(), "arkApiKey");
        var userKey = projectApiKeyResolver.resolveOwnerArkApiKey(job.getProjectId());
        return arkApiKeyResolver.resolve(fromPayload, userKey);
    }

    private String readPayloadField(String payloadJson, String field) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(payloadJson);
            if (node.has(field) && !node.get(field).isNull()) {
                var text = node.get(field).asText();
                return text != null && !text.isBlank() ? text : null;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private UUID readPayloadUuid(String payloadJson, String field) {
        var text = readPayloadField(payloadJson, field);
        if (text == null) {
            return null;
        }
        try {
            return UUID.fromString(text);
        } catch (Exception ex) {
            return null;
        }
    }

    private void tryEnqueueSync(DramaForgeJob job) {
        if (jobService.hasActiveSyncJob(job.getProjectId())) {
            return;
        }
        jobService.enqueue(
                job.getProjectId(),
                DramaForgeJobType.SYNC_VIDEOS,
                null,
                job.getEpisodeId(),
                job.getPayloadJson());
    }

    private boolean hasPendingVideos(UUID episodeId) {
        return dramaForgeService.hasPendingVideoSync(episodeId);
    }
}
