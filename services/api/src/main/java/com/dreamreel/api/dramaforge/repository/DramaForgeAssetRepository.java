package com.dreamreel.api.dramaforge.repository;

import com.dreamreel.api.dramaforge.domain.DramaForgeAsset;
import com.dreamreel.api.dramaforge.domain.DramaForgeAssetType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DramaForgeAssetRepository extends JpaRepository<DramaForgeAsset, UUID> {
    List<DramaForgeAsset> findByProjectIdOrderBySortOrderAscNameAsc(UUID projectId);

    List<DramaForgeAsset> findByProjectIdAndTypeOrderBySortOrderAscNameAsc(UUID projectId, DramaForgeAssetType type);

    long countByProjectIdAndType(UUID projectId, DramaForgeAssetType type);

    Optional<DramaForgeAsset> findByIdAndProjectId(UUID id, UUID projectId);
}
