package com.fluffybacon.observercam.camera;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LineOfSightRecoveryTrackerTest {
    @Test
    void briefObstructionsDoNotTriggerAReposition() {
        LineOfSightRecoveryTracker tracker = new LineOfSightRecoveryTracker();

        for (int ticks = 0; ticks < LineOfSightRecoveryTracker.RECOVERY_DELAY_TICKS - 4; ticks += 4) {
            assertFalse(tracker.sample(0, 4));
        }

        assertEquals(LineOfSightRecoveryTracker.RECOVERY_DELAY_TICKS - 4, tracker.obscuredTicks());
    }

    @Test
    void persistentLossTriggersAfterTwoSecondsAndVisibilityResetsIt() {
        LineOfSightRecoveryTracker tracker = new LineOfSightRecoveryTracker();

        for (int ticks = 0; ticks < LineOfSightRecoveryTracker.RECOVERY_DELAY_TICKS - 4; ticks += 4) {
            tracker.sample(0, 4);
        }
        assertTrue(tracker.sample(0, 4));
        assertFalse(tracker.sample(1, 4));
        assertEquals(0, tracker.obscuredTicks());
    }
}
