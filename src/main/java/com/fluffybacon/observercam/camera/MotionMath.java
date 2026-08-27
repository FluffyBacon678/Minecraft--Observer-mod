package com.fluffybacon.observercam.camera;

import net.minecraft.world.phys.Vec3;

public final class MotionMath {
    private MotionMath() {
    }

    public static Vec3 predict(Vec3 position, Vec3 velocity, double ticks) {
        return position.add(velocity.scale(ticks));
    }

    public static Vec3 nextVelocity(Vec3 currentVelocity, Vec3 positionError, double smoothing,
                                    double acceleration, double maximumSpeed) {
        Vec3 desiredVelocity = clamp(positionError.scale(smoothing), maximumSpeed);
        Vec3 velocityChange = clamp(desiredVelocity.subtract(currentVelocity), acceleration);
        return clamp(currentVelocity.add(velocityChange), maximumSpeed);
    }

    /**
     * Advances velocity while also limiting how quickly acceleration itself may change.
     * The extra derivative limit removes the small start/stop kicks which are especially
     * noticeable when the entity is being used as the player's active camera.
     */
    public static MotionStep nextMotion(Vec3 currentVelocity, Vec3 currentAcceleration, Vec3 positionError,
                                        double smoothing, double maximumAcceleration, double maximumSpeed) {
        if (positionError.lengthSqr() < 0.0009 && currentVelocity.lengthSqr() < 0.000225) {
            return new MotionStep(Vec3.ZERO, Vec3.ZERO);
        }

        Vec3 desiredVelocity = clamp(positionError.scale(smoothing), maximumSpeed);
        Vec3 desiredAcceleration = clamp(desiredVelocity.subtract(currentVelocity), maximumAcceleration);
        double maximumJerk = Math.max(0.004, maximumAcceleration * 0.35);
        Vec3 accelerationChange = clamp(desiredAcceleration.subtract(currentAcceleration), maximumJerk);
        Vec3 nextAcceleration = clamp(currentAcceleration.add(accelerationChange), maximumAcceleration);
        Vec3 nextVelocity = clamp(currentVelocity.add(nextAcceleration), maximumSpeed);
        return new MotionStep(nextVelocity, nextAcceleration);
    }

    public static double approach(double current, double target, double maximumChange) {
        return current + Math.max(-maximumChange, Math.min(maximumChange, target - current));
    }

    private static Vec3 clamp(Vec3 vector, double maximum) {
        double length = vector.length();
        return length > maximum && length > 0.0 ? vector.scale(maximum / length) : vector;
    }

    public record MotionStep(Vec3 velocity, Vec3 acceleration) {
    }
}
