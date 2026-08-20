package com.dreamreel.api.dramaforge.repository;

import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public class DramaForgeJobClaimRepository {

    private final EntityManager entityManager;

    public DramaForgeJobClaimRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * 锁定一个不同项目的最早排队任务。SKIP LOCKED 允许多个 API 实例并行抢占，
     * 不会等待另一个 worker 已经锁定的任务行。
     */
    public UUID lockNextClaimableId(Instant now) {
        @SuppressWarnings("unchecked")
        var rows = (java.util.List<Object[]>) entityManager.createNativeQuery("""
                        select j.id, j.project_id
                        from dramaforge_jobs j
                        where j.status = 'QUEUED'
                          and (j.lease_until is null or j.lease_until < :now)
                          and not exists (
                              select 1
                              from dramaforge_jobs running
                              where running.project_id = j.project_id
                                and running.status = 'RUNNING'
                          )
                        order by j.created_at asc
                        limit 1
                        for update skip locked
                        """)
                .setParameter("now", now)
                .getResultList();
        if (rows.isEmpty()) {
            return null;
        }
        var row = rows.getFirst();
        var id = toUuid(row[0]);
        var projectId = toUuid(row[1]);
        if (isPostgres()) {
            entityManager.createNativeQuery("""
                            select pg_advisory_xact_lock(
                                hashtextextended(cast(:projectId as text), 0)
                            )
                            """)
                    .setParameter("projectId", projectId)
                    .getSingleResult();
            var running = ((Number) entityManager.createNativeQuery("""
                            select count(*)
                            from dramaforge_jobs
                            where project_id = :projectId
                              and status = 'RUNNING'
                            """)
                    .setParameter("projectId", projectId)
                    .getSingleResult()).longValue();
            if (running > 0) {
                return null;
            }
        }
        return id;
    }

    public java.util.List<UUID> lockStaleRunningIds(Instant now, int limit) {
        @SuppressWarnings("unchecked")
        var rows = (java.util.List<Object>) entityManager.createNativeQuery("""
                        select j.id
                        from dramaforge_jobs j
                        where j.status = 'RUNNING'
                          and j.lease_until is not null
                          and j.lease_until < :now
                        order by j.lease_until asc
                        limit :limit
                        for update skip locked
                        """)
                .setParameter("now", now)
                .setParameter("limit", limit)
                .getResultList();
        return rows.stream()
                .map(DramaForgeJobClaimRepository::toUuid)
                .toList();
    }

    private boolean isPostgres() {
        return entityManager.unwrap(Session.class).doReturningWork(connection ->
                "PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName()));
    }

    private static UUID toUuid(Object value) {
        return value instanceof UUID uuid ? uuid : UUID.fromString(value.toString());
    }
}
