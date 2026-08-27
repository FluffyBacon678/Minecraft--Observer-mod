package com.fluffybacon.observercam.recording;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReplayBufferPolicyTest {
    @Test
    void timeLimitRetainsRequestedHistoryAndNeverDeletesActiveSegment() {
        List<Long> segments = java.util.Collections.nCopies(20, 1_000L);

        assertEquals(5, ReplayBufferPolicy.segmentsToDelete(
                InstantReplayLimitMode.TIME, 0.5, 99_999L, 2, segments));
        assertEquals(0, ReplayBufferPolicy.segmentsToDelete(
                InstantReplayLimitMode.TIME, 0.5, 99_999L, 2, List.of(1_000L)));
    }

    @Test
    void sizeLimitEvictsOldestCompleteSegments() {
        assertEquals(1, ReplayBufferPolicy.segmentsToDelete(
                InstantReplayLimitMode.SIZE, 2.0, 700L, 2,
                List.of(300L, 300L, 300L)));
        assertEquals(0, ReplayBufferPolicy.segmentsToDelete(
                InstantReplayLimitMode.SIZE, 2.0, 1_000L, 2,
                List.of(300L, 300L, 300L)));
    }

    @Test
    void globalHeadroomDeletionNeverSelectsActiveSegment() {
        assertEquals(2, ReplayBufferPolicy.completedSegmentsToFree(
                List.of(100L, 200L, 500L), 250L));
        assertEquals(1, ReplayBufferPolicy.completedSegmentsToFree(
                List.of(100L, 500L), 1_000L));
        assertEquals(0, ReplayBufferPolicy.completedSegmentsToFree(List.of(500L), 1L));
    }

    @Test
    void rejectsInvalidSizes() {
        assertThrows(IllegalArgumentException.class, () -> ReplayBufferPolicy.segmentsToDelete(
                InstantReplayLimitMode.SIZE, 1.0, 1_000L, 2, List.of(100L, -1L)));
    }
}
