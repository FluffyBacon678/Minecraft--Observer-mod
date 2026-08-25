package com.fluffybacon.observercam.camera;

import com.fluffybacon.observercam.entity.ObserverCameraEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public record CameraTransform(Vec3 origin, Vec3 forward, float yaw, float pitch) {
    public static final double FACE_OFFSET = 0.515;

    public static CameraTransform from(ObserverCameraEntity observer, float partialTick) {
        Vec3 base = new Vec3(
                Mth.lerp(partialTick, observer.xo, observer.getX()),
                Mth.lerp(partialTick, observer.yo, observer.getY()) + 0.5,
                Mth.lerp(partialTick, observer.zo, observer.getZ())
        );
        float yaw = observer.getYRot(partialTick);
        float pitch = observer.getXRot(partialTick);
        Vec3 forward = forward(yaw, pitch);
        return new CameraTransform(base.add(forward.scale(FACE_OFFSET)), forward, yaw, pitch);
    }

    public static Vec3 forward(float yaw, float pitch) {
        double yawRadians = Math.toRadians(yaw);
        double pitchRadians = Math.toRadians(pitch);
        double cosPitch = Math.cos(pitchRadians);
        return new Vec3(
                -Math.sin(yawRadians) * cosPitch,
                -Math.sin(pitchRadians),
                Math.cos(yawRadians) * cosPitch
        );
    }

    public static Rotation lookAt(Vec3 origin, Vec3 target) {
        Vec3 delta = target.subtract(origin);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
        float pitch = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-delta.y, horizontal)));
        return new Rotation(yaw, pitch);
    }

    public record Rotation(float yaw, float pitch) {
    }
}
