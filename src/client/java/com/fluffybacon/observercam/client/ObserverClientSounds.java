package com.fluffybacon.observercam.client;

import com.fluffybacon.observercam.ObserverCam;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;

/** Non-spatial local feedback for recording state changes. */
public final class ObserverClientSounds {
    private ObserverClientSounds() {
    }

    public static void playRecordingSwitch(Minecraft client, boolean enabled) {
        if (client == null || client.getSoundManager() == null) {
            return;
        }
        client.getSoundManager().play(SimpleSoundInstance.forUI(
                enabled ? ObserverCam.SWITCH_ON_SOUND : ObserverCam.SWITCH_OFF_SOUND,
                1.0F));
    }
}
