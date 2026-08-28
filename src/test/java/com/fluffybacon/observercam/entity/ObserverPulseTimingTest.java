package com.fluffybacon.observercam.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ObserverPulseTimingTest {
    @Test
    void poweredStateLastsExactlyOneRedstoneTick() {
        int remaining = ObserverPulseTiming.activate(0);

        assertEquals(2, remaining);
        assertTrue(ObserverPulseTiming.isPowered(remaining));
        remaining = ObserverPulseTiming.advance(remaining);
        assertTrue(ObserverPulseTiming.isPowered(remaining));
        remaining = ObserverPulseTiming.advance(remaining);
        assertFalse(ObserverPulseTiming.isPowered(remaining));
        assertEquals(0, remaining);
    }

    @Test
    void timingNeverBecomesNegative() {
        assertEquals(0, ObserverPulseTiming.advance(0));
        assertFalse(ObserverPulseTiming.isPowered(-1));
    }

    @Test
    void overlappingSignalsDoNotExtendAnActivePulse() {
        assertEquals(1, ObserverPulseTiming.activate(1));
        assertEquals(2, ObserverPulseTiming.activate(0));
    }
}
