package com.fluffybacon.observercam.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record PulseObserverPayload() implements CustomPacketPayload {
    public static final PulseObserverPayload INSTANCE = new PulseObserverPayload();
    public static final Type<PulseObserverPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("observercam", "pulse_observer"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PulseObserverPayload> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
