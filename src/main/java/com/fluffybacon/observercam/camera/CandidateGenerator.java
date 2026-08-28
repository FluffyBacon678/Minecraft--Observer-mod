package com.fluffybacon.observercam.camera;

import com.fluffybacon.observercam.config.ObserverCamConfig;
import com.fluffybacon.observercam.entity.ObserverCameraEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class CandidateGenerator {
    private static final double[] ANGLES = {-145.0, -90.0, -45.0, 0.0, 45.0, 90.0, 145.0, 180.0};
    private static final double[] CONFINED_RECOVERY_ANGLES = {0.0, 180.0, -90.0, 90.0, -45.0, 45.0, -135.0, 135.0};
    private static final double[] CONFINED_RECOVERY_DISTANCES = {1.25, 2.1, 3.0};
    private static final double CONFINED_RECOVERY_HEIGHT = 1.35;
    private static final double SECONDARY_SUBJECT_RADIUS = 12.0;
    private static final int MAX_SECONDARY_SUBJECT_CANDIDATES = 10;
    private static final int MAX_SECONDARY_SUBJECTS = 4;
    private static final double PRIMARY_SUBJECT_WEIGHT = 4.0;
    private static final double MAXIMUM_GROUP_SHIFT = 2.25;
    private static final double MAXIMUM_GROUP_VERTICAL_SHIFT = 0.9;
    private static final double PATH_PROBE_OFFSET = 0.42;
    private static final Vec3[] PATH_PROBE_OFFSETS = {
            Vec3.ZERO,
            new Vec3(-PATH_PROBE_OFFSET, -PATH_PROBE_OFFSET, -PATH_PROBE_OFFSET),
            new Vec3(-PATH_PROBE_OFFSET, -PATH_PROBE_OFFSET, PATH_PROBE_OFFSET),
            new Vec3(PATH_PROBE_OFFSET, -PATH_PROBE_OFFSET, -PATH_PROBE_OFFSET),
            new Vec3(PATH_PROBE_OFFSET, -PATH_PROBE_OFFSET, PATH_PROBE_OFFSET),
            new Vec3(-PATH_PROBE_OFFSET, PATH_PROBE_OFFSET, -PATH_PROBE_OFFSET),
            new Vec3(-PATH_PROBE_OFFSET, PATH_PROBE_OFFSET, PATH_PROBE_OFFSET),
            new Vec3(PATH_PROBE_OFFSET, PATH_PROBE_OFFSET, -PATH_PROBE_OFFSET),
            new Vec3(PATH_PROBE_OFFSET, PATH_PROBE_OFFSET, PATH_PROBE_OFFSET)
    };

    private CandidateGenerator() {
    }

    public static CameraPlan plan(ObserverCameraEntity observer, Entity target, int previousAngle, ObserverCamConfig config) {
        return plan(observer, target, previousAngle, config, false);
    }

    public static CameraPlan plan(ObserverCameraEntity observer, Entity target, int previousAngle,
                                  ObserverCamConfig config, boolean forceEmergencyRecovery) {
        SubjectGroup subjects = documentarySubjects(observer.level(), target, config);
        return plan(observer, target, previousAngle, config, forceEmergencyRecovery,
                subjects, targetForward(target));
    }

    static CameraPlan plan(ObserverCameraEntity observer, Entity target, int previousAngle,
                           ObserverCamConfig config, boolean forceEmergencyRecovery,
                           SubjectGroup subjects, Vec3 shotForward) {
        Level level = observer.level();
        double indoor = EnvironmentProbe.indoorFactor(level, target);
        double desiredDistance = lerp(config.outdoorDistance, config.indoorDistance, indoor);
        double ensembleExtent = subjects.secondarySubjects().stream()
                .mapToDouble(subject -> horizontalDistance(subjectPoint(subject), subjects.primaryFocus()))
                .max()
                .orElse(0.0);
        desiredDistance += Math.min(4.0, ensembleExtent * 0.32) * (1.0 - indoor * 0.45);
        desiredDistance = Math.max(config.minimumDistance, Math.min(config.maximumDistance, desiredDistance));

        Vec3 forward = horizontalUnit(shotForward);
        Vec3 focus = subjects.compositionFocus();
        List<CameraCandidate> candidates = new ArrayList<>(18);
        Vec3 currentCenter = observer.position().add(0.0, 0.5, 0.0);
        Vec3 currentOrigin = currentCenter.add(CameraTransform.forward(observer.getYRot(), observer.getXRot())
                .scale(CameraTransform.FACE_OFFSET));
        boolean emergencyRecovery = forceEmergencyRecovery
                || observer.distanceTo(target) > config.emergencyTeleportDistance
                || !level.noCollision(observer, observer.getBoundingBox())
                || !originClear(level, observer, currentOrigin);

        for (int index = 0; index < ANGLES.length; index++) {
            double radians = Math.toRadians(ANGLES[index]);
            Vec3 radial = rotateY(forward, radians);
            addCandidate(candidates, observer, target, subjects, radial, desiredDistance, config.cameraHeight,
                    desiredDistance, index, previousAngle, ANGLES[index] == 0.0, config);
            addCandidate(candidates, observer, target, subjects, radial, desiredDistance * 1.18,
                    config.cameraHeight + 0.75, desiredDistance, index, previousAngle,
                    ANGLES[index] == 0.0, config);
        }
        addCandidate(candidates, observer, target, subjects, rotateY(forward, Math.toRadians(-65.0)),
                desiredDistance * 0.82, config.cameraHeight - 0.45, desiredDistance,
                8, previousAngle, false, config);
        addCandidate(candidates, observer, target, subjects, rotateY(forward, Math.toRadians(65.0)),
                desiredDistance * 0.82, config.cameraHeight - 0.45, desiredDistance,
                9, previousAngle, false, config);
        if (forceEmergencyRecovery) {
            addConfinedRecoveryCandidates(candidates, observer, target, subjects, forward,
                    desiredDistance, previousAngle, config);
        }

        CameraCandidate best = candidates.stream()
                .filter(emergencyRecovery ? CameraCandidate::safeTeleportDestination : CameraCandidate::valid)
                .max(Comparator.comparingDouble(CameraCandidate::score))
                .orElse(null);
        return new CameraPlan(List.copyOf(candidates), best, indoor, desiredDistance, emergencyRecovery);
    }

    private static void addConfinedRecoveryCandidates(
            List<CameraCandidate> candidates,
            ObserverCameraEntity observer,
            Entity target,
            SubjectGroup subjects,
            Vec3 forward,
            double desiredDistance,
            int previousAngle,
            ObserverCamConfig config
    ) {
        double recoveryDesiredDistance = Math.min(2.1, desiredDistance);
        for (int angle = 0; angle < CONFINED_RECOVERY_ANGLES.length; angle++) {
            Vec3 radial = rotateY(forward, Math.toRadians(CONFINED_RECOVERY_ANGLES[angle]));
            for (double configuredDistance : CONFINED_RECOVERY_DISTANCES) {
                double distance = Math.min(configuredDistance, Math.max(1.25, desiredDistance));
                addCandidate(candidates, observer, target, subjects, radial, distance,
                        CONFINED_RECOVERY_HEIGHT, recoveryDesiredDistance,
                        10 + angle, previousAngle, angle == 0, config);
            }
        }
    }

    private static void addCandidate(
            List<CameraCandidate> candidates,
            ObserverCameraEntity observer,
            Entity target,
            SubjectGroup subjects,
            Vec3 radial,
            double distance,
            double cameraHeight,
            double desiredDistance,
            int angleIndex,
            int previousAngle,
            boolean frontFacing,
            ObserverCamConfig config
    ) {
        Vec3 focus = subjects.compositionFocus();
        double cameraVerticalOffset = cameraHeight - target.getBbHeight() * 0.72;
        Vec3 center = focus.add(0.0, cameraVerticalOffset, 0.0).add(radial.scale(distance));
        Vec3 position = center.add(0.0, -0.5, 0.0);
        Level level = observer.level();
        BlockPos centerBlock = BlockPos.containing(center);
        boolean loaded = level.hasChunk(centerBlock.getX() >> 4, centerBlock.getZ() >> 4);
        AABB box = new AABB(center.x - 0.48, center.y - 0.48, center.z - 0.48, center.x + 0.48, center.y + 0.48, center.z + 0.48);
        boolean clear = loaded && level.noCollision(observer, box);
        Vec3 direction = focus.subtract(center).normalize();
        Vec3 origin = center.add(direction.scale(CameraTransform.FACE_OFFSET));
        clear = clear && originClear(level, observer, origin);
        boolean pathClear = clear && pathClear(level, observer, observer.position().add(0.0, 0.5, 0.0), center);
        int visibility = clear ? VisibilityProbe.visibleSamples(level, observer, origin, target) : 0;
        double background = visibility > 0 ? EnvironmentProbe.backgroundDepth(level, observer, origin, focus) : 0.0;
        double actualDistance = origin.distanceTo(subjects.primaryFocus());
        double framing = ShotScorer.framingScore(actualDistance, config.cameraFov, config.preferredPlayerScreenSize, target.getBbHeight());
        double secondaryCoverage = secondaryCoverage(level, observer, origin, focus,
                subjects.secondarySubjects(), config.cameraFov);
        double score = ShotScorer.score(clear, pathClear, visibility, actualDistance, desiredDistance, framing, background,
                secondaryCoverage, angleIndex, previousAngle, frontFacing, config);
        candidates.add(new CameraCandidate(position, focus, angleIndex, clear, pathClear, visibility, background, framing, score));
    }

    static SubjectGroup documentarySubjects(Level level, Entity target, ObserverCamConfig config) {
        Vec3 velocity = target.getDeltaMovement();
        Vec3 targetPosition = target.position();
        Vec3 primaryFocus = new Vec3(
                targetPosition.x + velocity.x * config.movementPredictionTicks,
                target.getBoundingBox().minY + target.getBoundingBox().getYsize() * 0.72,
                targetPosition.z + velocity.z * config.movementPredictionTicks
        );
        Vec3 currentPrimaryFocus = new Vec3(
                targetPosition.x,
                target.getBoundingBox().minY + target.getBoundingBox().getYsize() * 0.72,
                targetPosition.z
        );
        List<LivingEntity> nearby = level.getEntities(
                        EntityTypeTest.forClass(LivingEntity.class),
                        target.getBoundingBox().inflate(SECONDARY_SUBJECT_RADIUS, 5.0, SECONDARY_SUBJECT_RADIUS),
                        entity -> entity != target
                                && (entity instanceof Player || entity instanceof Mob)
                                && entity.isAlive()
                                && !entity.isSpectator()
                                && !entity.isInvisible()
                ).stream()
                .sorted(Comparator.comparingDouble(entity -> entity.distanceToSqr(target)
                        / (entity instanceof Player ? 1.5 : 1.0)))
                .limit(MAX_SECONDARY_SUBJECT_CANDIDATES)
                .filter(entity -> VisibilityProbe.hasLineOfSight(
                        level, target, currentPrimaryFocus, subjectPoint(entity)))
                .limit(MAX_SECONDARY_SUBJECTS)
                .toList();
        List<DocumentaryFraming.WeightedPoint> companions = nearby.stream()
                .map(entity -> {
                    double distance = Math.sqrt(entity.distanceToSqr(target));
                    double subjectWeight = entity instanceof Player ? 1.25 : 1.0;
                    double weight = subjectWeight
                            * (0.3 + 0.7 * Math.max(0.0, 1.0 - distance / SECONDARY_SUBJECT_RADIUS));
                    return new DocumentaryFraming.WeightedPoint(subjectPoint(entity), weight);
                })
                .toList();
        Vec3 compositionFocus = DocumentaryFraming.groupFocus(primaryFocus, companions,
                PRIMARY_SUBJECT_WEIGHT, MAXIMUM_GROUP_SHIFT, MAXIMUM_GROUP_VERTICAL_SHIFT);
        return new SubjectGroup(primaryFocus, compositionFocus, nearby);
    }

    static Vec3 targetForward(Entity target) {
        Vec3 velocity = target.getDeltaMovement();
        Vec3 horizontal = new Vec3(velocity.x, 0.0, velocity.z);
        if (horizontal.lengthSqr() < 0.0025) {
            Vec3 look = target.getLookAngle();
            horizontal = new Vec3(look.x, 0.0, look.z);
        }
        return horizontalUnit(horizontal);
    }

    private static double secondaryCoverage(Level level, Entity camera, Vec3 origin, Vec3 focus,
                                            List<LivingEntity> subjects, double fovDegrees) {
        if (subjects.isEmpty()) {
            return 1.0;
        }
        int covered = 0;
        for (LivingEntity subject : subjects) {
            Vec3 point = subjectPoint(subject);
            if (DocumentaryFraming.insideFrame(origin, focus, point, fovDegrees)
                    && VisibilityProbe.hasLineOfSight(level, camera, origin, point)) {
                covered++;
            }
        }
        return covered / (double) subjects.size();
    }

    private static Vec3 subjectPoint(Entity subject) {
        return new Vec3(subject.getX(),
                subject.getBoundingBox().minY + subject.getBoundingBox().getYsize() * 0.62,
                subject.getZ());
    }

    private static boolean pathClear(Level level, Entity context, Vec3 start, Vec3 end) {
        if (start.distanceToSqr(end) < 0.25) {
            return true;
        }
        for (Vec3 offset : PATH_PROBE_OFFSETS) {
            Vec3 probeEnd = end.add(offset);
            HitResult hit = level.clip(new ClipContext(start.add(offset), probeEnd,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, context));
            if (hit.getType() != HitResult.Type.MISS && hit.getLocation().distanceToSqr(probeEnd) >= 0.3) {
                return false;
            }
        }
        return true;
    }

    private static boolean originClear(Level level, Entity context, Vec3 origin) {
        AABB originBox = new AABB(origin.x - 0.08, origin.y - 0.08, origin.z - 0.08,
                origin.x + 0.08, origin.y + 0.08, origin.z + 0.08);
        return level.noCollision(context, originBox);
    }

    private static Vec3 rotateY(Vec3 vector, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return new Vec3(vector.x * cos + vector.z * sin, 0.0, vector.z * cos - vector.x * sin);
    }

    private static Vec3 horizontalUnit(Vec3 vector) {
        Vec3 horizontal = new Vec3(vector.x, 0.0, vector.z);
        return horizontal.lengthSqr() < 1.0E-8 ? new Vec3(0.0, 0.0, 1.0) : horizontal.normalize();
    }

    private static double horizontalDistance(Vec3 first, Vec3 second) {
        double x = first.x - second.x;
        double z = first.z - second.z;
        return Math.sqrt(x * x + z * z);
    }

    private static double lerp(double start, double end, double amount) {
        return start + (end - start) * amount;
    }

    public record CameraPlan(List<CameraCandidate> candidates, CameraCandidate best, double indoorFactor,
                             double desiredDistance, boolean emergencyRecovery) {
    }

    public record SubjectGroup(Vec3 primaryFocus, Vec3 compositionFocus, List<LivingEntity> secondarySubjects) {
        public SubjectGroup {
            secondarySubjects = List.copyOf(secondarySubjects);
        }

        public SubjectGroup withCompositionFocus(Vec3 smoothedFocus) {
            return new SubjectGroup(primaryFocus, smoothedFocus, secondarySubjects);
        }
    }
}
