package com.fluffybacon.observercam.recording;

public enum RecordingQuality {
    HIGH("high", 18, 28),
    BALANCED("balanced", 20, 32),
    SMALL("small", 26, 38);

    private final String id;
    private final int h264Crf;
    private final int vp9Crf;

    RecordingQuality(String id, int h264Crf, int vp9Crf) {
        this.id = id;
        this.h264Crf = h264Crf;
        this.vp9Crf = vp9Crf;
    }

    public String id() {
        return id;
    }

    public int crf(RecordingVideoFormat format) {
        if (format == null) {
            throw new IllegalArgumentException("Video format must not be null");
        }
        return format == RecordingVideoFormat.WEBM ? vp9Crf : h264Crf;
    }

    public RecordingQuality next() {
        RecordingQuality[] qualities = values();
        return qualities[(ordinal() + 1) % qualities.length];
    }
}
