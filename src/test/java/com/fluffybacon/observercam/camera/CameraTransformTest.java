package com.fluffybacon.observercam.camera;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CameraTransformTest {
    private static final double EPSILON = 1.0E-6;

    @Test
    void forwardVectorMatchesMinecraftCardinalYaw() {
        assertVector(new Vec3(0, 0, 1), CameraTransform.forward(0, 0));
        assertVector(new Vec3(-1, 0, 0), CameraTransform.forward(90, 0));
        assertVector(new Vec3(0, 0, -1), CameraTransform.forward(180, 0));
    }

    @Test
    void positivePitchLooksDown() {
        assertVector(new Vec3(0, -1, 0), CameraTransform.forward(0, 90));
    }

    @Test
    void lookAtRoundTripsDirection() {
        Vec3 origin = new Vec3(2, 4, -1);
        Vec3 target = new Vec3(-5, 8, 7);
        CameraTransform.Rotation rotation = CameraTransform.lookAt(origin, target);
        assertVector(target.subtract(origin).normalize(), CameraTransform.forward(rotation.yaw(), rotation.pitch()));
    }

    private static void assertVector(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, EPSILON);
        assertEquals(expected.y, actual.y, EPSILON);
        assertEquals(expected.z, actual.z, EPSILON);
    }
}
