package com.fluffybacon.observercam.client;

import com.fluffybacon.observercam.network.PulseObserverPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Sends one vanilla-style Observer pulse when capture becomes active. */
public final class ObserverActivationPulse {
    private static boolean captureActive;

    private ObserverActivationPulse() {
    }

    public static void setCaptureActive(boolean active) {
        if (captureActive == active) {
            return;
        }
        captureActive = active;
        if (active) {
            pulse();
        }
    }

    public static void reset() {
        captureActive = false;
    }

    public static void sync() {
        if (captureActive || ObserverPictureInPicture.isEnabled()) {
            pulse();
        }
    }

    public static void pulse() {
        if (ClientPlayNetworking.canSend(PulseObserverPayload.TYPE)) {
            ClientPlayNetworking.send(PulseObserverPayload.INSTANCE);
        }
    }
}
