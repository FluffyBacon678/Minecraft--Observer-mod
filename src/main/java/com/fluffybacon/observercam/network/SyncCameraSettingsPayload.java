package com.fluffybacon.observercam.network;

import com.fluffybacon.observercam.config.ObserverCamConfig.CameraSettings;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SyncCameraSettingsPayload(CameraSettings settings) implements CustomPacketPayload {
    public static final Type<SyncCameraSettingsPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("observercam", "sync_camera_settings"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncCameraSettingsPayload> CODEC = new StreamCodec<>() {
        @Override
        public SyncCameraSettingsPayload decode(RegistryFriendlyByteBuf buffer) {
            return new SyncCameraSettingsPayload(new CameraSettings(
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readBoolean(),
                    buffer.readBoolean()
            ));
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, SyncCameraSettingsPayload payload) {
            CameraSettings settings = payload.settings();
            buffer.writeDouble(settings.outdoorDistance());
            buffer.writeDouble(settings.indoorDistance());
            buffer.writeDouble(settings.minimumDistance());
            buffer.writeDouble(settings.maximumDistance());
            buffer.writeDouble(settings.cameraHeight());
            buffer.writeDouble(settings.cameraFov());
            buffer.writeDouble(settings.maximumSpeed());
            buffer.writeDouble(settings.acceleration());
            buffer.writeDouble(settings.rotationSpeed());
            buffer.writeDouble(settings.positionSmoothing());
            buffer.writeDouble(settings.rotationSmoothing());
            buffer.writeDouble(settings.catchUpDistance());
            buffer.writeDouble(settings.emergencyTeleportDistance());
            buffer.writeDouble(settings.backgroundImportance());
            buffer.writeDouble(settings.playerVisibilityImportance());
            buffer.writeDouble(settings.shotStability());
            buffer.writeDouble(settings.reframeThreshold());
            buffer.writeDouble(settings.preferredPlayerScreenSize());
            buffer.writeDouble(settings.movementPredictionTicks());
            buffer.writeBoolean(settings.followTargetAutomatically());
            buffer.writeBoolean(settings.allowFrontFacingShots());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
