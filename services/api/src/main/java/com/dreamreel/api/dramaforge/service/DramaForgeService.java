package com.dreamreel.api.dramaforge.service;

import com.dreamreel.api.dramaforge.domain.*;
import com.dreamreel.api.dramaforge.dto.DramaForgeDtos.*;
import com.dreamreel.api.dramaforge.repository.*;
import com.dreamreel.api.client.TokenFreeClient;
import com.dreamreel.api.client.VolcengineTtsClient;
import com.dreamreel.api.config.TokenFreeProperties;
import com.dreamreel.api.domain.GenerationStatus;
import com.dreamreel.api.domain.Project;
import com.dreamreel.api.dto.CreateImageGenerationRequest;
import com.dreamreel.api.dto.CreateVideoGenerationRequest;
import com.dreamreel.api.dto.ImageGenerationResponse;
import com.dreamreel.api.exception.ResourceNotFoundException;
import com.dreamreel.api.repository.GenerationJobRepository;
import com.dreamreel.api.repository.ProjectRepository;
import com.dreamreel.api.security.CurrentUserService;
import com.dreamreel.api.security.UserPrincipal;
import com.dreamreel.api.service.ImageGenerationService;
import com.dreamreel.api.service.ProjectApiKeyResolver;
import com.dreamreel.api.service.UploadStorageService;
import com.dreamreel.api.service.VideoGenerationService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Lazy;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional
public class DramaForgeService {

    private final DramaForgeConfigRepository configRepository;
    private final DramaForgeAssetRepository assetRepository;
    private final DramaForgeEpisodeRepository episodeRepository;
    private final DramaForgeShotRepository shotRepository;
    private final ProjectRepository projectRepository;
    private final GenerationJobRepository generationJobRepository;
    private final CurrentUserService currentUserService;
    private final DramaForgeStatusCalculator statusCalculator;
    private final DramaForgeVersionService versionService;
    private final DramaForgeAssetVersionService assetVersionService;
    private final DramaForgeShotAssetPlanner shotAssetPlanner;
    private final ImageGenerationService imageGenerationService;
    private final VideoGenerationService videoGenerationService;
    private final ObjectMapper objectMapper;
    private final TokenFreeProperties tokenFreeProperties;
    private final TokenFreeClient tokenFreeClient;
    private final VolcengineTtsClient volcengineTtsClient;
    private final ProjectApiKeyResolver projectApiKeyResolver;
    private final UploadStorageService uploadStorageService;
    private final DramaForgeConsistencyService consistencyService;
    private final DramaForgeVideoContinuityService videoContinuityService;
    private final DramaForgeShotAudioRemasterService shotAudioRemasterService;
    private final DramaForgeImportService importService;
    private final EntityManager entityManager;
    private final DramaForgeService self;

    public DramaForgeService(
            DramaForgeConfigRepository configRepository,
            DramaForgeAssetRepository assetRepository,
            DramaForgeEpisodeRepository episodeRepository,
            DramaForgeShotRepository shotRepository,
            ProjectRepository projectRepository,
            GenerationJobRepository generationJobRepository,
            CurrentUserService currentUserService,
            DramaForgeStatusCalculator statusCalculator,
            DramaForgeVersionService versionService,
            DramaForgeAssetVersionService assetVersionService,
            DramaForgeShotAssetPlanner shotAssetPlanner,
            ImageGenerationService imageGenerationService,
            VideoGenerationService videoGenerationService,
            ObjectMapper objectMapper,
            TokenFreeProperties tokenFreeProperties,
            TokenFreeClient tokenFreeClient,
            VolcengineTtsClient volcengineTtsClient,
            ProjectApiKeyResolver projectApiKeyResolver,
            UploadStorageService uploadStorageService,
            DramaForgeConsistencyService consistencyService,
            DramaForgeVideoContinuityService videoContinuityService,
            DramaForgeShotAudioRemasterService shotAudioRemasterService,
            DramaForgeImportService importService,
            EntityManager entityManager,
            @Lazy DramaForgeService self) {
        this.configRepository = configRepository;
        this.assetRepository = assetRepository;
        this.episodeRepository = episodeRepository;
        this.shotRepository = shotRepository;
        this.projectRepository = projectRepository;
        this.generationJobRepository = generationJobRepository;
        this.currentUserService = currentUserService;
        this.statusCalculator = statusCalculator;
        this.versionService = versionService;
        this.assetVersionService = assetVersionService;
        this.shotAssetPlanner = shotAssetPlanner;
        this.imageGenerationService = imageGenerationService;
        this.videoGenerationService = videoGenerationService;
        this.objectMapper = objectMapper;
        this.tokenFreeProperties = tokenFreeProperties;
        this.tokenFreeClient = tokenFreeClient;
        this.volcengineTtsClient = volcengineTtsClient;
        this.projectApiKeyResolver = projectApiKeyResolver;
        this.uploadStorageService = uploadStorageService;
        this.consistencyService = consistencyService;
        this.videoContinuityService = videoContinuityService;
        this.shotAudioRemasterService = shotAudioRemasterService;
        this.importService = importService;
        this.entityManager = entityManager;
        this.self = self;
    }

    @Transactional(readOnly = true)
    public PipelineOverviewResponse getOverview(UUID projectId) {
        var config = requireConfig(projectId);
        return statusCalculator.calculate(projectId, config);
    }

    @Transactional(readOnly = true)
    public ConfigResponse getConfig(UUID projectId) {
        return ConfigResponse.from(requireConfig(projectId));
    }

    public ConfigResponse updateConfig(UUID projectId, UpdateConfigRequest request) {
        var config = requireConfig(projectId);
        if (request.contentMode() != null) {
            config.setContentMode(parseContentMode(request.contentMode()));
        }
        if (request.generationMode() != null) {
            config.setGenerationMode(parseGenerationMode(request.generationMode()));
        }
        if (request.aspectRatio() != null) {
            config.setAspectRatio(parseAspectRatio(request.aspectRatio()));
        }
        if (request.imageBackend() != null) {
            config.setImageBackend(request.imageBackend());
        }
        if (request.videoBackend() != null) {
            config.setVideoBackend(request.videoBackend());
        }
        if (request.textBackend() != null) {
            config.setTextBackend(request.textBackend());
        }
        if (request.imageQuality() != null && !request.imageQuality().isBlank()) {
            config.setImageQuality(com.dreamreel.api.util.MediaSizeHelper.normalizeResolution(request.imageQuality()));
        }
        if (request.videoQuality() != null && !request.videoQuality().isBlank()) {
            config.setVideoQuality(com.dreamreel.api.util.MediaSizeHelper.normalizeResolution(request.videoQuality()));
        }
        if (request.stylePrompt() != null) {
            config.setStylePrompt(request.stylePrompt());
        }
        if (request.sourceText() != null) {
            config.setSourceText(request.sourceText());
        }
        if (request.projectSummary() != null) {
            config.setProjectSummary(request.projectSummary());
        }
        if (request.worldview() != null) {
            config.setWorldview(request.worldview());
        }
        if (request.colorGradePreset() != null && !request.colorGradePreset().isBlank()) {
            config.setColorGradePreset(request.colorGradePreset().trim().toLowerCase(Locale.ROOT));
        }
        if (request.mixDialogueAudioInCompose() != null) {
            config.setMixDialogueAudioInCompose(request.mixDialogueAudioInCompose());
        }
        if (request.bgmUrl() != null) {
            config.setBgmUrl(request.bgmUrl().isBlank() ? null : request.bgmUrl().trim());
        }
        if (request.bgmVolume() != null) {
            config.setBgmVolume(Math.max(0.05, Math.min(0.5, request.bgmVolume())));
        }
        if (request.lipSyncEnabled() != null) {
            config.setLipSyncEnabled(request.lipSyncEnabled());
        }
        if (request.lipSyncEndpoint() != null) {
            config.setLipSyncEndpoint(request.lipSyncEndpoint().isBlank()
                    ? null
                    : request.lipSyncEndpoint().trim());
        }
        if (request.preferModelMultiShot() != null) {
            config.setPreferModelMultiShot(request.preferModelMultiShot());
        }
        return ConfigResponse.from(configRepository.save(config));
    }

    @Transactional(readOnly = true)
    public List<AssetResponse> listAssets(UUID projectId) {
        requireOwnedProject(projectId);
        return assetRepository.findByProjectIdOrderBySortOrderAscNameAsc(projectId).stream()
                .map(AssetResponse::from)
                .toList();
    }

    public AssetResponse createAsset(UUID projectId, CreateAssetRequest request) {
        requireOwnedProject(projectId);
        var asset = new DramaForgeAsset();
        asset.setProjectId(projectId);
        asset.setType(parseAssetType(request.type()));
        asset.setName(request.name().trim());
        asset.setDescription(request.description());
        asset.setDesignPrompt(request.designPrompt());
        asset.setReferenceImageUrl(request.referenceImageUrl());
        asset.setVoiceLabel(request.voiceLabel());
        asset.setVoiceSampleUrl(request.voiceSampleUrl());
        asset.setVoiceSpeakerId(request.voiceSpeakerId());
        if (request.identityLockStrength() != null) {
            asset.setIdentityLockStrength(Math.max(0, Math.min(100, request.identityLockStrength())));
        }
        if (request.loraRef() != null && !request.loraRef().isBlank()) {
            asset.setLoraRef(request.loraRef().trim());
        }
        asset.setSortOrder(request.sortOrder() != null ? request.sortOrder() : 0);
        return AssetResponse.from(assetRepository.save(asset));
    }

    public AssetResponse updateAsset(UUID projectId, UUID assetId, UpdateAssetRequest request) {
        var asset = requireAsset(projectId, assetId);
        if (request.name() != null) {
            asset.setName(request.name().trim());
        }
        if (request.description() != null) {
            asset.setDescription(request.description());
        }
        if (request.designPrompt() != null) {
            asset.setDesignPrompt(request.designPrompt());
        }
        if (request.referenceImageUrl() != null) {
            asset.setReferenceImageUrl(request.referenceImageUrl());
        }
        if (request.voiceLabel() != null) {
            asset.setVoiceLabel(request.voiceLabel());
        }
        if (request.voiceSampleUrl() != null) {
            asset.setVoiceSampleUrl(request.voiceSampleUrl());
        }
        if (request.voiceSpeakerId() != null) {
            asset.setVoiceSpeakerId(request.voiceSpeakerId());
        }
        if (request.identityLockStrength() != null) {
            asset.setIdentityLockStrength(Math.max(0, Math.min(100, request.identityLockStrength())));
        }
        if (request.loraRef() != null) {
            asset.setLoraRef(request.loraRef().isBlank() ? null : request.loraRef().trim());
        }
        if (request.sortOrder() != null) {
            asset.setSortOrder(request.sortOrder());
        }
        return AssetResponse.from(assetRepository.save(asset));
    }

    public void deleteAsset(UUID projectId, UUID assetId) {
        var asset = requireAsset(projectId, assetId);
        assetVersionService.deleteByAssetId(assetId);
        assetRepository.delete(asset);
    }

    @Transactional(readOnly = true)
    public List<EpisodeResponse> listEpisodes(UUID projectId) {
        requireOwnedProject(projectId);
        return episodeRepository.findByProjectIdOrderByEpisodeNumberAsc(projectId).stream()
                .map(episode -> EpisodeResponse.from(episode, shotRepository.countByEpisodeId(episode.getId())))
                .toList();
    }

    public EpisodeResponse createEpisode(UUID projectId, CreateEpisodeRequest request) {
        requireOwnedProject(projectId);
        var episode = new DramaForgeEpisode();
        episode.setProjectId(projectId);
        episode.setTitle(request.title().trim());
        episode.setScriptJson(request.scriptJson());
        episode.setEpisodeNumber(resolveEpisodeNumber(projectId, request.episodeNumber()));
        return EpisodeResponse.from(episodeRepository.save(episode), 0);
    }

    public EpisodeResponse updateEpisode(UUID projectId, UUID episodeId, UpdateEpisodeRequest request) {
        var episode = requireEpisode(projectId, episodeId);
        if (request.title() != null) {
            episode.setTitle(request.title().trim());
        }
        if (request.scriptJson() != null) {
            episode.setScriptJson(request.scriptJson());
        }
        if (request.timelineJson() != null) {
            episode.setTimelineJson(request.timelineJson());
        }
        return EpisodeResponse.from(
                episodeRepository.save(episode),
                shotRepository.countByEpisodeId(episode.getId()));
    }

    public void deleteEpisode(UUID projectId, UUID episodeId) {
        var episode = requireEpisode(projectId, episodeId);
        shotRepository.findByEpisodeIdOrderByShotNumberAsc(episode.getId())
                .forEach(shotRepository::delete);
        episodeRepository.delete(episode);
    }

    @Transactional(readOnly = true)
    public List<ShotResponse> listShots(UUID projectId, UUID episodeId) {
        var episode = requireEpisode(projectId, episodeId);
        var config = requireConfig(projectId);
        var assets = assetRepository.findByProjectIdOrderBySortOrderAscNameAsc(projectId);
        var shots = shotRepository.findByEpisodeIdOrderByShotNumberAsc(episodeId);
        var results = new ArrayList<ShotResponse>();
        String previousLastFrameUrl = null;
        for (var shot : shots) {
            results.add(toShotResponse(shot, config, assets, episode, previousLastFrameUrl));
            if (shot.getLastFrameUrl() != null && !shot.getLastFrameUrl().isBlank()) {
                previousLastFrameUrl = shot.getLastFrameUrl().trim();
            }
        }
        return results;
    }

    @Transactional(readOnly = true)
    public ShotResponse getShot(UUID projectId, UUID episodeId, UUID shotId) {
        var episode = requireEpisode(projectId, episodeId);
        var config = requireConfig(projectId);
        var assets = assetRepository.findByProjectIdOrderBySortOrderAscNameAsc(projectId);
        var shot = requireShot(episodeId, shotId);
        return toShotResponse(shot, config, assets, episode, findPreviousShotLastFrameUrl(shot));
    }

    /** 一次性补齐旧镜头规划描述（勿在列表接口里反复写库） */
    @Transactional
    public int materializeEpisodePlanningPrompts(UUID projectId, UUID episodeId) {
        requireEpisode(projectId, episodeId);
        var assets = assetRepository.findByProjectIdOrderBySortOrderAscNameAsc(projectId);
        var shots = shotRepository.findByEpisodeIdOrderByShotNumberAsc(episodeId);
        var updated = 0;
        for (var shot : shots) {
            var before = shot.getDescription();
            ensurePlanningPromptMaterialized(shot, assets);
            if (!java.util.Objects.equals(before, shot.getDescription())) {
                updated++;
            }
        }
        return updated;
    }

    public ShotResponse createShot(UUID projectId, UUID episodeId, CreateShotRequest request) {
        requireEpisode(projectId, episodeId);
        var shot = new DramaForgeShot();
        shot.setEpisodeId(episodeId);
        shot.setDescription(request.description().trim());
        shot.setDialogue(request.dialogue());
        shot.setCameraNote(request.cameraNote());
        shot.setCharacterRefsJson(writeJson(request.characterRefs() != null ? request.characterRefs() : List.of()));
        shot.setShotNumber(resolveShotNumber(episodeId, request.shotNumber()));
        return toShotResponse(shotRepository.save(shot));
    }

