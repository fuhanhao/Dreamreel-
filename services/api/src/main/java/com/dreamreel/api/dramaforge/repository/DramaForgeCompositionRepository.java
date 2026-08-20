package com.dreamreel.api.dramaforge.repository;

import com.dreamreel.api.dramaforge.domain.DramaForgeComposition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DramaForgeCompositionRepository extends JpaRepository<DramaForgeComposition, UUID> {
    List<DramaForgeComposition> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    Optional<DramaForgeComposition> findTopByProjectIdAndEpisodeIdOrderByCreatedAtDesc(UUID projectId, UUID episodeId);
}
