package com.fluffybacon.observercam;

import com.fluffybacon.observercam.command.ObserverCamCommands;
import com.fluffybacon.observercam.config.ObserverCamConfig;
import com.fluffybacon.observercam.entity.ObserverCameraEntity;
import com.fluffybacon.observercam.entity.ObserverCameraManager;
import com.fluffybacon.observercam.network.RestoreViewPayload;
import com.fluffybacon.observercam.network.SetCameramanEnabledPayload;
import com.fluffybacon.observercam.network.ToggleViewPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ObserverCam implements ModInitializer {
    public static final String MOD_ID = "observercam";
    private static final Identifier ENTITY_ID = Identifier.fromNamespaceAndPath(MOD_ID, "observer_camera");
    private static final ResourceKey<EntityType<?>> ENTITY_KEY = ResourceKey.create(Registries.ENTITY_TYPE, ENTITY_ID);

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
        PayloadTypeRegistry.playS2C().register(ToggleViewPayload.TYPE, ToggleViewPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RestoreViewPayload.TYPE, RestoreViewPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SetCameramanEnabledPayload.TYPE, SetCameramanEnabledPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SetCameramanEnabledPayload.TYPE, (payload, context) -> {
            if (payload.enabled()) {
                ObserverCameraManager.enableFor(context.player());
                context.player().sendSystemMessage(Component.literal("Observer cameraman enabled."));
            } else {
                ObserverCameraManager.disableFor(context.player());
                ServerPlayNetworking.send(context.player(), RestoreViewPayload.INSTANCE);
                context.player().sendSystemMessage(Component.literal("Observer cameraman disabled."));
            }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> ObserverCamCommands.register(dispatcher));
    }
}
