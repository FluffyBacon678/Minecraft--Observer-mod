package com.fluffybacon.observercam.client.render;

import com.fluffybacon.observercam.entity.ObserverCameraEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
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
            if (state.recording) {
                submitRecordingEyes(poseStack, collector);
            }
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
        state.block.blockState = Blocks.OBSERVER.defaultBlockState();
        state.block.biome = entity.level().getBiome(blockPos);
        state.block.level = entity.level();
        state.yaw = entity.getYRot(partialTick);
        state.pitch = entity.getXRot(partialTick);
        state.recording = entity.isRecording();
    }

    private static void submitRecordingEyes(PoseStack poseStack, SubmitNodeCollector collector) {
        collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (pose, vertices) -> {
            submitEye(pose, vertices, 0.22F, 0.38F);
            submitEye(pose, vertices, 0.62F, 0.78F);
        });
    }

    private static void submitEye(PoseStack.Pose pose, com.mojang.blaze3d.vertex.VertexConsumer vertices,
                                  float left, float right) {
        int redstoneRed = 0xFFFF1808;
        float bottom = 0.48F;
        float top = 0.64F;
        float front = 1.002F;
        vertices.addVertex(pose, left, bottom, front).setColor(redstoneRed);
        vertices.addVertex(pose, right, bottom, front).setColor(redstoneRed);
        vertices.addVertex(pose, right, top, front).setColor(redstoneRed);
        vertices.addVertex(pose, left, top, front).setColor(redstoneRed);
    }
}
