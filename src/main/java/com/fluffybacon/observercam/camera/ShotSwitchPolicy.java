package com.fluffybacon.observercam.camera;

public final class ShotSwitchPolicy {
    private ShotSwitchPolicy() {
    }

    public static boolean shouldSwitch(double incumbentScore, double challengerScore, int ticksInShot,
                                       int minimumShotTicks, double switchThreshold, boolean forced) {
        return shouldSwitch(incumbentScore, challengerScore, ticksInShot, minimumShotTicks,
                Integer.MAX_VALUE, switchThreshold, 0.0, forced);
    }

    public static boolean shouldSwitch(double incumbentScore, double challengerScore, int ticksInShot,
                                       int minimumShotTicks, int maximumShotTicks, double switchThreshold,
                                       double periodicTolerance, boolean forced) {
        if (forced) {
            return true;
        }
        if (ticksInShot < minimumShotTicks) {
            return false;
        }
        if (challengerScore > incumbentScore + switchThreshold) {
            return true;
        }
        return ticksInShot >= maximumShotTicks && challengerScore >= incumbentScore - periodicTolerance;
    }
}
