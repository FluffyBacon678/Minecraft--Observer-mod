package com.fluffybacon.observercam.recording;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FFmpegAudioDevicesTest {
    @Test
    void parsesOnlyNamedDirectShowAudioDevicesInStableOrder() {
        String listing = """
                [dshow @ 0001] \"Desktop Microphone\" (audio)
                [dshow @ 0001]   Alternative name \"@device_cm_guid\"
                [dshow @ 0001] \"CABLE Output (VB-Audio Virtual Cable)\" (audio)
                [dshow @ 0001] \"Desktop Microphone\" (audio)
                """;

        assertEquals(List.of("Desktop Microphone", "CABLE Output (VB-Audio Virtual Cable)"),
                FFmpegAudioDevices.parseDirectShowListing(listing));
    }

    @Test
    void recognizesCommonGameLoopbackNamesWithoutTreatingMicrophonesAsLoopback() {
        assertTrue(FFmpegAudioDevices.isLikelyGameAudioDevice("Stereo Mix (Realtek(R) Audio)"));
        assertTrue(FFmpegAudioDevices.isLikelyGameAudioDevice("CABLE Output (VB-Audio Virtual Cable)"));
        assertTrue(FFmpegAudioDevices.isLikelyGameAudioDevice("Voicemeeter Output (VB-Audio Voicemeeter VAIO)"));
        assertTrue(FFmpegAudioDevices.isLikelyGameAudioDevice("Voicemeeter Out B1 (VB-Audio Voicemeeter VAIO)"));
        assertFalse(FFmpegAudioDevices.isLikelyGameAudioDevice("Desktop Microphone"));
    }
}
