package com.fluffybacon.observercam.client.mixin;

import com.fluffybacon.observercam.config.ObserverCamConfig;
import com.fluffybacon.observercam.entity.ObserverCameraEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Redirect(method = "pick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;raycastHitResult(FLnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/phys/HitResult;"))
    private HitResult observercam$pickFromRealPlayer(LocalPlayer player, float partialTick, Entity cameraEntity) {
        Entity interactionView = cameraEntity instanceof ObserverCameraEntity ? player : cameraEntity;
        return player.raycastHitResult(partialTick, interactionView);
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void observercam$hidePlayerHand(float partialTick, boolean sleeping, Matrix4f matrix,
                                            CallbackInfo callbackInfo) {
        if (Minecraft.getInstance().getCameraEntity() instanceof ObserverCameraEntity) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void observercam$cameraFov(Camera camera, float partialTick, boolean changingFov,
                                       CallbackInfoReturnable<Float> callbackInfo) {
        if (Minecraft.getInstance().getCameraEntity() instanceof ObserverCameraEntity) {
            callbackInfo.setReturnValue((float) ObserverCamConfig.get().cameraFov);
        }
    }
}
