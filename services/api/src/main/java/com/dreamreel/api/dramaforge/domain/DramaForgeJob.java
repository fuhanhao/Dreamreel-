package com.dreamreel.api.dramaforge.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dramaforge_jobs")
@Getter
@Setter
@NoArgsConstructor
public class DramaForgeJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Convert(converter = DramaForgeJobTypeConverter.class)
    @Column(name = "job_type", nullable = false, length = 32)
    private DramaForgeJobType jobType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DramaForgeJobStatus status = DramaForgeJobStatus.QUEUED;

    @Column(name = "target_id")
    private UUID targetId;

    @Column(name = "episode_id")
    private UUID episodeId;

    @Column(length = 4000)
    private String payloadJson;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "progress_current", nullable = false)
    private int progressCurrent;

    @Column(name = "progress_total", nullable = false)
    private int progressTotal;

    @Column(name = "progress_message", length = 500)
    private String progressMessage;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
