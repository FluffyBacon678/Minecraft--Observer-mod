package com.fluffybacon.observercam.client.render;

import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

public final class ObserverCameraRenderState extends EntityRenderState {
    public final MovingBlockRenderState block = new MovingBlockRenderState();
    public float yaw;
    public float pitch;
}
