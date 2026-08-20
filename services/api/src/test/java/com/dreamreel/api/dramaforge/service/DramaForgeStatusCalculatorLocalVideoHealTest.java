package com.dreamreel.api.dramaforge.service;

import com.dreamreel.api.dramaforge.domain.DramaForgeShot;
import com.dreamreel.api.dramaforge.domain.DramaForgeShotStatus;
import com.dreamreel.api.dramaforge.repository.DramaForgeAssetRepository;
import com.dreamreel.api.dramaforge.repository.DramaForgeCompositionRepository;
import com.dreamreel.api.dramaforge.repository.DramaForgeEpisodeRepository;
import com.dreamreel.api.dramaforge.repository.DramaForgeJobRepository;
import com.dreamreel.api.dramaforge.repository.DramaForgeShotRepository;
import com.dreamreel.api.domain.GenerationJob;
import com.dreamreel.api.domain.GenerationStatus;
import com.dreamreel.api.repository.GenerationJobRepository;
import com.dreamreel.api.service.MediaStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DramaForgeStatusCalculatorLocalVideoHealTest {

    @Mock DramaForgeAssetRepository assetRepository;
    @Mock DramaForgeEpisodeRepository episodeRepository;
    @Mock DramaForgeShotRepository shotRepository;
    @Mock DramaForgeCompositionRepository compositionRepository;
    @Mock DramaForgeJobRepository jobRepository;
    @Mock GenerationJobRepository generationJobRepository;
    @Mock MediaStorageService mediaStorageService;
    @Mock DramaForgeConsistencyService consistencyService;

    @Test
    void healsCompletedLocalJobToVideoDone() {
        var calc = newCalculator();
        var jobId = UUID.randomUUID();
        var shot = new DramaForgeShot();
        shot.setStatus(DramaForgeShotStatus.STORYBOARD_DONE);
        shot.setVideoJobId(jobId);

        var job = new GenerationJob();
        job.setId(jobId);
        job.setStatus(GenerationStatus.COMPLETED);
        when(generationJobRepository.findById(jobId)).thenReturn(Optional.of(job));

        assertTrue(calc.applyLocalVideoJobTerminalStatus(shot));
        assertEquals(DramaForgeShotStatus.VIDEO_DONE, shot.getStatus());
    }

    @Test
    void leavesPendingProviderJobUntouched() {
        var calc = newCalculator();
        var jobId = UUID.randomUUID();
        var shot = new DramaForgeShot();
        shot.setStatus(DramaForgeShotStatus.STORYBOARD_DONE);
        shot.setVideoJobId(jobId);

        var job = new GenerationJob();
        job.setId(jobId);
        job.setStatus(GenerationStatus.IN_PROGRESS);
        when(generationJobRepository.findById(jobId)).thenReturn(Optional.of(job));

        assertFalse(calc.applyLocalVideoJobTerminalStatus(shot));
        assertEquals(DramaForgeShotStatus.STORYBOARD_DONE, shot.getStatus());
    }

    private DramaForgeStatusCalculator newCalculator() {
        return new DramaForgeStatusCalculator(
                assetRepository,
                episodeRepository,
                shotRepository,
                compositionRepository,
                jobRepository,
                generationJobRepository,
                mediaStorageService,
                consistencyService);
    }
}
