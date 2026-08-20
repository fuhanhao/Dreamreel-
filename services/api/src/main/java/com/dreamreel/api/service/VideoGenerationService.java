package com.dreamreel.api.service;

import com.dreamreel.api.client.ArkSeedanceClient;
import com.dreamreel.api.config.ArkApiKeyContext;
import com.dreamreel.api.config.ArkApiKeyResolver;
import com.dreamreel.api.config.ArkProperties;
import com.dreamreel.api.domain.GenerationJob;
import com.dreamreel.api.domain.GenerationStatus;
import com.dreamreel.api.dto.CreateVideoGenerationRequest;
import com.dreamreel.api.dto.VideoGenerationResponse;
import com.dreamreel.api.dto.VideoModelResponse;
import com.dreamreel.api.exception.ResourceNotFoundException;
import com.dreamreel.api.repository.GenerationJobRepository;
import com.dreamreel.api.security.CurrentUserService;
import com.dreamreel.api.util.MediaSizeHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional
public class VideoGenerationService {

    private final ArkSeedanceClient arkSeedanceClient;
    private final ArkProperties arkProperties;
    private final ArkApiKeyResolver arkApiKeyResolver;
    private final GenerationJobRepository generationJobRepository;
    private final CurrentUserService currentUserService;
    private final MediaStorageService mediaStorageService;
    private final UploadStorageService uploadStorageService;
    private final ProjectApiKeyResolver projectApiKeyResolver;

    public VideoGenerationService(
            ArkSeedanceClient arkSeedanceClient,
            ArkProperties arkProperties,
            ArkApiKeyResolver arkApiKeyResolver,
            GenerationJobRepository generationJobRepository,
            CurrentUserService currentUserService,
            MediaStorageService mediaStorageService,
            UploadStorageService uploadStorageService,
            ProjectApiKeyResolver projectApiKeyResolver) {
        this.arkSeedanceClient = arkSeedanceClient;
        this.arkProperties = arkProperties;
        this.arkApiKeyResolver = arkApiKeyResolver;
        this.generationJobRepository = generationJobRepository;
        this.currentUserService = currentUserService;
        this.mediaStorageService = mediaStorageService;
        this.uploadStorageService = uploadStorageService;
        this.projectApiKeyResolver = projectApiKeyResolver;
    }

