package com.fluffybacon.observercam.camera;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class VisibilityProbe {
    private static final double[] SAMPLE_HEIGHTS = {0.92, 0.68, 0.43, 0.14};

    private VisibilityProbe() {
    }

    public static int visibleSamples(Level level, Entity camera, Vec3 origin, Entity target) {
        int visible = 0;
        double minY = target.getBoundingBox().minY;
        double height = target.getBoundingBox().getYsize();
        for (double sampleHeight : SAMPLE_HEIGHTS) {
            Vec3 sample = new Vec3(target.getX(), minY + height * sampleHeight, target.getZ());
            if (hasLineOfSight(level, camera, origin, sample)) {
                visible++;
            }
        }
        return visible;
    }

    public static boolean hasLineOfSight(Level level, Entity camera, Vec3 from, Vec3 to) {
        Vec3 direction = to.subtract(from).normalize();
        Vec3 start = from;
        for (int leavesPassed = 0; leavesPassed <= 2; leavesPassed++) {
            HitResult result = level.clip(new ClipContext(start, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, camera));
            if (result.getType() == HitResult.Type.MISS || result.getLocation().distanceToSqr(to) < 0.04) {
                return true;
            }
            if (!(result instanceof BlockHitResult blockHit)
                    || !level.getBlockState(blockHit.getBlockPos()).is(BlockTags.LEAVES)) {
                return false;
            }
            start = result.getLocation().add(direction.scale(0.08));
        }
        return false;
    }
}
