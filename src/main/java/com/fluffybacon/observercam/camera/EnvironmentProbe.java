package com.fluffybacon.observercam.camera;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class EnvironmentProbe {
    private EnvironmentProbe() {
    }

    public static double indoorFactor(Level level, Entity subject) {
        Vec3 head = subject.position().add(0.0, subject.getBbHeight() * 0.9, 0.0);
        double enclosure = level.canSeeSky(BlockPos.containing(head)) ? 0.0 : 0.35;
        enclosure += hitFraction(level, subject, head, head.add(0.0, 8.0, 0.0)) * 0.25;
        Vec3[] directions = {
                new Vec3(1.0, 0.0, 0.0), new Vec3(-1.0, 0.0, 0.0),
                new Vec3(0.0, 0.0, 1.0), new Vec3(0.0, 0.0, -1.0)
        };
        for (Vec3 direction : directions) {
            enclosure += hitFraction(level, subject, head, head.add(direction.scale(5.0))) * 0.10;
        }
        return Math.max(0.0, Math.min(1.0, enclosure));
    }

    public static double backgroundDepth(Level level, Entity camera, Vec3 origin, Vec3 focus) {
        Vec3 forward = focus.subtract(origin).normalize();
        Vec3 right = forward.cross(new Vec3(0.0, 1.0, 0.0));
        if (right.lengthSqr() < 1.0E-5) {
            right = new Vec3(1.0, 0.0, 0.0);
        } else {
            right = right.normalize();
        }
        Vec3 up = right.cross(forward).normalize();
        Vec3[] offsets = {
                Vec3.ZERO, right.scale(1.2), right.scale(-1.2), up.scale(1.0), up.scale(-0.8)
        };
        double total = 0.0;
        for (Vec3 offset : offsets) {
            Vec3 start = focus.add(forward.scale(0.6)).add(offset);
            Vec3 end = start.add(forward.scale(16.0));
            HitResult hit = level.clip(new ClipContext(start, end, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, camera));
            double depth = hit.getType() == HitResult.Type.MISS ? 16.0 : start.distanceTo(hit.getLocation());
            total += Math.min(depth / 16.0, 1.0);
        }
        return total / offsets.length;
    }

    private static double hitFraction(Level level, Entity context, Vec3 from, Vec3 to) {
        HitResult result = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, context));
        if (result.getType() == HitResult.Type.MISS) {
            return 0.0;
        }
        double fullDistance = from.distanceTo(to);
        double hitDistance = from.distanceTo(result.getLocation());
        return 1.0 - Math.min(hitDistance / fullDistance, 1.0);
    }
}
