package com.dreamreel.api.dramaforge.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DramaForgeShotNeedsVideoSyncTest {

    @Test
    void pendingWithVideoJobNeedsSync() {
        var shot = shot(DramaForgeShotStatus.PENDING, UUID.randomUUID());
        assertTrue(shot.needsVideoSync());
    }

    @Test
    void videoDoneDoesNotNeedSync() {
        var shot = shot(DramaForgeShotStatus.VIDEO_DONE, UUID.randomUUID());
        assertFalse(shot.needsVideoSync());
    }

    @Test
    void failedDoesNotNeedSync() {
        var shot = shot(DramaForgeShotStatus.FAILED, UUID.randomUUID());
        assertFalse(shot.needsVideoSync());
    }

    @Test
    void noVideoJobDoesNotNeedSync() {
        var shot = shot(DramaForgeShotStatus.PENDING, null);
        assertFalse(shot.needsVideoSync());
    }

    private static DramaForgeShot shot(DramaForgeShotStatus status, UUID videoJobId) {
        var shot = new DramaForgeShot();
        shot.setStatus(status);
        shot.setVideoJobId(videoJobId);
        return shot;
    }
}
