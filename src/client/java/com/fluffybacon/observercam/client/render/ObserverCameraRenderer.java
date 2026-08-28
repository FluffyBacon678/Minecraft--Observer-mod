package com.fluffybacon.observercam.client.render;

import com.fluffybacon.observercam.entity.ObserverCameraEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.RenderShape;

public final class ObserverCameraRenderer extends EntityRenderer<ObserverCameraEntity, ObserverCameraRenderState> {
    public ObserverCameraRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
        this.shadowStrength = 0.0F;
    }

    @Override
    public void submit(ObserverCameraRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraState) {
        if (state.block.blockState.getRenderShape() == RenderShape.MODEL) {
            poseStack.pushPose();
            poseStack.translate(0.0, 0.5, 0.0);
            poseStack.mulPose(Axis.YP.rotationDegrees(-state.yaw));
            poseStack.mulPose(Axis.XP.rotationDegrees(state.pitch));
            poseStack.translate(-0.5, -0.5, -0.5);
            collector.submitMovingBlock(poseStack, state.block);
            poseStack.popPose();
        }
        super.submit(state, poseStack, collector, cameraState);
    }

    @Override
    public ObserverCameraRenderState createRenderState() {
        return new ObserverCameraRenderState();
    }

    @Override
    public void extractRenderState(ObserverCameraEntity entity, ObserverCameraRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        BlockPos blockPos = entity.blockPosition();
        state.block.randomSeedPos = blockPos;
        state.block.blockPos = blockPos;
        state.block.blockState = Blocks.OBSERVER.defaultBlockState()
                .setValue(ObserverBlock.POWERED, entity.isPowered());
        state.block.biome = entity.level().getBiome(blockPos);
        state.block.level = entity.level();
        state.yaw = entity.getYRot(partialTick);
        state.pitch = entity.getXRot(partialTick);
    }
}
