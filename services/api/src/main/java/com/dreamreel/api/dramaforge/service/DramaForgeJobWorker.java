package com.dreamreel.api.dramaforge.service;

import com.dreamreel.api.config.DramaForgeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;

@Component
@ConditionalOnProperty(
        prefix = "dreamreel.dramaforge",
        name = "job-worker-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class DramaForgeJobWorker {

    private static final Logger log = LoggerFactory.getLogger(DramaForgeJobWorker.class);

    private final DramaForgeJobService jobService;
    private final DramaForgeJobProcessor processor;
    private final Executor jobExecutor;
    private final Semaphore capacity;

    public DramaForgeJobWorker(
            DramaForgeJobService jobService,
            DramaForgeJobProcessor processor,
            DramaForgeProperties properties,
            @Qualifier("dramaforgeJobExecutor") Executor jobExecutor) {
        this.jobService = jobService;
        this.processor = processor;
        this.jobExecutor = jobExecutor;
        this.capacity = new Semaphore(Math.max(1, properties.maxConcurrentJobs()), true);
    }

    @Scheduled(fixedDelayString = "${dreamreel.dramaforge.poll-interval-ms:1000}")
    public void poll() {
        jobService.recoverStaleJobs();
        while (capacity.tryAcquire()) {
            var job = claimNextSafely();
            if (job == null) {
                capacity.release();
                break;
            }
            try {
                jobExecutor.execute(() -> {
                    try {
                        processor.process(job);
                    } catch (Throwable ex) {
                        log.error("DramaForge job processor escaped unexpectedly: {}", job.getId(), ex);
                    } finally {
                        capacity.release();
                    }
                });
            } catch (RuntimeException ex) {
                capacity.release();
                log.warn("DramaForge executor rejected job {}, returning it to queue", job.getId(), ex);
                jobService.releaseClaim(job, "任务执行器繁忙，已重新排队");
                break;
            }
        }
    }

    private com.dreamreel.api.dramaforge.domain.DramaForgeJob claimNextSafely() {
        try {
            return jobService.claimNext();
        } catch (DataIntegrityViolationException ex) {
            // 多实例同时抢同一项目时，数据库唯一索引只允许一个 RUNNING。
            log.debug("Concurrent DramaForge claim lost; another worker won", ex);
            return null;
        }
    }

    int availableCapacity() {
        return capacity.availablePermits();
    }
}
