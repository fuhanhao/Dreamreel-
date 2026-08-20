package com.dreamreel.api.dramaforge.service;

import com.dreamreel.api.config.DramaForgeProperties;
import com.dreamreel.api.dramaforge.domain.DramaForgeJob;
import com.dreamreel.api.dramaforge.domain.DramaForgeJobType;
import com.dreamreel.api.dramaforge.repository.DramaForgeJobClaimRepository;
import com.dreamreel.api.dramaforge.repository.DramaForgeJobRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DramaForgeJobServiceSyncVideosTest {

    @Mock DramaForgeJobRepository jobRepository;
    @Mock DramaForgeJobClaimRepository claimRepository;
    @Mock DramaForgeEventHub eventHub;
    @Mock DramaForgeProperties properties;
    @InjectMocks DramaForgeJobService jobService;

    @Test
    void completeDeletesSyncVideosInsteadOfKeepingHistory() {
        var job = new DramaForgeJob();
        job.setId(UUID.randomUUID());
        job.setProjectId(UUID.randomUUID());
        job.setJobType(DramaForgeJobType.SYNC_VIDEOS);

        jobService.complete(job);

        verify(jobRepository).delete(job);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(eventHub).publish(eq(job.getProjectId()), eq("job_removed"), payload.capture());
        assertEquals(job.getId().toString(), payload.getValue().get("jobId"));
    }
}
