package com.dreamreel.api.dramaforge.service;

import com.dreamreel.api.dramaforge.domain.DramaForgeConfig;
import com.dreamreel.api.dramaforge.domain.DramaForgeEpisode;
import com.dreamreel.api.dramaforge.dto.DramaForgeDtos.EpisodeResponse;
import com.dreamreel.api.dramaforge.dto.DramaForgeDtos.PipelineOverviewResponse;
import com.dreamreel.api.dramaforge.repository.DramaForgeAssetRepository;
import com.dreamreel.api.dramaforge.repository.DramaForgeConfigRepository;
import com.dreamreel.api.dramaforge.repository.DramaForgeEpisodeRepository;
import com.dreamreel.api.dramaforge.repository.DramaForgeShotRepository;
import com.dreamreel.api.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
public class DramaForgeWorkflowLockService {

    private final DramaForgeConfigRepository configRepository;
    private final DramaForgeEpisodeRepository episodeRepository;
    private final DramaForgeAssetRepository assetRepository;
    private final DramaForgeShotRepository shotRepository;
    private final DramaForgeStatusCalculator statusCalculator;

    public DramaForgeWorkflowLockService(
            DramaForgeConfigRepository configRepository,
            DramaForgeEpisodeRepository episodeRepository,
            DramaForgeAssetRepository assetRepository,
            DramaForgeShotRepository shotRepository,
            DramaForgeStatusCalculator statusCalculator) {
        this.configRepository = configRepository;
        this.episodeRepository = episodeRepository;
        this.assetRepository = assetRepository;
        this.shotRepository = shotRepository;
        this.statusCalculator = statusCalculator;
    }

    public EpisodeResponse lockScript(UUID projectId, UUID episodeId) {
        var episode = requireEpisode(projectId, episodeId);
        if (episode.getScriptJson() == null || episode.getScriptJson().isBlank()) {
            throw new IllegalStateException("请先保存剧本内容");
        }
        if (shotRepository.countByEpisodeId(episodeId) == 0) {
            throw new IllegalStateException("请先解析为镜头后再确认剧本");
        }
        episode.setScriptLockedAt(Instant.now());
        episode = episodeRepository.save(episode);
        return EpisodeResponse.from(episode, shotRepository.countByEpisodeId(episodeId));
    }

    public PipelineOverviewResponse lockAssets(UUID projectId) {
        var config = requireConfig(projectId);
        var assets = assetRepository.findByProjectIdOrderBySortOrderAscNameAsc(projectId);
        if (assets.isEmpty()) {
            throw new IllegalStateException("请先提取或创建资产");
        }
        var missing = assets.stream()
                .filter(a -> a.getReferenceImageUrl() == null || a.getReferenceImageUrl().isBlank())
                .map(a -> a.getName())
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("以下资产尚未选定定妆图：" + String.join("、", missing));
        }
        config.setAssetsLockedAt(Instant.now());
        configRepository.save(config);
        return statusCalculator.calculate(projectId, config);
    }

    public EpisodeResponse lockStoryboard(UUID projectId, UUID episodeId) {
        var config = requireConfig(projectId);
        var episode = requireEpisode(projectId, episodeId);
        if (config.getAssetsLockedAt() == null) {
            throw new IllegalStateException("请先确认资产库");
        }
        var shots = shotRepository.findByEpisodeIdOrderByShotNumberAsc(episodeId);
        if (shots.isEmpty()) {
            throw new IllegalStateException("本集尚无镜头");
        }
        var missing = shots.stream()
                .filter(s -> s.getStoryboardUrl() == null || s.getStoryboardUrl().isBlank())
                .map(s -> "镜头" + s.getShotNumber())
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("以下镜头缺少分镜图：" + String.join("、", missing));
        }
        episode.setStoryboardLockedAt(Instant.now());
        episode = episodeRepository.save(episode);
        return EpisodeResponse.from(episode, shots.size());
    }

    private DramaForgeConfig requireConfig(UUID projectId) {
        return configRepository.findByProjectId(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("DramaForge 配置不存在"));
    }

    private DramaForgeEpisode requireEpisode(UUID projectId, UUID episodeId) {
        return episodeRepository.findByIdAndProjectId(episodeId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("剧集不存在"));
    }
}
