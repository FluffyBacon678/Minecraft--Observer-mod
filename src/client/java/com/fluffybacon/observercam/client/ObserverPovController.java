package com.fluffybacon.observercam.client;

import com.fluffybacon.observercam.ObserverCam;
import com.fluffybacon.observercam.entity.ObserverCameraEntity;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Comparator;
import java.util.UUID;

public final class ObserverPovController {
    private static final int FIND_OBSERVER_RETRY_TICKS = 40;
    private static UUID cachedOwnedObserver;

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
                client.player.displayClientMessage(Component.translatable("observercam.message.pov.cancelled"), true);
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
        ObserverCameraEntity observer = findOwnedObserver(client);
        if (observer == null) {
            return false;
        }

        previousCameraType = client.options.getCameraType();
        activeObserver = observer.getUUID();
        ObserverCameraSmoother.reset();
        client.options.setCameraType(CameraType.FIRST_PERSON);
        client.setCameraEntity(observer);
        client.levelRenderer.needsUpdate();
        client.player.displayClientMessage(Component.translatable("observercam.message.pov.enabled"), true);
        return true;
    }

    public static ObserverCameraEntity findOwnedObserver(Minecraft client) {
        if (client == null || client.level == null || client.player == null) {
            cachedOwnedObserver = null;
            return null;
        }
        if (cachedOwnedObserver != null
                && client.level.getEntity(cachedOwnedObserver) instanceof ObserverCameraEntity cached
                && cached.isAlive()
                && isOwnedBy(cached, client.player.getUUID())) {
            return cached;
        }
        ObserverCameraEntity found = client.level
                .getEntities(ObserverCam.OBSERVER_CAMERA, client.player.getBoundingBox().inflate(160.0), ObserverCameraEntity::isAlive)
                .stream()
                .filter(entity -> isOwnedBy(entity, client.player.getUUID()))
                .min(Comparator.comparingDouble(entity -> entity.distanceToSqr(client.player)))
                .orElse(null);
        cachedOwnedObserver = found == null ? null : found.getUUID();
        return found;
    }

    private static boolean isOwnedBy(ObserverCameraEntity observer, UUID playerUuid) {
        return observer.isOwnedBy(playerUuid)
                || observer.getOwnerUuid() == null && playerUuid.equals(observer.getTargetUuid());
    }

    public void tick(Minecraft client) {
        if (activeObserver == null) {
            if (retryTicks > 0 && client.level != null && client.player != null) {
                if (tryEnable(client)) {
                    retryTicks = 0;
                } else if (--retryTicks == 0) {
                    client.player.displayClientMessage(Component.translatable("observercam.message.pov.unavailable"), true);
                }
            }
            return;
        }
        if (client.level == null || client.player == null) {
            disconnect(client);
            return;
        }
        if (!(client.level.getEntity(activeObserver) instanceof ObserverCameraEntity observer)
                || !observer.isAlive()
                || observer.level() != client.player.level()) {
            restore(client, Component.translatable("observercam.message.pov.lost"));
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
        restore(client, Component.translatable("observercam.message.pov.disabled"));
    }

    public void disconnect(Minecraft client) {
        cachedOwnedObserver = null;
        restore(client, null);
    }

    private void restore(Minecraft client, Component message) {
        if (activeObserver == null) {
            retryTicks = 0;
            return;
        }
        activeObserver = null;
        retryTicks = 0;
        ObserverCameraSmoother.reset();
        if (client.player != null) {
            client.setCameraEntity(client.player);
        }
        client.options.setCameraType(previousCameraType);
        if (client.levelRenderer != null) {
            client.levelRenderer.needsUpdate();
        }
        if (client.player != null && message != null) {
            client.player.displayClientMessage(message, true);
        }
    }

    public boolean isViewing() {
        return activeObserver != null;
    }

    public boolean isRequestPending() {
        return retryTicks > 0;
    }
}
