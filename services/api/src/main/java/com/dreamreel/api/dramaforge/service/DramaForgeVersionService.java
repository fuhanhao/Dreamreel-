package com.dreamreel.api.dramaforge.service;

import com.dreamreel.api.dramaforge.domain.DramaForgeShot;
import com.dreamreel.api.dramaforge.domain.DramaForgeShotStatus;
import com.dreamreel.api.dramaforge.domain.DramaForgeShotVersion;
import com.dreamreel.api.dramaforge.dto.DramaForgeDtos.ShotVersionResponse;
import com.dreamreel.api.dramaforge.repository.DramaForgeShotRepository;
import com.dreamreel.api.dramaforge.repository.DramaForgeShotVersionRepository;
import com.dreamreel.api.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class DramaForgeVersionService {

    private final DramaForgeShotVersionRepository versionRepository;
    private final DramaForgeShotRepository shotRepository;
    private final DramaForgeStatusCalculator statusCalculator;

    public DramaForgeVersionService(
            DramaForgeShotVersionRepository versionRepository,
            DramaForgeShotRepository shotRepository,
            DramaForgeStatusCalculator statusCalculator) {
        this.versionRepository = versionRepository;
        this.shotRepository = shotRepository;
        this.statusCalculator = statusCalculator;
    }

    public void archiveCurrent(DramaForgeShot shot) {
        if ((shot.getStoryboardUrl() == null || shot.getStoryboardUrl().isBlank())
                && shot.getVideoJobId() == null) {
            return;
        }

        var nextNo = versionRepository.findTopByShotIdOrderByVersionNoDesc(shot.getId())
                .map(v -> v.getVersionNo() + 1)
                .orElse(1);

        var version = new DramaForgeShotVersion();
        version.setShotId(shot.getId());
        version.setVersionNo(nextNo);
        version.setStoryboardUrl(shot.getStoryboardUrl());
        version.setVideoJobId(shot.getVideoJobId());
        version.setVideoUrl(statusCalculator.resolveVideoUrl(shot));
        version.setActive(false);
        versionRepository.save(version);
    }

    public void clearEpisodeShots(UUID episodeId) {
        var shots = shotRepository.findByEpisodeIdOrderByShotNumberAsc(episodeId);
        for (var shot : shots) {
            versionRepository.deleteByShotId(shot.getId());
        }
        shotRepository.deleteAll(shots);
    }

    /** 删除单个镜头并收紧后续镜头编号 */
    public void deleteShotAndCompact(UUID episodeId, int shotNumber) {
        var shot = shotRepository.findByEpisodeIdOrderByShotNumberAsc(episodeId).stream()
                .filter(s -> s.getShotNumber() == shotNumber)
                .findFirst()
                .orElse(null);
        if (shot == null) {
            return;
        }
        versionRepository.deleteByShotId(shot.getId());
        shotRepository.delete(shot);
        for (var remaining : shotRepository.findByEpisodeIdOrderByShotNumberAsc(episodeId)) {
            if (remaining.getShotNumber() > shotNumber) {
                remaining.setShotNumber(remaining.getShotNumber() - 1);
                shotRepository.save(remaining);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<ShotVersionResponse> listVersions(UUID shotId) {
        return versionRepository.findByShotIdOrderByVersionNoDesc(shotId).stream()
                .map(this::toResponse)
                .toList();
    }

    public ShotVersionResponse activateVersion(UUID shotId, UUID versionId) {
        var shot = shotRepository.findById(shotId)
                .orElseThrow(() -> new ResourceNotFoundException("镜头不存在: " + shotId));
        var version = versionRepository.findById(versionId)
                .filter(v -> v.getShotId().equals(shotId))
                .orElseThrow(() -> new ResourceNotFoundException("版本不存在: " + versionId));

        versionRepository.findByShotIdAndActiveTrue(shotId).ifPresent(active -> {
            active.setActive(false);
            versionRepository.save(active);
        });

        version.setActive(true);
        versionRepository.save(version);

        shot.setStoryboardUrl(version.getStoryboardUrl());
        shot.setVideoJobId(version.getVideoJobId());
        if (version.getVideoUrl() != null && !version.getVideoUrl().isBlank()) {
            shot.setStatus(DramaForgeShotStatus.VIDEO_DONE);
        } else if (version.getStoryboardUrl() != null && !version.getStoryboardUrl().isBlank()) {
            shot.setStatus(DramaForgeShotStatus.STORYBOARD_DONE);
        } else {
            shot.setStatus(DramaForgeShotStatus.PENDING);
        }
        shotRepository.save(shot);
        return toResponse(version);
    }

    private ShotVersionResponse toResponse(DramaForgeShotVersion version) {
        return new ShotVersionResponse(
                version.getId(),
                version.getShotId(),
                version.getVersionNo(),
                version.getStoryboardUrl(),
                version.getVideoJobId(),
                version.getVideoUrl(),
                version.isActive(),
                version.getCreatedAt()
        );
    }
}
