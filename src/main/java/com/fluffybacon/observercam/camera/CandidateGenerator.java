package com.fluffybacon.observercam.camera;

import com.fluffybacon.observercam.config.ObserverCamConfig;
import com.fluffybacon.observercam.entity.ObserverCameraEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class CandidateGenerator {
    private static final double[] ANGLES = {-145.0, -90.0, -45.0, 0.0, 45.0, 90.0, 145.0, 180.0};

    private CandidateGenerator() {
    }

    public static CameraPlan plan(ObserverCameraEntity observer, Entity target, int previousAngle, ObserverCamConfig config) {
        Level level = observer.level();
        double indoor = EnvironmentProbe.indoorFactor(level, target);
        double desiredDistance = lerp(config.outdoorDistance, config.indoorDistance, indoor);
        desiredDistance = Math.max(config.minimumDistance, Math.min(config.maximumDistance, desiredDistance));

        Vec3 velocity = target.getDeltaMovement();
        Vec3 horizontal = new Vec3(velocity.x, 0.0, velocity.z);
        if (horizontal.lengthSqr() < 0.0025) {
            Vec3 look = target.getLookAngle();
            horizontal = new Vec3(look.x, 0.0, look.z);
        }
        Vec3 forward = horizontal.lengthSqr() < 1.0E-5 ? new Vec3(0.0, 0.0, 1.0) : horizontal.normalize();
        Vec3 predicted = MotionMath.predict(target.position(), velocity, config.movementPredictionTicks);
        Vec3 focus = predicted.add(0.0, target.getBbHeight() * 0.72, 0.0);
        List<CameraCandidate> candidates = new ArrayList<>(18);

        for (int index = 0; index < ANGLES.length; index++) {
            double radians = Math.toRadians(ANGLES[index]);
            Vec3 radial = rotateY(forward, radians);
            addCandidate(candidates, observer, target, focus, radial, desiredDistance, config.cameraHeight, desiredDistance, index, previousAngle, ANGLES[index] == 0.0, config);
            addCandidate(candidates, observer, target, focus, radial, desiredDistance * 1.18, config.cameraHeight + 0.75, desiredDistance, index, previousAngle, ANGLES[index] == 0.0, config);
        }
        addCandidate(candidates, observer, target, focus, rotateY(forward, Math.toRadians(-65.0)), desiredDistance * 0.82, config.cameraHeight - 0.45, desiredDistance, 8, previousAngle, false, config);
        addCandidate(candidates, observer, target, focus, rotateY(forward, Math.toRadians(65.0)), desiredDistance * 0.82, config.cameraHeight - 0.45, desiredDistance, 9, previousAngle, false, config);

        CameraCandidate best = candidates.stream()
                .filter(CameraCandidate::valid)
                .max(Comparator.comparingDouble(CameraCandidate::score))
                .orElse(null);
        return new CameraPlan(List.copyOf(candidates), best, indoor, desiredDistance);
    }

    private static void addCandidate(
            List<CameraCandidate> candidates,
            ObserverCameraEntity observer,
            Entity target,
            Vec3 focus,
            Vec3 radial,
            double distance,
            double cameraHeight,
            double desiredDistance,
            int angleIndex,
            int previousAngle,
            boolean frontFacing,
            ObserverCamConfig config
    ) {
        Vec3 center = new Vec3(focus.x, target.getY() + cameraHeight, focus.z).add(radial.scale(distance));
        Vec3 position = center.add(0.0, -0.5, 0.0);
        Level level = observer.level();
        boolean loaded = level.hasChunkAt(BlockPos.containing(center));
        AABB box = new AABB(center.x - 0.48, center.y - 0.48, center.z - 0.48, center.x + 0.48, center.y + 0.48, center.z + 0.48);
        boolean clear = loaded && level.noCollision(observer, box);
        Vec3 direction = focus.subtract(center).normalize();
        Vec3 origin = center.add(direction.scale(CameraTransform.FACE_OFFSET));
        boolean pathClear = clear && pathClear(level, observer, observer.position().add(0.0, 0.5, 0.0), center);
        int visibility = clear ? VisibilityProbe.visibleSamples(level, observer, origin, target) : 0;
        double background = visibility > 0 ? EnvironmentProbe.backgroundDepth(level, observer, origin, focus) : 0.0;
        double actualDistance = origin.distanceTo(focus);
        double framing = ShotScorer.framingScore(actualDistance, config.cameraFov, config.preferredPlayerScreenSize, target.getBbHeight());
        double score = ShotScorer.score(clear, pathClear, visibility, actualDistance, desiredDistance, framing, background,
                angleIndex, previousAngle, frontFacing, config);
        candidates.add(new CameraCandidate(position, focus, angleIndex, clear, pathClear, visibility, background, framing, score));
    }

    private static boolean pathClear(Level level, Entity context, Vec3 start, Vec3 end) {
        if (start.distanceToSqr(end) < 0.25) {
            return true;
        }
        HitResult hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, context));
        return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(end) < 0.3;
    }

    private static Vec3 rotateY(Vec3 vector, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return new Vec3(vector.x * cos + vector.z * sin, 0.0, vector.z * cos - vector.x * sin);
    }

    private static double lerp(double start, double end, double amount) {
        return start + (end - start) * amount;
    }

    public record CameraPlan(List<CameraCandidate> candidates, CameraCandidate best, double indoorFactor, double desiredDistance) {
    }
}
