package com.dreamreel.api.dramaforge.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class DramaForgeJobStartupRecovery {

    private static final Logger log = LoggerFactory.getLogger(DramaForgeJobStartupRecovery.class);

    private final DramaForgeJobService jobService;

    public DramaForgeJobStartupRecovery(DramaForgeJobService jobService) {
        this.jobService = jobService;
    }

    /** 仅回收租约已经过期的任务；其它实例仍可能继续执行租约有效的 RUNNING 任务。 */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverOrphanedJobs() {
        var recovered = jobService.recoverStaleJobs();
        if (recovered > 0) {
            log.warn("服务启动时恢复了 {} 个租约过期的 DramaForge 任务", recovered);
        }
    }
}
