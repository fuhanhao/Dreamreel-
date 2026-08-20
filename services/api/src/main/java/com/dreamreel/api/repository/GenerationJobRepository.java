package com.dreamreel.api.repository;

import com.dreamreel.api.domain.GenerationJob;
import com.dreamreel.api.domain.GenerationMediaType;
import com.dreamreel.api.domain.GenerationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface GenerationJobRepository extends JpaRepository<GenerationJob, UUID> {

    Page<GenerationJob> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<GenerationJob> findByUserIdAndMediaTypeOrderByCreatedAtDesc(
            UUID userId, GenerationMediaType mediaType, Pageable pageable);

    Page<GenerationJob> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<GenerationJob> findByMediaTypeOrderByCreatedAtDesc(GenerationMediaType mediaType, Pageable pageable);

    Optional<GenerationJob> findByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);

    long countByStatus(GenerationStatus status);

    long countByUserIdAndMediaTypeAndStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            UUID userId,
            GenerationMediaType mediaType,
            GenerationStatus status,
            Instant startInclusive,
            Instant endExclusive);
}
