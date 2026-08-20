package com.dreamreel.api.dramaforge.service;

import com.dreamreel.api.dramaforge.domain.*;
import com.dreamreel.api.dramaforge.dto.DramaForgeDtos.PipelineOverviewResponse;
import com.dreamreel.api.dramaforge.repository.DramaForgeAssetRepository;
import com.dreamreel.api.dramaforge.repository.DramaForgeEpisodeRepository;
import com.dreamreel.api.dramaforge.repository.DramaForgeCompositionRepository;
import com.dreamreel.api.dramaforge.repository.DramaForgeJobRepository;
import com.dreamreel.api.dramaforge.repository.DramaForgeShotRepository;
import com.dreamreel.api.domain.GenerationJob;
import com.dreamreel.api.domain.GenerationStatus;
import com.dreamreel.api.repository.GenerationJobRepository;
import com.dreamreel.api.service.MediaStorageService;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

@Component
public class DramaForgeStatusCalculator {

    private final DramaForgeAssetRepository assetRepository;
    private final DramaForgeEpisodeRepository episodeRepository;
    private final DramaForgeShotRepository shotRepository;
    private final DramaForgeCompositionRepository compositionRepository;
    private final DramaForgeJobRepository jobRepository;
    private final GenerationJobRepository generationJobRepository;
    private final MediaStorageService mediaStorageService;
    private final DramaForgeConsistencyService consistencyService;

    public DramaForgeStatusCalculator(
            DramaForgeAssetRepository assetRepository,
            DramaForgeEpisodeRepository episodeRepository,
            DramaForgeShotRepository shotRepository,
            DramaForgeCompositionRepository compositionRepository,
            DramaForgeJobRepository jobRepository,
            GenerationJobRepository generationJobRepository,
            MediaStorageService mediaStorageService,
            DramaForgeConsistencyService consistencyService) {
        this.assetRepository = assetRepository;
        this.episodeRepository = episodeRepository;
        this.shotRepository = shotRepository;
        this.compositionRepository = compositionRepository;
        this.jobRepository = jobRepository;
        this.generationJobRepository = generationJobRepository;
        this.mediaStorageService = mediaStorageService;
        this.consistencyService = consistencyService;
    }

    public PipelineOverviewResponse calculate(UUID projectId, DramaForgeConfig config) {
        var assetCounts = new LinkedHashMap<String, Long>();
        for (var type : DramaForgeAssetType.values()) {
            assetCounts.put(type.name().toLowerCase(), assetRepository.countByProjectIdAndType(projectId, type));
        }

        var episodes = episodeRepository.findByProjectIdOrderByEpisodeNumberAsc(projectId);
        long shotCount = 0;
        long storyboardDone = 0;
        long videoDone = 0;
        var allShots = new ArrayList<DramaForgeShot>();
        for (var episode : episodes) {
            var shots = shotRepository.findByEpisodeIdOrderByShotNumberAsc(episode.getId());
            allShots.addAll(shots);
            shotCount += shots.size();
            for (var shot : shots) {
                var hasFirst = (shot.getFirstFrameUrl() != null && !shot.getFirstFrameUrl().isBlank())
                        || (shot.getStoryboardUrl() != null && !shot.getStoryboardUrl().isBlank());
                var hasLast = shot.getLastFrameUrl() != null && !shot.getLastFrameUrl().isBlank();
                if (hasFirst && hasLast) {
                    storyboardDone++;
                }
                if (shot.getStatus() == DramaForgeShotStatus.VIDEO_DONE) {
                    videoDone++;
                }
            }
        }

        long totalAssets = assetCounts.values().stream().mapToLong(Long::longValue).sum();
        long assetsWithImage = assetRepository.findByProjectIdOrderBySortOrderAscNameAsc(projectId).stream()
                .filter(asset -> asset.getReferenceImageUrl() != null && !asset.getReferenceImageUrl().isBlank())
                .count();

        var stage = resolveStage(projectId, config, episodes, totalAssets, shotCount, assetsWithImage, storyboardDone, videoDone);
        var progress = resolveProgress(stage, shotCount, storyboardDone, videoDone);
        var nextActions = resolveNextActions(stage, config, totalAssets, episodes, shotCount, assetsWithImage, storyboardDone, videoDone);
        var assets = assetRepository.findByProjectIdOrderBySortOrderAscNameAsc(projectId);
        var consistency = consistencyService.buildReport(projectId, assets, episodes, allShots);

        return new PipelineOverviewResponse(
                projectId,
                stage.name().toLowerCase(),
                progress,
                config.getContentMode().name().toLowerCase(),
                config.getGenerationMode().name().toLowerCase(),
                assetCounts,
                episodes.size(),
                shotCount,
                storyboardDone,
                videoDone,
                nextActions,
                consistency,
                config.getAssetsLockedAt(),
                config.getStoryboardLockedAt(),
                isScriptLocked(episodes),
                isStoryboardLocked(episodes, config)
        );
    }

