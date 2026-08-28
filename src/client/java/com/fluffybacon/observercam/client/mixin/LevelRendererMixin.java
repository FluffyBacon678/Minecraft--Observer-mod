package com.fluffybacon.observercam.client.mixin;

import com.fluffybacon.observercam.entity.ObserverCameraEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Inject(method = "shouldShowEntityOutlines", at = @At("HEAD"), cancellable = true)
    private void observercam$hideOutlinesInAuxiliaryFeed(CallbackInfoReturnable<Boolean> callbackInfo) {
        if (com.fluffybacon.observercam.client.ObserverPictureInPicture.isRenderingFeed()) {
            callbackInfo.setReturnValue(false);
        }
    }

    @Inject(method = "extractVisibleEntities", at = @At("TAIL"))
    private void observercam$extractLocalPlayer(Camera camera, Frustum frustum, DeltaTracker deltaTracker,
                                                LevelRenderState levelRenderState, CallbackInfo callbackInfo) {
        Minecraft minecraft = Minecraft.getInstance();
        if (camera.entity() instanceof ObserverCameraEntity && minecraft.player != null && minecraft.player.isAlive()) {
            float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
            EntityRenderState playerRenderState = minecraft.getEntityRenderDispatcher()
                    .extractEntity(minecraft.player, partialTick);
            playerRenderState.nameTag = null;
            levelRenderState.entityRenderStates.add(playerRenderState);
        }
    }
}
