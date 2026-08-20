package com.dreamreel.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * DramaForge 任务执行策略。本机连共享库时建议 process-jobs-inline=true，
 * 防止生产机旧 worker 抢走分镜任务并回退到 nano-banana-2。
 */
@ConfigurationProperties(prefix = "dreamreel.dramaforge")
public record DramaForgeProperties(
        @DefaultValue("false") boolean processJobsInline,
        @DefaultValue("true") boolean jobWorkerEnabled,
        @DefaultValue("4") int maxConcurrentJobs,
        @DefaultValue("4") int executorQueueCapacity,
        @DefaultValue("1000") long pollIntervalMs,
        @DefaultValue("1800") long leaseSeconds,
        @DefaultValue("3") int maxAttempts,
        @DefaultValue("120") int shutdownAwaitSeconds
) {}
