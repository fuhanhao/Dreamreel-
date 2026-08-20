package com.dreamreel.api.service;

import com.dreamreel.api.client.ArkSeedreamClient;
import com.dreamreel.api.client.TokenFreeClient;
import com.dreamreel.api.config.TokenFreeApiKeyResolver;
import com.dreamreel.api.config.TokenFreeProperties;
import com.dreamreel.api.domain.GenerationJob;
import com.dreamreel.api.domain.GenerationMediaType;
import com.dreamreel.api.domain.GenerationStatus;
import com.dreamreel.api.dto.CreateImageGenerationRequest;
import com.dreamreel.api.dto.ImageGenerationResponse;
import com.dreamreel.api.dto.ImageModelResponse;
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
public class ImageGenerationService {

    private final TokenFreeClient tokenFreeClient;
    private final ArkSeedreamClient arkSeedreamClient;
    private final TokenFreeProperties properties;
    private final TokenFreeApiKeyResolver apiKeyResolver;
    private final GenerationJobRepository generationJobRepository;
    private final CurrentUserService currentUserService;
    private final MediaStorageService mediaStorageService;
    private final UploadStorageService uploadStorageService;
    private final ProjectApiKeyResolver projectApiKeyResolver;

    public ImageGenerationService(
            TokenFreeClient tokenFreeClient,
            ArkSeedreamClient arkSeedreamClient,
            TokenFreeProperties properties,
            TokenFreeApiKeyResolver apiKeyResolver,
            GenerationJobRepository generationJobRepository,
            CurrentUserService currentUserService,
            MediaStorageService mediaStorageService,
            UploadStorageService uploadStorageService,
            ProjectApiKeyResolver projectApiKeyResolver) {
        this.tokenFreeClient = tokenFreeClient;
        this.arkSeedreamClient = arkSeedreamClient;
        this.properties = properties;
        this.apiKeyResolver = apiKeyResolver;
        this.generationJobRepository = generationJobRepository;
        this.currentUserService = currentUserService;
        this.mediaStorageService = mediaStorageService;
        this.uploadStorageService = uploadStorageService;
        this.projectApiKeyResolver = projectApiKeyResolver;
    }

