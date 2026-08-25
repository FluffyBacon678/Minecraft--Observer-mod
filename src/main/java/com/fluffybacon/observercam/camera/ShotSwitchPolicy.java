package com.fluffybacon.observercam.camera;

public final class ShotSwitchPolicy {
    private ShotSwitchPolicy() {
    }

    public static boolean shouldSwitch(double incumbentScore, double challengerScore, int ticksInShot,
                                       int minimumShotTicks, double switchThreshold, boolean forced) {
        return forced || ticksInShot >= minimumShotTicks && challengerScore > incumbentScore + switchThreshold;
    }
}
