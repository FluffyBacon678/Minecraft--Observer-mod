package com.fluffybacon.observercam.client.mixin;

import com.fluffybacon.observercam.camera.CameraTransform;
import com.fluffybacon.observercam.entity.ObserverCameraEntity;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Inject(method = "setup", at = @At("TAIL"))
    private void observercam$useObserverFace(Level level, Entity entity, boolean detached, boolean inverseView,
                                            float partialTick, CallbackInfo callbackInfo) {
        if (entity instanceof ObserverCameraEntity observer && !detached) {
            setPosition(CameraTransform.from(observer, partialTick).origin());
        }
    }
}