    @Transactional(readOnly = true)
    public List<ImageModelResponse> listModels(String headerApiKey) {
        var user = currentUserService.requireUserEntity();
        var apiKey = requireApiKey(headerApiKey, user.getTokenfreeApiKey());
        return tokenFreeClient.listImageModels(apiKey).stream()
                .map(model -> new ImageModelResponse(model.id(), model.provider()))
                .toList();
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ImageGenerationResponse create(CreateImageGenerationRequest request, String headerApiKey) {
        var user = currentUserService.requireUserEntity();
        return createInternal(user.getId(), request, requireApiKey(headerApiKey, user.getTokenfreeApiKey()));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ImageGenerationResponse createForProject(UUID projectId, CreateImageGenerationRequest request, String headerApiKey) {
        var userId = projectApiKeyResolver.resolveOwnerId(projectId);
        var apiKey = projectApiKeyResolver.resolve(projectId, headerApiKey);
        return createInternal(userId, request, apiKey);
    }

    /**
     * 分镜专用：绕过 TokenFree，直接调用火山方舟 Seedream。
     * API Key 由 ArkApiKeyContext / 用户配置 / ARK_API_KEY 解析。
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ImageGenerationResponse createSeedreamForProject(
            UUID projectId,
            CreateImageGenerationRequest request) {
        var userId = projectApiKeyResolver.resolveOwnerId(projectId);
        var providerUrls = resolveProviderUrls(request);
        var primaryReference = providerUrls.isEmpty() ? null : providerUrls.getFirst();
        var result = arkSeedreamClient.createImage(new ArkSeedreamClient.CreateImagePayload(
                request.model(),
                request.prompt(),
                request.ratio(),
                primaryReference,
                providerUrls));

        var job = new GenerationJob();
        job.setUserId(userId);
        job.setProjectId(request.projectId());
        job.setNodeId(request.nodeId());
        job.setMediaType(GenerationMediaType.IMAGE);
        job.setProviderTaskId("ark-sync-" + UUID.randomUUID());
        job.setModel(request.model());
        job.setPrompt(request.prompt());
        job.setGenerationMode(request.mode() != null ? request.mode() : "text-to-image");
        job.setRatio(request.ratio());
        job.setStrength(request.strength());
        job.setStatus(result.outputUrl() != null ? GenerationStatus.COMPLETED : GenerationStatus.FAILED);
        job.setProgress(result.outputUrl() != null ? 100 : null);
        job.setErrorMessage(result.errorMessage());
        if (request.imageUrl() != null && !request.imageUrl().isBlank()) {
            job.setReferenceImageUrl(request.imageUrl().trim());
        }
        job = generationJobRepository.save(job);

        if (result.outputUrl() != null) {
            mediaStorageService.persistRemoteOutput(job, result.outputUrl());
            mediaStorageService.ensureStoredOutput(job);
            job = generationJobRepository.save(job);
        }
        return ImageGenerationResponse.from(job);
    }

    private ImageGenerationResponse createInternal(UUID userId, CreateImageGenerationRequest request, String apiKey) {

        var model = request.model() != null && !request.model().isBlank()
                ? request.model()
                : properties.defaultImageModel();
        var size = MediaSizeHelper.toImageSize(request.ratio(), request.quality());

        var providerUrls = new java.util.ArrayList<String>();
        if (request.imageUrls() != null) {
            for (var url : request.imageUrls()) {
                if (url == null || url.isBlank()) {
                    continue;
                }
                var resolved = uploadStorageService.resolveForProvider(url.trim());
                if (resolved != null && !resolved.isBlank() && !providerUrls.contains(resolved)) {
                    providerUrls.add(resolved);
                }
            }
        }
        if (request.imageUrl() != null && !request.imageUrl().isBlank()) {
            var resolved = uploadStorageService.resolveForProvider(request.imageUrl().trim());
            if (resolved != null && !resolved.isBlank() && !providerUrls.contains(resolved)) {
                providerUrls.add(0, resolved);
            }
        }
        var primaryReference = providerUrls.isEmpty() ? null : providerUrls.getFirst();

        var result = tokenFreeClient.createImage(apiKey, new TokenFreeClient.CreateImagePayload(
                model,
                request.prompt(),
                size,
                request.ratio(),
                request.quality(),
                primaryReference,
                request.strength(),
                providerUrls.isEmpty() ? null : providerUrls
        ));

        var job = new GenerationJob();
        job.setUserId(userId);
        job.setProjectId(request.projectId());
        job.setNodeId(request.nodeId());
        job.setMediaType(GenerationMediaType.IMAGE);
        job.setProviderTaskId("sync-" + UUID.randomUUID());
        job.setModel(model);
        job.setPrompt(request.prompt());
        job.setGenerationMode(request.mode() != null ? request.mode() : "text-to-image");
        job.setRatio(request.ratio());
        job.setStrength(request.strength());
        if (request.imageUrl() != null && !request.imageUrl().isBlank()) {
            job.setReferenceImageUrl(request.imageUrl().trim());
        }
        var originalUrls = new java.util.ArrayList<String>();
        if (request.imageUrls() != null) {
            for (var url : request.imageUrls()) {
                if (url != null && !url.isBlank() && !originalUrls.contains(url.trim())) {
                    originalUrls.add(url.trim());
                }
            }
        }
        if (request.imageUrl() != null && !request.imageUrl().isBlank()
                && !originalUrls.contains(request.imageUrl().trim())) {
            originalUrls.add(0, request.imageUrl().trim());
        }
        if (!originalUrls.isEmpty()) {
            try {
                job.setReferenceImageUrls(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(originalUrls));
            } catch (Exception ignored) {
                // 展示字段失败不影响生成
            }
            if (job.getReferenceImageUrl() == null || job.getReferenceImageUrl().isBlank()) {
                job.setReferenceImageUrl(originalUrls.getFirst());
            }
        }
        job.setStatus(mapStatus(result.status()));
        job.setProgress(result.status().equals("completed") ? 100 : null);
        job.setErrorMessage(result.errorMessage());

        job = generationJobRepository.save(job);

        if (result.outputUrl() != null && job.getStatus() == GenerationStatus.COMPLETED) {
            mediaStorageService.persistRemoteOutput(job, result.outputUrl());
            mediaStorageService.ensureStoredOutput(job);
            job = generationJobRepository.save(job);
        } else if (result.outputUrl() != null) {
            job.setOutputUrl(result.outputUrl());
            job = generationJobRepository.save(job);
        }

        return ImageGenerationResponse.from(job);
    }

    private java.util.ArrayList<String> resolveProviderUrls(CreateImageGenerationRequest request) {
        var providerUrls = new java.util.ArrayList<String>();
        if (request.imageUrls() != null) {
            for (var url : request.imageUrls()) {
                addProviderUrl(providerUrls, url);
            }
        }
        if (request.imageUrl() != null && !request.imageUrl().isBlank()) {
            var resolved = uploadStorageService.resolveForProvider(request.imageUrl().trim());
            if (resolved != null && !resolved.isBlank()) {
                providerUrls.remove(resolved);
                providerUrls.addFirst(resolved);
            }
        }
        return providerUrls;
    }

    private void addProviderUrl(java.util.ArrayList<String> providerUrls, String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        var resolved = uploadStorageService.resolveForProvider(url.trim());
        if (resolved != null && !resolved.isBlank() && !providerUrls.contains(resolved)) {
            providerUrls.add(resolved);
        }
    }

    public ImageGenerationResponse get(UUID id) {
        var job = findOwnedJob(id);
        mediaStorageService.ensureStoredOutput(job);
        return ImageGenerationResponse.from(generationJobRepository.save(job));
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

    private String requireApiKey(String headerApiKey, String userApiKey) {
        var apiKey = apiKeyResolver.resolve(headerApiKey, userApiKey);
        if (apiKey == null) {
            throw new IllegalStateException("请配置 TokenFree API Key（个人设置或环境变量 TOKENFREE_API_KEY）");
        }
        return apiKey;
    }

    private GenerationStatus mapStatus(String providerStatus) {
        if (providerStatus == null) {
            return GenerationStatus.QUEUED;
        }
        return switch (providerStatus.toLowerCase(Locale.ROOT)) {
            case "completed", "succeeded", "success" -> GenerationStatus.COMPLETED;
            case "failed", "error", "cancelled" -> GenerationStatus.FAILED;
            default -> GenerationStatus.QUEUED;
        };
    }
}
