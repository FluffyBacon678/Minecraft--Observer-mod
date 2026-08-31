package com.fluffybacon.observercam.network;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

/** Small server-owned guard against repeated control payloads from modified clients. */
public final class ObserverControlRateLimiter {
    private static final Map<MinecraftServer, Map<UUID, EnumMap<Action, Long>>> NEXT_ALLOWED_TICKS =
            new IdentityHashMap<>();

    private ObserverControlRateLimiter() {
    }

    public static boolean allow(ServerPlayer player, Action action) {
        MinecraftServer server = player.level().getServer();
        long now = server.getTickCount();
        Map<UUID, EnumMap<Action, Long>> players =
                NEXT_ALLOWED_TICKS.computeIfAbsent(server, ignored -> new HashMap<>());
        EnumMap<Action, Long> controls =
                players.computeIfAbsent(player.getUUID(), ignored -> new EnumMap<>(Action.class));
        long nextAllowed = controls.getOrDefault(action, Long.MIN_VALUE);
        if (now < nextAllowed) {
            return false;
        }
        controls.put(action, now + action.cooldownTicks);
        return true;
    }

    public static void clear(MinecraftServer server, UUID playerUuid) {
        Map<UUID, EnumMap<Action, Long>> players = NEXT_ALLOWED_TICKS.get(server);
        if (players == null) {
            return;
        }
        players.remove(playerUuid);
        if (players.isEmpty()) {
            NEXT_ALLOWED_TICKS.remove(server);
        }
    }

    public static void clear(MinecraftServer server) {
        NEXT_ALLOWED_TICKS.remove(server);
    }

    public enum Action {
        ENABLE(5L),
        SETTINGS(5L),
        PULSE(5L);

        private final long cooldownTicks;

        Action(long cooldownTicks) {
            this.cooldownTicks = cooldownTicks;
        }
    }
}
