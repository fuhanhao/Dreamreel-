package com.dreamreel.api.dramaforge.repository;

import com.dreamreel.api.dramaforge.domain.DramaForgeJob;
import com.dreamreel.api.dramaforge.domain.DramaForgeJobStatus;
import com.dreamreel.api.dramaforge.domain.DramaForgeJobType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DramaForgeJobRepository extends JpaRepository<DramaForgeJob, UUID> {
    List<DramaForgeJob> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    List<DramaForgeJob> findByProjectIdOrderByCreatedAtDesc(UUID projectId, Pageable pageable);

    List<DramaForgeJob> findByProjectIdAndStatusInOrderByCreatedAtDesc(
            UUID projectId, Collection<DramaForgeJobStatus> statuses);

    long countByProjectIdAndStatusIn(UUID projectId, Collection<DramaForgeJobStatus> statuses);

    List<DramaForgeJob> findByStatusOrderByCreatedAtAsc(DramaForgeJobStatus status);

    boolean existsByStatus(DramaForgeJobStatus status);

    java.util.Optional<DramaForgeJob> findByIdAndProjectId(UUID id, UUID projectId);

    long deleteByProjectIdAndStatusIn(UUID projectId, List<DramaForgeJobStatus> statuses);

    java.util.Optional<DramaForgeJob> findFirstByTargetIdAndStatusOrderByCreatedAtDesc(
            UUID targetId, DramaForgeJobStatus status);

    long countByProjectIdAndStatus(UUID projectId, DramaForgeJobStatus status);

    boolean existsByProjectIdAndJobTypeAndStatusIn(UUID projectId, DramaForgeJobType jobType, List<DramaForgeJobStatus> statuses);

    java.util.Optional<DramaForgeJob> findFirstByProjectIdAndJobTypeAndStatusInOrderByCreatedAtDesc(
            UUID projectId, DramaForgeJobType jobType, Collection<DramaForgeJobStatus> statuses);

    java.util.Optional<DramaForgeJob> findFirstByProjectIdAndEpisodeIdAndJobTypeAndStatusInOrderByCreatedAtDesc(
            UUID projectId,
            UUID episodeId,
            DramaForgeJobType jobType,
            Collection<DramaForgeJobStatus> statuses);

    long deleteByProjectIdAndJobTypeAndStatusIn(
            UUID projectId, DramaForgeJobType jobType, List<DramaForgeJobStatus> statuses);

    List<DramaForgeJob> findByStatus(DramaForgeJobStatus status);
}
