package com.fluffybacon.observercam.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ToggleViewPayload() implements CustomPacketPayload {
    public static final ToggleViewPayload INSTANCE = new ToggleViewPayload();
    public static final Type<ToggleViewPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("observercam", "toggle_view"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ToggleViewPayload> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
