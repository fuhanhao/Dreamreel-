package com.dreamreel.api.dramaforge.repository;

import com.dreamreel.api.dramaforge.domain.DramaForgeShotVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DramaForgeShotVersionRepository extends JpaRepository<DramaForgeShotVersion, UUID> {
    List<DramaForgeShotVersion> findByShotIdOrderByVersionNoDesc(UUID shotId);

    Optional<DramaForgeShotVersion> findByShotIdAndActiveTrue(UUID shotId);

    Optional<DramaForgeShotVersion> findTopByShotIdOrderByVersionNoDesc(UUID shotId);

    void deleteByShotId(UUID shotId);
}
