package com.dreamreel.api.dramaforge.dto;

import com.dreamreel.api.dramaforge.domain.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DramaForgeDtos {

    private DramaForgeDtos() {
    }

    public record ConfigResponse(
            UUID projectId,
            String contentMode,
            String generationMode,
            String aspectRatio,
            String imageBackend,
            String videoBackend,
            String textBackend,
            String imageQuality,
            String videoQuality,
            String stylePrompt,
            String sourceText,
            String projectSummary,
            String worldview,
            String colorGradePreset,
            boolean mixDialogueAudioInCompose,
            String bgmUrl,
            Double bgmVolume,
            boolean lipSyncEnabled,
            String lipSyncEndpoint,
            boolean preferModelMultiShot,
            Instant assetsLockedAt,
            Instant storyboardLockedAt,
            Instant updatedAt
    ) {
        public static ConfigResponse from(DramaForgeConfig config) {
            return new ConfigResponse(
                    config.getProjectId(),
                    config.getContentMode().name().toLowerCase(),
                    config.getGenerationMode().name().toLowerCase(),
                    config.getAspectRatio(),
                    config.getImageBackend(),
                    config.getVideoBackend(),
                    config.getTextBackend(),
                    config.getImageQuality() != null ? config.getImageQuality() : "720p",
                    config.getVideoQuality() != null ? config.getVideoQuality() : "480p",
                    config.getStylePrompt(),
                    config.getSourceText(),
                    config.getProjectSummary(),
                    config.getWorldview(),
                    config.getColorGradePreset() != null ? config.getColorGradePreset() : "none",
                    config.isMixDialogueAudioInCompose(),
                    config.getBgmUrl(),
                    config.getBgmVolume() != null ? config.getBgmVolume() : 0.18,
                    config.isLipSyncEnabled(),
                    config.getLipSyncEndpoint(),
                    config.isPreferModelMultiShot(),
                    config.getAssetsLockedAt(),
                    config.getStoryboardLockedAt(),
                    config.getUpdatedAt()
            );
        }
    }

    public record UpdateConfigRequest(
            String contentMode,
            String generationMode,
            @Size(max = 8) String aspectRatio,
            @Size(max = 128) String imageBackend,
            @Size(max = 128) String videoBackend,
            @Size(max = 128) String textBackend,
            @Size(max = 2000) String stylePrompt,
            String sourceText,
            String projectSummary,
            String worldview,
            @Size(max = 16) String imageQuality,
            @Size(max = 16) String videoQuality,
            @Size(max = 24) String colorGradePreset,
            Boolean mixDialogueAudioInCompose,
            @Size(max = 2000) String bgmUrl,
            Double bgmVolume,
            Boolean lipSyncEnabled,
            @Size(max = 2000) String lipSyncEndpoint,
            Boolean preferModelMultiShot
    ) {}

    public record AssetResponse(
            UUID id,
            UUID projectId,
            String type,
            String name,
            String description,
            String designPrompt,
            String referenceImageUrl,
            String voiceLabel,
            String voiceSampleUrl,
            String voiceSpeakerId,
            Integer identityLockStrength,
            String loraRef,
            int sortOrder,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static AssetResponse from(DramaForgeAsset asset) {
            return new AssetResponse(
                    asset.getId(),
                    asset.getProjectId(),
                    asset.getType().name().toLowerCase(),
                    asset.getName(),
                    asset.getDescription(),
                    asset.getDesignPrompt(),
                    asset.getReferenceImageUrl(),
                    asset.getVoiceLabel(),
                    asset.getVoiceSampleUrl(),
                    asset.getVoiceSpeakerId(),
                    asset.getIdentityLockStrength(),
                    asset.getLoraRef(),
                    asset.getSortOrder(),
                    asset.getCreatedAt(),
                    asset.getUpdatedAt()
            );
        }
    }

    public record CreateAssetRequest(
            @NotBlank String type,
            @NotBlank @Size(max = 128) String name,
            @Size(max = 2000) String description,
            @Size(max = 4000) String designPrompt,
            @Size(max = 2000) String referenceImageUrl,
            @Size(max = 500) String voiceLabel,
            @Size(max = 2000) String voiceSampleUrl,
            @Size(max = 128) String voiceSpeakerId,
            Integer identityLockStrength,
            @Size(max = 256) String loraRef,
            Integer sortOrder
    ) {}

    public record UpdateAssetRequest(
            @Size(max = 128) String name,
            @Size(max = 2000) String description,
            @Size(max = 4000) String designPrompt,
            @Size(max = 2000) String referenceImageUrl,
            @Size(max = 500) String voiceLabel,
            @Size(max = 2000) String voiceSampleUrl,
            @Size(max = 128) String voiceSpeakerId,
            Integer identityLockStrength,
            @Size(max = 256) String loraRef,
            Integer sortOrder
    ) {}

    public record EpisodeResponse(
            UUID id,
            UUID projectId,
            int episodeNumber,
            String title,
            String scriptJson,
            long shotCount,
            Instant scriptLockedAt,
            Instant storyboardLockedAt,
            String timelineJson,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static EpisodeResponse from(DramaForgeEpisode episode, long shotCount) {
            return new EpisodeResponse(
                    episode.getId(),
                    episode.getProjectId(),
                    episode.getEpisodeNumber(),
                    episode.getTitle(),
                    episode.getScriptJson(),
                    shotCount,
                    episode.getScriptLockedAt(),
                    episode.getStoryboardLockedAt(),
                    episode.getTimelineJson(),
                    episode.getCreatedAt(),
                    episode.getUpdatedAt()
            );
        }
    }

    public record CreateEpisodeRequest(
            @NotBlank @Size(max = 200) String title,
            Integer episodeNumber,
            String scriptJson
    ) {}

    public record UpdateEpisodeRequest(
            @Size(max = 200) String title,
            String scriptJson,
            String timelineJson
    ) {}

    public record ShotResponse(
            UUID id,
            UUID episodeId,
            int shotNumber,
            String description,
            /** 规划/生视频用的完整提示词预览（含风格、角色线索、参考映射） */
            String videoPrompt,
            /** 分镜首帧专用提示词（静态关键帧，与 videoPrompt 分离） */
            String storyboardPrompt,
            String dialogue,
            String cameraNote,
            List<String> characterRefs,
            String sceneRef,
            List<String> propRefs,
            /** 实际提交的 @ImageN 绑定（与提示词一致） */
            List<MediaBinding> imageBindings,
            /** 实际提交的 @AudioN 绑定 */
            List<MediaBinding> audioBindings,
            String storyboardUrl,
            UUID videoJobId,
            String videoUrl,
            Integer durationSeconds,
            Boolean forceCharacterBinding,
            String referenceVideoMode,
            String referenceVideoUrl,
            String firstFrameUrl,
            String lastFrameUrl,
            String dialogueAudioUrl,
            String qaStatus,
            Boolean modelMultiShot,
            String multiShotTemplate,
            String status,
            String errorMessage,
            Instant createdAt,
            Instant updatedAt
    ) {}

    /** Seedance 参考媒体槽位 */
    public record MediaBinding(
            int index,
            String tag,
            String label,
            String url
    ) {}

    public record CreateShotRequest(
            @NotBlank @Size(max = 16000) String description,
            @Size(max = 4000) String dialogue,
            @Size(max = 2000) String cameraNote,
            List<String> characterRefs,
            Integer shotNumber,
            @Min(2) @Max(15) Integer durationSeconds
    ) {}

    public record UpdateShotRequest(
            @Size(max = 16000) String description,
            @Size(max = 4000) String dialogue,
            @Size(max = 2000) String cameraNote,
            List<String> characterRefs,
            @Size(max = 200) String sceneRef,
            List<String> propRefs,
            @Size(max = 2000) String storyboardUrl,
            String status,
            @Min(2) @Max(15) Integer durationSeconds,
            Boolean forceCharacterBinding,
            @Size(max = 32) String referenceVideoMode,
            @Size(max = 2000) String referenceVideoUrl,
            @Size(max = 2000) String firstFrameUrl,
            @Size(max = 2000) String lastFrameUrl,
            @Size(max = 8000) String storyboardPrompt,
            @Size(max = 2000) String dialogueAudioUrl,
            @Size(max = 16) String qaStatus,
            Boolean modelMultiShot,
            @Size(max = 32) String multiShotTemplate
    ) {}

    public record ExpandMultiCamRequest(
            @NotBlank @Size(max = 32) String template,
            /** true=单次 Seedance 多机位成片；false=展开为多个独立镜头 */
            Boolean modelMultiShot,
            /** 物理展开时删除原镜头并收紧编号 */
            Boolean removeSource
    ) {}

    public record MultiCamPresetItem(
            String id,
            String label,
            String cameraNote
    ) {}

    public record MultiCamTemplateItem(
            String id,
            String label,
            List<MultiCamPresetItem> presets
    ) {}

    public record MultiCamTemplatesResponse(
            List<MultiCamTemplateItem> templates
    ) {}

    public record BatchDialogueAudioResponse(
            int attempted,
            int succeeded,
            List<String> errors
    ) {}

    public record ExpandEpisodeMultiCamRequest(
            @NotBlank @Size(max = 32) String template,
            Boolean modelMultiShot,
            @Size(max = 128) String sceneRef,
            /** 每个场景仅展开该场景下首个镜头 */
            Boolean firstPerSceneOnly,
            Boolean removeSource
    ) {}

    public record BatchExpandMultiCamResponse(
            int attempted,
            int expanded,
            int removedSources,
            List<String> errors
    ) {}

    public record ComposeReadinessResponse(
            int totalShots,
            int videoDoneShots,
            int shotsWithDialogue,
            int missingDialogueAudio,
            boolean lipSyncEnabled,
            boolean lipSyncEndpointConfigured,
            boolean mixDialogueAudio,
            List<String> warnings,
            List<String> blockers
    ) {}

    public record ConsistencyReportResponse(
            long assetsMissingDesignImage,
            long charactersMissingVoiceSample,
            long shotsMissingBindings,
            long shotsReadyForVideo,
            long shotsPendingVideo,
            List<String> warnings
    ) {}

    public record PipelineOverviewResponse(
            UUID projectId,
            String stage,
            int progress,
            String contentMode,
            String generationMode,
            Map<String, Long> assetCounts,
            long episodeCount,
            long shotCount,
            long storyboardDoneCount,
            long videoDoneCount,
            List<String> nextActions,
            ConsistencyReportResponse consistency,
            Instant assetsLockedAt,
            Instant storyboardLockedAt,
            boolean scriptLocked,
            boolean storyboardLocked
    ) {}

    public record PlanEpisodesResponse(
            int plannedCount,
            List<PlanEpisodeOutline> episodes
    ) {}

    public record PlanEpisodeOutline(
            int episodeNumber,
            String title,
            String summary
    ) {}

    public record AssetDesignCandidatesResponse(
            UUID assetId,
            List<AssetVersionResponse> candidates
    ) {}

    public record JobResponse(
            UUID id,
            UUID projectId,
            String jobType,
            String status,
            UUID episodeId,
            UUID targetId,
            String errorMessage,
            int progressCurrent,
            int progressTotal,
            String progressMessage,
            Integer queuePosition,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static JobResponse from(DramaForgeJob job) {
            return from(job, null);
        }

        public static JobResponse from(DramaForgeJob job, Integer queuePosition) {
            return new JobResponse(
                    job.getId(),
                    job.getProjectId(),
                    job.getJobType().name().toLowerCase(),
                    job.getStatus().name().toLowerCase(),
                    job.getEpisodeId(),
                    job.getTargetId(),
                    job.getErrorMessage(),
                    job.getProgressCurrent(),
                    job.getProgressTotal(),
                    job.getProgressMessage(),
                    queuePosition,
                    job.getCreatedAt(),
                    job.getUpdatedAt()
            );
        }
    }

    public record CompositionResponse(
            UUID id,
            UUID projectId,
            UUID episodeId,
            String outputUrl,
            String status,
            String errorMessage,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static CompositionResponse from(DramaForgeComposition composition) {
            return new CompositionResponse(
                    composition.getId(),
                    composition.getProjectId(),
                    composition.getEpisodeId(),
                    composition.getOutputUrl(),
                    composition.getStatus(),
                    composition.getErrorMessage(),
                    composition.getCreatedAt(),
                    composition.getUpdatedAt()
            );
        }
    }

    public record ShotVersionResponse(
            UUID id,
            UUID shotId,
            int versionNo,
            String storyboardUrl,
            UUID videoJobId,
            String videoUrl,
            boolean active,
            Instant createdAt
    ) {}

    public record AssetVersionResponse(
            UUID id,
            UUID assetId,
            int versionNo,
            String referenceImageUrl,
            String designPrompt,
            boolean active,
            Instant createdAt
    ) {}

    public record ExportResponse(
            String type,
            String downloadUrl,
            String message
    ) {}

    public record AgentMessageDto(
            String role,
            String content
    ) {}

    public record AgentChatRequest(
            @NotBlank @Size(max = 4000) String message,
            UUID selectedEpisodeId,
            List<AgentMessageDto> history
    ) {}

    public record AgentActionDto(
            String tool,
            String status,
            String detail
    ) {}

    public record AgentChatResponse(
            String reply,
            List<AgentActionDto> actions
    ) {}

    public record OptimizePromptRequest(
            @NotBlank String kind,
            @Size(max = 4000) String draft,
            UUID assetId,
            UUID episodeId,
            UUID shotId,
            @Size(max = 32) String assetType,
            @Size(max = 128) String assetName,
            @Size(max = 2000) String assetDescription
    ) {}

    public record OptimizePromptResponse(
            String optimizedText
    ) {}
}
