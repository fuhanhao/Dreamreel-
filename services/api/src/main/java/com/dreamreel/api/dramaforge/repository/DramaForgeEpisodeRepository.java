package com.dreamreel.api.dramaforge.repository;

import com.dreamreel.api.dramaforge.domain.DramaForgeEpisode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DramaForgeEpisodeRepository extends JpaRepository<DramaForgeEpisode, UUID> {
    List<DramaForgeEpisode> findByProjectIdOrderByEpisodeNumberAsc(UUID projectId);

    Optional<DramaForgeEpisode> findByIdAndProjectId(UUID id, UUID projectId);

    long countByProjectId(UUID projectId);

    Optional<DramaForgeEpisode> findTopByProjectIdOrderByEpisodeNumberDesc(UUID projectId);
}
