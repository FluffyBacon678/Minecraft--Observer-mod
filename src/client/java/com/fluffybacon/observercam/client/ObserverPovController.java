package com.fluffybacon.observercam.client;

import com.fluffybacon.observercam.ObserverCam;
import com.fluffybacon.observercam.entity.ObserverCameraEntity;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;

import java.util.Comparator;
import java.util.UUID;

public final class ObserverPovController {
    private static final int FIND_OBSERVER_RETRY_TICKS = 40;

    private UUID activeObserver;
    private CameraType previousCameraType = CameraType.FIRST_PERSON;
    private int retryTicks;

    public boolean toggle(Minecraft client) {
        if (activeObserver != null) {
            restore(client);
            return false;
        }
        if (retryTicks > 0) {
            retryTicks = 0;
            if (client.player != null) {
                client.player.displayClientMessage(net.minecraft.network.chat.Component.literal("Observer POV request cancelled."), true);
            }
            return false;
        }
        if (client.level == null || client.player == null) {
            return false;
        }
        if (tryEnable(client)) {
            return true;
        }

        // Spawn packets and custom payloads travel independently. A short retry window
        // makes a summon followed immediately by a POV request deterministic.
        retryTicks = FIND_OBSERVER_RETRY_TICKS;
        return false;
    }

    private boolean tryEnable(Minecraft client) {
        ObserverCameraEntity observer = client.level
                .getEntities(ObserverCam.OBSERVER_CAMERA, client.player.getBoundingBox().inflate(160.0), ObserverCameraEntity::isAlive)
                .stream()
                .filter(entity -> entity.getTargetUuid() == null || entity.getTargetUuid().equals(client.player.getUUID()))
                .min(Comparator.comparingDouble(entity -> entity.distanceToSqr(client.player)))
                .orElse(null);
        if (observer == null) {
            return false;
        }

        previousCameraType = client.options.getCameraType();
        activeObserver = observer.getUUID();
        client.options.setCameraType(CameraType.FIRST_PERSON);
        client.setCameraEntity(observer);
        client.levelRenderer.needsUpdate();
        client.player.displayClientMessage(net.minecraft.network.chat.Component.literal("Observer POV enabled."), true);
        return true;
    }

    public void tick(Minecraft client) {
        if (activeObserver == null) {
            if (retryTicks > 0 && client.level != null && client.player != null) {
                if (tryEnable(client)) {
                    retryTicks = 0;
                } else if (--retryTicks == 0) {
                    client.player.displayClientMessage(net.minecraft.network.chat.Component.literal("No Observer cameraman is available."), true);
                }
            }
            return;
        }
        if (client.level == null || client.player == null) {
            restore(client);
            return;
        }
        if (!(client.level.getEntity(activeObserver) instanceof ObserverCameraEntity observer)
                || !observer.isAlive()
                || observer.level() != client.player.level()) {
            restore(client);
            return;
        }
        if (client.getCameraEntity() != observer) {
            client.setCameraEntity(observer);
        }
        if (client.options.getCameraType() != CameraType.FIRST_PERSON) {
            client.options.setCameraType(CameraType.FIRST_PERSON);
        }
    }

    public void restore(Minecraft client) {
        if (activeObserver == null) {
            retryTicks = 0;
            return;
        }
        activeObserver = null;
        retryTicks = 0;
        client.setCameraEntity(client.player);
        client.options.setCameraType(previousCameraType);
        if (client.levelRenderer != null) {
            client.levelRenderer.needsUpdate();
        }
        if (client.player != null) {
            client.player.displayClientMessage(net.minecraft.network.chat.Component.literal("Observer POV disabled."), true);
        }
    }

    public boolean isViewing() {
        return activeObserver != null;
    }
}
