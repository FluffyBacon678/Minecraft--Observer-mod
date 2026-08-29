package com.fluffybacon.observercam.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Confirms an actual server-side Observer enable/disable transition to its owner. */
public record ObserverSwitchSoundPayload(boolean enabled) implements CustomPacketPayload {
    public static final Type<ObserverSwitchSoundPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("observercam", "observer_switch_sound"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ObserverSwitchSoundPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            ObserverSwitchSoundPayload::enabled,
            ObserverSwitchSoundPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
