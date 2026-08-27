package com.fluffybacon.observercam.recording;

/** Pure frame-pacing helpers shared by the recorder and its unit tests. */
public final class RecordingTimeline {
    public static final int MAX_CATCH_UP_FRAMES = 5;

    private RecordingTimeline() {
    }

    public static long expectedFrames(long elapsedNanos, int framesPerSecond) {
        if (elapsedNanos <= 0L || framesPerSecond <= 0) {
            return 1L;
        }
        return Math.max(1L, elapsedNanos * framesPerSecond / 1_000_000_000L + 1L);
    }

    public static int copiesForCapture(long expectedFrames, long timelineFrames) {
        long missing = Math.max(1L, expectedFrames - timelineFrames);
        return (int) Math.min(MAX_CATCH_UP_FRAMES, missing);
    }
}
