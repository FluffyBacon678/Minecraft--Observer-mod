package com.fluffybacon.observercam.camera;

import net.minecraft.world.phys.Vec3;

public record CameraCandidate(
        Vec3 position,
        Vec3 focus,
        int angleIndex,
        boolean clear,
        boolean pathClear,
        int visibleSamples,
        double backgroundDepth,
        double framing,
        double score
) {
    public boolean valid() {
        return clear && pathClear && visibleSamples > 0;
    }

    public boolean safeTeleportDestination() {
        return clear && visibleSamples > 0;
    }
}
