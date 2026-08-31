package com.fluffybacon.observercam.client.mixin;

import com.fluffybacon.observercam.config.ObserverCamConfig;
import com.fluffybacon.observercam.client.recording.ObserverFrameCapture;
import com.fluffybacon.observercam.client.ObserverPictureInPicture;
import com.fluffybacon.observercam.entity.ObserverCameraEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;)V",
            shift = At.Shift.BEFORE))
    private void observercam$capturePictureInPicture(DeltaTracker deltaTracker, boolean renderLevel,
                                                     CallbackInfo callbackInfo) {
        ObserverPictureInPicture.captureBeforeMainWorld((GameRenderer) (Object) this, deltaTracker, renderLevel);
    }

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/render/GuiRenderer;render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V",
            shift = At.Shift.BEFORE))
    private void observercam$captureWithoutHud(DeltaTracker deltaTracker, boolean renderLevel,
                                                CallbackInfo callbackInfo) {
        ObserverFrameCapture.captureIfNeeded(false);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void observercam$captureWithHud(DeltaTracker deltaTracker, boolean renderLevel,
                                             CallbackInfo callbackInfo) {
        ObserverFrameCapture.captureIfNeeded(true);
    }

    @ModifyArg(method = "pick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;raycastHitResult(FLnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/phys/HitResult;"),
            index = 1)
    private Entity observercam$pickFromRealPlayer(Entity cameraEntity) {
        return cameraEntity instanceof ObserverCameraEntity
                ? Minecraft.getInstance().player : cameraEntity;
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
