package com.dreamreel.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class DramaForgeJobExecutorConfig {

    @Bean(name = "dramaforgeJobExecutor")
    ThreadPoolTaskExecutor dramaforgeJobExecutor(DramaForgeProperties properties) {
        var concurrency = Math.max(1, properties.maxConcurrentJobs());
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(concurrency);
        executor.setMaxPoolSize(concurrency);
        executor.setQueueCapacity(Math.max(0, properties.executorQueueCapacity()));
        executor.setThreadNamePrefix("dramaforge-job-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(Math.max(1, properties.shutdownAwaitSeconds()));
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
