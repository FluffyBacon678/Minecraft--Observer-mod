package com.fluffybacon.observercam.command;

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
import net.minecraft.server.level.ServerPlayer;

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
        context.getSource().sendSuccess(() -> Component.translatable("observercam.message.command.summoned"), false);
        return 1;
    }

    private static int target(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer source = context.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        ObserverCameraEntity observer = ObserverCameraManager.findFor(source);
        if (observer == null) {
            context.getSource().sendFailure(Component.translatable("observercam.message.command.no_nearby"));
            return 0;
        }
        observer.setTarget(target);
        context.getSource().sendSuccess(() -> Component.translatable("observercam.message.command.targeting",
                target.getGameProfile().name()), false);
        return 1;
    }

    private static int follow(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ObserverCameraEntity observer = ObserverCameraManager.findFor(player);
        if (observer == null) {
            observer = ObserverCameraManager.enableFor(player);
        }
        observer.setTarget(player);
        observer.setFollowing(true);
        context.getSource().sendSuccess(() -> Component.translatable("observercam.message.command.following"), false);
        return 1;
    }

    private static int dismiss(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        int dismissed = ObserverCameraManager.disableFor(player);
        if (dismissed == 0) {
            context.getSource().sendFailure(Component.translatable("observercam.message.command.no_nearby"));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable("observercam.message.command.dismissed"), false);
        return dismissed;
    }

    private static int view(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerPlayNetworking.send(player, ToggleViewPayload.INSTANCE);
        return 1;
    }

}
