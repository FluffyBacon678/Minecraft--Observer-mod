package com.fluffybacon.observercam.camera;

/** Delays disruptive camera recovery until a real obstruction persists. */
final class LineOfSightRecoveryTracker {
    static final int RECOVERY_DELAY_TICKS = 40;

    private int obscuredTicks;

    boolean sample(int visibleSamples, int elapsedTicks) {
        if (visibleSamples > 0) {
            obscuredTicks = 0;
            return false;
        }
        obscuredTicks = Math.min(RECOVERY_DELAY_TICKS,
                obscuredTicks + Math.max(1, elapsedTicks));
        return obscuredTicks >= RECOVERY_DELAY_TICKS;
    }

    void reset() {
        obscuredTicks = 0;
    }

    int obscuredTicks() {
        return obscuredTicks;
    }
}
