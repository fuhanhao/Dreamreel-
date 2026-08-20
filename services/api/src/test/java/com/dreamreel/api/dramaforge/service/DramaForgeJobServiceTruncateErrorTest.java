package com.dreamreel.api.dramaforge.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DramaForgeJobServiceTruncateErrorTest {

    @Test
    void truncateErrorMessage_usesFallbackWhenBlank() {
        assertEquals("任务执行失败", DramaForgeJobService.truncateErrorMessage(null));
        assertEquals("任务执行失败", DramaForgeJobService.truncateErrorMessage("  "));
    }

    @Test
    void truncateErrorMessage_keepsShortMessage() {
        assertEquals("镜头 1 分镜失败", DramaForgeJobService.truncateErrorMessage("镜头 1 分镜失败"));
    }

    @Test
    void truncateErrorMessage_capsAt2000() {
        var longMsg = "x".repeat(2500);
        var truncated = DramaForgeJobService.truncateErrorMessage(longMsg);
        assertEquals(2000, truncated.length());
        assertTrue(truncated.startsWith("xxx"));
    }
}
