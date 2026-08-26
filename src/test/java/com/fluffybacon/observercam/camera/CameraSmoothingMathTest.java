package com.fluffybacon.observercam.camera;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraSmoothingMathTest {
    @Test
    void exponentialBlendIsFrameRateIndependentOverEqualTime() {
        double oneStep = CameraSmoothingMath.exponentialBlend(1.0 / 30.0, 0.12);
        double halfStep = CameraSmoothingMath.exponentialBlend(1.0 / 60.0, 0.12);
        double twoHalfSteps = halfStep + (1.0 - halfStep) * halfStep;

        assertEquals(oneStep, twoHalfSteps, 1.0E-12);
        assertTrue(oneStep > 0.0 && oneStep < 1.0);
    }

    @Test
    void angleSmoothingTakesTheShortPathAcrossWrapBoundary() {
        float smoothed = CameraSmoothingMath.smoothAngle(179.0F, -179.0F, 0.5);

        assertEquals(180.0F, smoothed, 1.0E-5F);
    }
}
