package com.fluffybacon.observercam.recording;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses the human-readable audio-device list emitted by FFmpeg's DirectShow input. */
public final class FFmpegAudioDevices {
    private static final Pattern AUDIO_DEVICE = Pattern.compile("\\\"([^\\\"]+)\\\"\\s+\\(audio\\)");

    private FFmpegAudioDevices() {
    }

    public static List<String> parseDirectShowListing(String listing) {
        if (listing == null || listing.isBlank()) {
            return List.of();
        }
        Set<String> devices = new LinkedHashSet<>();
        for (String line : listing.lines().toList()) {
            Matcher matcher = AUDIO_DEVICE.matcher(line);
            if (matcher.find()) {
                String name = matcher.group(1).trim();
                if (!name.isBlank()) {
                    devices.add(name);
                }
            }
        }
        return List.copyOf(devices);
    }

    public static List<String> likelyGameAudioDevices(List<String> devices) {
        if (devices == null || devices.isEmpty()) {
            return List.of();
        }
        List<String> likely = new ArrayList<>();
        for (String device : devices) {
            if (device != null && isLikelyGameAudioDevice(device)) {
                likely.add(device);
            }
        }
        return List.copyOf(likely);
    }

    public static boolean isLikelyGameAudioDevice(String device) {
        if (device == null) {
            return false;
        }
        String normalized = device.toLowerCase(Locale.ROOT);
        return normalized.contains("stereo mix")
                || normalized.contains("what u hear")
                || normalized.contains("wave out mix")
                || normalized.contains("loopback")
                || normalized.contains("monitor")
                || normalized.contains("cable output")
                || normalized.contains("voicemeeter out")
                || normalized.contains("voicemeeter output")
                || normalized.contains("voicemeeter aux output")
                || normalized.contains("voicemeeter vaio3 output");
    }
}
