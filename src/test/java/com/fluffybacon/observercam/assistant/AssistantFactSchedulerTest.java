package com.fluffybacon.observercam.assistant;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssistantFactSchedulerTest {
    @Test
    void waitsForTheWholeIntervalAndRepeatsAtThatInterval() {
        AssistantFactScheduler scheduler = new AssistantFactScheduler();
        long start = TimeUnit.MINUTES.toNanos(10L);

        assertFalse(scheduler.shouldSpeak(start, true, 2.0));
        assertFalse(scheduler.shouldSpeak(start + TimeUnit.MINUTES.toNanos(2L) - 1L, true, 2.0));
        assertTrue(scheduler.shouldSpeak(start + TimeUnit.MINUTES.toNanos(2L), true, 2.0));
        assertFalse(scheduler.shouldSpeak(start + TimeUnit.MINUTES.toNanos(3L), true, 2.0));
        assertTrue(scheduler.shouldSpeak(start + TimeUnit.MINUTES.toNanos(4L), true, 2.0));
    }

    @Test
    void disablingResetsTheCountdownInsteadOfQueuingAMessage() {
        AssistantFactScheduler scheduler = new AssistantFactScheduler();
        long minute = TimeUnit.MINUTES.toNanos(1L);

        assertFalse(scheduler.shouldSpeak(0L, true, 1.0));
        assertFalse(scheduler.shouldSpeak(minute, false, 1.0));
        assertFalse(scheduler.shouldSpeak(minute, true, 1.0));
        assertTrue(scheduler.shouldSpeak(minute * 2L, true, 1.0));
    }

    @Test
    void invalidAndOutOfRangeIntervalsAreSanitized() {
        assertEquals(AssistantFactScheduler.DEFAULT_INTERVAL_MINUTES,
                AssistantFactScheduler.sanitizeMinutes(Double.NaN));
        assertEquals(AssistantFactScheduler.MINIMUM_INTERVAL_MINUTES,
                AssistantFactScheduler.sanitizeMinutes(-20.0));
        assertEquals(AssistantFactScheduler.MAXIMUM_INTERVAL_MINUTES,
                AssistantFactScheduler.sanitizeMinutes(20.0));
    }
}
