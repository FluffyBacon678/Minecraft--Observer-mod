package com.fluffybacon.observercam.entity;

import com.fluffybacon.observercam.ObserverCam;
import com.fluffybacon.observercam.config.ObserverCamConfig;
import com.fluffybacon.observercam.config.ObserverCamConfig.CameraSettings;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ObserverCameraManager {
    private static final Map<MinecraftServer, Map<UUID, ObserverCamConfig>> RUNTIME_CONFIGS = new IdentityHashMap<>();

    private ObserverCameraManager() {
    }

    public static ObserverCameraEntity spawnFor(ServerPlayer player) {
        return activateFor(player, cameraConfigFor(player).followTargetAutomatically);
    }

    private static ObserverCameraEntity createFor(ServerPlayer player, boolean following) {
        ServerLevel level = player.level();
        ObserverCameraEntity observer = new ObserverCameraEntity(ObserverCam.OBSERVER_CAMERA, level);
        observer.setOwner(player.getUUID());
        observer.setTarget(player);
        observer.setFollowing(following);
        Vec3 start = player.position().add(0.0, 2.0, 0.0).subtract(player.getLookAngle().scale(8.0));
        observer.snapTo(start);
        observer.setYRot(player.getYRot());
        level.addFreshEntity(observer);
        return observer;
    }

    public static ObserverCameraEntity enableFor(ServerPlayer player) {
        return activateFor(player, cameraConfigFor(player).followTargetAutomatically);
    }

    public static void syncCameraSettings(ServerPlayer player, CameraSettings settings) {
        ObserverCamConfig runtimeConfig = ObserverCamConfig.fromCameraSettings(settings);
        MinecraftServer server = player.level().getServer();
        RUNTIME_CONFIGS.computeIfAbsent(server, ignored -> new HashMap<>())
                .put(player.getUUID(), runtimeConfig);
        findAllOwnedBy(server, player.getUUID()).forEach(observer ->
                observer.setFollowing(runtimeConfig.followTargetAutomatically));
    }

    public static ObserverCamConfig cameraConfigFor(ObserverCameraEntity observer) {
        if (!(observer.level() instanceof ServerLevel serverLevel)) {
            return ObserverCamConfig.get();
        }
        UUID ownerUuid = observer.getOwnerUuid();
        if (ownerUuid == null) {
            ownerUuid = observer.getTargetUuid();
        }
        return ownerUuid == null
                ? ObserverCamConfig.get()
                : cameraConfigFor(serverLevel.getServer(), ownerUuid);
    }

    public static void clearCameraSettings(MinecraftServer server, UUID ownerUuid) {
        Map<UUID, ObserverCamConfig> serverConfigs = RUNTIME_CONFIGS.get(server);
        if (serverConfigs == null) {
            return;
        }
        serverConfigs.remove(ownerUuid);
        if (serverConfigs.isEmpty()) {
            RUNTIME_CONFIGS.remove(server);
        }
    }

    public static void clearCameraSettings(MinecraftServer server) {
        RUNTIME_CONFIGS.remove(server);
    }

    private static ObserverCamConfig cameraConfigFor(ServerPlayer player) {
        return cameraConfigFor(player.level().getServer(), player.getUUID());
    }

    private static ObserverCamConfig cameraConfigFor(MinecraftServer server, UUID ownerUuid) {
        Map<UUID, ObserverCamConfig> serverConfigs = RUNTIME_CONFIGS.get(server);
        return serverConfigs == null
                ? ObserverCamConfig.get()
                : serverConfigs.getOrDefault(ownerUuid, ObserverCamConfig.get());
    }

    private static ObserverCameraEntity activateFor(ServerPlayer player, boolean following) {
        List<ObserverCameraEntity> owned = findAllOwnedBy(player.level().getServer(), player.getUUID());
        ObserverCameraEntity existing = owned.stream()
                .filter(observer -> observer.level() == player.level())
                .min(Comparator.comparingDouble(observer -> observer.distanceToSqr(player)))
                .orElse(null);
        if (existing != null) {
            ObserverCameraEntity retained = existing;
            owned.stream().filter(observer -> observer != retained).forEach(ObserverCameraEntity::discard);
            existing.setOwner(player.getUUID());
            existing.setTarget(player);
            existing.setFollowing(following);
            return existing;
        }
        owned.forEach(ObserverCameraEntity::discard);
        return createFor(player, following);
    }

    public static int disableFor(ServerPlayer player) {
        return disableFor(player.level().getServer(), player.getUUID());
    }

    public static void pulseFor(ServerPlayer player) {
        ObserverCameraEntity observer = findFor(player);
        if (observer != null) {
            observer.pulse();
        }
    }

    public static int disableFor(MinecraftServer server, UUID ownerUuid) {
        List<ObserverCameraEntity> owned = findAllOwnedBy(server, ownerUuid);
        owned.forEach(ObserverCameraEntity::discard);
        return owned.size();
    }

    public static @Nullable ObserverCameraEntity findFor(MinecraftServer server, UUID ownerUuid) {
        return retainOnly(findAllOwnedBy(server, ownerUuid), null);
    }

    public static @Nullable ObserverCameraEntity findFor(ServerPlayer owner) {
        List<ObserverCameraEntity> owned = findAllOwnedBy(owner.level().getServer(), owner.getUUID());
        ObserverCameraEntity preferred = owned.stream()
                .filter(observer -> observer.level() == owner.level())
                .min(Comparator.comparingDouble(observer -> observer.distanceToSqr(owner)))
                .orElse(null);
        return retainOnly(owned, preferred);
    }

    private static List<ObserverCameraEntity> findAllOwnedBy(MinecraftServer server, UUID ownerUuid) {
        List<ObserverCameraEntity> found = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (ObserverCameraEntity observer : level.getEntities(ObserverCam.OBSERVER_CAMERA, ObserverCameraEntity::isAlive)) {
                if (belongsToOwner(ownerUuid, observer.getOwnerUuid(), observer.getTargetUuid())) {
                    found.add(observer);
                }
            }
        }
        return found;
    }

    private static @Nullable ObserverCameraEntity retainOnly(
            List<ObserverCameraEntity> owned,
            @Nullable ObserverCameraEntity preferred
    ) {
        ObserverCameraEntity retained = preferred != null
                ? preferred
                : owned.stream().findFirst().orElse(null);
        if (retained != null) {
            owned.stream().filter(observer -> observer != retained).forEach(ObserverCameraEntity::discard);
        }
        return retained;
    }

    static boolean belongsToOwner(UUID ownerUuid, @Nullable UUID storedOwnerUuid, @Nullable UUID targetUuid) {
        return ownerUuid.equals(storedOwnerUuid)
                || storedOwnerUuid == null && ownerUuid.equals(targetUuid);
    }
}
