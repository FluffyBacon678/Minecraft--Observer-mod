package com.fluffybacon.observercam.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SetRecordingStatePayload(boolean recording) implements CustomPacketPayload {
    public static final Type<SetRecordingStatePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("observercam", "set_recording_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SetRecordingStatePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            SetRecordingStatePayload::recording,
            SetRecordingStatePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
