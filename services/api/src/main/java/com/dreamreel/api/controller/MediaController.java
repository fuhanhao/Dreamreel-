package com.dreamreel.api.controller;

import com.dreamreel.api.domain.GenerationMediaType;
import com.dreamreel.api.exception.ResourceNotFoundException;
import com.dreamreel.api.service.MediaStorageService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/media")
public class MediaController {

    private final MediaStorageService mediaStorageService;

    public MediaController(MediaStorageService mediaStorageService) {
        this.mediaStorageService = mediaStorageService;
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<?> get(@PathVariable UUID jobId) {
        for (var mediaType : new GenerationMediaType[] {GenerationMediaType.IMAGE, GenerationMediaType.VIDEO}) {
            var ossUrl = mediaStorageService.findOssPublicUrl(jobId, mediaType);
            if (ossUrl.isPresent()) {
                return ResponseEntity.status(HttpStatus.FOUND)
                        .location(URI.create(ossUrl.get()))
                        .build();
            }
        }

        var stored = mediaStorageService.loadStoredMedia(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("媒体文件不存在: " + jobId));

        return ResponseEntity.ok()
                .contentType(stored.contentType())
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                .body(stored.resource());
    }
}
