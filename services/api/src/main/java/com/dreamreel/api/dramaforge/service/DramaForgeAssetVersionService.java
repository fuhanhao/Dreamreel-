package com.dreamreel.api.dramaforge.service;

import com.dreamreel.api.dramaforge.domain.DramaForgeAsset;
import com.dreamreel.api.dramaforge.domain.DramaForgeAssetVersion;
import com.dreamreel.api.dramaforge.dto.DramaForgeDtos.AssetVersionResponse;
import com.dreamreel.api.dramaforge.repository.DramaForgeAssetRepository;
import com.dreamreel.api.dramaforge.repository.DramaForgeAssetVersionRepository;
import com.dreamreel.api.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class DramaForgeAssetVersionService {

    private final DramaForgeAssetVersionRepository versionRepository;
    private final DramaForgeAssetRepository assetRepository;

    public DramaForgeAssetVersionService(
            DramaForgeAssetVersionRepository versionRepository,
            DramaForgeAssetRepository assetRepository) {
        this.versionRepository = versionRepository;
        this.assetRepository = assetRepository;
    }

    public void archiveCurrent(DramaForgeAsset asset) {
        if (asset.getReferenceImageUrl() == null || asset.getReferenceImageUrl().isBlank()) {
            return;
        }
        var nextNo = versionRepository.findTopByAssetIdOrderByVersionNoDesc(asset.getId())
                .map(v -> v.getVersionNo() + 1)
                .orElse(1);

        var version = new DramaForgeAssetVersion();
        version.setAssetId(asset.getId());
        version.setVersionNo(nextNo);
        version.setReferenceImageUrl(asset.getReferenceImageUrl());
        version.setDesignPrompt(asset.getDesignPrompt());
        version.setActive(false);
        versionRepository.save(version);
    }

    public void deleteByAssetId(UUID assetId) {
        versionRepository.deleteByAssetId(assetId);
    }

    @Transactional(readOnly = true)
    public List<AssetVersionResponse> listVersions(UUID assetId) {
        return versionRepository.findByAssetIdOrderByVersionNoDesc(assetId).stream()
                .map(this::toResponse)
                .toList();
    }

    public AssetVersionResponse activateVersion(UUID assetId, UUID versionId) {
        var asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new ResourceNotFoundException("资产不存在: " + assetId));
        var version = versionRepository.findById(versionId)
                .filter(v -> v.getAssetId().equals(assetId))
                .orElseThrow(() -> new ResourceNotFoundException("版本不存在: " + versionId));

        versionRepository.findByAssetIdAndActiveTrue(assetId).ifPresent(active -> {
            active.setActive(false);
            versionRepository.save(active);
        });

        version.setActive(true);
        versionRepository.save(version);

        asset.setReferenceImageUrl(version.getReferenceImageUrl());
        if (version.getDesignPrompt() != null && !version.getDesignPrompt().isBlank()) {
            asset.setDesignPrompt(version.getDesignPrompt());
        }
        assetRepository.save(asset);
        return toResponse(version);
    }

    public AssetVersionResponse saveCandidate(DramaForgeAsset asset, String imageUrl, String designPrompt) {
        var nextNo = versionRepository.findTopByAssetIdOrderByVersionNoDesc(asset.getId())
                .map(v -> v.getVersionNo() + 1)
                .orElse(1);
        var version = new DramaForgeAssetVersion();
        version.setAssetId(asset.getId());
        version.setVersionNo(nextNo);
        version.setReferenceImageUrl(imageUrl);
        version.setDesignPrompt(designPrompt);
        version.setActive(false);
        version = versionRepository.save(version);
        return toResponse(version);
    }

    private AssetVersionResponse toResponse(DramaForgeAssetVersion version) {
        return new AssetVersionResponse(
                version.getId(),
                version.getAssetId(),
                version.getVersionNo(),
                version.getReferenceImageUrl(),
                version.getDesignPrompt(),
                version.isActive(),
                version.getCreatedAt()
        );
    }
}
