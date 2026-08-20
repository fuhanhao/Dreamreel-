package com.dreamreel.api.service;

import com.dreamreel.api.domain.GenerationMediaType;
import com.dreamreel.api.dto.GenerationJobResponse;
import com.dreamreel.api.dto.PageResponse;
import com.dreamreel.api.exception.ResourceNotFoundException;
import com.dreamreel.api.repository.GenerationJobRepository;
import com.dreamreel.api.security.CurrentUserService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class GenerationQueryService {

    private final GenerationJobRepository generationJobRepository;
    private final CurrentUserService currentUserService;
    private final MediaStorageService mediaStorageService;
    private final GenerationReferenceImageEnricher referenceImageEnricher;

    public GenerationQueryService(
            GenerationJobRepository generationJobRepository,
            CurrentUserService currentUserService,
            MediaStorageService mediaStorageService,
            GenerationReferenceImageEnricher referenceImageEnricher) {
        this.generationJobRepository = generationJobRepository;
        this.currentUserService = currentUserService;
        this.mediaStorageService = mediaStorageService;
        this.referenceImageEnricher = referenceImageEnricher;
    }

    @Transactional(readOnly = true)
    public PageResponse<GenerationJobResponse> listMyGenerations(
            int page, int size, GenerationMediaType mediaType) {
        var userId = currentUserService.requireUserId();
        var pageable = PageRequest.of(page, size);
        var result = mediaType == null
                ? generationJobRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                : generationJobRepository.findByUserIdAndMediaTypeOrderByCreatedAtDesc(userId, mediaType, pageable);
        var items = result.getContent().stream()
                .map(job -> GenerationJobResponse.from(job, referenceImageEnricher.resolve(job)))
                .toList();
        return new PageResponse<>(
                items,
                result.getTotalElements(),
                page,
                size
        );
    }

    public GenerationJobResponse getMyGeneration(UUID id) {
        var userId = currentUserService.requireUserId();
        var principal = currentUserService.requirePrincipal();
        var job = principal.isAdmin()
                ? generationJobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("生成任务不存在: " + id))
                : generationJobRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("生成任务不存在: " + id));

        mediaStorageService.ensureStoredOutput(job);
        var urls = referenceImageEnricher.resolveAndMaybeBackfill(job);
        return GenerationJobResponse.from(generationJobRepository.save(job), urls);
    }

    public void deleteMyGeneration(UUID id) {
        var userId = currentUserService.requireUserId();
        var principal = currentUserService.requirePrincipal();
        var job = principal.isAdmin()
                ? generationJobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("生成任务不存在: " + id))
                : generationJobRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("生成任务不存在: " + id));

        mediaStorageService.deleteStoredOutput(job);
        generationJobRepository.delete(job);
    }
}
