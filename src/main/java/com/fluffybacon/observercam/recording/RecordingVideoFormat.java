package com.fluffybacon.observercam.recording;

public enum RecordingVideoFormat {
    MP4("mp4"),
    MKV("mkv"),
    WEBM("webm");

    private final String id;

    RecordingVideoFormat(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public RecordingVideoFormat next() {
        RecordingVideoFormat[] formats = values();
        return formats[(ordinal() + 1) % formats.length];
    }
}