    /**
     * 本地 generation_jobs 已终态时回写镜头状态，不访问方舟。
     * 用于修复「视频已完成但镜头仍停在 STORYBOARD_DONE / 生成中」的脏数据，
     * 以及「等待超时误标 FAILED 但任务其实已完成」的脏数据。
     *
     * @return true 表示已根据本地任务改写了镜头状态
     */
    public boolean applyLocalVideoJobTerminalStatus(DramaForgeShot shot) {
        if (shot.getVideoJobId() == null) {
            return false;
        }
        if (shot.getStatus() == DramaForgeShotStatus.VIDEO_DONE) {
            return false;
        }
        var job = generationJobRepository.findById(shot.getVideoJobId()).orElse(null);
        if (job == null) {
            return false;
        }
        if (job.getStatus() == GenerationStatus.COMPLETED) {
            shot.setStatus(DramaForgeShotStatus.VIDEO_DONE);
            shot.setErrorMessage(null);
            return true;
        }
        if (job.getStatus() == GenerationStatus.FAILED) {
            if (shot.getStatus() == DramaForgeShotStatus.FAILED
                    && shot.getErrorMessage() != null
                    && !shot.getErrorMessage().isBlank()) {
                return false;
            }
            var err = job.getErrorMessage() != null && !job.getErrorMessage().isBlank()
                    ? job.getErrorMessage().trim()
                    : "视频生成失败";
            shot.setStatus(DramaForgeShotStatus.FAILED);
            shot.setErrorMessage(err.length() > 2000 ? err.substring(0, 2000) : err);
            return true;
        }
        return false;
    }

    public String resolveVideoUrl(DramaForgeShot shot) {
        return resolveVideoUrl(shot, true);
    }

    /**
     * @param ensureStored false 时仅读已有 outputUrl，不做远端转存（列表接口必须用这个，否则会按镜头串行下载视频）。
     */
    public String resolveVideoUrl(DramaForgeShot shot, boolean ensureStored) {
        if (shot.getVideoJobId() == null) {
            return null;
        }
        return generationJobRepository.findById(shot.getVideoJobId())
                .filter(job -> job.getStatus() == GenerationStatus.COMPLETED)
                .map(job -> {
                    if (!ensureStored) {
                        return job.getOutputUrl();
                    }
                    var before = job.getOutputUrl();
                    mediaStorageService.ensureStoredOutput(job);
                    if (before == null || !before.equals(job.getOutputUrl())
                            || (job.getErrorMessage() != null && job.getErrorMessage().startsWith("媒体转存未完成"))) {
                        generationJobRepository.save(job);
                    }
                    return job.getOutputUrl();
                })
                .orElse(null);
    }

    /** 失败镜头优先用落库原因；成功态不回挂历史失败任务。 */
    public String resolveErrorMessage(DramaForgeShot shot) {
        if (shot.getErrorMessage() != null && !shot.getErrorMessage().isBlank()) {
            return shot.getErrorMessage().trim();
        }
        if (shot.getStatus() == DramaForgeShotStatus.STORYBOARD_DONE
                || shot.getStatus() == DramaForgeShotStatus.VIDEO_DONE) {
            return null;
        }
        if (shot.getVideoJobId() != null) {
            var fromGen = generationJobRepository.findById(shot.getVideoJobId())
                    .map(GenerationJob::getErrorMessage)
                    .filter(msg -> msg != null && !msg.isBlank())
                    .map(String::trim)
                    .orElse(null);
            if (fromGen != null) {
                return fromGen;
            }
        }
        if (shot.getStatus() != DramaForgeShotStatus.FAILED) {
            return null;
        }
        return jobRepository.findFirstByTargetIdAndStatusOrderByCreatedAtDesc(
                        shot.getId(), DramaForgeJobStatus.FAILED)
                .map(DramaForgeJob::getErrorMessage)
                .filter(msg -> msg != null && !msg.isBlank())
                .map(String::trim)
                .orElse(null);
    }

