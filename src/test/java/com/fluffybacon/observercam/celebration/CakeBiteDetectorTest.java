package com.fluffybacon.observercam.celebration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CakeBiteDetectorTest {
    @Test
    void detectsAConsumedSliceAndTheFinalRemovedSlice() {
        assertTrue(CakeBiteDetector.wasSliceEaten(0, true, 1));
        assertTrue(CakeBiteDetector.wasSliceEaten(5, true, 6));
        assertTrue(CakeBiteDetector.wasSliceEaten(6, false, -1));
    }

    @Test
    void rejectsFailedClicksAndUnrelatedBlockChanges() {
        assertFalse(CakeBiteDetector.wasSliceEaten(0, true, 0));
        assertFalse(CakeBiteDetector.wasSliceEaten(0, false, -1));
        assertFalse(CakeBiteDetector.wasSliceEaten(5, true, 2));
        assertFalse(CakeBiteDetector.wasSliceEaten(7, false, -1));
    }
}
