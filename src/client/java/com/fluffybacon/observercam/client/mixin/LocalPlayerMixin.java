package com.fluffybacon.observercam.client.mixin;

import com.fluffybacon.observercam.entity.ObserverCameraEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    @Inject(method = "isControlledCamera", at = @At("HEAD"), cancellable = true)
    private void observercam$keepPlayerControllable(CallbackInfoReturnable<Boolean> callbackInfo) {
        if (Minecraft.getInstance().getCameraEntity() instanceof ObserverCameraEntity) {
            callbackInfo.setReturnValue(true);
        }
    }
}
