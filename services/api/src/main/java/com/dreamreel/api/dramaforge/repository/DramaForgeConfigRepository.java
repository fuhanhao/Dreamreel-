package com.dreamreel.api.dramaforge.repository;

import com.dreamreel.api.dramaforge.domain.DramaForgeConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DramaForgeConfigRepository extends JpaRepository<DramaForgeConfig, UUID> {
    Optional<DramaForgeConfig> findByProjectId(UUID projectId);
}