    @Transactional(readOnly = true)
    public List<VideoModelResponse> listModels(String headerApiKey) {
        var user = currentUserService.requireUserEntity();
        return withArkKey(headerApiKey, user.getArkApiKey(), () -> arkSeedanceClient.listModels().stream()
                .map(model -> new VideoModelResponse(model.id(), model.provider()))
                .toList());
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public VideoGenerationResponse create(CreateVideoGenerationRequest request, String headerApiKey) {
        var user = currentUserService.requireUserEntity();
        return withArkKey(headerApiKey, user.getArkApiKey(), () -> createInternal(user.getId(), request));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public VideoGenerationResponse createForProject(UUID projectId, CreateVideoGenerationRequest request, String headerApiKey) {
        var userId = projectApiKeyResolver.resolveOwnerId(projectId);
        var userArkKey = projectApiKeyResolver.resolveOwnerArkApiKey(projectId);
        return withArkKey(headerApiKey, userArkKey, () -> createInternal(userId, request));
    }

    private VideoGenerationResponse createInternal(UUID userId, CreateVideoGenerationRequest request) {
        requireArkConfigured();

        var model = request.model() != null && !request.model().isBlank()
                ? request.model()
                : (arkProperties.defaultModel() != null ? arkProperties.defaultModel() : "doubao-seedance-2-5-260628");
        var seconds = request.seconds() != null ? request.seconds() : 5;
        var resolution = MediaSizeHelper.normalizeResolution(request.quality());
        var mode = request.mode() != null ? request.mode() : "text-to-video";
        // 合并 imageUrl + imageUrls，避免有资产图时丢掉首/尾帧
        var imageUrls = new java.util.ArrayList<String>();
        if (request.imageUrls() != null) {
            for (var url : request.imageUrls()) {
                if (url == null || url.isBlank()) {
                    continue;
                }
                var resolved = uploadStorageService.resolveForProvider(url);
                if (resolved != null && !resolved.isBlank() && !imageUrls.contains(resolved)) {
                    imageUrls.add(resolved);
                }
            }
        }
        if (request.imageUrl() != null && !request.imageUrl().isBlank()) {
            var resolved = uploadStorageService.resolveForProvider(request.imageUrl().trim());
            if (resolved != null && !resolved.isBlank()) {
                imageUrls.remove(resolved);
                imageUrls.addFirst(resolved);
            }
        }
        var audioUrls = new java.util.ArrayList<String>();
        if (request.audioUrls() != null) {
            for (var url : request.audioUrls()) {
                if (url != null && !url.isBlank()) {
                    audioUrls.add(uploadStorageService.resolveForProvider(url));
                }
            }
        }
        var primaryImageUrl = imageUrls.isEmpty() ? null : imageUrls.getFirst();
        var videoUrl = request.videoUrl() != null && !request.videoUrl().isBlank()
                ? uploadStorageService.resolveForProvider(request.videoUrl())
                : null;

        var task = arkSeedanceClient.createVideoTask(new ArkSeedanceClient.CreateVideoPayload(
                model,
                request.prompt(),
                seconds,
                request.ratio(),
                mode,
                primaryImageUrl,
                videoUrl,
                imageUrls,
                audioUrls,
                resolution
        ));

        if (task.taskId() == null || task.taskId().isBlank()) {
            var message = task.errorMessage() != null && !task.errorMessage().isBlank()
                    ? task.errorMessage()
                    : "视频生成任务创建失败";
            if (message.contains("HTTP 401") || message.contains("HTTP 403") || message.toLowerCase(Locale.ROOT).contains("auth")) {
                message = "火山方舟视频 API 鉴权失败，请确认 Seedance / ARK API Key 有效且已开通服务";
            }
            throw new IllegalStateException(message);
        }

        var job = new GenerationJob();
        job.setUserId(userId);
        job.setProjectId(request.projectId());
        job.setNodeId(request.nodeId());
        job.setMediaType(com.dreamreel.api.domain.GenerationMediaType.VIDEO);
        job.setProviderTaskId(task.taskId());
        job.setModel(model);
        job.setPrompt(request.prompt());
        job.setGenerationMode(mode);
        job.setRatio(request.ratio());
        var originalImageUrls = new java.util.ArrayList<String>();
        if (request.imageUrls() != null) {
            for (var url : request.imageUrls()) {
                if (url != null && !url.isBlank() && !originalImageUrls.contains(url.trim())) {
                    originalImageUrls.add(url.trim());
                }
            }
        }
        if (request.imageUrl() != null && !request.imageUrl().isBlank()) {
            var single = request.imageUrl().trim();
            originalImageUrls.remove(single);
            originalImageUrls.addFirst(single);
        }
        if (!originalImageUrls.isEmpty()) {
            job.setReferenceImageUrl(originalImageUrls.getFirst());
            try {
                job.setReferenceImageUrls(new com.fasterxml.jackson.databind.ObjectMapper()
                        .writeValueAsString(originalImageUrls));
            } catch (Exception ex) {
                job.setReferenceImageUrls(null);
            }
        }
        if (request.videoUrl() != null && !request.videoUrl().isBlank()) {
            job.setReferenceVideoUrl(request.videoUrl().trim());
        }
        job.setStatus(mapStatus(task.status()));
        job.setProgress(task.progress());
        job.setErrorMessage(task.errorMessage());

        job = generationJobRepository.save(job);

        if (task.outputUrl() != null && job.getStatus() == GenerationStatus.COMPLETED) {
            mediaStorageService.persistRemoteOutput(job, task.outputUrl());
            job = generationJobRepository.save(job);
        } else if (task.outputUrl() != null) {
            job.setOutputUrl(task.outputUrl());
            job = generationJobRepository.save(job);
        }

        return VideoGenerationResponse.from(job);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public VideoGenerationResponse get(UUID id, String headerApiKey) {
        var user = currentUserService.requireUserEntity();
        var job = findOwnedJob(id);
        return withArkKey(headerApiKey, user.getArkApiKey(), () -> getInternal(job));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public VideoGenerationResponse getForProject(UUID projectId, UUID id, String headerApiKey) {
        projectApiKeyResolver.resolveOwnerId(projectId);
        var userArkKey = projectApiKeyResolver.resolveOwnerArkApiKey(projectId);
        var job = generationJobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("生成任务不存在: " + id));
        return withArkKey(headerApiKey, userArkKey, () -> getInternal(job));
    }

    private VideoGenerationResponse getInternal(GenerationJob job) {
        requireArkConfigured();

        if (!isTerminal(job.getStatus())) {
            return refreshFromProvider(job);
        }

        ensureLocalOutput(job);
        if (job.getStatus() == GenerationStatus.COMPLETED
                && !mediaStorageService.hasLocalFile(job.getId(), job.getMediaType())
                && (job.getProviderOutputUrl() == null || job.getProviderOutputUrl().isBlank())
                && (job.getOutputUrl() == null || job.getOutputUrl().isBlank()
                || mediaStorageService.isLocalMediaUrl(job.getOutputUrl()))) {
            return refreshFromProvider(job);
        }

        return VideoGenerationResponse.from(generationJobRepository.save(job));
    }

    private void ensureLocalOutput(GenerationJob job) {
        mediaStorageService.ensureStoredOutput(job);
    }

    private VideoGenerationResponse refreshFromProvider(GenerationJob job) {
        var task = arkSeedanceClient.getVideoTask(job.getProviderTaskId());
        job.setStatus(mapStatus(task.status()));
        job.setProgress(task.progress());
        if (task.outputUrl() != null) {
            if (job.getStatus() == GenerationStatus.COMPLETED) {
                mediaStorageService.persistRemoteOutput(job, task.outputUrl());
            } else {
                job.setOutputUrl(task.outputUrl());
            }
        }
        if (task.errorMessage() != null && !task.errorMessage().isBlank()) {
            job.setErrorMessage(task.errorMessage());
        } else if (job.getStatus() == GenerationStatus.FAILED
                && (job.getErrorMessage() == null || job.getErrorMessage().isBlank())) {
            job.setErrorMessage("视频生成失败（上游未返回详细原因）");
        }
        return VideoGenerationResponse.from(generationJobRepository.save(job));
    }

    private GenerationJob findOwnedJob(UUID id) {
        var userId = currentUserService.requireUserId();
        var principal = currentUserService.requirePrincipal();
        if (principal.isAdmin()) {
            return generationJobRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("生成任务不存在: " + id));
        }
        return generationJobRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("生成任务不存在: " + id));
    }

    private void requireArkConfigured() {
        if (!arkApiKeyResolver.isConfigured(ArkApiKeyContext.get(), null)) {
            throw new IllegalStateException(
                    "请配置火山方舟 / Seedance API Key（前端设置，或环境变量 ARK_API_KEY）");
        }
    }

    private <T> T withArkKey(String headerApiKey, java.util.function.Supplier<T> action) {
        return withArkKey(headerApiKey, null, action);
    }

    private <T> T withArkKey(String headerApiKey, String userApiKey, java.util.function.Supplier<T> action) {
        var previous = ArkApiKeyContext.get();
        var resolved = arkApiKeyResolver.resolve(headerApiKey, userApiKey);
        try {
            if (resolved != null && !resolved.isBlank()) {
                ArkApiKeyContext.set(resolved);
            }
            return action.get();
        } finally {
            if (previous != null && !previous.isBlank()) {
                ArkApiKeyContext.set(previous);
            } else {
                ArkApiKeyContext.clear();
            }
        }
    }

    private boolean isTerminal(GenerationStatus status) {
        return status == GenerationStatus.COMPLETED || status == GenerationStatus.FAILED;
    }

    private GenerationStatus mapStatus(String providerStatus) {
        if (providerStatus == null) {
            return GenerationStatus.QUEUED;
        }
        return switch (providerStatus.toLowerCase(Locale.ROOT)) {
            case "completed", "succeeded", "success" -> GenerationStatus.COMPLETED;
            case "failed", "error", "cancelled", "expired" -> GenerationStatus.FAILED;
            case "in_progress", "processing", "running" -> GenerationStatus.IN_PROGRESS;
            default -> GenerationStatus.QUEUED;
        };
    }
}
