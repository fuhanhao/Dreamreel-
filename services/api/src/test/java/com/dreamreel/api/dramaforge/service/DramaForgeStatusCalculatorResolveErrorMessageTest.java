package com.dreamreel.api.dramaforge.service;

import com.dreamreel.api.dramaforge.domain.DramaForgeJob;
import com.dreamreel.api.dramaforge.domain.DramaForgeJobStatus;
import com.dreamreel.api.dramaforge.domain.DramaForgeShot;
import com.dreamreel.api.dramaforge.domain.DramaForgeShotStatus;
import com.dreamreel.api.dramaforge.repository.DramaForgeAssetRepository;
import com.dreamreel.api.dramaforge.repository.DramaForgeCompositionRepository;
import com.dreamreel.api.dramaforge.repository.DramaForgeEpisodeRepository;
import com.dreamreel.api.dramaforge.repository.DramaForgeJobRepository;
import com.dreamreel.api.dramaforge.repository.DramaForgeShotRepository;
import com.dreamreel.api.repository.GenerationJobRepository;
import com.dreamreel.api.service.MediaStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DramaForgeStatusCalculatorResolveErrorMessageTest {

    @Mock DramaForgeAssetRepository assetRepository;
    @Mock DramaForgeEpisodeRepository episodeRepository;
    @Mock DramaForgeShotRepository shotRepository;
    @Mock DramaForgeCompositionRepository compositionRepository;
    @Mock DramaForgeJobRepository jobRepository;
    @Mock GenerationJobRepository generationJobRepository;
    @Mock MediaStorageService mediaStorageService;
    @Mock DramaForgeConsistencyService consistencyService;

    private DramaForgeStatusCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new DramaForgeStatusCalculator(
                assetRepository,
                episodeRepository,
                shotRepository,
                compositionRepository,
                jobRepository,
                generationJobRepository,
                mediaStorageService,
                consistencyService);
    }

    @Test
    void prefersShotEntityErrorMessage() {
        var shot = shot(DramaForgeShotStatus.FAILED, "镜头侧错误");
        assertEquals("镜头侧错误", calculator.resolveErrorMessage(shot));
        verify(jobRepository, never()).findFirstByTargetIdAndStatusOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void successfulStoryboardDoesNotResurfaceHistoricalFailedJob() {
        var shot = shot(DramaForgeShotStatus.STORYBOARD_DONE, null);
        assertNull(calculator.resolveErrorMessage(shot));
        verify(jobRepository, never()).findFirstByTargetIdAndStatusOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void successfulVideoDoesNotResurfaceHistoricalFailedJob() {
        var shot = shot(DramaForgeShotStatus.VIDEO_DONE, null);
        assertNull(calculator.resolveErrorMessage(shot));
    }

    @Test
    void failedShotFallsBackToLatestFailedDramaforgeJob() {
        var shot = shot(DramaForgeShotStatus.FAILED, null);
        var failedJob = new DramaForgeJob();
        failedJob.setErrorMessage("上游生成失败");
        when(jobRepository.findFirstByTargetIdAndStatusOrderByCreatedAtDesc(
                shot.getId(), DramaForgeJobStatus.FAILED))
                .thenReturn(Optional.of(failedJob));

        assertEquals("上游生成失败", calculator.resolveErrorMessage(shot));
    }

    @Test
    void pendingShotWithoutEntityErrorDoesNotUseHistoricalJob() {
        var shot = shot(DramaForgeShotStatus.PENDING, null);
        assertNull(calculator.resolveErrorMessage(shot));
        verify(jobRepository, never()).findFirstByTargetIdAndStatusOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private static DramaForgeShot shot(DramaForgeShotStatus status, String errorMessage) {
        var shot = new DramaForgeShot();
        shot.setId(UUID.randomUUID());
        shot.setStatus(status);
        shot.setErrorMessage(errorMessage);
        return shot;
    }
}
