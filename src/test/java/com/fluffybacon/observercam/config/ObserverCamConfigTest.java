package com.fluffybacon.observercam.config;

import com.fluffybacon.observercam.config.ObserverCamConfig.CameraSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ObserverCamConfigTest {
    @Test
    void clientCameraSettingsAreFiniteAndClampedBeforeServerUse() {
        CameraSettings untrusted = new CameraSettings(
                Double.NaN, 100.0, -20.0, -10.0, 99.0, Double.POSITIVE_INFINITY,
                99.0, -4.0, 400.0, 0.0, 4.0, 100.0, 1.0,
                99.0, -20.0, 4.0, 1_000.0, 0.0, 100.0,
                false, false
        );

        ObserverCamConfig config = ObserverCamConfig.fromCameraSettings(untrusted);

        assertEquals(8.0, config.outdoorDistance);
        assertEquals(10.0, config.indoorDistance);
        assertEquals(2.0, config.minimumDistance);
        assertEquals(2.0, config.maximumDistance);
        assertEquals(8.0, config.cameraHeight);
        assertEquals(70.0, config.cameraFov);
        assertEquals(2.0, config.maximumSpeed);
        assertEquals(0.01, config.acceleration);
        assertEquals(30.0, config.rotationSpeed);
        assertEquals(40.0, config.catchUpDistance);
        assertEquals(42.0, config.emergencyTeleportDistance);
        assertEquals(12.0, config.movementPredictionTicks);
        assertFalse(config.followTargetAutomatically);
        assertFalse(config.allowFrontFacingShots);
    }
}
