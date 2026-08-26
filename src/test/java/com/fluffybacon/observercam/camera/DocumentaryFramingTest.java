package com.fluffybacon.observercam.camera;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentaryFramingTest {
    @Test
    void softZoneIgnoresSmallPlayerMotion() {
        Vec3 current = new Vec3(4.0, 2.0, 7.0);
        Vec3 result = DocumentaryFraming.softFollow(current, new Vec3(4.5, 2.4, 7.2),
                0.75, 0.55, 0.42, 0.9);

        assertEquals(current, result);
    }

    @Test
    void jumpAndLargeMovementAreDamped() {
        Vec3 current = Vec3.ZERO;
        Vec3 result = DocumentaryFraming.softFollow(current, new Vec3(3.0, 1.2, 0.0),
                0.75, 0.55, 0.42, 0.9);

        assertTrue(result.x > 0.0 && result.x < 1.0);
        assertTrue(result.y > 0.0 && result.y < 0.4);
        assertTrue(result.length() <= 0.9 + 1.0E-9);
    }

    @Test
    void nearbySubjectsInfluenceButDoNotTakeOverComposition() {
        Vec3 primary = Vec3.ZERO;
        Vec3 focus = DocumentaryFraming.groupFocus(primary, List.of(
                new DocumentaryFraming.WeightedPoint(new Vec3(10.0, 4.0, 0.0), 1.0),
                new DocumentaryFraming.WeightedPoint(new Vec3(10.0, 4.0, 0.0), 1.0)
        ), 4.0, 2.25, 0.9);

        assertTrue(focus.x > 0.0);
        assertTrue(focus.x <= 2.25);
        assertEquals(0.9, focus.y, 1.0E-9);
    }

    @Test
    void headingChangesAreBounded() {
        Vec3 current = new Vec3(0.0, 0.0, 1.0);
        Vec3 result = DocumentaryFraming.smoothHeading(current, new Vec3(1.0, 0.0, 0.0),
                0.5, Math.toRadians(5.0));
        double angle = Math.acos(current.dot(result));

        assertEquals(Math.toRadians(5.0), angle, 1.0E-8);
    }
}
