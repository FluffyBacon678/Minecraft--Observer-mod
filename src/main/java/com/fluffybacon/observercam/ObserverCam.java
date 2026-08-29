package com.fluffybacon.observercam;

import com.fluffybacon.observercam.celebration.ObserverCelebrations;
import com.fluffybacon.observercam.command.ObserverCamCommands;
import com.fluffybacon.observercam.config.ObserverCamConfig;
import com.fluffybacon.observercam.entity.ObserverCameraEntity;
import com.fluffybacon.observercam.entity.ObserverCameraManager;
import com.fluffybacon.observercam.network.ObserverSwitchSoundPayload;
import com.fluffybacon.observercam.network.RestoreViewPayload;
import com.fluffybacon.observercam.network.SetCameramanEnabledPayload;
import com.fluffybacon.observercam.network.PulseObserverPayload;
import com.fluffybacon.observercam.network.SyncCameraSettingsPayload;
import com.fluffybacon.observercam.network.ToggleViewPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ObserverCam implements ModInitializer {
    public static final String MOD_ID = "observercam";
    private static final Identifier ENTITY_ID = Identifier.fromNamespaceAndPath(MOD_ID, "observer_camera");
    private static final ResourceKey<EntityType<?>> ENTITY_KEY = ResourceKey.create(Registries.ENTITY_TYPE, ENTITY_ID);
    public static final SoundEvent SWITCH_ON_SOUND = registerSound("switch_on");
    public static final SoundEvent SWITCH_OFF_SOUND = registerSound("switch_off");
    public static final SoundEvent CAKE_IS_A_LIE_SOUND = registerSound("cake_is_a_lie");

    public static final EntityType<ObserverCameraEntity> OBSERVER_CAMERA = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ENTITY_KEY,
            EntityType.Builder.<ObserverCameraEntity>of(ObserverCameraEntity::new, MobCategory.MISC)
                    .sized(0.96F, 0.96F)
                    .eyeHeight(0.5F)
                    .fireImmune()
                    .noLootTable()
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build(ENTITY_KEY)
    );

    @Override
    public void onInitialize() {
        ObserverCamConfig.load();
        ObserverCelebrations.register();
        PayloadTypeRegistry.playS2C().register(ToggleViewPayload.TYPE, ToggleViewPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RestoreViewPayload.TYPE, RestoreViewPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ObserverSwitchSoundPayload.TYPE, ObserverSwitchSoundPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SetCameramanEnabledPayload.TYPE, SetCameramanEnabledPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(PulseObserverPayload.TYPE, PulseObserverPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SyncCameraSettingsPayload.TYPE, SyncCameraSettingsPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SyncCameraSettingsPayload.TYPE, (payload, context) ->
                ObserverCameraManager.syncCameraSettings(context.player(), payload.settings()));
        ServerPlayNetworking.registerGlobalReceiver(SetCameramanEnabledPayload.TYPE, (payload, context) -> {
            if (payload.enabled()) {
                ObserverCameraManager.enableFor(context.player());
                context.player().sendSystemMessage(Component.translatable("observercam.message.cameraman.enabled"));
            } else {
                ObserverCameraManager.disableFor(context.player());
                ServerPlayNetworking.send(context.player(), RestoreViewPayload.INSTANCE);
                context.player().sendSystemMessage(Component.translatable("observercam.message.cameraman.disabled"));
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(PulseObserverPayload.TYPE, (payload, context) ->
                ObserverCameraManager.pulseFor(context.player()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ObserverCameraManager.disableFor(server, handler.player.getUUID());
            ObserverCameraManager.clearCameraSettings(server, handler.player.getUUID());
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(ObserverCameraManager::clearCameraSettings);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> ObserverCamCommands.register(dispatcher));
    }

    private static SoundEvent registerSound(String path) {
        Identifier identifier = Identifier.fromNamespaceAndPath(MOD_ID, path);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, identifier,
                SoundEvent.createVariableRangeEvent(identifier));
    }
}