    private DramaForgePipelineStage resolveStage(
            UUID projectId,
            DramaForgeConfig config,
            List<DramaForgeEpisode> episodes,
            long totalAssets,
            long shotCount,
            long assetsWithImage,
            long storyboardDone,
            long videoDone) {
        if (config.getSourceText() == null || config.getSourceText().isBlank()) {
            return DramaForgePipelineStage.STORY_INPUT;
        }
        if (!isScriptLocked(episodes)) {
            return DramaForgePipelineStage.SCRIPT_LOCKED;
        }
        if (totalAssets == 0 || assetsWithImage < totalAssets || config.getAssetsLockedAt() == null) {
            return DramaForgePipelineStage.ASSETS_LOCKED;
        }
        if (shotCount == 0 || videoDone < shotCount) {
            return DramaForgePipelineStage.VIDEO_DONE;
        }
        var hasCompletedComposition = compositionRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .anyMatch(c -> "completed".equals(c.getStatus()) && c.getOutputUrl() != null);
        if (hasCompletedComposition) {
            return DramaForgePipelineStage.COMPOSED;
        }
        return DramaForgePipelineStage.VIDEO_DONE;
    }

    private boolean isScriptLocked(List<DramaForgeEpisode> episodes) {
        return episodes.stream().anyMatch(ep ->
                ep.getScriptLockedAt() != null
                        && ep.getScriptJson() != null
                        && !ep.getScriptJson().isBlank()
                        && shotRepository.countByEpisodeId(ep.getId()) > 0);
    }

    private boolean isStoryboardLocked(List<DramaForgeEpisode> episodes, DramaForgeConfig config) {
        if (config.getStoryboardLockedAt() != null) {
            return true;
        }
        return episodes.stream().anyMatch(ep -> ep.getStoryboardLockedAt() != null);
    }

    private int resolveProgress(
            DramaForgePipelineStage stage,
            long shotCount,
            long storyboardDone,
            long videoDone) {
        return switch (stage) {
            case STORY_INPUT -> 5;
            case SCRIPT_LOCKED -> 20;
            case ASSETS_LOCKED -> 40;
            case VIDEO_DONE -> {
                if (shotCount <= 0) {
                    yield 85;
                }
                yield (int) Math.round(100.0 * Math.min(1.0, (double) videoDone / shotCount));
            }
            case COMPOSED -> 100;
        };
    }

    private List<String> resolveNextActions(
            DramaForgePipelineStage stage,
            DramaForgeConfig config,
            long totalAssets,
            List<DramaForgeEpisode> episodes,
            long shotCount,
            long assetsWithImage,
            long storyboardDone,
            long videoDone) {
        var actions = new ArrayList<String>();
        switch (stage) {
            case STORY_INPUT -> actions.add("配置画幅/模型并粘贴原文后保存");
            case SCRIPT_LOCKED -> {
                actions.add("按集编辑正文并解析为剧本/镜头");
                actions.add("确认本集剧本后进入建资产");
            }
            case ASSETS_LOCKED -> {
                if (totalAssets == 0) {
                    actions.add("从已确认剧本提取角色、场景与道具");
                } else if (assetsWithImage < totalAssets) {
                    actions.add("为资产生成定妆候选并选定");
                } else {
                    actions.add("确认资产库后进入出成片");
                }
            }
            case VIDEO_DONE -> {
                if (videoDone < shotCount) {
                    actions.add("按集生成镜头视频");
                } else {
                    actions.add("进入 AI 剪辑：按集合成与导出");
                }
            }
            case COMPOSED -> actions.add("流水线已完成，可继续按集微调");
        }
        if (config.getStylePrompt() == null || config.getStylePrompt().isBlank()) {
            actions.add("可选：设置全局风格参考提示词");
        }
        return actions;
    }
}
