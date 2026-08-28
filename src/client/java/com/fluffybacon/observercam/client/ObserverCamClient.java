package com.fluffybacon.observercam.client;

import com.fluffybacon.observercam.ObserverCam;
import com.fluffybacon.observercam.client.assistant.ObserverAssistant;
import com.fluffybacon.observercam.client.render.ObserverCameraRenderer;
import com.fluffybacon.observercam.client.recording.ObserverRecordingHud;
import com.fluffybacon.observercam.client.recording.ObserverRecordingManager;
import com.fluffybacon.observercam.config.ObserverCamConfig;
import com.fluffybacon.observercam.network.RestoreViewPayload;
import com.fluffybacon.observercam.network.SetCameramanEnabledPayload;
import com.fluffybacon.observercam.network.SyncCameraSettingsPayload;
import com.fluffybacon.observercam.network.ToggleViewPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class ObserverCamClient implements ClientModInitializer {
    private static final ObserverPovController POV = new ObserverPovController();
    private static KeyMapping cameramanKey;
    private static KeyMapping recordKey;
    private static KeyMapping saveReplayKey;

    @Override
    public void onInitializeClient() {
        EntityRenderers.register(ObserverCam.OBSERVER_CAMERA, ObserverCameraRenderer::new);

        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(ObserverCam.MOD_ID, "controls"));
        cameramanKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.observercam.toggle_cameraman", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));
        KeyMapping viewKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.observercam.toggle_view", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));
        recordKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.observercam.toggle_recording", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));
        KeyMapping pictureInPictureKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.observercam.toggle_pip", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));
        saveReplayKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.observercam.save_replay", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));

        ClientPlayNetworking.registerGlobalReceiver(ToggleViewPayload.TYPE, (payload, context) -> POV.toggle(context.client()));
        ClientPlayNetworking.registerGlobalReceiver(RestoreViewPayload.TYPE, (payload, context) -> POV.restore(context.client()));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            syncCameraSettings();
            sendCameramanEnabled(ObserverCamConfig.get().cameramanEnabled);
            ObserverActivationPulse.sync();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ObserverRecordingManager.get().stop(client,
                    net.minecraft.network.chat.Component.translatable("observercam.recording.stop.disconnect"));
            ObserverRecordingManager.get().discardInstantReplay(client);
            POV.disconnect(client);
            ObserverActivationPulse.reset();
            ObserverPictureInPicture.reset();
            ObserverAssistant.reset();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            ObserverRecordingManager.get().shutdown(client);
            ObserverPictureInPicture.reset();
            ObserverAssistant.reset();
        });
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(ObserverCam.MOD_ID, "debug_hud"),
                (graphics, tickCounter) -> ObserverDebugHud.render(graphics));
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(ObserverCam.MOD_ID, "recording_hud"),
                (graphics, tickCounter) -> ObserverRecordingHud.render(graphics));
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(ObserverCam.MOD_ID, "picture_in_picture"),
                (graphics, tickCounter) -> ObserverPictureInPicture.render(graphics));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (cameramanKey.consumeClick()) {
                toggleCameraman();
            }
            while (viewKey.consumeClick()) {
                POV.toggle(client);
            }
            POV.tick(client);
            while (recordKey.consumeClick()) {
                ObserverRecordingManager.get().toggle(client);
            }
            while (pictureInPictureKey.consumeClick()) {
                ObserverPictureInPicture.toggle();
            }
            while (saveReplayKey.consumeClick()) {
                ObserverRecordingManager.get().saveInstantReplay(client);
            }
            ObserverRecordingManager.get().tick(client);
            ObserverAssistant.tick(client);
            ObserverDebugRenderer.tick(client);
        });
    }

    public static void setCameramanEnabled(boolean enabled) {
        ObserverCamConfig.get().cameramanEnabled = enabled;
        ObserverCamConfig.get().save();
        syncCameraSettings();
        sendCameramanEnabled(enabled);
    }

    public static void toggleCameraman() {
        setCameramanEnabled(!ObserverCamConfig.get().cameramanEnabled);
    }

    public static Component cameramanKeyText() {
        return keyText(cameramanKey);
    }

    public static Component recordingKeyText() {
        return keyText(recordKey);
    }

    public static Component replaySaveHint() {
        return saveReplayKey == null || saveReplayKey.isUnbound()
                ? Component.translatable("observercam.replay.hud.save_menu")
                : Component.translatable("observercam.replay.hud.save_key", saveReplayKey.getTranslatedKeyMessage());
    }

    private static Component keyText(KeyMapping mapping) {
        return mapping == null || mapping.isUnbound()
                ? Component.translatable("observercam.config.key.unbound")
                : mapping.getTranslatedKeyMessage();
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
