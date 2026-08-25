package com.fluffybacon.observercam.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record RestoreViewPayload() implements CustomPacketPayload {
    public static final RestoreViewPayload INSTANCE = new RestoreViewPayload();
    public static final Type<RestoreViewPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("observercam", "restore_view"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RestoreViewPayload> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
