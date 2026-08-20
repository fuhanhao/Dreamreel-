package com.dreamreel.api.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "generation_jobs")
@Getter
@Setter
@NoArgsConstructor
public class GenerationJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID projectId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(length = 100)
    private String nodeId;

    @Column(nullable = false, length = 128)
    private String providerTaskId;

    @Column(nullable = false, length = 128)
    private String model;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private GenerationMediaType mediaType = GenerationMediaType.VIDEO;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String prompt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private GenerationStatus status = GenerationStatus.QUEUED;

    private Integer progress;

    @Column(length = 2000)
    private String outputUrl;

    @Column(length = 2000)
    private String providerOutputUrl;

    @Column(columnDefinition = "TEXT")
    private String outputText;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "generation_mode", length = 32)
    private String generationMode;

    @Column(name = "reference_image_url", length = 2000)
    private String referenceImageUrl;

    /** JSON 数组，存储多张参考图原始 URL（UI 展示用） */
    @Column(name = "reference_image_urls", columnDefinition = "TEXT")
    private String referenceImageUrls;

    @Column(name = "reference_video_url", length = 2000)
    private String referenceVideoUrl;

    @Column(length = 16)
    private String ratio;

    private Double strength;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
