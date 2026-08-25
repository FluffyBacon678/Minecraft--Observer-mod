package com.fluffybacon.observercam.command;

import com.fluffybacon.observercam.ObserverCam;
import com.fluffybacon.observercam.entity.ObserverCameraEntity;
import com.fluffybacon.observercam.entity.ObserverCameraManager;
import com.fluffybacon.observercam.network.ToggleViewPayload;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Comparator;

public final class ObserverCamCommands {
    private ObserverCamCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("observercam")
                .then(Commands.literal("summon").executes(ObserverCamCommands::summon))
                .then(Commands.literal("target")
                        .then(Commands.argument("player", EntityArgument.player()).executes(ObserverCamCommands::target)))
                .then(Commands.literal("follow").executes(ObserverCamCommands::follow))
                .then(Commands.literal("dismiss").executes(ObserverCamCommands::dismiss))
                .then(Commands.literal("view").executes(ObserverCamCommands::view)));
    }

    private static int summon(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ObserverCameraManager.spawnFor(player);
        context.getSource().sendSuccess(() -> Component.literal("Observer cameraman summoned."), false);
        return 1;
    }

    private static int target(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer source = context.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        ObserverCameraEntity observer = nearest(context.getSource().getLevel(), source);
        if (observer == null) {
            context.getSource().sendFailure(Component.literal("No nearby Observer cameraman found."));
            return 0;
        }
        observer.setTarget(target);
        context.getSource().sendSuccess(() -> Component.literal("Observer now targets " + target.getGameProfile().name() + "."), false);
        return 1;
    }

    private static int follow(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ObserverCameraEntity observer = nearest(context.getSource().getLevel(), player);
        if (observer == null) {
            return summon(context);
        }
        observer.setTarget(player);
        observer.setFollowing(true);
        context.getSource().sendSuccess(() -> Component.literal("Observer is following you."), false);
        return 1;
    }

    private static int dismiss(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ObserverCameraEntity observer = nearest(context.getSource().getLevel(), player);
        if (observer == null) {
            context.getSource().sendFailure(Component.literal("No nearby Observer cameraman found."));
            return 0;
        }
        observer.discard();
        context.getSource().sendSuccess(() -> Component.literal("Observer cameraman dismissed."), false);
        return 1;
    }

    private static int view(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerPlayNetworking.send(player, ToggleViewPayload.INSTANCE);
        return 1;
    }

    private static ObserverCameraEntity nearest(ServerLevel level, ServerPlayer player) {
        return level.getEntities(ObserverCam.OBSERVER_CAMERA, ObserverCameraEntity::isAlive).stream()
                .min(Comparator.comparingDouble(observer -> observer.distanceToSqr(player)))
                .orElse(null);
    }
}
