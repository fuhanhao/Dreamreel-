package com.dreamreel.api.controller;

import com.dreamreel.api.common.ApiResponse;
import com.dreamreel.api.config.ArkApiKeyContext;
import com.dreamreel.api.dto.CreateVideoGenerationRequest;
import com.dreamreel.api.dto.VideoGenerationResponse;
import com.dreamreel.api.dto.VideoModelResponse;
import com.dreamreel.api.service.VideoGenerationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/video")
public class VideoController {

    /** 兼容旧调用；视频鉴权以 {@link ArkApiKeyContext#HEADER} 为准 */
    public static final String API_KEY_HEADER = "X-Tokenfree-Api-Key";
    public static final String ARK_API_KEY_HEADER = ArkApiKeyContext.HEADER;

    private final VideoGenerationService videoGenerationService;

    public VideoController(VideoGenerationService videoGenerationService) {
        this.videoGenerationService = videoGenerationService;
    }

    @GetMapping("/models")
    public ApiResponse<List<VideoModelResponse>> listModels(
            @RequestHeader(value = ARK_API_KEY_HEADER, required = false) String arkApiKey) {
        return ApiResponse.ok(videoGenerationService.listModels(arkApiKey));
    }

    @PostMapping("/generations")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<VideoGenerationResponse> create(
            @RequestHeader(value = ARK_API_KEY_HEADER, required = false) String arkApiKey,
            @Valid @RequestBody CreateVideoGenerationRequest request) {
        return ApiResponse.ok(videoGenerationService.create(request, arkApiKey));
    }

    @GetMapping("/generations/{id}")
    public ApiResponse<VideoGenerationResponse> get(
            @RequestHeader(value = ARK_API_KEY_HEADER, required = false) String arkApiKey,
            @PathVariable UUID id) {
        return ApiResponse.ok(videoGenerationService.get(id, arkApiKey));
    }

}
