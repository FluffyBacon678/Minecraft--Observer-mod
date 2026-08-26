package com.fluffybacon.observercam.camera;

import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Small, deterministic composition helpers used by the camera director.
 * The primary subject is allowed to drift inside a soft central region so
 * ordinary footsteps and jumps do not turn into constant camera corrections.
 */
public final class DocumentaryFraming {
    private DocumentaryFraming() {
    }

    public static Vec3 softFollow(Vec3 current, Vec3 desired, double horizontalDeadZone,
                                  double verticalDeadZone, double response, double maximumStep) {
        if (current == null) {
            return desired;
        }

        Vec3 delta = desired.subtract(current);
        double horizontalDistance = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        double horizontalScale = horizontalDistance <= horizontalDeadZone || horizontalDistance == 0.0
                ? 0.0
                : (horizontalDistance - horizontalDeadZone) / horizontalDistance;
        double verticalCorrection = Math.abs(delta.y) <= verticalDeadZone
                ? 0.0
                : Math.copySign(Math.abs(delta.y) - verticalDeadZone, delta.y);
        Vec3 correction = new Vec3(delta.x * horizontalScale, verticalCorrection, delta.z * horizontalScale)
                .scale(response);
        return current.add(clampLength(correction, maximumStep));
    }

    public static Vec3 smoothHeading(Vec3 current, Vec3 desired, double response, double maximumTurnRadians) {
        Vec3 target = horizontalUnit(desired);
        if (target == null) {
            return current == null ? new Vec3(0.0, 0.0, 1.0) : current;
        }
        Vec3 existing = horizontalUnit(current);
        if (existing == null) {
            return target;
        }

        double currentAngle = Math.atan2(existing.z, existing.x);
        double targetAngle = Math.atan2(target.z, target.x);
        double delta = wrapRadians(targetAngle - currentAngle);
        double step = clamp(delta * response, -maximumTurnRadians, maximumTurnRadians);
        double result = currentAngle + step;
        return new Vec3(Math.cos(result), 0.0, Math.sin(result));
    }

    public static Vec3 groupFocus(Vec3 primary, List<WeightedPoint> companions, double primaryWeight,
                                  double maximumHorizontalShift, double maximumVerticalShift) {
        Vec3 weightedOffset = Vec3.ZERO;
        double totalWeight = Math.max(0.01, primaryWeight);
        for (WeightedPoint companion : companions) {
            double weight = Math.max(0.0, companion.weight());
            weightedOffset = weightedOffset.add(companion.position().subtract(primary).scale(weight));
            totalWeight += weight;
        }
        Vec3 offset = weightedOffset.scale(1.0 / totalWeight);
        double horizontalLength = Math.sqrt(offset.x * offset.x + offset.z * offset.z);
        if (horizontalLength > maximumHorizontalShift) {
            double scale = maximumHorizontalShift / horizontalLength;
            offset = new Vec3(offset.x * scale, offset.y, offset.z * scale);
        }
        offset = new Vec3(offset.x, clamp(offset.y, -maximumVerticalShift, maximumVerticalShift), offset.z);
        return primary.add(offset);
    }

    public static boolean insideFrame(Vec3 origin, Vec3 focus, Vec3 subject, double fovDegrees) {
        Vec3 view = focus.subtract(origin);
        Vec3 toSubject = subject.subtract(origin);
        if (view.lengthSqr() < 1.0E-8 || toSubject.lengthSqr() < 1.0E-8) {
            return true;
        }
        double cosine = view.normalize().dot(toSubject.normalize());
        double generousHalfAngle = Math.toRadians(Math.min(80.0, fovDegrees * 0.56));
        return cosine >= Math.cos(generousHalfAngle);
    }

    private static Vec3 horizontalUnit(Vec3 vector) {
        if (vector == null) {
            return null;
        }
        Vec3 horizontal = new Vec3(vector.x, 0.0, vector.z);
        return horizontal.lengthSqr() < 1.0E-8 ? null : horizontal.normalize();
    }

    private static Vec3 clampLength(Vec3 vector, double maximum) {
        double length = vector.length();
        return length > maximum && length > 0.0 ? vector.scale(maximum / length) : vector;
    }

    private static double wrapRadians(double angle) {
        while (angle <= -Math.PI) {
            angle += Math.PI * 2.0;
        }
        while (angle > Math.PI) {
            angle -= Math.PI * 2.0;
        }
        return angle;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public record WeightedPoint(Vec3 position, double weight) {
    }
}
