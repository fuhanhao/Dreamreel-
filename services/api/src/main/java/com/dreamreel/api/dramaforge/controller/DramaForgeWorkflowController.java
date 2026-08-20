package com.dreamreel.api.dramaforge.controller;

import com.dreamreel.api.dramaforge.domain.DramaForgeJobType;
import com.dreamreel.api.dramaforge.dto.DramaForgeDtos.*;
import com.dreamreel.api.dramaforge.repository.DramaForgeCompositionRepository;
import com.dreamreel.api.dramaforge.service.*;
import com.dreamreel.api.common.ApiResponse;
import com.dreamreel.api.controller.ImageController;
import com.dreamreel.api.controller.VideoController;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dramaforge/projects/{projectId}")
public class DramaForgeWorkflowController {

    private final DramaForgeWorkflowService workflowService;
    private final DramaForgeEnqueueService enqueueService;
    private final DramaForgeJobService jobService;
    private final DramaForgeEventHub eventHub;
    private final DramaForgeCompositionRepository compositionRepository;
    private final DramaForgeService dramaForgeService;
    private final DramaForgeWorkflowLockService lockService;
    private final DramaForgeImportService importService;

    public DramaForgeWorkflowController(
            DramaForgeWorkflowService workflowService,
            DramaForgeEnqueueService enqueueService,
            DramaForgeJobService jobService,
            DramaForgeEventHub eventHub,
            DramaForgeCompositionRepository compositionRepository,
            DramaForgeService dramaForgeService,
            DramaForgeWorkflowLockService lockService,
            DramaForgeImportService importService) {
        this.workflowService = workflowService;
        this.enqueueService = enqueueService;
        this.jobService = jobService;
        this.eventHub = eventHub;
        this.compositionRepository = compositionRepository;
        this.dramaForgeService = dramaForgeService;
        this.lockService = lockService;
        this.importService = importService;
    }

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable UUID projectId) {
        dramaForgeService.ensureConfig(projectId);
        return eventHub.subscribe(projectId);
    }

    @PostMapping("/workflow/run")
    public ApiResponse<PipelineOverviewResponse> runWorkflow(
            @PathVariable UUID projectId,
            @RequestHeader(value = ImageController.API_KEY_HEADER, required = false) String apiKey) {
        return ApiResponse.ok(workflowService.runPipeline(projectId, apiKey));
    }

    @PostMapping("/import/extract-assets")
    public ApiResponse<JobResponse> extractAssets(
            @PathVariable UUID projectId,
            @RequestHeader(value = ImageController.API_KEY_HEADER, required = false) String apiKey) {
        return ApiResponse.ok(enqueueService.enqueue(projectId, DramaForgeJobType.EXTRACT_ASSETS, null, apiKey));
    }

    @PostMapping("/import/generate-script")
    public ApiResponse<JobResponse> generateScript(
            @PathVariable UUID projectId,
            @RequestHeader(value = ImageController.API_KEY_HEADER, required = false) String apiKey) {
        return ApiResponse.ok(enqueueService.enqueue(projectId, DramaForgeJobType.GENERATE_SCRIPT, null, apiKey));
    }

    @PostMapping("/import/plan-episodes")
    public ApiResponse<PlanEpisodesResponse> planEpisodes(
            @PathVariable UUID projectId,
            @RequestHeader(value = ImageController.API_KEY_HEADER, required = false) String apiKey) {
        return ApiResponse.ok(importService.planEpisodesAndApply(projectId, apiKey));
    }

    @PostMapping("/episodes/{episodeId}/workflow/lock-script")
    public ApiResponse<EpisodeResponse> lockScript(
            @PathVariable UUID projectId,
            @PathVariable UUID episodeId) {
        return ApiResponse.ok(lockService.lockScript(projectId, episodeId));
    }

    @PostMapping("/workflow/lock-assets")
    public ApiResponse<PipelineOverviewResponse> lockAssets(@PathVariable UUID projectId) {
        return ApiResponse.ok(lockService.lockAssets(projectId));
    }

    @PostMapping("/episodes/{episodeId}/workflow/lock-storyboard")
    public ApiResponse<EpisodeResponse> lockStoryboard(
            @PathVariable UUID projectId,
            @PathVariable UUID episodeId) {
        return ApiResponse.ok(lockService.lockStoryboard(projectId, episodeId));
    }

    @GetMapping("/jobs")
    public ApiResponse<List<JobResponse>> listJobs(
            @PathVariable UUID projectId,
            @RequestParam(required = false) Integer limit) {
        return ApiResponse.ok(jobService.listJobs(projectId, limit));
    }

    @DeleteMapping("/jobs/{jobId}")
    public ApiResponse<JobResponse> cancelJob(
            @PathVariable UUID projectId,
            @PathVariable UUID jobId) {
        return ApiResponse.ok(jobService.cancelJob(projectId, jobId));
    }

    @PostMapping("/jobs/{jobId}/retry")
    public ApiResponse<JobResponse> retryJob(
            @PathVariable UUID projectId,
            @PathVariable UUID jobId) {
        return ApiResponse.ok(jobService.retryJob(projectId, jobId));
    }

    @DeleteMapping("/jobs/finished")
    public ApiResponse<Integer> clearFinishedJobs(@PathVariable UUID projectId) {
        return ApiResponse.ok(jobService.clearFinishedJobs(projectId));
    }

    @PostMapping("/episodes/{episodeId}/compose")
    public ApiResponse<JobResponse> composeEpisode(
            @PathVariable UUID projectId,
            @PathVariable UUID episodeId) {
        return ApiResponse.ok(enqueueService.enqueue(projectId, DramaForgeJobType.COMPOSE, episodeId, null));
    }

    @GetMapping("/compositions")
    public ApiResponse<List<CompositionResponse>> listCompositions(@PathVariable UUID projectId) {
        return ApiResponse.ok(compositionRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .map(CompositionResponse::from)
                .toList());
    }

    @PostMapping("/episodes/{episodeId}/sync-videos")
    public ApiResponse<JobResponse> syncVideos(
            @PathVariable UUID projectId,
            @PathVariable UUID episodeId,
            @RequestHeader(value = VideoController.API_KEY_HEADER, required = false) String apiKey) {
        return ApiResponse.ok(enqueueService.enqueue(projectId, DramaForgeJobType.SYNC_VIDEOS, episodeId, apiKey));
    }

    @PostMapping("/export/project")
    public ApiResponse<JobResponse> exportProject(@PathVariable UUID projectId) {
        return ApiResponse.ok(enqueueService.enqueue(projectId, DramaForgeJobType.EXPORT_PROJECT, null, null));
    }

    @PostMapping("/episodes/{episodeId}/export/jianying")
    public ApiResponse<JobResponse> exportJianying(
            @PathVariable UUID projectId,
            @PathVariable UUID episodeId) {
        return ApiResponse.ok(enqueueService.enqueue(projectId, DramaForgeJobType.EXPORT_JIANYING, episodeId, null));
    }
}
