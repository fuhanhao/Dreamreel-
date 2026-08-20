package com.dreamreel.api.dramaforge.repository;

import com.dreamreel.api.dramaforge.domain.DramaForgeAssetVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DramaForgeAssetVersionRepository extends JpaRepository<DramaForgeAssetVersion, UUID> {
    List<DramaForgeAssetVersion> findByAssetIdOrderByVersionNoDesc(UUID assetId);

    Optional<DramaForgeAssetVersion> findByAssetIdAndActiveTrue(UUID assetId);

    Optional<DramaForgeAssetVersion> findTopByAssetIdOrderByVersionNoDesc(UUID assetId);

    void deleteByAssetId(UUID assetId);
}
