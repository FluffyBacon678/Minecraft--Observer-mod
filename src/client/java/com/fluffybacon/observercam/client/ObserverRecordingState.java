package com.fluffybacon.observercam.client;

import com.fluffybacon.observercam.network.SetRecordingStatePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Narrow bridge between the future recorder and the in-world recording light.
 */
public final class ObserverRecordingState {
    private static boolean active;

    private ObserverRecordingState() {
    }

    public static boolean isActive() {
        return active;
    }

    public static void setActive(boolean recording) {
        if (active == recording) {
            return;
        }
        active = recording;
        send();
    }

    public static void reset() {
        active = false;
    }

    public static void sync() {
        send();
    }

    private static void send() {
        if (ClientPlayNetworking.canSend(SetRecordingStatePayload.TYPE)) {
            ClientPlayNetworking.send(new SetRecordingStatePayload(active));
        }
    }
}
