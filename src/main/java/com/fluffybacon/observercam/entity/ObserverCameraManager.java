package com.fluffybacon.observercam.entity;

import com.fluffybacon.observercam.ObserverCam;
import com.fluffybacon.observercam.config.ObserverCamConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ObserverCameraManager {
    private ObserverCameraManager() {
    }

    public static ObserverCameraEntity spawnFor(ServerPlayer player) {
        ServerLevel level = player.level();
        ObserverCameraEntity observer = new ObserverCameraEntity(ObserverCam.OBSERVER_CAMERA, level);
        observer.setTarget(player);
        observer.setFollowing(ObserverCamConfig.get().followTargetAutomatically);
        Vec3 start = player.position().add(0.0, 2.0, 0.0).subtract(player.getLookAngle().scale(8.0));
        observer.snapTo(start);
        observer.setYRot(player.getYRot());
        level.addFreshEntity(observer);
        return observer;
    }

    public static ObserverCameraEntity enableFor(ServerPlayer player) {
        ObserverCameraEntity existing = findFor(player.level().getServer(), player.getUUID());
        if (existing != null) {
            existing.setTarget(player);
            existing.setFollowing(true);
            return existing;
        }
        ObserverCameraEntity created = spawnFor(player);
        created.setFollowing(true);
        return created;
    }

    public static int disableFor(ServerPlayer player) {
        List<ObserverCameraEntity> owned = findAllFor(player.level().getServer(), player.getUUID());
        owned.forEach(ObserverCameraEntity::discard);
        return owned.size();
    }

    public static @Nullable ObserverCameraEntity findFor(MinecraftServer server, UUID targetUuid) {
        for (ServerLevel level : server.getAllLevels()) {
            for (ObserverCameraEntity observer : level.getEntities(ObserverCam.OBSERVER_CAMERA, ObserverCameraEntity::isAlive)) {
                if (targetUuid.equals(observer.getTargetUuid())) {
                    return observer;
                }
            }
        }
        return null;
    }

    private static List<ObserverCameraEntity> findAllFor(MinecraftServer server, UUID targetUuid) {
        List<ObserverCameraEntity> found = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (ObserverCameraEntity observer : level.getEntities(ObserverCam.OBSERVER_CAMERA, ObserverCameraEntity::isAlive)) {
                if (targetUuid.equals(observer.getTargetUuid())) {
                    found.add(observer);
                }
            }
        }
        return found;
    }
}
