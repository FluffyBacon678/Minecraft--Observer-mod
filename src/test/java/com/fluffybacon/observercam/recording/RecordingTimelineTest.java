package com.fluffybacon.observercam.recording;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecordingTimelineTest {
    @Test
    void realTimeMapsToFixedRateFrameSlots() {
        assertEquals(1L, RecordingTimeline.expectedFrames(0L, 30));
        assertEquals(16L, RecordingTimeline.expectedFrames(500_000_000L, 30));
        assertEquals(31L, RecordingTimeline.expectedFrames(1_000_000_000L, 30));
    }

    @Test
    void briefMissesDuplicateFramesButLongStallsRemainBounded() {
        assertEquals(1, RecordingTimeline.copiesForCapture(10L, 9L));
        assertEquals(3, RecordingTimeline.copiesForCapture(12L, 9L));
        assertEquals(RecordingTimeline.MAX_CATCH_UP_FRAMES,
                RecordingTimeline.copiesForCapture(100L, 9L));
    }
}
