package com.fluffybacon.observercam.client;

import com.fluffybacon.observercam.camera.CameraSmoothingMath;
import com.fluffybacon.observercam.camera.CameraTransform;
import com.fluffybacon.observercam.config.ObserverCamConfig;
import com.fluffybacon.observercam.entity.ObserverCameraEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Applies a render-rate low-pass filter after normal entity interpolation.
 * The server remains authoritative; this only removes the visible 20 TPS
 * stepping from the camera transform while the player is in Observer POV.
 */
public final class ObserverCameraSmoother {
    private static final double MAXIMUM_FRAME_SECONDS = 0.1;
    private static final double RESET_AFTER_SECONDS = 0.5;

    private static UUID activeObserver;
    private static Vec3 smoothedBase;
    private static float smoothedYaw;
    private static float smoothedPitch;
    private static long previousFrameNanos;

    private ObserverCameraSmoother() {
    }

    public static CameraTransform sample(ObserverCameraEntity observer, float partialTick) {
        CameraTransform raw = CameraTransform.from(observer, partialTick);
        Vec3 rawBase = raw.origin().subtract(raw.forward().scale(CameraTransform.FACE_OFFSET));
        long now = System.nanoTime();
        double elapsed = previousFrameNanos == 0L ? 0.0 : (now - previousFrameNanos) / 1_000_000_000.0;
        double resetDistance = Math.min(16.0, Math.max(6.0, ObserverCamConfig.get().catchUpDistance));

        if (!observer.getUUID().equals(activeObserver)
                || smoothedBase == null
                || elapsed <= 0.0
                || elapsed > RESET_AFTER_SECONDS
                || smoothedBase.distanceTo(rawBase) > resetDistance) {
            activeObserver = observer.getUUID();
            smoothedBase = rawBase;
            smoothedYaw = raw.yaw();
            smoothedPitch = raw.pitch();
            previousFrameNanos = now;
            return raw;
        }

        double frameSeconds = Math.min(elapsed, MAXIMUM_FRAME_SECONDS);
        ObserverCamConfig config = ObserverCamConfig.get();
        double positionTimeConstant = 0.045 + (1.0 - config.positionSmoothing) * 0.10;
        double rotationTimeConstant = 0.035 + (1.0 - config.rotationSmoothing) * 0.085;

        double positionError = smoothedBase.distanceTo(rawBase);
        double catchUp = Mth.clamp(positionError / 2.5, 0.0, 1.0);
        positionTimeConstant *= 1.0 - catchUp * 0.65;

        double positionBlend = CameraSmoothingMath.exponentialBlend(frameSeconds, positionTimeConstant);
        double rotationBlend = CameraSmoothingMath.exponentialBlend(frameSeconds, rotationTimeConstant);
        smoothedBase = smoothedBase.lerp(rawBase, positionBlend);
        smoothedYaw = CameraSmoothingMath.smoothAngle(smoothedYaw, raw.yaw(), rotationBlend);
        smoothedPitch = (float) Mth.lerp(rotationBlend, smoothedPitch, raw.pitch());
        previousFrameNanos = now;

        Vec3 forward = CameraTransform.forward(smoothedYaw, smoothedPitch);
        Vec3 origin = smoothedBase.add(forward.scale(CameraTransform.FACE_OFFSET));
        return new CameraTransform(origin, forward, smoothedYaw, smoothedPitch);
    }

    public static void reset() {
        activeObserver = null;
        smoothedBase = null;
        previousFrameNanos = 0L;
    }

}
