package com.fluffybacon.observercam.assistant;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssistantCaptionTimerTest {
    private static final long SECOND = TimeUnit.SECONDS.toNanos(1L);

    @Test
    void expiresAfterActiveDuration() {
        AssistantCaptionTimer timer = new AssistantCaptionTimer();
        timer.start(10L * SECOND, 8L * SECOND, true);

        assertTrue(timer.isActive(17L * SECOND, true));
        assertFalse(timer.isActive(18L * SECOND, true));
    }

    @Test
    void menuTimeDoesNotConsumeCaption() {
        AssistantCaptionTimer timer = new AssistantCaptionTimer();
        timer.start(10L * SECOND, 8L * SECOND, true);

        assertTrue(timer.isActive(12L * SECOND, false));
        assertTrue(timer.isActive(42L * SECOND, false));
        assertTrue(timer.isActive(47L * SECOND, true));
        assertFalse(timer.isActive(53L * SECOND, true));
    }

    @Test
    void previewStartedInMenuGetsFullDurationInWorld() {
        AssistantCaptionTimer timer = new AssistantCaptionTimer();
        timer.start(10L * SECOND, 8L * SECOND, false);

        assertTrue(timer.isActive(40L * SECOND, false));
        assertTrue(timer.isActive(47L * SECOND, true));
        assertTrue(timer.isActive(54L * SECOND, true));
        assertFalse(timer.isActive(55L * SECOND, true));
    }

    @Test
    void resetClearsCaption() {
        AssistantCaptionTimer timer = new AssistantCaptionTimer();
        timer.start(10L * SECOND, 8L * SECOND, true);
        timer.reset();

        assertFalse(timer.isActive(11L * SECOND, true));
    }
}
