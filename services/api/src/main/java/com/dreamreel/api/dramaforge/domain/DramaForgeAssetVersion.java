package com.dreamreel.api.dramaforge.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dramaforge_asset_versions")
@Getter
@Setter
@NoArgsConstructor
public class DramaForgeAssetVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Column(name = "reference_image_url", length = 2000)
    private String referenceImageUrl;

    @Column(name = "design_prompt", length = 4000)
    private String designPrompt;

    @Column(nullable = false)
    private boolean active;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
