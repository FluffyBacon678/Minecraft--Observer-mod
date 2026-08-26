package com.fluffybacon.observercam.camera;

import net.minecraft.util.Mth;

public final class CameraSmoothingMath {
    private CameraSmoothingMath() {
    }

    public static double exponentialBlend(double elapsedSeconds, double timeConstantSeconds) {
        if (elapsedSeconds <= 0.0) {
            return 0.0;
        }
        return 1.0 - Math.exp(-elapsedSeconds / Math.max(0.001, timeConstantSeconds));
    }

    public static float smoothAngle(float current, float target, double blend) {
        return current + Mth.wrapDegrees(target - current) * (float) blend;
    }
}
