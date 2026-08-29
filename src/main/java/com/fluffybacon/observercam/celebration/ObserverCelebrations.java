package com.fluffybacon.observercam.celebration;

import com.fluffybacon.observercam.entity.ObserverCameraManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/** Server-side easter-egg triggers that do not alter vanilla interactions. */
public final class ObserverCelebrations {
    private static final List<PendingCakeUse> PENDING_CAKE_USES = new ArrayList<>();

    private ObserverCelebrations() {
    }

    public static void register() {
        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer
                    && !player.isSpectator() && player.canEat(false)) {
                BlockPos position = hitResult.getBlockPos();
                BlockState state = level.getBlockState(position);
                if (state.is(Blocks.CAKE)) {
                    PENDING_CAKE_USES.add(new PendingCakeUse(
                            serverLevel, serverPlayer, position.immutable(), state.getValue(CakeBlock.BITES)));
                }
            }
            return InteractionResult.PASS;
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> processPendingCakeUses());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> PENDING_CAKE_USES.clear());
    }

    private static void processPendingCakeUses() {
        if (PENDING_CAKE_USES.isEmpty()) {
            return;
        }
        List<PendingCakeUse> pending = List.copyOf(PENDING_CAKE_USES);
        PENDING_CAKE_USES.clear();
        for (PendingCakeUse use : pending) {
            if (use.player().isRemoved() || use.player().level() != use.level()) {
                continue;
            }
            BlockState after = use.level().getBlockState(use.position());
            boolean cakeRemains = after.is(Blocks.CAKE);
            int bitesAfter = cakeRemains ? after.getValue(CakeBlock.BITES) : -1;
            if (CakeBiteDetector.wasSliceEaten(use.bitesBefore(), cakeRemains, bitesAfter)) {
                ObserverCameraManager.celebrateCake(use.player());
            }
        }
    }

    private record PendingCakeUse(
            ServerLevel level,
            ServerPlayer player,
            BlockPos position,
            int bitesBefore
    ) {
    }
}
