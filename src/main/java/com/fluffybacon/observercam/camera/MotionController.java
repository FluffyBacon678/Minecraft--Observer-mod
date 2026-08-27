package com.fluffybacon.observercam.camera;

import com.fluffybacon.observercam.config.ObserverCamConfig;
import com.fluffybacon.observercam.entity.ObserverCameraEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

public final class MotionController {
    private Vec3 acceleration = Vec3.ZERO;
    private double yawVelocity;
    private double pitchVelocity;

    public boolean tick(ObserverCameraEntity observer, Vec3 destination, Vec3 focus, ObserverCamConfig config) {
        Vec3 error = destination.subtract(observer.position());
        if (error.length() > config.emergencyTeleportDistance) {
            observer.snapTo(destination);
            reset(observer);
            aim(observer, focus, config, true);
            return true;
        }

        MotionMath.MotionStep step = MotionMath.nextMotion(observer.getDeltaMovement(), acceleration, error,
                config.positionSmoothing, config.acceleration, config.maximumSpeed);
        Vec3 velocity = step.velocity();
        acceleration = step.acceleration();
        Vec3 before = observer.position();
        observer.move(MoverType.SELF, velocity);
        Vec3 actual = observer.position().subtract(before);
        observer.setDeltaMovement(actual);
        if (actual.distanceToSqr(velocity) > 0.0025) {
            acceleration = acceleration.scale(0.35);
        }
        aim(observer, focus, config, false);
        return false;
    }

    public void reset(ObserverCameraEntity observer) {
        acceleration = Vec3.ZERO;
        yawVelocity = 0.0;
        pitchVelocity = 0.0;
        observer.setDeltaMovement(Vec3.ZERO);
    }

    private void aim(ObserverCameraEntity observer, Vec3 focus, ObserverCamConfig config, boolean snap) {
        CameraTransform.Rotation target = CameraTransform.lookAt(observer.position().add(0.0, 0.5, 0.0), focus);
        if (snap) {
            observer.setYRot(target.yaw());
            observer.setXRot(target.pitch());
            yawVelocity = 0.0;
            pitchVelocity = 0.0;
            return;
        }
        float yawDelta = Mth.wrapDegrees(target.yaw() - observer.getYRot());
        float pitchDelta = Mth.wrapDegrees(target.pitch() - observer.getXRot());
        double angularAcceleration = Math.max(0.35, config.rotationSpeed * 0.24);
        yawVelocity = MotionMath.nextAngularVelocity(yawVelocity, yawDelta,
                config.rotationSmoothing, config.rotationSpeed, angularAcceleration);
        pitchVelocity = MotionMath.nextAngularVelocity(pitchVelocity, pitchDelta,
                config.rotationSmoothing, config.rotationSpeed, angularAcceleration);
        observer.setYRot(observer.getYRot() + (float) yawVelocity);
        observer.setXRot(observer.getXRot() + (float) pitchVelocity);
    }
}
