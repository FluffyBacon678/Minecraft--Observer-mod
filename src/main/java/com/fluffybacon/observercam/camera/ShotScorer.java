package com.fluffybacon.observercam.camera;

import com.fluffybacon.observercam.config.ObserverCamConfig;

public final class ShotScorer {
    private ShotScorer() {
    }

    public static double framingScore(double distance, double fovDegrees, double preferredScreenSize, double subjectHeight) {
        double projected = subjectHeight / (2.0 * Math.max(distance, 0.1) * Math.tan(Math.toRadians(fovDegrees) * 0.5));
        double tolerance = Math.max(0.12, preferredScreenSize * 0.8);
        return clamp01(1.0 - Math.abs(projected - preferredScreenSize) / tolerance);
    }

    public static double score(
            boolean clear,
            boolean pathClear,
            int visibleSamples,
            double distance,
            double desiredDistance,
            double framing,
            double backgroundDepth,
            int angleIndex,
            int previousAngle,
            boolean frontFacing,
            ObserverCamConfig config
    ) {
        if (!clear || visibleSamples == 0) {
            return -1000.0;
        }
        double visibility = visibleSamples / 4.0;
        double distanceScore = distanceScore(distance, desiredDistance);
        double stability = previousAngle < 0 ? 0.5 : angleIndex == previousAngle ? 1.0 : 0.0;
        double weighted = visibility * 5.5 * config.playerVisibilityImportance
                + distanceScore * 1.8
                + framing * 2.0
                + backgroundDepth * 2.2 * config.backgroundImportance
                + stability * 1.4 * config.shotStability
                + (pathClear ? 0.7 : 0.0);
        double total = 5.5 * config.playerVisibilityImportance
                + 1.8 + 2.0 + 2.2 * config.backgroundImportance
                + 1.4 * config.shotStability + 0.7;
        double result = 100.0 * weighted / total;
        if (frontFacing && !config.allowFrontFacingShots) {
            result -= 24.0;
        }
        return result;
    }

    public static double distanceScore(double distance, double desiredDistance) {
        return clamp01(1.0 - Math.abs(distance - desiredDistance) / Math.max(desiredDistance, 1.0));
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
