package com.dreamreel.api.dramaforge.repository;

import com.dreamreel.api.dramaforge.domain.DramaForgeJob;
import com.dreamreel.api.dramaforge.domain.DramaForgeJobStatus;
import com.dreamreel.api.dramaforge.domain.DramaForgeJobType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@DataJpaTest
@Import(DramaForgeJobClaimRepository.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DramaForgeJobClaimPostgresTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired DramaForgeJobRepository jobRepository;
    @Autowired DramaForgeJobClaimRepository claimRepository;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void installRunningProjectGuard() {
        jobRepository.deleteAll();
        jdbcTemplate.execute("""
                create unique index if not exists uq_dramaforge_jobs_running_project
                on dramaforge_jobs (project_id)
                where status = 'RUNNING'
                """);
    }

    @Test
    void concurrentWorkersClaimDifferentJobsOnlyOnce() throws Exception {
        jobRepository.saveAndFlush(queuedJob(UUID.randomUUID()));
        jobRepository.saveAndFlush(queuedJob(UUID.randomUUID()));

        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(this::claimOnce);
            var second = executor.submit(this::claimOnce);
            var firstId = first.get(5, TimeUnit.SECONDS);
            var secondId = second.get(5, TimeUnit.SECONDS);

            assertNotEquals(firstId, secondId);
            assertEquals(2, jobRepository.findByStatus(DramaForgeJobStatus.RUNNING).size());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void databaseGuardKeepsSameProjectSerialized() throws Exception {
        var projectId = UUID.randomUUID();
        jobRepository.saveAndFlush(queuedJob(projectId));
        jobRepository.saveAndFlush(queuedJob(projectId));

        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(this::claimOnce);
            var second = executor.submit(this::claimOnce);
            int successes = 0;
            for (var future : java.util.List.of(first, second)) {
                try {
                    future.get(5, TimeUnit.SECONDS);
                    successes++;
                } catch (java.util.concurrent.ExecutionException ignored) {
                    // The partial unique index rejects the competing RUNNING transition.
                }
            }

            assertEquals(1, successes);
            assertEquals(1, jobRepository.findByStatus(DramaForgeJobStatus.RUNNING).size());
            assertEquals(1, jobRepository.findByStatus(DramaForgeJobStatus.QUEUED).size());
        } finally {
            executor.shutdownNow();
        }
    }

    private UUID claimOnce() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            var id = claimRepository.lockNextClaimableId(Instant.now());
            if (id == null) {
                throw new IllegalStateException("No claimable job");
            }
            var job = jobRepository.findById(id).orElseThrow();
            job.setStatus(DramaForgeJobStatus.RUNNING);
            job.setAttempts(job.getAttempts() + 1);
            job.setLeaseUntil(Instant.now().plusSeconds(60));
            jobRepository.saveAndFlush(job);
            return id;
        });
    }

    private static DramaForgeJob queuedJob(UUID projectId) {
        var job = new DramaForgeJob();
        job.setProjectId(projectId);
        job.setJobType(DramaForgeJobType.SHOT_STORYBOARD);
        job.setStatus(DramaForgeJobStatus.QUEUED);
        return job;
    }
}
