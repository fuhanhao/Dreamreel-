package com.dreamreel.api.controller;

import com.dreamreel.api.common.ApiResponse;
import com.dreamreel.api.domain.GenerationMediaType;
import com.dreamreel.api.dto.GenerationJobResponse;
import com.dreamreel.api.dto.PageResponse;
import com.dreamreel.api.service.GenerationQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/generations")
public class GenerationController {

    private final GenerationQueryService generationQueryService;

    public GenerationController(GenerationQueryService generationQueryService) {
        this.generationQueryService = generationQueryService;
    }

    @GetMapping
    public ApiResponse<PageResponse<GenerationJobResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) GenerationMediaType mediaType) {
        return ApiResponse.ok(generationQueryService.listMyGenerations(page, size, mediaType));
    }

    @GetMapping("/{id}")
    public ApiResponse<GenerationJobResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(generationQueryService.getMyGeneration(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        generationQueryService.deleteMyGeneration(id);
    }
}
