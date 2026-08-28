package com.fluffybacon.observercam.client.mixin;

import com.fluffybacon.observercam.client.ObserverPictureInPicture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "getMainRenderTarget", at = @At("HEAD"), cancellable = true)
    private void observercam$useObserverFeedTarget(CallbackInfoReturnable<RenderTarget> callbackInfo) {
        RenderTarget observerTarget = ObserverPictureInPicture.activeRenderTarget();
        if (observerTarget != null) {
            callbackInfo.setReturnValue(observerTarget);
        }
    }
}
