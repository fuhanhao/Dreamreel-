package com.dreamreel.api.dramaforge.controller;

import com.dreamreel.api.dramaforge.domain.DramaForgeJobType;
import com.dreamreel.api.dramaforge.dto.DramaForgeDtos.*;
import com.dreamreel.api.dramaforge.service.DramaForgeEnqueueService;
import com.dreamreel.api.dramaforge.service.DramaForgePromptService;
import com.dreamreel.api.dramaforge.service.DramaForgeService;
import com.dreamreel.api.common.ApiResponse;
import com.dreamreel.api.controller.ImageController;
import com.dreamreel.api.controller.VideoController;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dramaforge/projects/{projectId}")
public class DramaForgeController {

    private final DramaForgeService dramaForgeService;
    private final DramaForgeEnqueueService enqueueService;
    private final DramaForgePromptService promptService;

    public DramaForgeController(
            DramaForgeService dramaForgeService,
            DramaForgeEnqueueService enqueueService,
            DramaForgePromptService promptService) {
        this.dramaForgeService = dramaForgeService;
        this.enqueueService = enqueueService;
        this.promptService = promptService;
    }

    @GetMapping("/overview")
    public ApiResponse<PipelineOverviewResponse> overview(@PathVariable UUID projectId) {
        return ApiResponse.ok(dramaForgeService.getOverview(projectId));
    }

    @GetMapping("/config")
    public ApiResponse<ConfigResponse> getConfig(@PathVariable UUID projectId) {
        return ApiResponse.ok(dramaForgeService.getConfig(projectId));
    }

    @PutMapping("/config")
    public ApiResponse<ConfigResponse> updateConfig(
            @PathVariable UUID projectId,
            @Valid @RequestBody UpdateConfigRequest request) {
        return ApiResponse.ok(dramaForgeService.updateConfig(projectId, request));
    }

    @GetMapping("/assets")
    public ApiResponse<List<AssetResponse>> listAssets(@PathVariable UUID projectId) {
        return ApiResponse.ok(dramaForgeService.listAssets(projectId));
    }

    @PostMapping("/assets")
    public ApiResponse<AssetResponse> createAsset(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateAssetRequest request) {
        return ApiResponse.ok(dramaForgeService.createAsset(projectId, request));
    }

    @PatchMapping("/assets/{assetId}")
    public ApiResponse<AssetResponse> updateAsset(
            @PathVariable UUID projectId,
            @PathVariable UUID assetId,
            @Valid @RequestBody UpdateAssetRequest request) {
        return ApiResponse.ok(dramaForgeService.updateAsset(projectId, assetId, request));
    }

