package com.fluffybacon.observercam.entity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ObserverCameraManagerTest {
    private static final UUID OWNER = UUID.fromString("4a4ea87e-ae25-4b5a-b750-1d19f06199fe");
    private static final UUID TARGET = UUID.fromString("da752a2a-5e68-4bbb-b221-2628282357b8");

    @Test
    void explicitOwnerRemainsOwnerAfterRetargeting() {
        assertTrue(ObserverCameraManager.belongsToOwner(OWNER, OWNER, TARGET));
        assertFalse(ObserverCameraManager.belongsToOwner(TARGET, OWNER, TARGET));
    }

    @Test
    void legacyCameraFallsBackToItsTargetAsOwner() {
        assertTrue(ObserverCameraManager.belongsToOwner(TARGET, null, TARGET));
        assertFalse(ObserverCameraManager.belongsToOwner(OWNER, null, TARGET));
        assertFalse(ObserverCameraManager.belongsToOwner(OWNER, null, null));
    }
}
