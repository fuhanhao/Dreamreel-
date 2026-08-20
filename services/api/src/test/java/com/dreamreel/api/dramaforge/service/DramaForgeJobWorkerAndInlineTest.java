package com.dreamreel.api.dramaforge.service;

import com.dreamreel.api.config.DramaForgeProperties;
import com.dreamreel.api.dramaforge.domain.DramaForgeJob;
import com.dreamreel.api.dramaforge.domain.DramaForgeJobStatus;
import com.dreamreel.api.dramaforge.domain.DramaForgeJobType;
import com.dreamreel.api.dramaforge.dto.DramaForgeDtos.JobResponse;
import com.dreamreel.api.dramaforge.repository.DramaForgeJobClaimRepository;
import com.dreamreel.api.dramaforge.repository.DramaForgeJobRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.UUID;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DramaForgeJobWorkerAndInlineTest {

    @Mock DramaForgeJobService jobService;
    @Mock DramaForgeJobProcessor jobProcessor;
    @Mock DramaForgeJobRepository jobRepository;
    @Mock DramaForgeJobClaimRepository claimRepository;
    @Mock DramaForgeEventHub eventHub;

    @Test
    void workerRequiresJobWorkerEnabledProperty() {
        var ann = DramaForgeJobWorker.class.getAnnotation(ConditionalOnProperty.class);
        assertEquals("dreamreel.dramaforge", ann.prefix());
        assertArrayEquals(new String[] {"job-worker-enabled"}, ann.name());
        assertEquals("true", ann.havingValue());
        assertTrue(ann.matchIfMissing());
    }

    @Test
    void inlineEnqueueStartsRunningAndSchedulesProcessor() throws Exception {
        var properties = properties(true, false, 2);
        var enqueue = new DramaForgeEnqueueService(
                jobService, jobProcessor, properties, new ObjectMapper(), Runnable::run);
        var projectId = UUID.randomUUID();
        var episodeId = UUID.randomUUID();
        var shotId = UUID.randomUUID();
        var job = new DramaForgeJob();
        job.setId(UUID.randomUUID());
        job.setProjectId(projectId);
        job.setStatus(DramaForgeJobStatus.RUNNING);
        job.setJobType(DramaForgeJobType.SHOT_STORYBOARD);

        when(jobService.hasRunningJob(projectId)).thenReturn(false);
        when(jobService.enqueue(
                eq(projectId),
                eq(DramaForgeJobType.SHOT_STORYBOARD),
                eq(shotId),
                eq(episodeId),
                any(),
                eq(true)))
                .thenReturn(job);
        when(jobService.toResponse(job)).thenReturn(JobResponse.from(job, null));

        enqueue.enqueueShotStoryboard(projectId, episodeId, shotId, "sk-test");

        verify(jobService).enqueue(
                eq(projectId),
                eq(DramaForgeJobType.SHOT_STORYBOARD),
                eq(shotId),
                eq(episodeId),
                any(),
                eq(true));
        verify(jobProcessor).process(job);
    }

    @Test
    void inlineEnqueueQueuesWhenProjectAlreadyHasRunningJob() {
        var properties = properties(true, false, 2);
        var enqueue = new DramaForgeEnqueueService(
                jobService, jobProcessor, properties, new ObjectMapper(), Runnable::run);
        var projectId = UUID.randomUUID();
        var episodeId = UUID.randomUUID();
        var shotId = UUID.randomUUID();
        var job = new DramaForgeJob();
        job.setId(UUID.randomUUID());
        job.setProjectId(projectId);
        job.setStatus(DramaForgeJobStatus.QUEUED);
        job.setJobType(DramaForgeJobType.SHOT_VIDEO);

        when(jobService.hasRunningJob(projectId)).thenReturn(true);
        when(jobService.enqueue(
                eq(projectId),
                eq(DramaForgeJobType.SHOT_VIDEO),
                eq(shotId),
                eq(episodeId),
                any(),
                eq(false)))
                .thenReturn(job);
        when(jobService.toResponse(job)).thenReturn(JobResponse.from(job, 1));

        var response = enqueue.enqueueShotVideo(projectId, episodeId, shotId, "sk-test");

        assertEquals(DramaForgeJobStatus.QUEUED, job.getStatus());
        assertEquals(job.getId(), response.id());
        verify(jobService).enqueue(
                eq(projectId),
                eq(DramaForgeJobType.SHOT_VIDEO),
                eq(shotId),
                eq(episodeId),
                any(),
                eq(false));
        verify(jobProcessor, never()).process(job);
    }

    @Test
    void nonInlineEnqueueStaysQueuedWithoutProcessor() {
        var properties = properties(false, true, 2);
        var enqueue = new DramaForgeEnqueueService(
                jobService, jobProcessor, properties, new ObjectMapper(), Runnable::run);
        var projectId = UUID.randomUUID();
        var episodeId = UUID.randomUUID();
        var shotId = UUID.randomUUID();
        var job = new DramaForgeJob();
        job.setId(UUID.randomUUID());
        job.setProjectId(projectId);
        job.setStatus(DramaForgeJobStatus.QUEUED);
        job.setJobType(DramaForgeJobType.SHOT_STORYBOARD);

        when(jobService.enqueue(
                eq(projectId),
                eq(DramaForgeJobType.SHOT_STORYBOARD),
                eq(shotId),
                eq(episodeId),
                any(),
                eq(false)))
                .thenReturn(job);
        when(jobService.toResponse(job)).thenReturn(JobResponse.from(job, null));

        enqueue.enqueueShotStoryboard(projectId, episodeId, shotId, "sk-test");

        verify(jobService).enqueue(
                eq(projectId),
                eq(DramaForgeJobType.SHOT_STORYBOARD),
                eq(shotId),
                eq(episodeId),
                any(),
                eq(false));
        verify(jobProcessor, never()).process(any());
    }

    @Test
    void jobFailBelowMaxAttemptsRequeues() {
        var service = new DramaForgeJobService(jobRepository, claimRepository, eventHub, properties(false, true, 2));
        var job = new DramaForgeJob();
        job.setId(UUID.randomUUID());
        job.setProjectId(UUID.randomUUID());
        job.setJobType(DramaForgeJobType.SHOT_STORYBOARD);
        job.setTargetId(UUID.randomUUID());
        job.setAttempts(1);
        job.setStatus(DramaForgeJobStatus.RUNNING);
        when(jobRepository.save(any(DramaForgeJob.class))).thenAnswer(inv -> inv.getArgument(0));

        service.fail(job, "transient");

        assertEquals(DramaForgeJobStatus.QUEUED, job.getStatus());
        assertFalse(DramaForgeJobProcessor.shouldMarkShotFailedAfterJobFail(job));
    }

    @Test
    void jobFailAtMaxAttemptsIsTerminalAndMarksShot() {
        var service = new DramaForgeJobService(jobRepository, claimRepository, eventHub, properties(false, true, 2));
        var job = new DramaForgeJob();
        job.setId(UUID.randomUUID());
        job.setProjectId(UUID.randomUUID());
        job.setTargetId(UUID.randomUUID());
        job.setJobType(DramaForgeJobType.SHOT_STORYBOARD);
        job.setAttempts(3);
        job.setStatus(DramaForgeJobStatus.RUNNING);
        when(jobRepository.save(any(DramaForgeJob.class))).thenAnswer(inv -> inv.getArgument(0));

        service.fail(job, "final");

        assertEquals(DramaForgeJobStatus.FAILED, job.getStatus());
        assertTrue(DramaForgeJobProcessor.shouldMarkShotFailedAfterJobFail(job));
    }

    @Test
    void requeuedStoryboardJobDoesNotMarkShotFailed() {
        var job = new DramaForgeJob();
        job.setStatus(DramaForgeJobStatus.QUEUED);
        job.setJobType(DramaForgeJobType.SHOT_STORYBOARD);
        job.setTargetId(UUID.randomUUID());
        assertFalse(DramaForgeJobProcessor.shouldMarkShotFailedAfterJobFail(job));
    }

    @Test
    void workerDispatchesOnlyUpToConfiguredConcurrencyAndReleasesCapacity() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        try {
            var started = new CountDownLatch(2);
            var release = new CountDownLatch(1);
            var first = queuedJob();
            var second = queuedJob();
            when(jobService.claimNext()).thenReturn(first, second);
            doAnswer(invocation -> {
                started.countDown();
                release.await(2, TimeUnit.SECONDS);
                return null;
            }).when(jobProcessor).process(any());

            var worker = new DramaForgeJobWorker(
                    jobService, jobProcessor, properties(false, true, 2), executor);
            worker.poll();

            assertTrue(started.await(1, TimeUnit.SECONDS));
            assertEquals(0, worker.availableCapacity());
            release.countDown();
            for (int i = 0; i < 20 && worker.availableCapacity() != 2; i++) {
                Thread.sleep(25);
            }
            assertEquals(2, worker.availableCapacity());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectedSubmissionReturnsJobToQueueAndReleasesCapacity() {
        var job = queuedJob();
        when(jobService.claimNext()).thenReturn(job);
        var rejectingExecutor = (java.util.concurrent.Executor) command -> {
            throw new RejectedExecutionException("full");
        };
        var worker = new DramaForgeJobWorker(
                jobService, jobProcessor, properties(false, true, 1), rejectingExecutor);

        worker.poll();

        verify(jobService).releaseClaim(job, "任务执行器繁忙，已重新排队");
        assertEquals(1, worker.availableCapacity());
        verify(jobProcessor, never()).process(any());
    }

    @Test
    void releaseClaimDoesNotConsumeAnAttempt() {
        var service = new DramaForgeJobService(
                jobRepository, claimRepository, eventHub, properties(false, true, 2));
        var job = queuedJob();
        job.setStatus(DramaForgeJobStatus.RUNNING);
        job.setAttempts(2);
        when(jobRepository.save(any(DramaForgeJob.class))).thenAnswer(inv -> inv.getArgument(0));

        service.releaseClaim(job, "busy");

        assertEquals(DramaForgeJobStatus.QUEUED, job.getStatus());
        assertEquals(1, job.getAttempts());
    }

    @Test
    void startupRecoveryOnlyDelegatesLeaseExpiryRecovery() {
        when(jobService.recoverStaleJobs()).thenReturn(2);

        new DramaForgeJobStartupRecovery(jobService).recoverOrphanedJobs();

        verify(jobService).recoverStaleJobs();
        verify(jobService, never()).forceFail(any(), any());
    }

    @Test
    void expiredLeaseRequeuesUntilConfiguredAttemptLimit() {
        var service = new DramaForgeJobService(
                jobRepository, claimRepository, eventHub, properties(false, true, 2));
        var job = queuedJob();
        job.setStatus(DramaForgeJobStatus.RUNNING);
        job.setAttempts(2);
        when(claimRepository.lockStaleRunningIds(any(), eq(100))).thenReturn(List.of(job.getId()));
        when(jobRepository.findAllById(any())).thenReturn(List.of(job));
        when(jobRepository.save(any(DramaForgeJob.class))).thenAnswer(inv -> inv.getArgument(0));

        assertEquals(1, service.recoverStaleJobs());

        assertEquals(DramaForgeJobStatus.QUEUED, job.getStatus());
        assertEquals("任务超时，已自动重试", job.getErrorMessage());
    }

    private static DramaForgeProperties properties(boolean inline, boolean worker, int concurrency) {
        return new DramaForgeProperties(inline, worker, concurrency, concurrency, 1000, 1800, 3, 30);
    }

    private static DramaForgeJob queuedJob() {
        var job = new DramaForgeJob();
        job.setId(UUID.randomUUID());
        job.setProjectId(UUID.randomUUID());
        job.setStatus(DramaForgeJobStatus.QUEUED);
        job.setJobType(DramaForgeJobType.SHOT_STORYBOARD);
        return job;
    }
}
