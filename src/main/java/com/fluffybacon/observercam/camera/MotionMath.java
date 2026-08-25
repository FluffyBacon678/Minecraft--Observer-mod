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

    private static Vec3 clamp(Vec3 vector, double maximum) {
        double length = vector.length();
        return length > maximum && length > 0.0 ? vector.scale(maximum / length) : vector;
    }
}
