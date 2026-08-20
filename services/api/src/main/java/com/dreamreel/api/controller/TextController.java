package com.dreamreel.api.controller;

import com.dreamreel.api.common.ApiResponse;
import com.dreamreel.api.dto.CreateTextGenerationRequest;
import com.dreamreel.api.dto.TextGenerationResponse;
import com.dreamreel.api.dto.TextModelResponse;
import com.dreamreel.api.service.TextGenerationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/text")
public class TextController {

    public static final String API_KEY_HEADER = "X-Tokenfree-Api-Key";

    private final TextGenerationService textGenerationService;

    public TextController(TextGenerationService textGenerationService) {
        this.textGenerationService = textGenerationService;
    }

    @GetMapping("/models")
    public ApiResponse<List<TextModelResponse>> listModels(
            @RequestHeader(value = API_KEY_HEADER, required = false) String apiKey) {
        return ApiResponse.ok(textGenerationService.listModels(apiKey));
    }

    @PostMapping("/generations")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TextGenerationResponse> create(
            @RequestHeader(value = API_KEY_HEADER, required = false) String apiKey,
            @Valid @RequestBody CreateTextGenerationRequest request) {
        return ApiResponse.ok(textGenerationService.create(request, apiKey));
    }

    @GetMapping("/generations/{id}")
    public ApiResponse<TextGenerationResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(textGenerationService.get(id));
    }
}
