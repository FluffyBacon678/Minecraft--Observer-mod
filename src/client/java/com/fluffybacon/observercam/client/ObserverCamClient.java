package com.fluffybacon.observercam.client;

import com.fluffybacon.observercam.ObserverCam;
import com.fluffybacon.observercam.client.render.ObserverCameraRenderer;
import com.fluffybacon.observercam.config.ObserverCamConfig;
import com.fluffybacon.observercam.network.RestoreViewPayload;
import com.fluffybacon.observercam.network.SetCameramanEnabledPayload;
import com.fluffybacon.observercam.network.SyncCameraSettingsPayload;
import com.fluffybacon.observercam.network.ToggleViewPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class ObserverCamClient implements ClientModInitializer {
    private static final ObserverPovController POV = new ObserverPovController();

    @Override
    public void onInitializeClient() {
        EntityRenderers.register(ObserverCam.OBSERVER_CAMERA, ObserverCameraRenderer::new);

        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(ObserverCam.MOD_ID, "controls"));
        KeyMapping viewKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.observercam.toggle_view", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, category));

        ClientPlayNetworking.registerGlobalReceiver(ToggleViewPayload.TYPE, (payload, context) -> POV.toggle(context.client()));
        ClientPlayNetworking.registerGlobalReceiver(RestoreViewPayload.TYPE, (payload, context) -> POV.restore(context.client()));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            syncCameraSettings();
            sendCameramanEnabled(ObserverCamConfig.get().cameramanEnabled);
            ObserverRecordingState.sync();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            POV.disconnect(client);
            ObserverRecordingState.reset();
        });
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(ObserverCam.MOD_ID, "debug_hud"),
                (graphics, tickCounter) -> ObserverDebugHud.render(graphics));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (viewKey.consumeClick()) {
                POV.toggle(client);
            }
            POV.tick(client);
            ObserverDebugRenderer.tick(client);
        });
    }

    public static void setCameramanEnabled(boolean enabled) {
        ObserverCamConfig.get().cameramanEnabled = enabled;
        ObserverCamConfig.get().save();
        syncCameraSettings();
        sendCameramanEnabled(enabled);
    }

    public static void syncCameraSettings() {
        if (ClientPlayNetworking.canSend(SyncCameraSettingsPayload.TYPE)) {
            ClientPlayNetworking.send(new SyncCameraSettingsPayload(ObserverCamConfig.get().cameraSettings()));
        }
    }

    public static void togglePov() {
        POV.toggle(net.minecraft.client.Minecraft.getInstance());
    }

    public static boolean isViewingObserver() {
        return POV.isViewing();
    }

    public static boolean isPovRequestPending() {
        return POV.isRequestPending();
    }

    private static void sendCameramanEnabled(boolean enabled) {
        if (ClientPlayNetworking.canSend(SetCameramanEnabledPayload.TYPE)) {
            ClientPlayNetworking.send(new SetCameramanEnabledPayload(enabled));
        }
    }
}
