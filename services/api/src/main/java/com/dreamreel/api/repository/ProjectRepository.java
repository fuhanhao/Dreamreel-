package com.dreamreel.api.repository;

import com.dreamreel.api.domain.Project;
import com.dreamreel.api.domain.ProjectType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    List<Project> findAllByOrderByUpdatedAtDesc();

    List<Project> findByUserIdOrderByUpdatedAtDesc(UUID userId);

    Page<Project> findAllByOrderByUpdatedAtDesc(Pageable pageable);

    Page<Project> findByUserIdOrderByUpdatedAtDesc(UUID userId, Pageable pageable);

    Optional<Project> findByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);

    long countByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            UUID userId, Instant startInclusive, Instant endExclusive);

    interface ProjectSummaryView {
        UUID getId();
        String getName();
        ProjectType getType();
        String getDescription();
        Instant getCreatedAt();
        Instant getUpdatedAt();
        UUID getUserId();
    }

    @Query("""
            select p.id as id, p.name as name, p.type as type, p.description as description,
                   p.createdAt as createdAt, p.updatedAt as updatedAt, p.userId as userId
            from Project p
            where p.id = :id
            """)
    Optional<ProjectSummaryView> findSummaryById(@Param("id") UUID id);

    @Query("""
            select p.id as id, p.name as name, p.type as type, p.description as description,
                   p.createdAt as createdAt, p.updatedAt as updatedAt, p.userId as userId
            from Project p
            where p.id = :id and p.userId = :userId
            """)
    Optional<ProjectSummaryView> findSummaryByIdAndUserId(
            @Param("id") UUID id, @Param("userId") UUID userId);
}
