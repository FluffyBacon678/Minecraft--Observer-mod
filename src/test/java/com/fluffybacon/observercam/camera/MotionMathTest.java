package com.fluffybacon.observercam.camera;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MotionMathTest {
    @Test
    void predictionIsConservativeLinearExtrapolation() {
        Vec3 predicted = MotionMath.predict(new Vec3(2, 3, 4), new Vec3(0.2, 0, -0.1), 5);
        assertEquals(3.0, predicted.x, 1.0E-9);
        assertEquals(3.5, predicted.z, 1.0E-9);
    }

    @Test
    void smoothingRespectsAccelerationAndSpeedCaps() {
        Vec3 first = MotionMath.nextVelocity(Vec3.ZERO, new Vec3(100, 0, 0), 0.2, 0.1, 0.65);
        assertEquals(0.1, first.length(), 1.0E-9);
        Vec3 fast = MotionMath.nextVelocity(new Vec3(1, 0, 0), new Vec3(100, 0, 0), 0.2, 0.5, 0.65);
        assertTrue(fast.length() <= 0.65 + 1.0E-9);
    }
}
