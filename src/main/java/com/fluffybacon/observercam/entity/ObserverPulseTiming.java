package com.fluffybacon.observercam.entity;

/** Pure timing policy for the vanilla one-redstone-tick activation flash. */
final class ObserverPulseTiming {
    static final int DURATION_GAME_TICKS = 2;

    private ObserverPulseTiming() {
    }

    static int start() {
        return DURATION_GAME_TICKS;
    }

    static int advance(int remainingTicks) {
        return Math.max(0, remainingTicks - 1);
    }

    static boolean isPowered(int remainingTicks) {
        return remainingTicks > 0;
    }
}