    @DeleteMapping("/assets/{assetId}")
    public ApiResponse<Void> deleteAsset(
            @PathVariable UUID projectId,
            @PathVariable UUID assetId) {
        dramaForgeService.deleteAsset(projectId, assetId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/assets/generate-designs")
    public ApiResponse<JobResponse> generateAssetDesigns(
            @PathVariable UUID projectId,
            @RequestHeader(value = ImageController.API_KEY_HEADER, required = false) String apiKey) {
        return ApiResponse.ok(enqueueService.enqueue(projectId, DramaForgeJobType.ASSET_DESIGN, null, apiKey));
    }

    @PostMapping("/assets/{assetId}/generate-design")
    public ApiResponse<JobResponse> regenerateAssetDesign(
            @PathVariable UUID projectId,
            @PathVariable UUID assetId,
            @RequestParam(defaultValue = "false") boolean privacySafe,
            @RequestHeader(value = ImageController.API_KEY_HEADER, required = false) String apiKey) {
        return ApiResponse.ok(enqueueService.enqueueAssetDesign(projectId, assetId, apiKey, privacySafe));
    }

    @PostMapping("/assets/{assetId}/generate-design-candidates")
    public ApiResponse<AssetDesignCandidatesResponse> generateAssetDesignCandidates(
            @PathVariable UUID projectId,
            @PathVariable UUID assetId,
            @RequestHeader(value = ImageController.API_KEY_HEADER, required = false) String apiKey) {
        return ApiResponse.ok(dramaForgeService.generateAssetDesignCandidates(projectId, assetId, apiKey));
    }

    @PostMapping("/assets/{assetId}/select-candidate/{versionId}")
    public ApiResponse<AssetVersionResponse> selectAssetCandidate(
            @PathVariable UUID projectId,
            @PathVariable UUID assetId,
            @PathVariable UUID versionId) {
        return ApiResponse.ok(dramaForgeService.activateAssetVersion(projectId, assetId, versionId));
    }

    @PostMapping("/assets/{assetId}/generate-voice")
    public ApiResponse<AssetResponse> generateAssetVoice(
            @PathVariable UUID projectId,
            @PathVariable UUID assetId,
            @RequestHeader(value = ImageController.API_KEY_HEADER, required = false) String apiKey) {
        return ApiResponse.ok(dramaForgeService.generateCharacterVoice(projectId, assetId, apiKey));
    }

    @GetMapping("/assets/{assetId}/versions")
    public ApiResponse<List<AssetVersionResponse>> listAssetVersions(
            @PathVariable UUID projectId,
            @PathVariable UUID assetId) {
        return ApiResponse.ok(dramaForgeService.listAssetVersions(projectId, assetId));
    }

    @PostMapping("/assets/{assetId}/versions/{versionId}/activate")
    public ApiResponse<AssetVersionResponse> activateAssetVersion(
            @PathVariable UUID projectId,
            @PathVariable UUID assetId,
            @PathVariable UUID versionId) {
        return ApiResponse.ok(dramaForgeService.activateAssetVersion(projectId, assetId, versionId));
    }

    @PostMapping("/assets/optimize-design-prompts")
    public ApiResponse<List<AssetResponse>> optimizeAssetDesignPrompts(
            @PathVariable UUID projectId,
            @RequestHeader(value = ImageController.API_KEY_HEADER, required = false) String apiKey) {
        return ApiResponse.ok(promptService.optimizeAllAssetDesignPrompts(projectId, apiKey));
    }

    @PostMapping("/prompts/optimize")
    public ApiResponse<OptimizePromptResponse> optimizePrompt(
            @PathVariable UUID projectId,
            @Valid @RequestBody OptimizePromptRequest request,
            @RequestHeader(value = ImageController.API_KEY_HEADER, required = false) String apiKey) {
        return ApiResponse.ok(promptService.optimize(projectId, request, apiKey));
    }

    @GetMapping("/episodes")
    public ApiResponse<List<EpisodeResponse>> listEpisodes(@PathVariable UUID projectId) {
        return ApiResponse.ok(dramaForgeService.listEpisodes(projectId));
    }

    @PostMapping("/episodes")
    public ApiResponse<EpisodeResponse> createEpisode(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateEpisodeRequest request) {
        return ApiResponse.ok(dramaForgeService.createEpisode(projectId, request));
    }

    @PatchMapping("/episodes/{episodeId}")
    public ApiResponse<EpisodeResponse> updateEpisode(
            @PathVariable UUID projectId,
            @PathVariable UUID episodeId,
            @Valid @RequestBody UpdateEpisodeRequest request) {
        return ApiResponse.ok(dramaForgeService.updateEpisode(projectId, episodeId, request));
    }

    @DeleteMapping("/episodes/{episodeId}")
    public ApiResponse<Void> deleteEpisode(
            @PathVariable UUID projectId,
            @PathVariable UUID episodeId) {
        dramaForgeService.deleteEpisode(projectId, episodeId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/episodes/{episodeId}/shots")
    public ApiResponse<List<ShotResponse>> listShots(
            @PathVariable UUID projectId,
            @PathVariable UUID episodeId) {
        return ApiResponse.ok(dramaForgeService.listShots(projectId, episodeId));
    }

    @GetMapping("/episodes/{episodeId}/shots/{shotId}")
    public ApiResponse<ShotResponse> getShot(
            @PathVariable UUID projectId,
            @PathVariable UUID episodeId,
            @PathVariable UUID shotId) {
        return ApiResponse.ok(dramaForgeService.getShot(projectId, episodeId, shotId));
    }

    @PostMapping("/episodes/{episodeId}/shots")
    public ApiResponse<ShotResponse> createShot(
            @PathVariable UUID projectId,
            @PathVariable UUID episodeId,
            @Valid @RequestBody CreateShotRequest request) {
        return ApiResponse.ok(dramaForgeService.createShot(projectId, episodeId, request));
    }

    @PatchMapping("/episodes/{episodeId}/shots/{shotId}")
    public ApiResponse<ShotResponse> updateShot(
            @PathVariable UUID projectId,
            @PathVariable UUID episodeId,
            @PathVariable UUID shotId,
            @Valid @RequestBody UpdateShotRequest request) {
        return ApiResponse.ok(dramaForgeService.updateShot(projectId, episodeId, shotId, request));
    }

    @PostMapping("/episodes/{episodeId}/structure-script")
    public ApiResponse<EpisodeResponse> structureEpisodeScript(
            @PathVariable UUID projectId,
            @PathVariable UUID episodeId,
            @RequestHeader(value = ImageController.API_KEY_HEADER, required = false) String apiKey) {
        return ApiResponse.ok(dramaForgeService.structureEpisodeScriptFromBody(projectId, episodeId, apiKey));
    }

    @PostMapping("/episodes/{episodeId}/structure-shots")
    public ApiResponse<EpisodeResponse> structureEpisodeShots(
            @PathVariable UUID projectId,
            @PathVariable UUID episodeId,
            @RequestHeader(value = ImageController.API_KEY_HEADER, required = false) String apiKey) {
        return ApiResponse.ok(dramaForgeService.structureEpisodeShotsFromScript(projectId, episodeId, apiKey));
    }

    @PostMapping("/episodes/{episodeId}/shots/parse-script")
    public ApiResponse<List<ShotResponse>> parseShotsFromScript(
            @PathVariable UUID projectId,
            @PathVariable UUID episodeId,
            @RequestHeader(value = ImageController.API_KEY_HEADER, required = false) String apiKey) {
        return ApiResponse.ok(dramaForgeService.parseShotsFromScript(projectId, episodeId, apiKey));
    }

    @PostMapping("/episodes/{episodeId}/generate-storyboards")
    public ApiResponse<JobResponse> generateStoryboards(
            @PathVariable UUID projectId,
            @PathVariable UUID episodeId,
            @RequestHeader(value = ImageController.API_KEY_HEADER, required = false) String apiKey) {
        return ApiResponse.ok(enqueueService.enqueue(projectId, DramaForgeJobType.STORYBOARD, episodeId, apiKey));
    }

    @PostMapping("/episodes/{episodeId}/shots/{shotId}/generate-storyboard")
    public ApiResponse<JobResponse> regenerateShotStoryboard(
            @PathVariable UUID projectId,
            @PathVariable UUID episodeId,
            @PathVariable UUID shotId,
            @RequestHeader(value = ImageController.API_KEY_HEADER, required = false) String apiKey) {
        return ApiResponse.ok(enqueueService.enqueueShotStoryboard(projectId, episodeId, shotId, apiKey));
    }

    @GetMapping("/multicam-templates")
    public ApiResponse<MultiCamTemplatesResponse> listMultiCamTemplates(@PathVariable UUID projectId) {
        return ApiResponse.ok(dramaForgeService.listMultiCamTemplates());
    }

    @GetMapping("/episodes/{episodeId}/compose-readiness")
    public ApiResponse<ComposeReadinessResponse> getEpisodeComposeReadiness(
            @PathVariable UUID projectId,
            @PathVariable UUID episodeId) {
        return ApiResponse.ok(dramaForgeService.getEpisodeComposeReadiness(projectId, episodeId));
    }

    @PostMapping("/episodes/{episodeId}/expand-multicam")
    public ApiResponse<BatchExpandMultiCamResponse> expandEpisodeMultiCamera(
            @PathVariable UUID projectId,
            @PathVariable UUID episodeId,
            @Valid @RequestBody ExpandEpisodeMultiCamRequest request) {
        return ApiResponse.ok(dramaForgeService.expandEpisodeMultiCamera(
                projectId,
                episodeId,
                request.template(),
                request.modelMultiShot(),
                request.sceneRef(),
                request.firstPerSceneOnly(),
                request.removeSource()));
    }

    @PostMapping("/episodes/{episodeId}/shots/{shotId}/expand-multicam")
    public ApiResponse<List<ShotResponse>> expandShotMultiCamera(
            @PathVariable UUID projectId,
            @PathVariable UUID episodeId,
            @PathVariable UUID shotId,
            @Valid @RequestBody ExpandMultiCamRequest request) {
        return ApiResponse.ok(dramaForgeService.expandShotMultiCamera(
                projectId,
                episodeId,
                shotId,
                request.template(),
                request.modelMultiShot(),
                request.removeSource()));
    }

    @PostMapping("/episodes/{episodeId}/shots/{shotId}/generate-dialogue-audio")
    public ApiResponse<ShotResponse> generateShotDialogueAudio(
            @PathVariable UUID projectId,
            @PathVariable UUID episodeId,
            @PathVariable UUID shotId,
            @RequestHeader(value = VideoController.API_KEY_HEADER, required = false) String apiKey) {
        return ApiResponse.ok(dramaForgeService.generateShotDialogueAudio(
                projectId, episodeId, shotId, apiKey));
    }

    @PostMapping("/episodes/{episodeId}/generate-dialogue-audio")
    public ApiResponse<BatchDialogueAudioResponse> generateEpisodeDialogueAudio(
            @PathVariable UUID projectId,
            @PathVariable UUID episodeId,
            @RequestHeader(value = VideoController.API_KEY_HEADER, required = false) String apiKey) {
        return ApiResponse.ok(dramaForgeService.generateEpisodeDialogueAudio(
                projectId, episodeId, apiKey));
    }

    @PostMapping("/episodes/{episodeId}/generate-videos")
    public ApiResponse<JobResponse> generateVideos(
            @PathVariable UUID projectId,
            @PathVariable UUID episodeId,
            @RequestHeader(value = VideoController.API_KEY_HEADER, required = false) String apiKey) {
        return ApiResponse.ok(enqueueService.enqueue(projectId, DramaForgeJobType.VIDEO, episodeId, apiKey));
    }

    @PostMapping("/episodes/{episodeId}/shots/{shotId}/generate-video")
    public ApiResponse<JobResponse> generateShotVideo(
            @PathVariable UUID projectId,
            @PathVariable UUID episodeId,
            @PathVariable UUID shotId,
            @RequestHeader(value = VideoController.API_KEY_HEADER, required = false) String apiKey) {
        return ApiResponse.ok(enqueueService.enqueueShotVideo(projectId, episodeId, shotId, apiKey));
    }

    /** 强化 Seedance 原声人声（不叠 TTS；字幕由生成时 Seedance 自绘） */
    @PostMapping("/episodes/{episodeId}/shots/{shotId}/remaster-dialogue-audio")
    public ApiResponse<ShotResponse> remasterShotDialogueAudio(
            @PathVariable UUID projectId,
            @PathVariable UUID episodeId,
            @PathVariable UUID shotId) {
        return ApiResponse.ok(dramaForgeService.remasterShotDialogueAudio(projectId, episodeId, shotId));
    }

    @PostMapping("/episodes/{episodeId}/shots/{shotId}/promote-assets")
    public ApiResponse<List<AssetResponse>> promoteShotAssets(
            @PathVariable UUID projectId,
            @PathVariable UUID episodeId,
            @PathVariable UUID shotId) {
        return ApiResponse.ok(dramaForgeService.promoteShotAssets(projectId, episodeId, shotId));
    }

    @PostMapping("/episodes/{episodeId}/shots/{shotId}/promote-previous-assets")
    public ApiResponse<List<AssetResponse>> promotePreviousShotAssets(
            @PathVariable UUID projectId,
            @PathVariable UUID episodeId,
            @PathVariable UUID shotId) {
        return ApiResponse.ok(dramaForgeService.promotePreviousShotAssets(projectId, episodeId, shotId));
    }

    @GetMapping("/episodes/{episodeId}/shots/{shotId}/versions")
    public ApiResponse<List<ShotVersionResponse>> listShotVersions(
            @PathVariable UUID projectId,
            @PathVariable UUID episodeId,
            @PathVariable UUID shotId) {
        return ApiResponse.ok(dramaForgeService.listShotVersions(projectId, episodeId, shotId));
    }

    @PostMapping("/episodes/{episodeId}/shots/{shotId}/versions/{versionId}/activate")
    public ApiResponse<ShotVersionResponse> activateShotVersion(
            @PathVariable UUID projectId,
            @PathVariable UUID episodeId,
            @PathVariable UUID shotId,
            @PathVariable UUID versionId) {
        return ApiResponse.ok(dramaForgeService.activateShotVersion(projectId, episodeId, shotId, versionId));
    }
}
