package com.fluffybacon.observercam.camera;

import com.fluffybacon.observercam.config.ObserverCamConfig;
import com.fluffybacon.observercam.entity.ObserverCameraEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

public final class MotionController {
    private MotionController() {
    }

    public static boolean tick(ObserverCameraEntity observer, Vec3 destination, Vec3 focus, ObserverCamConfig config) {
        Vec3 error = destination.subtract(observer.position());
        if (error.length() > config.emergencyTeleportDistance) {
            observer.snapTo(destination);
            observer.setDeltaMovement(Vec3.ZERO);
            aim(observer, focus, config, true);
            return true;
        }

        Vec3 velocity = MotionMath.nextVelocity(observer.getDeltaMovement(), error, config.positionSmoothing,
                config.acceleration, config.maximumSpeed);
        Vec3 before = observer.position();
        observer.move(MoverType.SELF, velocity);
        Vec3 actual = observer.position().subtract(before);
        observer.setDeltaMovement(actual.scale(0.82));
        aim(observer, focus, config, false);
        return false;
    }

    private static void aim(ObserverCameraEntity observer, Vec3 focus, ObserverCamConfig config, boolean snap) {
        CameraTransform.Rotation target = CameraTransform.lookAt(observer.position().add(0.0, 0.5, 0.0), focus);
        if (snap) {
            observer.setYRot(target.yaw());
            observer.setXRot(target.pitch());
            return;
        }
        float yawDelta = Mth.wrapDegrees(target.yaw() - observer.getYRot());
        float pitchDelta = Mth.wrapDegrees(target.pitch() - observer.getXRot());
        float yawStep = (float) Mth.clamp(yawDelta * config.rotationSmoothing, -config.rotationSpeed, config.rotationSpeed);
        float pitchStep = (float) Mth.clamp(pitchDelta * config.rotationSmoothing, -config.rotationSpeed, config.rotationSpeed);
        observer.setYRot(observer.getYRot() + yawStep);
        observer.setXRot(observer.getXRot() + pitchStep);
    }
}
