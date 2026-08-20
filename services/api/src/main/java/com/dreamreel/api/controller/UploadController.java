package com.dreamreel.api.controller;

import com.dreamreel.api.common.ApiResponse;
import com.dreamreel.api.dto.UploadResponse;
import com.dreamreel.api.exception.ResourceNotFoundException;
import com.dreamreel.api.service.UploadStorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/uploads")
public class UploadController {

    private final UploadStorageService uploadStorageService;

    public UploadController(UploadStorageService uploadStorageService) {
        this.uploadStorageService = uploadStorageService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UploadResponse> upload(@RequestParam("file") MultipartFile file) throws Exception {
        var result = uploadStorageService.store(file);
        return ApiResponse.ok(new UploadResponse(
                result.id(),
                result.url(),
                result.contentType(),
                result.originalFilename()
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable UUID id) {
        var ossUrl = uploadStorageService.findOssPublicUrl(id);
        if (ossUrl.isPresent()) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(ossUrl.get()))
                    .build();
        }

        var stored = uploadStorageService.load(id)
                .orElseThrow(() -> new ResourceNotFoundException("上传文件不存在: " + id));

        return ResponseEntity.ok()
                .contentType(stored.contentType())
                .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic())
                .body(stored.resource());
    }
}
