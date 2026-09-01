package com.fluffybacon.observercam.client.mixin;

import com.fluffybacon.observercam.client.ObserverPictureInPicture;
import com.fluffybacon.observercam.entity.ObserverCameraEntity;
import net.fabricmc.loader.api.FabricLoader;
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
    private static final boolean OBSERVERCAM$BETTER_CLOUDS_LOADED =
            FabricLoader.getInstance().isModLoaded("betterclouds");

    @Inject(method = "shouldShowEntityOutlines", at = @At("HEAD"), cancellable = true)
    private void observercam$hideOutlinesInAuxiliaryFeed(CallbackInfoReturnable<Boolean> callbackInfo) {
        if (ObserverPictureInPicture.isRenderingFeed()) {
            callbackInfo.setReturnValue(false);
        }
    }

    /**
     * Better Clouds owns a size-dependent framebuffer and otherwise rebuilds it every time the
     * low-resolution Observer pass alternates with the main window. Keep Better Clouds on the main
     * view, but omit clouds from the auxiliary feed to avoid that allocation and log-spam loop.
     */
    @Inject(method = "addCloudsPass", at = @At("HEAD"), cancellable = true)
    private void observercam$skipBetterCloudsInAuxiliaryFeed(CallbackInfo callbackInfo) {
        if (OBSERVERCAM$BETTER_CLOUDS_LOADED && ObserverPictureInPicture.isRenderingFeed()) {
            callbackInfo.cancel();
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
