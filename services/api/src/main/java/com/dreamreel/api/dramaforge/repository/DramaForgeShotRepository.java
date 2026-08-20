package com.dreamreel.api.dramaforge.repository;

import com.dreamreel.api.dramaforge.domain.DramaForgeShot;
import com.dreamreel.api.dramaforge.domain.DramaForgeShotStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DramaForgeShotRepository extends JpaRepository<DramaForgeShot, UUID> {
    List<DramaForgeShot> findByEpisodeIdOrderByShotNumberAsc(UUID episodeId);

    Optional<DramaForgeShot> findByIdAndEpisodeId(UUID id, UUID episodeId);

    long countByEpisodeId(UUID episodeId);

    long countByEpisodeIdAndStatus(UUID episodeId, DramaForgeShotStatus status);

    Optional<DramaForgeShot> findTopByEpisodeIdOrderByShotNumberDesc(UUID episodeId);
}
