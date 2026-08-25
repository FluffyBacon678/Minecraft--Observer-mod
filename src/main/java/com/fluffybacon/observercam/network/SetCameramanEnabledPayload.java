package com.fluffybacon.observercam.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SetCameramanEnabledPayload(boolean enabled) implements CustomPacketPayload {
    public static final Type<SetCameramanEnabledPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("observercam", "set_cameraman_enabled"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SetCameramanEnabledPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            SetCameramanEnabledPayload::enabled,
            SetCameramanEnabledPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