    public ShotResponse updateShot(UUID projectId, UUID episodeId, UUID shotId, UpdateShotRequest request) {
        var shot = requireShot(episodeId, shotId);
        requireEpisode(projectId, episodeId);
        if (request.description() != null) {
            shot.setDescription(request.description().trim());
        }
        if (request.dialogue() != null) {
            shot.setDialogue(request.dialogue());
        }
        if (request.cameraNote() != null) {
            shot.setCameraNote(request.cameraNote());
        }
        if (request.characterRefs() != null) {
            shot.setCharacterRefsJson(writeJson(request.characterRefs()));
        }
        if (request.sceneRef() != null) {
            shot.setSceneRef(request.sceneRef().isBlank() ? null : request.sceneRef().trim());
        }
        if (request.propRefs() != null) {
            shot.setPropRefsJson(writeJson(request.propRefs()));
        }
        if (request.storyboardUrl() != null) {
            versionService.archiveCurrent(shot);
            shot.setStoryboardUrl(request.storyboardUrl());
            if (shot.getStatus() == DramaForgeShotStatus.PENDING) {
                shot.setStatus(DramaForgeShotStatus.STORYBOARD_DONE);
            }
        }
        if (request.status() != null) {
            shot.setStatus(parseShotStatus(request.status()));
        }
        if (request.durationSeconds() != null) {
            shot.setDurationSeconds(Math.max(2, Math.min(15, request.durationSeconds())));
        }
        if (request.forceCharacterBinding() != null) {
            shot.setForceCharacterBinding(request.forceCharacterBinding());
        }
        if (request.referenceVideoMode() != null) {
            shot.setReferenceVideoMode(request.referenceVideoMode().isBlank()
                    ? "auto"
                    : request.referenceVideoMode().trim().toLowerCase(Locale.ROOT));
        }
        if (request.referenceVideoUrl() != null) {
            shot.setReferenceVideoUrl(request.referenceVideoUrl().isBlank()
                    ? null
                    : request.referenceVideoUrl().trim());
        }
        if (request.firstFrameUrl() != null) {
            shot.setFirstFrameUrl(request.firstFrameUrl().isBlank() ? null : request.firstFrameUrl().trim());
        }
        if (request.lastFrameUrl() != null) {
            shot.setLastFrameUrl(request.lastFrameUrl().isBlank() ? null : request.lastFrameUrl().trim());
        }
        if (request.storyboardPrompt() != null) {
            shot.setStoryboardPrompt(request.storyboardPrompt().isBlank()
                    ? null
                    : request.storyboardPrompt().trim());
        }
        if (request.dialogueAudioUrl() != null) {
            shot.setDialogueAudioUrl(request.dialogueAudioUrl().isBlank()
                    ? null
                    : request.dialogueAudioUrl().trim());
        }
        if (request.qaStatus() != null && !request.qaStatus().isBlank()) {
            shot.setQaStatus(request.qaStatus().trim().toLowerCase(Locale.ROOT));
        }
        if (request.modelMultiShot() != null) {
            shot.setModelMultiShot(request.modelMultiShot());
        }
        if (request.multiShotTemplate() != null) {
            shot.setMultiShotTemplate(request.multiShotTemplate().isBlank()
                    ? null
                    : request.multiShotTemplate().trim().toLowerCase(Locale.ROOT));
        }
        return toShotResponse(shotRepository.save(shot));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public EpisodeResponse structureEpisodeScriptFromBody(UUID projectId, UUID episodeId, String apiKeyHeader) {
        var episode = requireEpisode(projectId, episodeId);
        var apiKey = projectApiKeyResolver.resolve(projectId, apiKeyHeader);
        var draft = episode.getScriptJson();
        if (draft == null || draft.isBlank()) {
            throw new IllegalStateException("本集正文为空");
        }
        importService.structureEpisodeScriptFromBody(projectId, episodeId, draft, apiKey);
        entityManager.clear();
        episode = requireEpisode(projectId, episodeId);
        return EpisodeResponse.from(episode, shotRepository.countByEpisodeId(episodeId));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public EpisodeResponse structureEpisodeShotsFromScript(UUID projectId, UUID episodeId, String apiKeyHeader) {
        var apiKey = projectApiKeyResolver.resolve(projectId, apiKeyHeader);
        importService.structureEpisodeShotsFromScript(projectId, episodeId, apiKey);
        entityManager.clear();
        var episode = requireEpisode(projectId, episodeId);
        return EpisodeResponse.from(episode, shotRepository.countByEpisodeId(episodeId));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<ShotResponse> parseShotsFromScript(UUID projectId, UUID episodeId) {
        return parseShotsFromScript(projectId, episodeId, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<ShotResponse> parseShotsFromScript(UUID projectId, UUID episodeId, String apiKeyHeader) {
        var episode = requireEpisode(projectId, episodeId);
        if (episode.getScriptJson() == null || episode.getScriptJson().isBlank()) {
            throw new IllegalStateException("剧集缺少剧本，请先在「剧集」中编辑并保存");
        }

        var script = episode.getScriptJson();
        JsonNode root = null;
        try {
            root = objectMapper.readTree(script);
        } catch (Exception ignored) {
            // 纯文本剧本，下面走结构化
        }
        if (!DramaForgeImportService.hasShotStructure(root)) {
            if (DramaForgeImportService.hasScriptStructure(root)) {
                var apiKey = projectApiKeyResolver.resolve(projectId, apiKeyHeader);
                if (apiKey == null || apiKey.isBlank()) {
                    throw new IllegalStateException("剧本已有场次但未拆镜，请配置 API Key 后点击「剧本→镜头」");
                }
                importService.structureEpisodeShotsFromScript(projectId, episodeId, apiKey);
                entityManager.clear();
                episode = requireEpisode(projectId, episodeId);
                script = episode.getScriptJson();
                try {
                    root = objectMapper.readTree(script);
                } catch (Exception ex) {
                    throw new IllegalStateException("镜头结构解析失败: " + ex.getMessage());
                }
            } else {
                var apiKey = projectApiKeyResolver.resolve(projectId, apiKeyHeader);
                if (apiKey == null || apiKey.isBlank()) {
                    throw new IllegalStateException("请先点击「正文→剧本」，再点击「剧本→镜头」");
                }
                importService.structureEpisodeScriptFromBody(projectId, episodeId, script, apiKey);
                importService.structureEpisodeShotsFromScript(projectId, episodeId, apiKey);
                entityManager.clear();
                episode = requireEpisode(projectId, episodeId);
                script = episode.getScriptJson();
                try {
                    root = objectMapper.readTree(script);
                } catch (Exception ex) {
                    throw new IllegalStateException("结构化剧本解析失败: " + ex.getMessage());
                }
            }
            if (!DramaForgeImportService.hasShotStructure(root)) {
                throw new IllegalStateException("未能从剧本生成镜头结构");
            }
        }

        // 重新解析前清空本集旧镜头，并刷新持久化上下文，避免后续 save 命中已删行
        versionService.clearEpisodeShots(episodeId);
        entityManager.flush();
        entityManager.clear();

        try {
            root = objectMapper.readTree(requireEpisode(projectId, episodeId).getScriptJson());
            int index = 1;

            if (root.has("scenes") && root.get("scenes").isArray()) {
                for (var scene : root.get("scenes")) {
                    if (!scene.has("shots") || !scene.get("shots").isArray()) {
                        continue;
                    }
                    var sceneName = firstText(scene, "name");
                    for (var shotNode : scene.get("shots")) {
                        var shot = buildShotDraftFromNode(episodeId, shotNode, sceneName);
                        if (shot == null) {
                            continue;
                        }
                        shot.setShotNumber(index++);
                        shotRepository.save(shot);
                    }
                }
            } else if (root.has("shots") && root.get("shots").isArray()) {
                for (var shotNode : root.get("shots")) {
                    var shot = buildShotDraftFromNode(episodeId, shotNode, null);
                    if (shot == null) {
                        continue;
                    }
                    shot.setShotNumber(index++);
                    shotRepository.save(shot);
                }
            } else {
                throw new IllegalStateException("剧本 JSON 需包含 scenes[].shots 或 shots 数组");
            }

            entityManager.flush();
            var savedCount = shotRepository.countByEpisodeId(episodeId);
            if (savedCount == 0) {
                throw new IllegalStateException("未能从剧本解析出任何镜头");
            }

            var projectAssets = assetRepository.findByProjectIdOrderBySortOrderAscNameAsc(projectId);
            shotAssetPlanner.planEpisodeShotsLocally(projectId, episodeId, projectAssets);
            return shotRepository.findByEpisodeIdOrderByShotNumberAsc(episodeId).stream()
                    .map(shot -> toShotResponse(shot, requireConfig(projectId), projectAssets))
                    .toList();
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("剧本解析失败: " + ex.getMessage());
        }
    }

    private DramaForgeShot buildShotDraftFromNode(UUID episodeId, JsonNode shotNode, String parentSceneName) {
        var description = firstText(shotNode, "description", "visual", "prompt");
        if (description == null || description.isBlank()) {
            return null;
        }
        var shot = new DramaForgeShot();
        shot.setEpisodeId(episodeId);
        shot.setDescription(description.trim());
        shot.setDialogue(firstText(shotNode, "dialogue", "narration"));
        shot.setDurationSeconds(parseDurationSeconds(shotNode, shot.getDescription(), shot.getDialogue()));
        shot.setCameraNote(firstText(shotNode, "camera", "camera_note"));
        shot.setSceneRef(parentSceneName != null
                ? resolveSceneNameFromNode(shotNode, parentSceneName)
                : firstText(shotNode, "scene"));
        if (shotNode.has("characters") && shotNode.get("characters").isArray()) {
            var refs = new ArrayList<String>();
            shotNode.get("characters").forEach(node -> refs.add(node.asText()));
            shot.setCharacterRefsJson(writeJson(refs));
        }
        if (shotNode.has("props") && shotNode.get("props").isArray()) {
            var props = new ArrayList<String>();
            shotNode.get("props").forEach(node -> props.add(node.asText()));
            shot.setPropRefsJson(writeJson(props));
        }
        return shot;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<AssetResponse> generateAssetDesigns(UUID projectId, String apiKeyHeader) {
        return generateAssetDesigns(projectId, apiKeyHeader, null);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<AssetResponse> generateAssetDesigns(UUID projectId, String apiKeyHeader, DramaForgeBatchProgress progress) {
        var config = requireConfig(projectId);
        var projectAssets = assetRepository.findByProjectIdOrderBySortOrderAscNameAsc(projectId);
        var assets = projectAssets.stream()
                .filter(asset -> asset.getReferenceImageUrl() == null || asset.getReferenceImageUrl().isBlank())
                .toList();
        if (assets.isEmpty()) {
            throw new IllegalStateException("所有资产已有设计图");
        }

        var characterStyleAnchor = findPrimaryCharacterDesignUrl(projectAssets);
        var total = assets.size();
        var results = new ArrayList<AssetResponse>();
        for (int i = 0; i < assets.size(); i++) {
            var asset = assets.get(i);
            if (progress != null) {
                progress.report(i, total, "正在生成资产设计图：" + asset.getName());
            }
            var result = generateAssetDesignImage(
                    projectId, config, asset, projectAssets, characterStyleAnchor, apiKeyHeader);
            if (result.outputUrl() != null) {
                asset.setReferenceImageUrl(result.outputUrl());
                results.add(AssetResponse.from(assetRepository.save(asset)));
                if (asset.getType() == DramaForgeAssetType.CHARACTER && characterStyleAnchor == null) {
                    characterStyleAnchor = result.outputUrl();
                }
            } else if (isArkPrivacyBlockMessage(result.errorMessage())) {
                // 隐私拦截：自动降级为合规纯文生图再试一次（不绑旧参考图）
                if (progress != null) {
                    progress.report(i, total, "隐私拦截，合规文生图重试：" + asset.getName());
                }
                var safe = generateAssetDesignImage(
                        projectId, config, asset, projectAssets, null, apiKeyHeader, true);
                if (safe.outputUrl() != null) {
                    asset.setReferenceImageUrl(safe.outputUrl());
                    results.add(AssetResponse.from(assetRepository.save(asset)));
                    if (asset.getType() == DramaForgeAssetType.CHARACTER && characterStyleAnchor == null) {
                        characterStyleAnchor = safe.outputUrl();
                    }
                } else {
                    throwAssetDesignFailure(asset.getName(),
                            safe.errorMessage() != null ? safe.errorMessage() : result.errorMessage());
                }
            }
            if (progress != null) {
                progress.report(i + 1, total, "已完成资产：" + asset.getName());
            }
        }
        return results;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AssetResponse regenerateAssetDesign(UUID projectId, UUID assetId, String apiKeyHeader) {
        return regenerateAssetDesign(projectId, assetId, apiKeyHeader, false);
    }

    /**
     * 重新生成资产定妆图。
     *
     * @param privacySafe true：强化虚构半写实提示词，强制纯文生图（不绑旧参考/角色锚点），用于方舟真人隐私拦截后的合规重生
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AssetResponse regenerateAssetDesign(
            UUID projectId, UUID assetId, String apiKeyHeader, boolean privacySafe) {
        var config = requireConfig(projectId);
        var asset = requireAsset(projectId, assetId);
        var projectAssets = assetRepository.findByProjectIdOrderBySortOrderAscNameAsc(projectId);
        assetVersionService.archiveCurrent(asset);
        if (privacySafe) {
            // 清掉可能触发隐私审核的旧参考图，避免后续镜头/图生图继续污染
            asset.setReferenceImageUrl(null);
            assetRepository.save(asset);
        }
        var characterStyleAnchor = privacySafe
                ? null
                : findPrimaryCharacterDesignUrlExcluding(projectAssets, assetId);
        var result = generateAssetDesignImage(
                projectId, config, asset, projectAssets, characterStyleAnchor, apiKeyHeader, privacySafe);
        if (result.outputUrl() == null && !privacySafe && isArkPrivacyBlockMessage(result.errorMessage())) {
            // 普通重生被隐私拦：自动再走一遍合规文生图
            result = generateAssetDesignImage(
                    projectId, config, asset, projectAssets, null, apiKeyHeader, true);
        }
        if (result.outputUrl() == null) {
            throwAssetDesignFailure(asset.getName(), result.errorMessage());
        }
        asset.setReferenceImageUrl(result.outputUrl());
        return AssetResponse.from(assetRepository.save(asset));
    }

    /** 生成 3 张定妆候选图，写入版本表供用户选定 */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AssetDesignCandidatesResponse generateAssetDesignCandidates(
            UUID projectId, UUID assetId, String apiKeyHeader) {
        var config = requireConfig(projectId);
        var asset = requireAsset(projectId, assetId);
        var projectAssets = assetRepository.findByProjectIdOrderBySortOrderAscNameAsc(projectId);
        var characterStyleAnchor = findPrimaryCharacterDesignUrlExcluding(projectAssets, assetId);
        var candidates = new ArrayList<AssetVersionResponse>();
        for (int i = 0; i < 3; i++) {
            var result = generateAssetDesignImage(
                    projectId, config, asset, projectAssets, characterStyleAnchor, apiKeyHeader);
            if (result.outputUrl() == null && isArkPrivacyBlockMessage(result.errorMessage())) {
                result = generateAssetDesignImage(
                        projectId, config, asset, projectAssets, null, apiKeyHeader, true);
            }
            if (result.outputUrl() == null) {
                throwAssetDesignFailure(asset.getName() + " 候选 " + (i + 1), result.errorMessage());
            }
            candidates.add(assetVersionService.saveCandidate(asset, result.outputUrl(), asset.getDesignPrompt()));
        }
        return new AssetDesignCandidatesResponse(assetId, candidates);
    }

    /** AI voice label + TTS sample for Seedance @AudioN */
    public AssetResponse generateCharacterVoice(UUID projectId, UUID assetId, String apiKeyHeader) {
        var asset = requireAsset(projectId, assetId);
        if (asset.getType() != DramaForgeAssetType.CHARACTER) {
            throw new IllegalStateException("only character assets support voice generation");
        }
        var apiKey = projectApiKeyResolver.resolve(projectId, apiKeyHeader);
        var voiceLabel = asset.getVoiceLabel();
        if (voiceLabel == null || voiceLabel.isBlank()) {
            voiceLabel = inventVoiceLabel(apiKey, asset);
            asset.setVoiceLabel(voiceLabel);
        }
        var sampleText = buildVoiceSampleLine(asset, voiceLabel);
        var speaker = asset.getVoiceSpeakerId();
        if (speaker == null || speaker.isBlank()) {
            speaker = mapDoubaoSpeaker(voiceLabel + " " + (asset.getDescription() != null ? asset.getDescription() : ""));
        }
        try {
            var audio = volcengineTtsClient.synthesize(sampleText, speaker, voiceLabel);
            var uploaded = uploadStorageService.storeBytes(
                    audio, "audio/mpeg", asset.getName() + "-voice.mp3");
            asset.setVoiceSampleUrl(uploaded.url());
            asset.setVoiceSpeakerId(speaker);
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "角色音色生成失败（豆包语音 " + speaker + "）：" + ex.getMessage(),
                    ex);
        }
        return AssetResponse.from(assetRepository.save(asset));
    }

    private static String mapDoubaoSpeaker(String profile) {
        var text = profile == null ? "" : profile.toLowerCase(Locale.ROOT);
        var female = text.contains("女") || text.contains("妹") || text.contains("娘") || text.contains("姐")
                || text.contains("female") || text.contains("girl") || text.contains("woman");
        var male = text.contains("男") || text.contains("哥") || text.contains("叔") || text.contains("爷")
                || text.contains("male") || text.contains("man") || text.contains("boy");
        if (female) {
            return "zh_female_vv_uranus_bigtts";
        }
        if (male) {
            return "zh_male_m191_uranus_bigtts";
        }
        return "zh_female_xiaohe_uranus_bigtts";
    }

    private String inventVoiceLabel(String apiKey, DramaForgeAsset asset) {
        var model = tokenFreeProperties.defaultChatModel() != null
                ? tokenFreeProperties.defaultChatModel() : "qwen-max";
        var user = "请为以下短剧角色生成一句简短的中文音色描述（15字以内，含性别与气质）："
                + asset.getName() + "，设定："
                + (asset.getDescription() != null ? asset.getDescription() : "无");
        var result = tokenFreeClient.createChatCompletion(apiKey, model, java.util.List.of(
                new TokenFreeClient.ChatMessage("system", "你只输出音色描述短语，不要解释。"),
                new TokenFreeClient.ChatMessage("user", user)));
        if (result.outputText() == null || result.outputText().isBlank()) {
            return "清晰自然";
        }
        var out = result.outputText().trim().replace("\"", "");
        if (out.length() > 80) {
            out = out.substring(0, 80);
        }
        return out;
    }

    private static String buildVoiceSampleLine(DramaForgeAsset asset, String voiceLabel) {
        // Seedance r2v：单段参考音频须 2–15s，总时长 ≤15.2s；音色样本控制在约 2–3 秒
        var name = asset.getName() != null ? asset.getName().trim() : "角色";
        if (name.length() > 8) {
            name = name.substring(0, 8);
        }
        return "你好，我是" + name + "。";
    }

    public List<AssetVersionResponse> listAssetVersions(UUID projectId, UUID assetId) {
        requireAsset(projectId, assetId);
        return assetVersionService.listVersions(assetId);
    }

    public AssetVersionResponse activateAssetVersion(UUID projectId, UUID assetId, UUID versionId) {
        requireAsset(projectId, assetId);
        return assetVersionService.activateVersion(assetId, versionId);
    }

    private ImageGenerationResponse generateAssetDesignImage(
            UUID projectId,
            DramaForgeConfig config,
            DramaForgeAsset asset,
            List<DramaForgeAsset> projectAssets,
            String characterStyleAnchor,
            String apiKeyHeader) {
        return generateAssetDesignImage(
                projectId, config, asset, projectAssets, characterStyleAnchor, apiKeyHeader, false);
    }

    private ImageGenerationResponse generateAssetDesignImage(
            UUID projectId,
            DramaForgeConfig config,
            DramaForgeAsset asset,
            List<DramaForgeAsset> projectAssets,
            String characterStyleAnchor,
            String apiKeyHeader,
            boolean privacySafe) {
        var prompt = DramaForgeStylePrompts.assetDesignPrompt(config, asset, privacySafe);
        var ratio = DramaForgeStylePrompts.resolveAssetDesignAspectRatio(config, asset.getType());
        // 隐私重生：角色必须纯文生图，避免旧真人参考图继续污染；场景/道具同样不绑旧角色锚点
        if (!privacySafe
                && characterStyleAnchor != null
                && asset.getType() != DramaForgeAssetType.CHARACTER) {
            var strength = asset.getType() == DramaForgeAssetType.SCENE ? 0.45 : 0.50;
            return generateImageWithReference(
                    projectId, asset.getId().toString(), config, prompt, characterStyleAnchor, strength, ratio, apiKeyHeader);
        }
        return createDesignImagePreferringArk(
                projectId,
                asset.getId().toString(),
                config,
                prompt,
                ratio,
                null,
                null,
                apiKeyHeader);
    }

    private String findPrimaryCharacterDesignUrlExcluding(List<DramaForgeAsset> projectAssets, UUID excludeAssetId) {
        return projectAssets.stream()
                .filter(asset -> !asset.getId().equals(excludeAssetId))
                .filter(asset -> asset.getType() == DramaForgeAssetType.CHARACTER)
                .filter(asset -> asset.getReferenceImageUrl() != null && !asset.getReferenceImageUrl().isBlank())
                .map(DramaForgeAsset::getReferenceImageUrl)
                .findFirst()
                .orElse(null);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<ShotResponse> generateStoryboards(UUID projectId, UUID episodeId, String apiKeyHeader) {
        return generateStoryboards(projectId, episodeId, apiKeyHeader, null);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<ShotResponse> generateStoryboards(
            UUID projectId,
            UUID episodeId,
            String apiKeyHeader,
            DramaForgeBatchProgress progress) {
        var config = requireConfig(projectId);
        var episode = requireEpisode(projectId, episodeId);
        var allShotsOrdered = shotRepository.findByEpisodeIdOrderByShotNumberAsc(episode.getId());
        var pendingCount = (int) allShotsOrdered.stream().filter(this::needsStoryboardGeneration).count();
        if (pendingCount == 0) {
            throw new IllegalStateException("所有镜头已有完整分镜（首帧+尾帧）");
        }

        if (progress != null) {
            progress.report(0, pendingCount, "正在规划镜头资产引用...");
        }
        shotAssetPlanner.planEpisodeShots(projectId, episodeId, apiKeyHeader);
        allShotsOrdered = shotRepository.findByEpisodeIdOrderByShotNumberAsc(episode.getId());
        if (progress != null) {
            progress.report(0, pendingCount, "共 " + pendingCount + " 个镜头待生成分镜（首帧+尾帧）");
        }
        var projectAssets = assetRepository.findByProjectIdOrderBySortOrderAscNameAsc(projectId);
        var results = new ArrayList<ShotResponse>();
        String previousLastFrameUrl = null;
        var done = 0;
        for (var shot : allShotsOrdered) {
            if (isStoryboardComplete(shot)) {
                previousLastFrameUrl = resolveShotLastFrameForChain(shot);
                continue;
            }
            if (progress != null) {
                progress.report(done, pendingCount,
                        "正在生成镜头 " + shot.getShotNumber() + " 首帧/尾帧（" + (done + 1) + "/" + pendingCount + "）");
            }
            shot = generateAndSaveShotStoryboardFrames(
                    projectId, episode, shot, config, projectAssets,
                    previousLastFrameUrl, apiKeyHeader);
            previousLastFrameUrl = resolveShotLastFrameForChain(shot);
            results.add(toShotResponse(shot, config, projectAssets));
            done++;
            if (progress != null) {
                progress.report(done, pendingCount,
                        "已完成镜头 " + shot.getShotNumber() + "（" + done + "/" + pendingCount + "）");
            }
        }
        return results;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ShotResponse regenerateShotStoryboard(
            UUID projectId,
            UUID episodeId,
            UUID shotId,
            String apiKeyHeader) {
        var config = requireConfig(projectId);
        var episode = requireEpisode(projectId, episodeId);
        var shot = requireShot(episodeId, shotId);
        var allShots = shotRepository.findByEpisodeIdOrderByShotNumberAsc(episodeId);
        var projectAssets = assetRepository.findByProjectIdOrderBySortOrderAscNameAsc(projectId);
        // 尊重镜头已绑定资产，不再 planShot 自动补角色（避免空镜被塞入全剧主角）

        versionService.archiveCurrent(shot);
        shot.setFirstFrameUrl(null);
        shot.setLastFrameUrl(null);
        shot.setStoryboardUrl(null);
        shot.setStoryboardPrompt(null);
        shotRepository.save(shot);

        var previousLastFrameUrl = findPreviousLastFrameUrl(allShots, shot.getShotNumber());
        var saved = generateAndSaveShotStoryboardFrames(
                projectId, episode, shot, config, projectAssets,
                previousLastFrameUrl, apiKeyHeader);
        return toShotResponse(saved, config, projectAssets);
    }

    private DramaForgeShot generateAndSaveShotStoryboardFrames(
            UUID projectId,
            DramaForgeEpisode episode,
            DramaForgeShot shot,
            DramaForgeConfig config,
            List<DramaForgeAsset> projectAssets,
            String previousLastFrameUrl,
            String apiKeyHeader) {
        var characterRefs = readStringList(shot.getCharacterRefsJson());
        var propRefs = readStringList(shot.getPropRefsJson());

        String firstFrameUrl = shot.getFirstFrameUrl();
        if (firstFrameUrl == null || firstFrameUrl.isBlank()) {
            if (shot.getStoryboardUrl() != null && !shot.getStoryboardUrl().isBlank()) {
                firstFrameUrl = shot.getStoryboardUrl().trim();
            }
        }

        if (firstFrameUrl == null || firstFrameUrl.isBlank()) {
            var refCtx = resolveStoryboardReferenceContext(
                    shot, previousLastFrameUrl, projectAssets);
            if (refCtx.url() == null && config.getContentMode() == DramaForgeContentMode.DRAMA) {
                throw new IllegalStateException(
                        "镜头 " + shot.getShotNumber()
                                + " 缺少可用参考图：请先生成角色/场景/道具设计图，并确认镜头已规划出场资产。");
            }
            var firstPrompt = DramaForgeStylePrompts.storyboardFirstFramePrompt(
                    config, episode, shot, characterRefs, propRefs, projectAssets,
                    refCtx.url() != null, refCtx.fromCharacterDesign(), refCtx.fromSceneDesign(),
                    refCtx.chainFromPrevious(), refCtx.bindings());
            var firstResult = generateStoryboardImage(
                    projectId, shot, config, firstPrompt, refCtx.url(), refCtx.imageUrls(),
                    refCtx.fromCharacterDesign(), refCtx.fromSceneDesign(),
                    refCtx.chainFromPrevious(), false, apiKeyHeader);
            if (firstResult.outputUrl() == null) {
                var err = firstResult.errorMessage() != null ? firstResult.errorMessage() : "未知错误";
                throw new IllegalStateException("镜头 " + shot.getShotNumber() + " 首帧生成失败: " + err);
            }
            firstFrameUrl = firstResult.outputUrl();
            shot.setStoryboardPrompt(firstPrompt);
        }

        String lastFrameUrl = shot.getLastFrameUrl();
        if (lastFrameUrl == null || lastFrameUrl.isBlank()) {
            var lastPrompt = DramaForgeStylePrompts.storyboardLastFramePrompt(
                    config, episode, shot, characterRefs, propRefs, projectAssets);
            var lastResult = generateStoryboardImage(
                    projectId, shot, config, lastPrompt, firstFrameUrl, List.of(firstFrameUrl),
                    false, false, false, true, apiKeyHeader);
            if (lastResult.outputUrl() == null) {
                var err = lastResult.errorMessage() != null ? lastResult.errorMessage() : "未知错误";
                throw new IllegalStateException("镜头 " + shot.getShotNumber() + " 尾帧生成失败: " + err);
            }
            lastFrameUrl = lastResult.outputUrl();
        }

        versionService.archiveCurrent(shot);
        shot.setFirstFrameUrl(firstFrameUrl);
        shot.setLastFrameUrl(lastFrameUrl);
        shot.setStoryboardUrl(firstFrameUrl);
        shot.setStatus(DramaForgeShotStatus.STORYBOARD_DONE);
        shot.setErrorMessage(null);
        return shotRepository.save(shot);
    }

    private static boolean isStoryboardComplete(DramaForgeShot shot) {
        var hasFirst = hasUrl(shot.getFirstFrameUrl()) || hasUrl(shot.getStoryboardUrl());
        return hasFirst && hasUrl(shot.getLastFrameUrl());
    }

    private boolean needsStoryboardGeneration(DramaForgeShot shot) {
        return !isStoryboardComplete(shot);
    }

    private static boolean hasUrl(String url) {
        return url != null && !url.isBlank();
    }

    private static String resolveShotLastFrameForChain(DramaForgeShot shot) {
        if (hasUrl(shot.getLastFrameUrl())) {
            return shot.getLastFrameUrl().trim();
        }
        if (hasUrl(shot.getStoryboardUrl())) {
            return shot.getStoryboardUrl().trim();
        }
        if (hasUrl(shot.getFirstFrameUrl())) {
            return shot.getFirstFrameUrl().trim();
        }
        return null;
    }

    private static String findPreviousLastFrameUrl(List<DramaForgeShot> allShots, int shotNumber) {
        String previous = null;
        for (var s : allShots) {
            if (s.getShotNumber() >= shotNumber) {
                break;
            }
            var chain = resolveShotLastFrameForChain(s);
            if (chain != null) {
                previous = chain;
            }
        }
        return previous;
    }

    private ImageGenerationResponse generateStoryboardImage(
            UUID projectId,
            DramaForgeShot shot,
            DramaForgeConfig config,
            String prompt,
            String referenceUrl,
            List<String> referenceUrls,
            boolean fromCharacterDesign,
            boolean fromSceneDesign,
            boolean chainFromPrevious,
            boolean fromFirstFrame,
            String apiKeyHeader) {
        var ratio = DramaForgeStylePrompts.resolveAspectRatio(config);
        var multiUrls = new ArrayList<String>();
        if (referenceUrls != null) {
            for (var url : referenceUrls) {
                if (url != null && !url.isBlank() && !multiUrls.contains(url.trim())) {
                    multiUrls.add(url.trim());
                }
            }
        }
        if (referenceUrl != null && !referenceUrl.isBlank() && !multiUrls.contains(referenceUrl.trim())) {
            multiUrls.add(0, referenceUrl.trim());
        }
        var primaryUrl = multiUrls.isEmpty() ? null : multiUrls.getFirst();
        var requireReference = config.getContentMode() == DramaForgeContentMode.DRAMA
                || primaryUrl != null;

        if (primaryUrl != null) {
            // 多定妆身份锁：偏低 strength，避免漂成另一人
            var strength = fromFirstFrame ? 0.52
                    : fromCharacterDesign ? 0.28
                    : chainFromPrevious ? 0.42
                    : fromSceneDesign ? 0.48
                    : 0.45;
            var img2img = imageGenerationService.createSeedreamForProject(projectId,
                    new CreateImageGenerationRequest(
                            projectId,
                            shot.getId().toString(),
                            SEEDREAM_5_PRO_MODEL,
                            prompt,
                            ratio,
                            resolveImageQuality(config),
                            "image-to-image",
                            primaryUrl,
                            strength,
                            multiUrls));
            if (img2img.outputUrl() != null) {
                return img2img;
            }
            if (requireReference) {
                var err = img2img.errorMessage() != null && !img2img.errorMessage().isBlank()
                        ? SEEDREAM_5_PRO_MODEL + ": " + img2img.errorMessage()
                        : "未知错误";
                throw new IllegalStateException(
                        "镜头 " + shot.getShotNumber() + " 图生图失败: " + err);
            }
        }

        if (requireReference) {
            throw new IllegalStateException(
                    "镜头 " + shot.getShotNumber()
                            + " 缺少可用参考图：请先为关联角色/场景/道具生成设计图");
        }

        return imageGenerationService.createSeedreamForProject(projectId,
                new CreateImageGenerationRequest(
                        projectId,
                        shot.getId().toString(),
                        SEEDREAM_5_PRO_MODEL,
                        prompt,
                        ratio,
                        resolveImageQuality(config),
                        "text-to-image",
                        null,
                        null));
    }

    private ImageGenerationResponse generateImageWithReference(
            UUID projectId,
            String nodeId,
            DramaForgeConfig config,
            String prompt,
            String referenceUrl,
            double strength,
            String apiKeyHeader) {
        return generateImageWithReference(
                projectId, nodeId, config, prompt, referenceUrl, strength,
                DramaForgeStylePrompts.resolveAspectRatio(config), apiKeyHeader);
    }

    private ImageGenerationResponse generateImageWithReference(
            UUID projectId,
            String nodeId,
            DramaForgeConfig config,
            String prompt,
            String referenceUrl,
            double strength,
            String ratio,
            String apiKeyHeader) {
        if (referenceUrl != null && !referenceUrl.isBlank()) {
            var img2img = createDesignImagePreferringArk(
                    projectId, nodeId, config, prompt, ratio, referenceUrl, strength, apiKeyHeader);
            if (img2img.outputUrl() != null) {
                return img2img;
            }
        }
        return createDesignImagePreferringArk(
                projectId, nodeId, config, prompt, ratio, null, null, apiKeyHeader);
    }

    /**
     * 定妆图（含三维定妆/三候选）：仅用火山方舟 Seedream 5.0 Lite，失败直接抛错，不回退 TokenFree。
     */
    private ImageGenerationResponse createDesignImagePreferringArk(
            UUID projectId,
            String nodeId,
            DramaForgeConfig config,
            String prompt,
            String ratio,
            String referenceUrl,
            Double strength,
            String apiKeyHeader) {
        var quality = resolveImageQuality(config);
        var hasRef = referenceUrl != null && !referenceUrl.isBlank();
        var mode = hasRef ? "image-to-image" : "text-to-image";
        var arkResult = imageGenerationService.createSeedreamForProject(
                projectId,
                new CreateImageGenerationRequest(
                        projectId,
                        nodeId,
                        ARK_SEEDREAM_DESIGN_MODEL,
                        prompt,
                        ratio,
                        quality,
                        mode,
                        hasRef ? referenceUrl : null,
                        hasRef ? strength : null));
        if (arkResult.outputUrl() == null || arkResult.outputUrl().isBlank()) {
            var err = arkResult.errorMessage() != null && !arkResult.errorMessage().isBlank()
                    ? arkResult.errorMessage()
                    : "未返回图片";
            throw new IllegalStateException("方舟 Seedream 5.0 Lite 定妆失败：" + err);
        }
        return arkResult;
    }

    private record StoryboardReference(
            String url,
            List<String> imageUrls,
            List<DramaForgeStylePrompts.AssetImageBinding> bindings,
            boolean fromCharacterDesign,
            boolean fromSceneDesign,
            boolean chainFromPrevious) {}

    private StoryboardReference resolveStoryboardReferenceContext(
            DramaForgeShot shot,
            String previousLastFrameUrl,
            List<DramaForgeAsset> projectAssets) {
        var urls = new ArrayList<String>();
        var bindings = new ArrayList<DramaForgeStylePrompts.AssetImageBinding>();
        var fromCharacter = false;
        var fromScene = false;

        // 仅使用本镜头已绑定资产，禁止文案猜测 / 全剧主角兜底
        for (var name : readStringList(shot.getCharacterRefsJson())) {
            var url = findCharacterDesignUrl(name, projectAssets);
            if (addStoryboardRef(urls, bindings, name, "角色", url)) {
                fromCharacter = true;
            }
        }

        if (shot.getSceneRef() != null && !shot.getSceneRef().isBlank()) {
            var sceneUrl = findSceneDesignUrl(shot.getSceneRef(), projectAssets);
            if (addStoryboardRef(urls, bindings, shot.getSceneRef().trim(), "场景", sceneUrl)) {
                fromScene = true;
            }
        }

        for (var name : readStringList(shot.getPropRefsJson())) {
            var url = projectAssets.stream()
                    .filter(a -> a.getType() == DramaForgeAssetType.PROP)
                    .filter(a -> a.getName().equalsIgnoreCase(name))
                    .map(DramaForgeAsset::getReferenceImageUrl)
                    .filter(u -> u != null && !u.isBlank())
                    .findFirst()
                    .orElse(null);
            addStoryboardRef(urls, bindings, name, "道具", url);
        }

        // 上一镜尾帧仅作镜间连贯，不是资源库资产
        var chain = false;
        if (previousLastFrameUrl != null && !previousLastFrameUrl.isBlank()) {
            if (addStoryboardRef(urls, bindings, "上一镜尾帧", "连贯", previousLastFrameUrl.trim())) {
                chain = true;
            }
        }

        if (urls.isEmpty()) {
            return new StoryboardReference(null, List.of(), List.of(), false, false, false);
        }
        return new StoryboardReference(
                urls.getFirst(),
                List.copyOf(urls),
                List.copyOf(bindings),
                fromCharacter,
                fromScene,
                chain);
    }

    private static boolean addStoryboardRef(
            List<String> urls,
            List<DramaForgeStylePrompts.AssetImageBinding> bindings,
            String name,
            String kind,
            String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        var trimmed = url.trim();
        if (urls.contains(trimmed)) {
            return false;
        }
        urls.add(trimmed);
        bindings.add(new DramaForgeStylePrompts.AssetImageBinding(name, kind, trimmed));
        return true;
    }

    private String resolveSceneReferenceImage(DramaForgeShot shot, List<DramaForgeAsset> projectAssets) {
        if (shot.getSceneRef() != null && !shot.getSceneRef().isBlank()) {
            var url = findSceneDesignUrl(shot.getSceneRef(), projectAssets);
            if (url != null) {
                return url;
            }
        }
        var description = shot.getDescription() != null ? shot.getDescription().toLowerCase(Locale.ROOT) : "";
        for (var asset : projectAssets) {
            if (asset.getType() != DramaForgeAssetType.SCENE) {
                continue;
            }
            if (asset.getReferenceImageUrl() == null || asset.getReferenceImageUrl().isBlank()) {
                continue;
            }
            if (description.contains(asset.getName().toLowerCase(Locale.ROOT))) {
                return asset.getReferenceImageUrl();
            }
        }
        return null;
    }

    private String findSceneDesignUrl(String name, List<DramaForgeAsset> projectAssets) {
        return projectAssets.stream()
                .filter(item -> item.getType() == DramaForgeAssetType.SCENE)
                .filter(item -> item.getName().equalsIgnoreCase(name))
                .filter(item -> item.getReferenceImageUrl() != null && !item.getReferenceImageUrl().isBlank())
                .map(DramaForgeAsset::getReferenceImageUrl)
                .findFirst()
                .orElse(null);
    }

    private String resolvePrimaryCharacterReference(List<DramaForgeAsset> projectAssets, List<DramaForgeShot> shots) {
        for (var shot : shots) {
            var ref = resolveCharacterReferenceImage(shot, projectAssets);
            if (ref != null) {
                return ref;
            }
        }
        return findPrimaryCharacterDesignUrl(projectAssets);
    }

    private String findPrimaryCharacterDesignUrl(List<DramaForgeAsset> projectAssets) {
        return projectAssets.stream()
                .filter(asset -> asset.getType() == DramaForgeAssetType.CHARACTER)
                .filter(asset -> asset.getReferenceImageUrl() != null && !asset.getReferenceImageUrl().isBlank())
                .map(DramaForgeAsset::getReferenceImageUrl)
                .findFirst()
                .orElse(null);
    }

    private String resolveCharacterReferenceImage(DramaForgeShot shot, List<DramaForgeAsset> projectAssets) {
        var refs = readStringList(shot.getCharacterRefsJson());
        for (var name : refs) {
            var url = findCharacterDesignUrl(name, projectAssets);
            if (url != null) {
                return url;
            }
        }
        var description = shot.getDescription() != null ? shot.getDescription() : "";
        var dialogue = shot.getDialogue() != null ? shot.getDialogue() : "";
        var combined = description + dialogue;
        for (var asset : projectAssets) {
            if (asset.getType() != DramaForgeAssetType.CHARACTER) {
                continue;
            }
            if (asset.getReferenceImageUrl() == null || asset.getReferenceImageUrl().isBlank()) {
                continue;
            }
            if (combined.contains(asset.getName())) {
                return asset.getReferenceImageUrl();
            }
        }
        return null;
    }

    private String findCharacterDesignUrl(String name, List<DramaForgeAsset> projectAssets) {
        return projectAssets.stream()
                .filter(item -> item.getType() == DramaForgeAssetType.CHARACTER)
                .filter(item -> item.getName().equalsIgnoreCase(name))
                .filter(item -> item.getReferenceImageUrl() != null && !item.getReferenceImageUrl().isBlank())
                .map(DramaForgeAsset::getReferenceImageUrl)
                .findFirst()
                .orElse(null);
    }

    public MultiCamTemplatesResponse listMultiCamTemplates() {
        var items = DramaForgeCameraTemplates.listAll().stream()
                .map(t -> new MultiCamTemplateItem(
                        t.id(),
                        t.label(),
                        t.presets().stream()
                                .map(p -> new MultiCamPresetItem(p.id(), p.label(), p.cameraNote()))
                                .toList()))
                .toList();
        return new MultiCamTemplatesResponse(items);
    }

    public ComposeReadinessResponse getEpisodeComposeReadiness(UUID projectId, UUID episodeId) {
        requireEpisode(projectId, episodeId);
        var shots = shotRepository.findByEpisodeIdOrderByShotNumberAsc(episodeId);
        var config = configRepository.findByProjectId(projectId).orElse(null);
        return consistencyService.buildComposeReadiness(shots, config);
    }

    @Transactional
    public BatchExpandMultiCamResponse expandEpisodeMultiCamera(
            UUID projectId,
            UUID episodeId,
            String templateId,
            Boolean modelMultiShot,
            String sceneRef,
            Boolean firstPerSceneOnly,
            Boolean removeSource) {
        requireEpisode(projectId, episodeId);
        var shots = shotRepository.findByEpisodeIdOrderByShotNumberAsc(episodeId);
        var filtered = shots.stream()
                .filter(s -> sceneRef == null
                        || sceneRef.isBlank()
                        || (s.getSceneRef() != null && sceneRef.equalsIgnoreCase(s.getSceneRef().trim())))
                .toList();
        if (Boolean.TRUE.equals(firstPerSceneOnly)) {
            var seen = new java.util.LinkedHashSet<String>();
            var picked = new ArrayList<DramaForgeShot>();
            for (var shot : filtered) {
                var sceneKey = shot.getSceneRef() != null && !shot.getSceneRef().isBlank()
                        ? shot.getSceneRef().trim().toLowerCase(Locale.ROOT)
                        : "__none__";
                if (seen.add(sceneKey)) {
                    picked.add(shot);
                }
            }
            filtered = picked;
        }
        if (filtered.isEmpty()) {
            throw new IllegalStateException(sceneRef == null || sceneRef.isBlank()
                    ? "本集没有可展开的镜头"
                    : "场景「" + sceneRef + "」下没有镜头");
        }
        var reversed = new ArrayList<>(filtered);
        java.util.Collections.reverse(reversed);
        var expanded = 0;
        var removedSources = 0;
        var errors = new ArrayList<String>();
        for (var shot : reversed) {
            try {
                expandShotMultiCamera(
                        projectId, episodeId, shot.getId(), templateId, modelMultiShot, removeSource);
                expanded++;
                if (Boolean.TRUE.equals(removeSource) && !Boolean.TRUE.equals(modelMultiShot)) {
                    removedSources++;
                }
            } catch (Exception ex) {
                var msg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
                errors.add("镜头 " + shot.getShotNumber() + ": " + msg);
            }
        }
        return new BatchExpandMultiCamResponse(filtered.size(), expanded, removedSources, errors);
    }

    @Transactional
    public List<ShotResponse> expandShotMultiCamera(
            UUID projectId,
            UUID episodeId,
            UUID shotId,
            String templateId,
            Boolean modelMultiShot,
            Boolean removeSource) {
        requireEpisode(projectId, episodeId);
        var source = requireShot(episodeId, shotId);
        var presets = DramaForgeCameraTemplates.resolve(templateId);
        if (presets.isEmpty()) {
            throw new IllegalStateException("无效的多机位模板: " + templateId);
        }

        var config = requireConfig(projectId);
        var useModel = Boolean.TRUE.equals(modelMultiShot) || config.isPreferModelMultiShot();
        if (useModel) {
            // 模型级：不拆镜，标记当前镜头为单次多机位生成
            source.setModelMultiShot(true);
            source.setMultiShotTemplate(templateId.trim().toLowerCase(Locale.ROOT));
            var cameraNotes = new StringBuilder();
            for (int i = 0; i < presets.size(); i++) {
                if (i > 0) {
                    cameraNotes.append(" → ");
                }
                cameraNotes.append(presets.get(i).label()).append(": ").append(presets.get(i).cameraNote());
            }
            source.setCameraNote(cameraNotes.toString());
            var perShot = source.getDurationSeconds() != null ? source.getDurationSeconds() : 5;
            source.setDurationSeconds(Math.max(6, Math.min(15, perShot * Math.min(presets.size(), 3))));
            source.setForceCharacterBinding(true);
            return List.of(toShotResponse(shotRepository.save(source)));
        }

        var insertAfter = source.getShotNumber();
        var allShots = new ArrayList<>(shotRepository.findByEpisodeIdOrderByShotNumberAsc(episodeId));
        for (var s : allShots) {
            if (s.getShotNumber() > insertAfter) {
                s.setShotNumber(s.getShotNumber() + presets.size());
                shotRepository.save(s);
            }
        }

        var results = new ArrayList<ShotResponse>();
        for (int i = 0; i < presets.size(); i++) {
            var preset = presets.get(i);
            var shot = new DramaForgeShot();
            shot.setEpisodeId(episodeId);
            shot.setShotNumber(insertAfter + 1 + i);
            shot.setDescription(source.getDescription());
            shot.setDialogue(i == presets.size() - 1 ? source.getDialogue() : null);
            shot.setCameraNote(preset.cameraNote());
            shot.setCharacterRefsJson(source.getCharacterRefsJson());
            shot.setSceneRef(source.getSceneRef());
            shot.setPropRefsJson(source.getPropRefsJson());
            shot.setDurationSeconds(source.getDurationSeconds());
            shot.setForceCharacterBinding(source.getForceCharacterBinding());
            shot.setReferenceVideoMode("auto");
            shot.setQaStatus("pending");
            shot.setModelMultiShot(false);
            results.add(toShotResponse(shotRepository.save(shot)));
        }
        if (Boolean.TRUE.equals(removeSource)) {
            versionService.deleteShotAndCompact(episodeId, insertAfter);
        }
        return results;
    }

    /** 为镜头对白生成 TTS 并保存 dialogueAudioUrl */
    public ShotResponse generateShotDialogueAudio(
            UUID projectId,
            UUID episodeId,
            UUID shotId,
            String apiKeyHeader) {
        var shot = requireShot(episodeId, shotId);
        requireEpisode(projectId, episodeId);
        var dialogue = shot.getDialogue();
        if (dialogue == null || dialogue.isBlank()) {
            throw new IllegalStateException("镜头 " + shot.getShotNumber() + " 没有对白文本");
        }
        var assets = assetRepository.findByProjectIdOrderBySortOrderAscNameAsc(projectId);
        var characterRefs = readStringList(shot.getCharacterRefsJson());
        var speakerAsset = resolveDialogueSpeakerAsset(shot, characterRefs, assets, dialogue);
        if (speakerAsset == null) {
            throw new IllegalStateException("无法确定对白角色，请先绑定出镜角色并配置音色");
        }
        var speaker = speakerAsset.getVoiceSpeakerId();
        if (speaker == null || speaker.isBlank()) {
            speaker = mapDoubaoSpeaker(
                    (speakerAsset.getVoiceLabel() != null ? speakerAsset.getVoiceLabel() : "")
                            + " "
                            + (speakerAsset.getDescription() != null ? speakerAsset.getDescription() : ""));
        }
        var voiceLabel = speakerAsset.getVoiceLabel() != null ? speakerAsset.getVoiceLabel() : speakerAsset.getName();
        var text = stripDialogueSpeakerPrefix(dialogue, speakerAsset.getName());
        try {
            var audio = volcengineTtsClient.synthesize(text, speaker, voiceLabel);
            var uploaded = uploadStorageService.storeBytes(
                    audio, "audio/mpeg", "shot-" + shot.getShotNumber() + "-dialogue.mp3");
            shot.setDialogueAudioUrl(uploaded.url());
            if (speakerAsset.getVoiceSpeakerId() == null || speakerAsset.getVoiceSpeakerId().isBlank()) {
                speakerAsset.setVoiceSpeakerId(speaker);
                assetRepository.save(speakerAsset);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("对白 TTS 失败：" + ex.getMessage(), ex);
        }
        return toShotResponse(shotRepository.save(shot));
    }

    /** 为本集所有含对白镜头批量生成 TTS；单镜失败不阻断其余镜头 */
    public BatchDialogueAudioResponse generateEpisodeDialogueAudio(
            UUID projectId,
            UUID episodeId,
            String apiKeyHeader) {
        requireEpisode(projectId, episodeId);
        var shots = shotRepository.findByEpisodeIdOrderByShotNumberAsc(episodeId);
        var attempted = 0;
        var succeeded = 0;
        var errors = new java.util.ArrayList<String>();
        for (var shot : shots) {
            if (shot.getDialogue() == null || shot.getDialogue().isBlank()) {
                continue;
            }
            attempted++;
            try {
                generateShotDialogueAudio(projectId, episodeId, shot.getId(), apiKeyHeader);
                succeeded++;
            } catch (Exception ex) {
                var msg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
                errors.add("镜头 " + shot.getShotNumber() + ": " + msg);
            }
        }
        if (attempted == 0) {
            throw new IllegalStateException("本集没有含对白的镜头");
        }
        return new BatchDialogueAudioResponse(attempted, succeeded, errors);
    }

    private DramaForgeAsset resolveDialogueSpeakerAsset(
            DramaForgeShot shot,
            List<String> characterRefs,
            List<DramaForgeAsset> assets,
            String dialogue) {
        for (var name : characterRefs) {
            if (dialogue.contains(name)) {
                var asset = assets.stream()
                        .filter(a -> a.getType() == DramaForgeAssetType.CHARACTER)
                        .filter(a -> a.getName().equalsIgnoreCase(name))
                        .findFirst()
                        .orElse(null);
                if (asset != null) {
                    return asset;
                }
            }
        }
        var audio = DramaForgeStylePrompts.resolveAudioRefs(shot, characterRefs, assets);
        if (!audio.labels().isEmpty()) {
            var label = audio.labels().getFirst();
            var name = label.replace("voice:", "").trim();
            return assets.stream()
                    .filter(a -> a.getType() == DramaForgeAssetType.CHARACTER)
                    .filter(a -> a.getName().equalsIgnoreCase(name)
                            || (a.getVoiceLabel() != null && a.getVoiceLabel().equalsIgnoreCase(name)))
                    .findFirst()
                    .orElse(null);
        }
        return assets.stream()
                .filter(a -> a.getType() == DramaForgeAssetType.CHARACTER)
                .filter(a -> characterRefs.contains(a.getName()))
                .findFirst()
                .orElse(null);
    }

    private static String stripDialogueSpeakerPrefix(String dialogue, String speakerName) {
        var text = dialogue.trim();
        if (speakerName != null && !speakerName.isBlank()) {
            var prefix = speakerName + "：";
            if (text.startsWith(prefix)) {
                return text.substring(prefix.length()).trim();
            }
            prefix = speakerName + ":";
            if (text.startsWith(prefix)) {
                return text.substring(prefix.length()).trim();
            }
        }
        return text;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ShotResponse generateShotVideo(
            UUID projectId,
            UUID episodeId,
            UUID shotId,
            String apiKeyHeader) {
        var config = requireConfig(projectId);
        requireEpisode(projectId, episodeId);
        requireShot(episodeId, shotId);
        // 独立事务：避免方舟失败后 catch 重试把外层事务标成 rollback-only
        return self.generateShotVideoInNewTx(projectId, config, shotId, apiKeyHeader);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<ShotResponse> generateVideos(UUID projectId, UUID episodeId, String apiKeyHeader) {
        return generateVideos(projectId, episodeId, apiKeyHeader, null);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<ShotResponse> generateVideos(
            UUID projectId,
            UUID episodeId,
            String apiKeyHeader,
            DramaForgeBatchProgress progress) {
        var config = requireConfig(projectId);
        var episode = requireEpisode(projectId, episodeId);
        // 产品已取消「确认分镜」步骤；旧项目若仍是分镜转视频，出片时自动切到设计图直出
        if (config.getGenerationMode() == DramaForgeGenerationMode.STORYBOARD_TO_VIDEO) {
            config.setGenerationMode(DramaForgeGenerationMode.REFERENCE_TO_VIDEO);
            configRepository.save(config);
        }
        // 严格按镜头号顺序出片，保证镜间尾帧衔接；不按场景重排以免跳号
        var shots = shotRepository.findByEpisodeIdOrderByShotNumberAsc(episode.getId()).stream()
                .filter(shot -> shot.getStatus() != DramaForgeShotStatus.VIDEO_DONE)
                .toList();
        if (shots.isEmpty()) {
            throw new IllegalStateException("没有待生成视频的镜头");
        }

        // 批量前先用 AI 规划角色/场景/道具绑定
        shotAssetPlanner.planEpisodeShots(projectId, episodeId, apiKeyHeader);

        var total = shots.size();
        var results = new ArrayList<ShotResponse>();
        String continuityTailFrame = null;
        // 续跑时若从镜头 2+ 开始：必须先有紧邻上一镜号成片，再抽尾帧
        if (!shots.isEmpty() && shots.getFirst().getShotNumber() > 1) {
            var firstPending = shots.getFirst();
            var previous = videoContinuityService.findPreviousCompletedShot(firstPending)
                    .orElseThrow(() -> new IllegalStateException(
                            missingPreviousShotVideoMessage(firstPending)));
            continuityTailFrame = videoContinuityService.extractLastFrameUrl(projectId, previous);
            if (continuityTailFrame == null || continuityTailFrame.isBlank()) {
                throw new IllegalStateException(
                        "抽取镜头 " + previous.getShotNumber()
                                + " 成片尾帧失败；后续镜头无法衔接（不回退分镜首帧）");
            }
        }
        // 按镜头顺序：提交 → 等待成片 → 抽尾帧 → 下一镜
        for (int i = 0; i < shots.size(); i++) {
            var shot = shots.get(i);
            if (progress != null) {
                progress.report(i, total,
                        "正在提交镜头 " + shot.getShotNumber() + "（" + (i + 1) + "/" + total + "）"
                                + (continuityTailFrame != null ? "，已衔接上一镜成片尾帧" : ""));
            }
            try {
                self.generateShotVideoInNewTx(projectId, config, shot.getId(), apiKeyHeader, continuityTailFrame);
                if (progress != null) {
                    progress.report(i, total,
                            "等待镜头 " + shot.getShotNumber() + " 成片…（" + (i + 1) + "/" + total + "）");
                }
                var completed = videoContinuityService.waitForShotVideo(projectId, shot.getId(), apiKeyHeader);
                continuityTailFrame = videoContinuityService.extractLastFrameUrl(projectId, completed);
                // 已完成镜仍抽不出尾帧：下一条（镜头 2+）会硬失败，这里提前给出明确错误
                if (continuityTailFrame == null || continuityTailFrame.isBlank()) {
                    if (i + 1 < shots.size()) {
                        throw new IllegalStateException(
                                "镜头 " + shot.getShotNumber()
                                        + " 成片已完成，但抽取尾帧失败；后续镜头无法衔接（不回退分镜首帧）");
                    }
                }
                results.add(toShotResponse(completed));
                if (progress != null) {
                    progress.report(i + 1, total,
                            "已完成镜头 " + shot.getShotNumber() + "（" + (i + 1) + "/" + total + "）");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("镜头视频生成被中断");
            } catch (Exception ex) {
                continuityTailFrame = null;
                var msg = ex.getMessage() != null ? ex.getMessage() : "unknown";
                // 等待超时但上游仍在跑：不要标 FAILED，否则 SYNC_VIDEOS 会跳过该镜，界面也显示失败
                if (isSoftVideoWaitTimeout(msg)) {
                    if (progress != null) {
                        progress.report(i + 1, total,
                                "镜头 " + shot.getShotNumber() + " 仍在生成，已转后台同步");
                    }
                    continue;
                }
                try {
                    var failed = shotRepository.findById(shot.getId()).orElse(shot);
                    markShotFailed(failed, msg);
                    shotRepository.save(failed);
                } catch (Exception ignored) {
                    // 保留外层进度提示即可
                }
                if (progress != null) {
                    progress.report(i + 1, total,
                            "镜头 " + shot.getShotNumber() + " 失败: " + msg);
                }
                // 镜间尾帧衔接失败：整批停止，避免后续镜头在无衔接条件下继续烧钱出片
                if (isContinuityTailFrameFailure(msg)) {
                    throw new IllegalStateException(msg, ex);
                }
            }
        }
        if (results.isEmpty()) {
            throw new IllegalStateException("全部镜头视频生成失败，请检查资产设计图与方舟配置后重试");
        }
        return results;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ShotResponse generateShotVideoInNewTx(
            UUID projectId,
            DramaForgeConfig config,
            UUID shotId,
            String apiKeyHeader) {
        return generateShotVideoInNewTx(projectId, config, shotId, apiKeyHeader, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ShotResponse generateShotVideoInNewTx(
            UUID projectId,
            DramaForgeConfig config,
            UUID shotId,
            String apiKeyHeader,
            String continuityTailFrameUrl) {
        var shot = shotRepository.findById(shotId)
                .orElseThrow(() -> new ResourceNotFoundException("镜头不存在: " + shotId));
        // 当前产品无「确认分镜」步骤；出成片直接走资产参考，不再要求分镜锁定/分镜图
        return generateShotVideoInternal(projectId, config, shot, apiKeyHeader, continuityTailFrameUrl);
    }

    private ShotResponse generateShotVideoInternal(
            UUID projectId,
            DramaForgeConfig config,
            DramaForgeShot shot,
            String apiKeyHeader) {
        return generateShotVideoInternal(projectId, config, shot, apiKeyHeader, null);
    }

    private ShotResponse generateShotVideoInternal(
            UUID projectId,
            DramaForgeConfig config,
            DramaForgeShot shot,
            String apiKeyHeader,
            String continuityTailFrameUrl) {
        var projectAssets = assetRepository.findByProjectIdOrderBySortOrderAscNameAsc(projectId);
        // 单镜：先规划角色/场景/道具绑定
        shotAssetPlanner.planShot(projectId, shot, projectAssets, apiKeyHeader);
        projectAssets = assetRepository.findByProjectIdOrderBySortOrderAscNameAsc(projectId);

        // 先把镜头绑定资产提升到全局库，再解析参考图
        promoteShotAssetsToGlobal(projectId, shot, projectAssets);
        projectAssets = assetRepository.findByProjectIdOrderBySortOrderAscNameAsc(projectId);

        // 有对白时自动补齐角色音色样本；补齐后仍缺样本则由校验阻断出片
        ensureCharacterVoicesForDialogue(projectId, shot, projectAssets, apiKeyHeader);
        projectAssets = assetRepository.findByProjectIdOrderBySortOrderAscNameAsc(projectId);

        var readinessIssues = consistencyService.validateShotForVideo(shot, projectAssets);
        if (!readinessIssues.isEmpty()) {
            throw new IllegalStateException(String.join("；", readinessIssues));
        }

        var assetRefs = resolveShotAssetVideoReferences(shot, projectAssets);
        if (assetRefs.urls().isEmpty()) {
            throw new IllegalStateException(
                    "镜头 " + shot.getShotNumber()
                            + " 缺少可用参考图：请先为关联角色/场景/道具生成设计图");
        }

        if (assetRefs.labels().stream().noneMatch(l -> l.startsWith("场景"))) {
            var styleText = (config.getStylePrompt() != null ? config.getStylePrompt() : "")
                    + (shot.getDescription() != null ? shot.getDescription() : "");
            var inferred = DramaForgeShotAssetPlanner.fuzzyMatchScene(styleText, projectAssets);
            if (inferred != null) {
                shot.setSceneRef(inferred);
                shotRepository.save(shot);
                assetRefs = resolveShotAssetVideoReferences(shot, projectAssets);
            }
        }

        var characterRefs = readStringList(shot.getCharacterRefsJson());
        var propRefs = readStringList(shot.getPropRefsJson());
        // 有对白则尽量先准备干净 TTS，供 Seedance @Audio 口型同步、压低嘈杂环境音
        ensureDialogueAudioForVideo(projectId, shot, apiKeyHeader);
        shot = shotRepository.findById(shot.getId()).orElse(shot);

        // 视频首帧：镜头1无首帧参考；镜头2+仅用上一镜成片抽尾帧
        var openingFrame = resolveShotVideoOpeningUrl(projectId, shot, continuityTailFrameUrl);

        // 多帧参考：镜头1=资产图；镜头2+=@Image1上一镜成片尾帧 + 资产图
        var videoRefs = withSmartMultiFrameRefs(assetRefs, shot, openingFrame);
        var voiceRefs = resolveShotVoiceReferences(shot, projectAssets);
        var prompt = DramaForgeStylePrompts.videoPromptFromAssets(
                config, shot, characterRefs, propRefs, projectAssets,
                videoRefs.labels(), voiceRefs.labels(), videoRefs.urls());
        var ratio = DramaForgeStylePrompts.resolveAspectRatio(config);
        var audioUrls = voiceRefs.urls().isEmpty() ? null : voiceRefs.urls();
        // 有首帧参考图时不再传上一镜整段参考视频
        String prevVideo = null;

        var result = createShotVideoTask(
                projectId, shot, config, prompt, ratio, videoRefs, prevVideo, audioUrls, apiKeyHeader,
                openingFrame);

        // Seedance：参考音频总时长超限 → 去掉音频重试
        if (result == null && audioUrls != null) {
            prompt = DramaForgeStylePrompts.videoPromptFromAssets(
                    config, shot, characterRefs, propRefs, projectAssets,
                    videoRefs.labels(), List.of(), videoRefs.urls());
            result = createShotVideoTask(
                    projectId, shot, config, prompt, ratio, videoRefs, prevVideo, null, apiKeyHeader,
                    openingFrame);
        }

        if (result == null) {
            throw new IllegalStateException("镜头视频创建失败：请检查资产设计图与方舟配置后重试");
        }
        versionService.archiveCurrent(shot);
        shot.setVideoJobId(result.id());
        shot.setQaStatus("pending");
        shot.setErrorMessage(null);
        if (result.status() == GenerationStatus.COMPLETED) {
            shot.setStatus(DramaForgeShotStatus.VIDEO_DONE);
        } else if (result.status() == GenerationStatus.FAILED) {
            var err = result.errorMessage() != null && !result.errorMessage().isBlank()
                    ? result.errorMessage()
                    : "视频生成失败";
            markShotFailed(shot, err);
        } else {
            // 已提交异步任务：离开失败态，便于前端显示「生成中」
            if (shot.getStatus() == DramaForgeShotStatus.FAILED
                    || shot.getStatus() == DramaForgeShotStatus.VIDEO_DONE) {
                shot.setStatus(DramaForgeShotStatus.STORYBOARD_DONE);
            }
        }
        return toShotResponse(shotRepository.save(shot));
    }

    private static boolean isArkPrivacyBlockMessage(String msg) {
        if (msg == null || msg.isBlank()) {
            return false;
        }
        var lower = msg.toLowerCase(Locale.ROOT);
        return msg.contains("PrivacyInformation")
                || msg.contains("real person")
                || msg.contains("SensitiveContent")
                || lower.contains("privacy")
                || msg.contains("真人隐私");
    }

    private static void throwAssetDesignFailure(String assetLabel, String errorMessage) {
        var err = errorMessage != null && !errorMessage.isBlank() ? errorMessage : "unknown error";
        if (isArkPrivacyBlockMessage(err)) {
            throw new IllegalStateException(
                    "资产「" + assetLabel + "」定妆失败：触发方舟真人隐私拦截。"
                            + "请在资产卡片点击「合规重生」（虚构半写实文生图、不绑旧参考图），"
                            + "或改写描述去掉实拍/名人联想后重试。");
        }
        throw new IllegalStateException("资产「" + assetLabel + "」定妆失败：" + err);
    }

    private static void throwPrivacyBlocked(String context) {
        throw new IllegalStateException(
                context + "触发方舟真人隐私拦截。"
                        + "请先对相关角色做「合规重生」定妆（虚构半写实、非实拍人像），再重新出片。");
    }

    /**
     * 挂起外层镜头事务再调方舟：createForProject 失败会标记其自身事务回滚，
     * 若不挂起，外层 catch 重试后提交会报 rollback-only。
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public com.dreamreel.api.dto.VideoGenerationResponse createVideoForProjectSuspended(
            UUID projectId,
            CreateVideoGenerationRequest request,
            String apiKeyHeader) {
        return videoGenerationService.createForProject(projectId, request, com.dreamreel.api.config.ArkApiKeyContext.get());
    }

    private com.dreamreel.api.dto.VideoGenerationResponse createShotVideoTask(
            UUID projectId,
            DramaForgeShot shot,
            DramaForgeConfig config,
            String prompt,
            String ratio,
            ShotAssetVideoRefs assetRefs,
            String previousVideoUrl,
            List<String> audioUrls,
            String apiKeyHeader,
            String continuityTailFrameUrl) {
        if (assetRefs.urls().isEmpty()) {
            return null;
        }
        // 全能参考（reference_image）；首帧效果图已在 assetRefs 首位
        var primaryImage = assetRefs.urls().getFirst();
        try {
            return self.createVideoForProjectSuspended(projectId,
                    new CreateVideoGenerationRequest(
                            projectId,
                            shot.getId().toString(),
                            resolveVideoModel(config),
                            prompt,
                            resolveShotDurationSeconds(shot),
                            ratio,
                            resolveVideoQuality(config),
                            "reference-to-video",
                            primaryImage,
                            previousVideoUrl,
                            assetRefs.urls(),
                            audioUrls),
                    apiKeyHeader);
        } catch (IllegalStateException ex) {
            var msg = ex.getMessage() != null ? ex.getMessage() : "";
            var lower = msg.toLowerCase(Locale.ROOT);
            if (isArkPrivacyBlockMessage(msg)) {
                throwPrivacyBlocked("镜头 " + shot.getShotNumber() + " 视频失败：定妆/参考图");
            }
            if (audioUrls != null && !audioUrls.isEmpty()
                    && (msg.contains("audio total duration")
                    || msg.contains("参考音频")
                    || lower.contains("audio"))) {
                return null;
            }
            throw ex;
        }
    }

    private record ShotAssetVideoRefs(List<String> urls, List<String> labels) {}

    /**
     * 智能多帧参考：@Image1=上一镜成片抽尾帧，其后接角色场景道具。
     * 第一镜不传首帧参考图，仅用资产定妆图出片。
     * 镜头 2 起只使用上一镜成片抽尾帧，不做分镜/本镜首帧兜底。
     */
    private ShotAssetVideoRefs withSmartMultiFrameRefs(
            ShotAssetVideoRefs assetRefs,
            DramaForgeShot shot,
            String continuityTailFrameUrl) {
        var urls = new java.util.ArrayList<String>();
        var labels = new java.util.ArrayList<String>();

        // 第一镜：不传首帧参考；镜头 2+ 必须有上一镜成片抽尾帧
        if (!isFirstShot(shot)) {
            var opening = continuityTailFrameUrl != null && !continuityTailFrameUrl.isBlank()
                    ? continuityTailFrameUrl.trim()
                    : null;
            if (opening == null || opening.isBlank()) {
                throw new IllegalStateException(
                        "镜头 " + shot.getShotNumber()
                                + "：缺少上一镜成片尾帧（须先完成上一镜并成功抽帧），不再回退分镜/本镜首帧");
            }
            urls.add(opening);
            labels.add("上一镜成片尾帧");
        }

        for (int i = 0; i < assetRefs.urls().size() && urls.size() < 9; i++) {
            var url = assetRefs.urls().get(i);
            if (url == null || url.isBlank() || urls.contains(url)) {
                continue;
            }
            urls.add(url);
            labels.add(i < assetRefs.labels().size() ? assetRefs.labels().get(i) : ("参考" + (i + 1)));
        }
        return new ShotAssetVideoRefs(List.copyOf(urls), List.copyOf(labels));
    }

    private boolean isFirstShot(DramaForgeShot shot) {
        return shot == null || shot.getShotNumber() <= 1;
    }

    /** 解析镜头视频参考图：与 preview / 【参考映射】完全同一套顺序 */
    private ShotAssetVideoRefs resolveShotAssetVideoReferences(DramaForgeShot shot, List<DramaForgeAsset> projectAssets) {
        var refs = DramaForgeStylePrompts.resolveImageRefs(
                shot,
                readStringList(shot.getCharacterRefsJson()),
                readStringList(shot.getPropRefsJson()),
                projectAssets);
        return new ShotAssetVideoRefs(refs.urls(), refs.labels());
    }

    private void ensureDialogueAudioForVideo(UUID projectId, DramaForgeShot shot, String apiKeyHeader) {
        if (shot.getDialogue() == null || shot.getDialogue().isBlank()) {
            return;
        }
        if (shot.getDialogueAudioUrl() != null && !shot.getDialogueAudioUrl().isBlank()) {
            return;
        }
        try {
            generateShotDialogueAudio(projectId, shot.getEpisodeId(), shot.getId(), apiKeyHeader);
        } catch (Exception ignored) {
            // TTS 失败不阻断生视频，回退到角色音色样本
        }
    }

    /**
     * 镜头有对白时，为出场/说话角色自动生成音色样本（voiceSampleUrl），供 Seedance @Audio。
     * 单个角色生成失败不在此处抛错；补齐后仍缺样本则由 validateShotForVideo 阻断出片。
     */
    private void ensureCharacterVoicesForDialogue(
            UUID projectId,
            DramaForgeShot shot,
            List<DramaForgeAsset> projectAssets,
            String apiKeyHeader) {
        var dialogue = shot.getDialogue();
        if (dialogue == null || dialogue.isBlank()) {
            return;
        }
        var characterRefs = readStringList(shot.getCharacterRefsJson());
        if (characterRefs.isEmpty()) {
            return;
        }
        var desc = shot.getDescription() != null ? shot.getDescription() : "";
        var forceBind = Boolean.TRUE.equals(shot.getForceCharacterBinding());
        var neededNames = new LinkedHashSet<String>();
        for (var name : characterRefs) {
            if (name == null || name.isBlank()) {
                continue;
            }
            if (dialogue.contains(name)
                    || forceBind
                    || DramaForgeStylePrompts.isCharacterVisibleInShot(name, desc, dialogue)) {
                neededNames.add(name.trim());
            }
        }
        if (neededNames.isEmpty() && !characterRefs.isEmpty()) {
            // 对白里没写出角色名时，仍尽量给第一个绑定角色补音色
            neededNames.add(characterRefs.getFirst().trim());
        }
        for (var name : neededNames) {
            var asset = projectAssets.stream()
                    .filter(a -> a.getType() == DramaForgeAssetType.CHARACTER)
                    .filter(a -> a.getName() != null && a.getName().equalsIgnoreCase(name))
                    .findFirst()
                    .orElse(null);
            if (asset == null) {
                continue;
            }
            if (asset.getVoiceSampleUrl() != null && !asset.getVoiceSampleUrl().isBlank()) {
                continue;
            }
            try {
                generateCharacterVoice(projectId, asset.getId(), apiKeyHeader);
            } catch (Exception ignored) {
                // 单个角色失败不阻断整镜出片
            }
        }
    }

    private ShotVoiceRefs resolveShotVoiceReferences(DramaForgeShot shot, List<DramaForgeAsset> projectAssets) {
        // 有对白 TTS 时优先用干净对白轨做 @Audio，便于口型同步且压低模型乱加嘈杂环境音
        var dialogue = shot.getDialogue();
        var dialogueAudioUrl = shot.getDialogueAudioUrl();
        if (dialogue != null && !dialogue.isBlank()
                && dialogueAudioUrl != null && !dialogueAudioUrl.isBlank()) {
            var label = dialogue.trim();
            if (label.length() > 40) {
                label = label.substring(0, 40) + "…";
            }
            return new ShotVoiceRefs(
                    List.of(dialogueAudioUrl.trim()),
                    List.of("dialogue:" + label));
        }
        var refs = DramaForgeStylePrompts.resolveAudioRefs(
                shot,
                readStringList(shot.getCharacterRefsJson()),
                projectAssets);
        return new ShotVoiceRefs(refs.urls(), refs.labels());
    }

    public List<ShotResponse> generateGridStoryboards(UUID projectId, UUID episodeId, String apiKeyHeader) {
        var config = requireConfig(projectId);
        var episode = requireEpisode(projectId, episodeId);
        var shots = shotRepository.findByEpisodeIdOrderByShotNumberAsc(episode.getId()).stream()
                .filter(shot -> shot.getStoryboardUrl() == null || shot.getStoryboardUrl().isBlank())
                .toList();
        if (shots.isEmpty()) {
            throw new IllegalStateException("所有镜头已有分镜图");
        }

        var projectAssets = assetRepository.findByProjectIdOrderBySortOrderAscNameAsc(projectId);
        final int gridSize = 4;
        var results = new ArrayList<ShotResponse>();
        for (int i = 0; i < shots.size(); i += gridSize) {
            var group = shots.subList(i, Math.min(i + gridSize, shots.size()));
            var groupId = UUID.randomUUID();
            var prompt = DramaForgeStylePrompts.gridStoryboardPrompt(config, episode, group, projectAssets);
            var reference = resolvePrimaryCharacterReference(projectAssets, group);
            var result = generateImageWithReference(
                    projectId, groupId.toString(), config, prompt, reference, 0.55, apiKeyHeader);
            if (result.outputUrl() == null) {
                continue;
            }
            for (var shot : group) {
                versionService.archiveCurrent(shot);
                shot.setGridGroupId(groupId);
                shot.setStoryboardUrl(result.outputUrl());
                shot.setStatus(DramaForgeShotStatus.STORYBOARD_DONE);
                results.add(toShotResponse(shotRepository.save(shot)));
            }
        }
        if (results.isEmpty()) {
            throw new IllegalStateException("宫格分镜生成失败");
        }
        return results;
    }

    public List<ShotVersionResponse> listShotVersions(UUID projectId, UUID episodeId, UUID shotId) {
        requireShot(episodeId, shotId);
        requireEpisode(projectId, episodeId);
        return versionService.listVersions(shotId);
    }

    public ShotVersionResponse activateShotVersion(UUID projectId, UUID episodeId, UUID shotId, UUID versionId) {
        requireShot(episodeId, shotId);
        requireEpisode(projectId, episodeId);
        return versionService.activateVersion(shotId, versionId);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int syncPendingVideos(UUID projectId, UUID episodeId, String apiKeyHeader) {
        requireEpisode(projectId, episodeId);
        var shots = shotRepository.findByEpisodeIdOrderByShotNumberAsc(episodeId).stream()
                .filter(this::shouldSyncVideoShot)
                .toList();
        int synced = 0;
        for (var shot : shots) {
            // 本地已终态：直接回写，避免方舟 Key 异常时永远卡在「生成中」
            if (statusCalculator.applyLocalVideoJobTerminalStatus(shot)) {
                shotRepository.save(shot);
                synced++;
                continue;
            }
            try {
                var result = videoGenerationService.getForProject(
                        projectId, shot.getVideoJobId(), com.dreamreel.api.config.ArkApiKeyContext.get());
                if (result.status() == GenerationStatus.COMPLETED) {
                    shot.setStatus(DramaForgeShotStatus.VIDEO_DONE);
                    shot.setErrorMessage(null);
                    synced++;
                } else if (result.status() == GenerationStatus.FAILED) {
                    var err = result.errorMessage() != null && !result.errorMessage().isBlank()
                            ? result.errorMessage()
                            : "视频生成失败";
                    markShotFailed(shot, err);
                } else if (shot.getStatus() == DramaForgeShotStatus.FAILED) {
                    // 误标失败但上游仍在跑：恢复为可同步态，前端显示生成中
                    shot.setStatus(DramaForgeShotStatus.STORYBOARD_DONE);
                    shot.setErrorMessage(null);
                    synced++;
                }
                shotRepository.save(shot);
            } catch (Exception ex) {
                // 单镜同步失败不阻断整集；保留 pending 待下次再试
            }
        }
        // 已完成成片：强化 Seedance 原声（幂等，不叠 TTS；字幕由生成时模型自绘）
        for (var shot : shotRepository.findByEpisodeIdOrderByShotNumberAsc(episodeId)) {
            if (shotAudioRemasterService.remasterIfNeeded(shot)) {
                synced++;
            }
        }
        return synced;
    }

    /**
     * 需要同步的镜头：正常 pending，或误标 FAILED 但上游任务未失败（仍在跑/已完成待回写）。
     */
    private boolean shouldSyncVideoShot(DramaForgeShot shot) {
        if (shot.getVideoJobId() == null || shot.getStatus() == DramaForgeShotStatus.VIDEO_DONE) {
            return false;
        }
        if (shot.needsVideoSync()) {
            return true;
        }
        if (shot.getStatus() != DramaForgeShotStatus.FAILED) {
            return false;
        }
        return generationJobRepository.findById(shot.getVideoJobId())
                .map(job -> job.getStatus() != GenerationStatus.FAILED)
                .orElse(false);
    }

    /** 供 SYNC_VIDEOS 判断是否继续轮询（含误标失败但仍有未失败上游任务的镜头）。 */
    public boolean hasPendingVideoSync(UUID episodeId) {
        return shotRepository.findByEpisodeIdOrderByShotNumberAsc(episodeId).stream()
                .anyMatch(this::shouldSyncVideoShot);
    }

    /** 对白成片后处理：强化 Seedance 原声人声（不叠 TTS；字幕由生成提示词交给模型） */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ShotResponse remasterShotDialogueAudio(UUID projectId, UUID episodeId, UUID shotId) {
        var shot = requireShot(episodeId, shotId);
        requireEpisode(projectId, episodeId);
        if (shot.getStatus() != DramaForgeShotStatus.VIDEO_DONE) {
            throw new IllegalStateException("镜头 " + shot.getShotNumber() + " 尚无成片，请先生成视频");
        }
        if (shot.getDialogue() == null || shot.getDialogue().isBlank()) {
            throw new IllegalStateException("镜头 " + shot.getShotNumber() + " 没有对白");
        }
        // 强制从 Seedance 原片再处理（不叠 TTS）
        if (!shotAudioRemasterService.remasterIfNeeded(shot, true)) {
            shot = requireShot(episodeId, shotId);
            throw new IllegalStateException("镜头 " + shot.getShotNumber()
                    + " 原声增强失败，请确认已有成片且本机 ffmpeg 可用");
        }
        return toShotResponse(requireShot(episodeId, shotId));
    }

    public DramaForgeConfig ensureConfig(UUID projectId) {
        requireOwnedProject(projectId);
        return configRepository.findByProjectId(projectId).orElseGet(() -> {
            var created = new DramaForgeConfig();
            created.setProjectId(projectId);
            created.setGenerationMode(DramaForgeGenerationMode.REFERENCE_TO_VIDEO);
            return configRepository.save(created);
        });
    }

    private DramaForgeConfig requireConfig(UUID projectId) {
        return ensureConfig(projectId);
    }

    private Project requireOwnedProject(UUID projectId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            return projectRepository.findById(projectId)
                    .orElseThrow(() -> new ResourceNotFoundException("项目不存在: " + projectId));
        }
        if (principal.isAdmin()) {
            return projectRepository.findById(projectId)
                    .orElseThrow(() -> new ResourceNotFoundException("项目不存在: " + projectId));
        }
        return projectRepository.findByIdAndUserId(projectId, principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("项目不存在: " + projectId));
    }

    private DramaForgeAsset requireAsset(UUID projectId, UUID assetId) {
        return assetRepository.findByIdAndProjectId(assetId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("资产不存在: " + assetId));
    }

    private DramaForgeEpisode requireEpisode(UUID projectId, UUID episodeId) {
        return episodeRepository.findByIdAndProjectId(episodeId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("剧集不存在: " + episodeId));
    }

    private DramaForgeShot requireShot(UUID episodeId, UUID shotId) {
        return shotRepository.findByIdAndEpisodeId(shotId, episodeId)
                .orElseThrow(() -> new ResourceNotFoundException("镜头不存在: " + shotId));
    }

    /** 将镜头中的角色/场景/道具引用提升为项目全局资产（已存在则跳过） */
    public List<AssetResponse> promoteShotAssets(UUID projectId, UUID episodeId, UUID shotId) {
        requireEpisode(projectId, episodeId);
        var shot = requireShot(episodeId, shotId);
        var projectAssets = assetRepository.findByProjectIdOrderBySortOrderAscNameAsc(projectId);
        promoteShotAssetsToGlobal(projectId, shot, projectAssets);
        return listAssets(projectId);
    }

    /** 从上一个镜头提取绑定资产到全局，并合并引用到当前镜头 */
    public List<AssetResponse> promotePreviousShotAssets(UUID projectId, UUID episodeId, UUID shotId) {
        requireEpisode(projectId, episodeId);
        var shot = requireShot(episodeId, shotId);
        var previous = shotRepository.findByEpisodeIdOrderByShotNumberAsc(episodeId).stream()
                .filter(s -> s.getShotNumber() < shot.getShotNumber())
                .reduce((a, b) -> b)
                .orElseThrow(() -> new IllegalStateException("当前镜头没有上一个片段"));
        var projectAssets = assetRepository.findByProjectIdOrderBySortOrderAscNameAsc(projectId);
        promoteShotAssetsToGlobal(projectId, previous, projectAssets);
        mergeShotAssetRefs(shot, previous);
        shotRepository.save(shot);
        return listAssets(projectId);
    }

    private void mergeShotAssetRefs(DramaForgeShot target, DramaForgeShot source) {
        var chars = new java.util.LinkedHashSet<>(readStringList(target.getCharacterRefsJson()));
        chars.addAll(readStringList(source.getCharacterRefsJson()));
        target.setCharacterRefsJson(writeJson(new ArrayList<>(chars)));
        var props = new java.util.LinkedHashSet<>(readStringList(target.getPropRefsJson()));
        props.addAll(readStringList(source.getPropRefsJson()));
        target.setPropRefsJson(writeJson(new ArrayList<>(props)));
        if ((target.getSceneRef() == null || target.getSceneRef().isBlank())
                && source.getSceneRef() != null && !source.getSceneRef().isBlank()) {
            target.setSceneRef(source.getSceneRef());
        }
    }

    private void promoteShotAssetsToGlobal(
            UUID projectId,
            DramaForgeShot shot,
            List<DramaForgeAsset> projectAssets) {
        for (var name : readStringList(shot.getCharacterRefsJson())) {
            ensureGlobalAsset(projectId, DramaForgeAssetType.CHARACTER, name, projectAssets);
        }
        if (shot.getSceneRef() != null && !shot.getSceneRef().isBlank()) {
            ensureGlobalAsset(projectId, DramaForgeAssetType.SCENE, shot.getSceneRef(), projectAssets);
        }
        for (var name : readStringList(shot.getPropRefsJson())) {
            ensureGlobalAsset(projectId, DramaForgeAssetType.PROP, name, projectAssets);
        }
    }

    private void ensureGlobalAsset(
            UUID projectId,
            DramaForgeAssetType type,
            String name,
            List<DramaForgeAsset> projectAssets) {
        if (name == null || name.isBlank()) {
            return;
        }
        var trimmed = name.trim();
        var exists = projectAssets.stream()
                .anyMatch(a -> a.getType() == type && a.getName().equalsIgnoreCase(trimmed));
        if (exists) {
            return;
        }
        var asset = new DramaForgeAsset();
        asset.setProjectId(projectId);
        asset.setType(type);
        asset.setName(trimmed);
        asset.setDescription(trimmed);
        asset.setSortOrder(projectAssets.size());
        projectAssets.add(assetRepository.save(asset));
    }

    /** Seedance 平台时长规则：2–15 秒 */
    private int resolveShotDurationSeconds(DramaForgeShot shot) {
        if (shot.getDurationSeconds() != null && shot.getDurationSeconds() > 0) {
            return Math.max(2, Math.min(15, shot.getDurationSeconds()));
        }
        return estimateDurationSeconds(shot.getDescription(), shot.getDialogue());
    }

    private Integer parseDurationSeconds(JsonNode shotNode, String description, String dialogue) {
        for (var key : List.of("duration", "duration_seconds", "seconds", "时长")) {
            if (shotNode.has(key) && !shotNode.get(key).isNull()) {
                try {
                    var v = shotNode.get(key).isNumber()
                            ? shotNode.get(key).asInt()
                            : Integer.parseInt(shotNode.get(key).asText().replaceAll("[^0-9]", ""));
                    if (v > 0) {
                        return Math.max(2, Math.min(15, v));
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return estimateDurationSeconds(description, dialogue);
    }

    static int estimateDurationSeconds(String description, String dialogue) {
        var descLen = description != null ? description.trim().length() : 0;
        var dialLen = dialogue != null ? dialogue.trim().length() : 0;
        var fromDialogue = dialLen > 0 ? (int) Math.ceil(dialLen / 4.0) + 1 : 0;
        var fromDesc = descLen > 80 ? 8 : descLen > 40 ? 6 : 5;
        var seconds = Math.max(fromDialogue, fromDesc);
        return Math.max(2, Math.min(15, seconds));
    }

    /**
     * 出成片视频首帧（硬规则）：
     * - 镜头 1：不传首帧参考（返回 null），仅用资产定妆图
     * - 镜头 2 及后续：必须 ffmpeg 抽取「上一镜成片」尾帧；
     *   失败直接抛错，禁止回退分镜尾帧 / 本镜首帧
     */
    private String resolveShotVideoOpeningUrl(
            UUID projectId,
            DramaForgeShot shot,
            String continuityTailFrameUrl) {
        if (isFirstShot(shot)) {
            return null;
        }
        // 批处理已传入的上一镜抽帧结果优先复用
        if (continuityTailFrameUrl != null && !continuityTailFrameUrl.isBlank()) {
            return continuityTailFrameUrl.trim();
        }
        var previous = videoContinuityService.findPreviousCompletedShot(shot)
                .orElseThrow(() -> new IllegalStateException(missingPreviousShotVideoMessage(shot)));
        var extracted = videoContinuityService.extractLastFrameUrl(projectId, previous);
        if (extracted == null || extracted.isBlank()) {
            throw new IllegalStateException(
                    "镜头 " + shot.getShotNumber()
                            + "：抽取镜头 " + previous.getShotNumber()
                            + " 成片尾帧失败（ffmpeg/视频源不可用），已停止并不再回退分镜首帧");
        }
        return extracted.trim();
    }

    /** 镜头 N 要求镜头 N-1 已成片；报错明确写出缺哪一镜。 */
    private String missingPreviousShotVideoMessage(DramaForgeShot shot) {
        var prevNo = shot.getShotNumber() - 1;
        var prevExists = videoContinuityService.findImmediatePreviousShot(shot).isPresent();
        if (!prevExists) {
            return "镜头 " + shot.getShotNumber()
                    + "：缺少镜头 " + prevNo + "，无法抽取尾帧衔接。请先补齐上一镜后再生成";
        }
        return "镜头 " + shot.getShotNumber()
                + "：镜头 " + prevNo + " 尚无成片，无法抽取尾帧。请先完成上一镜视频生成";
    }

    /**
     * 列表/预览用首帧展示：有已解析的 preferred 时直接用；
     * 镜头 2+ 不回退本镜首帧（避免 UI 展示与真实出片语义不一致）。
     * 预览不在此处执行 ffmpeg 抽帧。
     */
    private String resolveVideoOpeningFrame(DramaForgeShot shot, String preferredOpeningUrl) {
        if (preferredOpeningUrl != null && !preferredOpeningUrl.isBlank()) {
            return preferredOpeningUrl.trim();
        }
        if (isFirstShot(shot)) {
            return null;
        }
        // 预览阶段可用上一镜分镜尾帧仅作展示占位；真实出片见 resolveShotVideoOpeningUrl
        if (shouldUseAutoContinuity(shot)) {
            var prevStoryboardLast = findPreviousShotLastFrameUrl(shot);
            if (prevStoryboardLast != null && !prevStoryboardLast.isBlank()) {
                return prevStoryboardLast;
            }
        }
        return null;
    }

    private String resolveFirstFrameUrl(DramaForgeShot shot, String continuityTailFrameUrl) {
        return resolveVideoOpeningFrame(shot, continuityTailFrameUrl);
    }

    /** 镜间连贯默认开启；镜头 2+ 出片硬规则始终抽上一镜成片尾帧，不受此开关影响。 */
    private boolean shouldUseAutoContinuity(DramaForgeShot shot) {
        var mode = shot.getReferenceVideoMode();
        if (mode != null && "none".equalsIgnoreCase(mode.trim())) {
            return false;
        }
        if (mode != null && "custom".equalsIgnoreCase(mode.trim())) {
            return false;
        }
        return true;
    }

    private String resolveFirstFrameUrl(DramaForgeShot shot) {
        return resolveFirstFrameUrl(shot, null);
    }

    private String resolvePreviousShotVideoUrl(DramaForgeShot shot) {
        var mode = shot.getReferenceVideoMode();
        if (mode != null && "none".equalsIgnoreCase(mode.trim())) {
            return null;
        }
        if (mode != null && "custom".equalsIgnoreCase(mode.trim())) {
            var custom = shot.getReferenceVideoUrl();
            return custom != null && !custom.isBlank() ? custom.trim() : null;
        }
        if (shot.getReferenceVideoUrl() != null && !shot.getReferenceVideoUrl().isBlank()) {
            return shot.getReferenceVideoUrl().trim();
        }
        return shotRepository.findByEpisodeIdOrderByShotNumberAsc(shot.getEpisodeId()).stream()
                .filter(s -> s.getShotNumber() < shot.getShotNumber())
                .filter(s -> s.getVideoJobId() != null)
                .reduce((a, b) -> b)
                .map(statusCalculator::resolveVideoUrl)
                .filter(url -> url != null && !url.isBlank())
                .orElse(null);
    }

    private int resolveEpisodeNumber(UUID projectId, Integer requested) {
        if (requested != null && requested > 0) {
            return requested;
        }
        return episodeRepository.findTopByProjectIdOrderByEpisodeNumberDesc(projectId)
                .map(episode -> episode.getEpisodeNumber() + 1)
                .orElse(1);
    }

    private int resolveShotNumber(UUID episodeId, Integer requested) {
        if (requested != null && requested > 0) {
            return requested;
        }
        return shotRepository.findTopByEpisodeIdOrderByShotNumberDesc(episodeId)
                .map(shot -> shot.getShotNumber() + 1)
                .orElse(1);
    }

    private ShotResponse toShotResponse(DramaForgeShot shot) {
        DramaForgeConfig config = null;
        List<DramaForgeAsset> assets = List.of();
        try {
            var episode = episodeRepository.findById(shot.getEpisodeId()).orElse(null);
            if (episode != null) {
                config = configRepository.findByProjectId(episode.getProjectId()).orElse(null);
                assets = assetRepository.findByProjectIdOrderBySortOrderAscNameAsc(episode.getProjectId());
            }
        } catch (Exception ignored) {
        }
        return toShotResponse(shot, config, assets);
    }

    private ShotResponse toShotResponse(DramaForgeShot shot, DramaForgeConfig config, List<DramaForgeAsset> assets) {
        return toShotResponse(shot, config, assets, null, findPreviousShotLastFrameUrl(shot));
    }

    private ShotResponse toShotResponse(
            DramaForgeShot shot,
            DramaForgeConfig config,
            List<DramaForgeAsset> assets,
            DramaForgeEpisode episode) {
        return toShotResponse(shot, config, assets, episode, findPreviousShotLastFrameUrl(shot));
    }

    private ShotResponse toShotResponse(
            DramaForgeShot shot,
            DramaForgeConfig config,
            List<DramaForgeAsset> assets,
            DramaForgeEpisode episode,
            String previousShotLastFrameUrl) {
        var characterRefs = readStringList(shot.getCharacterRefsJson());
        var propRefs = readStringList(shot.getPropRefsJson());
        String videoPrompt = null;
        String storyboardPrompt = shot.getStoryboardPrompt();
        List<MediaBinding> imageBindings = List.of();
        List<MediaBinding> audioBindings = List.of();
        if (config != null && assets != null) {
            var assetRefs = resolveShotAssetVideoReferences(shot, assets);
            // 与出成片提交一致的参考序；列表预览用分镜尾帧代替成片抽帧（避免列表接口做 ffmpeg）
            var videoRefs = buildVideoImageRefsForDisplay(assetRefs, shot, previousShotLastFrameUrl);
            var audio = DramaForgeStylePrompts.resolveAudioRefs(shot, characterRefs, assets);
            imageBindings = toMediaBindings(videoRefs.urls(), videoRefs.labels(), "@Image");
            audioBindings = toMediaBindings(audio.urls(), audio.labels(), "@Audio");
            videoPrompt = DramaForgeStylePrompts.videoPromptFromAssets(
                    config, shot, characterRefs, propRefs, assets,
                    videoRefs.labels(), audio.labels(), videoRefs.urls());
            if (storyboardPrompt == null || storyboardPrompt.isBlank()) {
                var ep = episode != null ? episode : episodeRepository.findById(shot.getEpisodeId()).orElse(null);
                if (ep != null) {
                    storyboardPrompt = DramaForgeStylePrompts.previewStoryboardPrompt(
                            config, ep, shot, characterRefs, propRefs, assets);
                }
            }
        }
        return new ShotResponse(
                shot.getId(),
                shot.getEpisodeId(),
                shot.getShotNumber(),
                shot.getDescription(),
                videoPrompt,
                storyboardPrompt,
                shot.getDialogue(),
                shot.getCameraNote(),
                characterRefs,
                shot.getSceneRef(),
                propRefs,
                imageBindings,
                audioBindings,
                shot.getStoryboardUrl(),
                shot.getVideoJobId(),
                statusCalculator.resolveVideoUrl(shot, false),
                resolveShotDurationSeconds(shot),
                shot.getForceCharacterBinding(),
                shot.getReferenceVideoMode() != null ? shot.getReferenceVideoMode() : "auto",
                shot.getReferenceVideoUrl(),
                shot.getFirstFrameUrl(),
                shot.getLastFrameUrl(),
                shot.getDialogueAudioUrl(),
                shot.getQaStatus() != null ? shot.getQaStatus() : "pending",
                shot.getModelMultiShot(),
                shot.getMultiShotTemplate(),
                shot.getStatus().name().toLowerCase(),
                statusCalculator.resolveErrorMessage(shot),
                shot.getCreatedAt(),
                shot.getUpdatedAt()
        );
    }

    /** 列表/详情预览：上一镜分镜尾帧作为「上一镜尾帧」展示。 */
    private String findPreviousShotLastFrameUrl(DramaForgeShot shot) {
        if (shot == null || shot.getEpisodeId() == null) {
            return null;
        }
        return shotRepository.findByEpisodeIdOrderByShotNumberAsc(shot.getEpisodeId()).stream()
                .filter(candidate -> candidate.getShotNumber() < shot.getShotNumber())
                .reduce((first, second) -> second)
                .map(DramaForgeShot::getLastFrameUrl)
                .filter(url -> url != null && !url.isBlank())
                .map(String::trim)
                .orElse(null);
    }

    /**
     * 出成片参考图预览：有首尾帧时与 withSmartMultiFrameRefs 同序；缺帧时回退资产图。
     */
    private ShotAssetVideoRefs buildVideoImageRefsForDisplay(
            ShotAssetVideoRefs assetRefs,
            DramaForgeShot shot,
            String previousShotLastFrameUrl) {
        var ending = shot.getLastFrameUrl();
        var opening = resolveVideoOpeningFrame(shot, previousShotLastFrameUrl);
        if (opening == null || opening.isBlank() || ending == null || ending.isBlank()) {
            return assetRefs;
        }
        var refs = withSmartMultiFrameRefs(assetRefs, shot, previousShotLastFrameUrl);
        // 预览界面：首帧来自上一镜成片抽帧，用中文占位符代替分镜尾帧 URL（实际生成接口不受影响）
        if (previousShotLastFrameUrl != null
                && !previousShotLastFrameUrl.isBlank()
                && !refs.urls().isEmpty()
                && previousShotLastFrameUrl.trim().equals(refs.urls().get(0).trim())) {
            var displayUrls = new ArrayList<>(refs.urls());
            displayUrls.set(0, "抽取上一个镜头尾帧");
            return new ShotAssetVideoRefs(displayUrls, refs.labels());
        }
        return refs;
    }

    private void markShotFailed(DramaForgeShot shot, String message) {
        shot.setStatus(DramaForgeShotStatus.FAILED);
        if (message != null && !message.isBlank()) {
            var trimmed = message.trim();
            shot.setErrorMessage(trimmed.length() > 2000 ? trimmed.substring(0, 2000) : trimmed);
        }
    }

    /** 等待成片超时但上游任务可能仍在跑，不应记为镜头失败。 */
    private static boolean isSoftVideoWaitTimeout(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        return message.contains("仍在生成中")
                || message.contains("等待超时")
                || message.contains("视频生成超时");
    }

    /** 上一镜成片尾帧抽取/衔接失败：应中断整批，勿继续提交后续镜头。 */
    private static boolean isContinuityTailFrameFailure(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        return message.contains("成片尾帧失败")
                || message.contains("抽取尾帧失败")
                || message.contains("缺少上一镜成片尾帧")
                || message.contains("无法抽取尾帧")
                || message.contains("后续镜头无法衔接")
                || message.contains("不再回退分镜首帧");
    }

    /** 供任务处理器在 SHOT_VIDEO / SHOT_STORYBOARD 失败时回写镜头错误。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markShotFailedById(UUID shotId, String message) {
        var shot = shotRepository.findById(shotId).orElse(null);
        if (shot == null) {
            return;
        }
        markShotFailed(shot, message);
        shotRepository.save(shot);
    }

    private static List<MediaBinding> toMediaBindings(List<String> urls, List<String> labels, String prefix) {
        var list = new ArrayList<MediaBinding>();
        for (int i = 0; i < labels.size(); i++) {
            var url = i < urls.size() ? urls.get(i) : null;
            list.add(new MediaBinding(i + 1, prefix + (i + 1), labels.get(i), url));
        }
        return list;
    }

    /** 一次性补齐旧镜头规划描述 */
    private void ensurePlanningPromptMaterialized(DramaForgeShot shot, List<DramaForgeAsset> assets) {
        var desc = shot.getDescription() != null ? shot.getDescription() : "";
        if (desc.contains("【镜头】") || desc.contains("【角色线索】")) {
            return;
        }
        var chars = readStringList(shot.getCharacterRefsJson());
        var props = readStringList(shot.getPropRefsJson());
        if (chars.isEmpty() && (shot.getSceneRef() == null || shot.getSceneRef().isBlank()) && props.isEmpty()) {
            return;
        }
        shotAssetPlanner.materializePlanningPrompt(shot, assets);
        shotRepository.save(shot);
    }

    private String resolveSceneNameFromNode(JsonNode shotNode, String parentSceneName) {
        var scene = firstText(shotNode, "scene");
        if (scene != null && !scene.isBlank()) {
            return scene.trim();
        }
        return parentSceneName != null && !parentSceneName.isBlank() ? parentSceneName.trim() : null;
    }

    private String resolveShotReferenceImage(UUID projectId, DramaForgeShot shot) {
        if (shot.getStoryboardUrl() != null && !shot.getStoryboardUrl().isBlank()) {
            return shot.getStoryboardUrl();
        }
        var projectAssets = assetRepository.findByProjectIdOrderBySortOrderAscNameAsc(projectId);
        return resolveCharacterReferenceImage(shot, projectAssets);
    }

    private String resolveVideoReference(DramaForgeConfig config, UUID projectId, DramaForgeShot shot) {
        if (config.getGenerationMode() == DramaForgeGenerationMode.REFERENCE_TO_VIDEO) {
            return resolveShotReferenceImage(projectId, shot);
        }
        // 默认用分镜图 URL 作为视频参考
        if (shot.getStoryboardUrl() != null && !shot.getStoryboardUrl().isBlank()) {
            return shot.getStoryboardUrl();
        }
        return null;
    }

    /** 三维定妆 / 三候选定妆：火山方舟 Seedream 5.0 Lite（无 TokenFree 回退） */
    private static final String ARK_SEEDREAM_DESIGN_MODEL = "doubao-seedream-5-0-260128";
    /** 分镜图生图：火山方舟 Seedream 5 Pro */
    private static final String SEEDREAM_5_PRO_MODEL = "doubao-seedream-5-0-pro-260628";

    private String resolveStoryboardTextModel(DramaForgeConfig config) {
        return resolveSeedreamModel(config, false);
    }

    private String resolveStoryboardImageModel(DramaForgeConfig config) {
        return resolveSeedreamModel(config, true);
    }

    private String resolveImageModel(DramaForgeConfig config) {
        return resolveSeedreamModel(config, false);
    }

    private List<String> resolveImageEditModelCandidates(DramaForgeConfig config) {
        return List.of(ARK_SEEDREAM_DESIGN_MODEL);
    }

    private String resolveImageEditModel(DramaForgeConfig config) {
        return resolveSeedreamModel(config, true);
    }

    /** 配置层默认定妆用 Seedream 5.0 Lite；实际定妆调用见 createDesignImagePreferringArk。 */
    private String resolveSeedreamModel(DramaForgeConfig config, boolean edit) {
        var configured = resolveConfiguredImageModel(config, edit);
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return ARK_SEEDREAM_DESIGN_MODEL;
    }

    private String resolveConfiguredImageModel(DramaForgeConfig config, boolean edit) {
        if (config.getImageBackend() != null && !config.getImageBackend().isBlank()) {
            return config.getImageBackend().trim();
        }
        if (edit) {
            var editDefault = tokenFreeProperties.defaultImageEditModel();
            if (editDefault != null && !editDefault.isBlank()) {
                return editDefault.trim();
            }
        }
        var configured = tokenFreeProperties.defaultImageModel();
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        if (!edit) {
            var editDefault = tokenFreeProperties.defaultImageEditModel();
            if (editDefault != null && !editDefault.isBlank()) {
                return editDefault.trim();
            }
        }
        return null;
    }

    private static boolean isSeedreamModel(String model) {
        return model != null && model.toLowerCase(Locale.ROOT).contains("seedream");
    }

    private String resolveVideoModel(DramaForgeConfig config) {
        if (config.getVideoBackend() != null && !config.getVideoBackend().isBlank()) {
            return config.getVideoBackend();
        }
        return "doubao-seedance-2-5-260628";
    }

    private String resolveImageQuality(DramaForgeConfig config) {
        return com.dreamreel.api.util.MediaSizeHelper.normalizeResolution(config.getImageQuality());
    }

    private String resolveVideoQuality(DramaForgeConfig config) {
        if (config.getVideoQuality() != null && !config.getVideoQuality().isBlank()) {
            return com.dreamreel.api.util.MediaSizeHelper.normalizeResolution(config.getVideoQuality());
        }
        return "720p";
    }

    private record ShotVoiceRefs(List<String> urls, List<String> labels) {}

    private DramaForgeContentMode parseContentMode(String value) {
        return DramaForgeContentMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    private DramaForgeGenerationMode parseGenerationMode(String value) {
        return DramaForgeGenerationMode.valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
    }

    private String parseAspectRatio(String value) {
        var normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return switch (normalized) {
            case "9:16", "16:9", "1:1", "4:3", "3:4" -> normalized;
            default -> throw new IllegalArgumentException("不支持的画面比例: " + value);
        };
    }

    private DramaForgeAssetType parseAssetType(String value) {
        return DramaForgeAssetType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    private DramaForgeShotStatus parseShotStatus(String value) {
        return DramaForgeShotStatus.valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private String firstText(JsonNode node, String... fields) {
        for (var field : fields) {
            if (node.has(field) && !node.get(field).isNull()) {
                var text = node.get(field).asText();
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        return null;
    }
}
