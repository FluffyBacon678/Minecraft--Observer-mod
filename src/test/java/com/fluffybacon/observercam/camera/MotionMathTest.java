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

    @Test
    void accelerationChangesGraduallyInsteadOfKickingAtFullStrength() {
        MotionMath.MotionStep first = MotionMath.nextMotion(
                Vec3.ZERO, Vec3.ZERO, new Vec3(100, 0, 0), 0.22, 0.10, 0.65);
        MotionMath.MotionStep second = MotionMath.nextMotion(
                first.velocity(), first.acceleration(), new Vec3(100, 0, 0), 0.22, 0.10, 0.65);

        assertEquals(0.035, first.acceleration().length(), 1.0E-9);
        assertEquals(0.070, second.acceleration().length(), 1.0E-9);
        assertTrue(second.velocity().length() < 0.11);
    }

    @Test
    void settledMotionStopsTinyCameraDrift() {
        MotionMath.MotionStep settled = MotionMath.nextMotion(
                new Vec3(0.005, 0, 0), new Vec3(0.003, 0, 0),
                new Vec3(0.02, 0.0, 0.0), 0.22, 0.10, 0.65);

        assertEquals(Vec3.ZERO, settled.velocity());
        assertEquals(Vec3.ZERO, settled.acceleration());
    }

    @Test
    void angularVelocityUsesAnAccelerationLimit() {
        assertEquals(1.5, MotionMath.approach(0.0, 7.0, 1.5), 1.0E-9);
        assertEquals(-1.5, MotionMath.approach(0.0, -7.0, 1.5), 1.0E-9);
        assertEquals(1.0, MotionMath.approach(0.5, 1.0, 1.5), 1.0E-9);
    }

    @Test
    void angularTrackingBrakesImmediatelyWhenTheSubjectCrossesSides() {
        double next = MotionMath.nextAngularVelocity(7.0, -90.0, 0.28, 7.0, 1.68);
        assertTrue(next < 0.0, "a stale positive turn must not continue away from the subject");
        assertTrue(Math.abs(next) <= 7.0 * 1.55 + 1.0E-9);
    }

    @Test
    void angularTrackingCannotOvershootTheRemainingError() {
        double positive = MotionMath.nextAngularVelocity(2.0, 0.35, 0.28, 7.0, 1.68);
        double negative = MotionMath.nextAngularVelocity(-1.0, -0.2, 0.28, 7.0, 1.68);
        assertTrue(positive >= 0.0 && positive <= 0.35);
        assertTrue(negative <= 0.0 && negative >= -0.2);
    }

    @Test
    void largeAngularErrorsReceiveBoundedCatchUpSpeed() {
        double next = 0.0;
        for (int tick = 0; tick < 10; tick++) {
            next = MotionMath.nextAngularVelocity(next, 120.0, 0.28, 7.0, 1.68);
        }
        assertTrue(next > 7.0);
        assertTrue(next <= 7.0 * 1.55 + 1.0E-9);
    }
}
