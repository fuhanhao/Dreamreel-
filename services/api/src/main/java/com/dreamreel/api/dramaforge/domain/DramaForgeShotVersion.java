package com.dreamreel.api.dramaforge.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dramaforge_shot_versions")
@Getter
@Setter
@NoArgsConstructor
public class DramaForgeShotVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "shot_id", nullable = false)
    private UUID shotId;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Column(name = "storyboard_url", length = 2000)
    private String storyboardUrl;

    @Column(name = "video_job_id")
    private UUID videoJobId;

    @Column(name = "video_url", length = 2000)
    private String videoUrl;

    @Column(nullable = false)
    private boolean active;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
