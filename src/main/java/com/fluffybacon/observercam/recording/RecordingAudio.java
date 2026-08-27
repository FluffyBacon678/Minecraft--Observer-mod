package com.fluffybacon.observercam.recording;

/** Immutable audio selection passed to FFmpeg. Blank device names mean video-only recording. */
public record RecordingAudio(String deviceName) {
    private static final RecordingAudio DISABLED = new RecordingAudio("");

    public RecordingAudio {
        deviceName = deviceName == null ? "" : deviceName.trim();
    }

    public static RecordingAudio disabled() {
        return DISABLED;
    }

    public static RecordingAudio directShow(String deviceName) {
        RecordingAudio audio = new RecordingAudio(deviceName);
        if (!audio.enabled()) {
            throw new IllegalArgumentException("Audio device must not be blank");
        }
        return audio;
    }

    public boolean enabled() {
        return !deviceName.isBlank();
    }
}
