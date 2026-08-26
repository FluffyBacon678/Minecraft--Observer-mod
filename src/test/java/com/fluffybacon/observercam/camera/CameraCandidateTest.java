package com.fluffybacon.observercam.camera;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraCandidateTest {
    @Test
    void normalFlightRequiresAReachableVisibleDestination() {
        CameraCandidate reachable = candidate(true, true, 4);
        CameraCandidate acrossWall = candidate(true, false, 4);
        CameraCandidate hidden = candidate(true, true, 0);

        assertTrue(reachable.valid());
        assertFalse(acrossWall.valid());
        assertFalse(hidden.valid());
    }

    @Test
    void emergencyTeleportStillRequiresClearVisibleDestination() {
        assertTrue(candidate(true, false, 4).safeTeleportDestination());
        assertFalse(candidate(false, false, 4).safeTeleportDestination());
        assertFalse(candidate(true, false, 0).safeTeleportDestination());
    }

    private static CameraCandidate candidate(boolean clear, boolean pathClear, int visibleSamples) {
        return new CameraCandidate(Vec3.ZERO, Vec3.ZERO, 0, clear, pathClear, visibleSamples,
                1.0, 1.0, 100.0);
    }
}
