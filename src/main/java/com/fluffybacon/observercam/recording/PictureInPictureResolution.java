package com.fluffybacon.observercam.recording;

public enum PictureInPictureResolution {
    PERFORMANCE("performance", 320, 180),
    BALANCED("balanced", 480, 270),
    SHARP("sharp", 640, 360);

    private final String id;
    private final int maximumWidth;
    private final int maximumHeight;

    PictureInPictureResolution(String id, int maximumWidth, int maximumHeight) {
        this.id = id;
        this.maximumWidth = maximumWidth;
        this.maximumHeight = maximumHeight;
    }

    public String id() {
        return id;
    }

    public PictureInPictureResolution next() {
        PictureInPictureResolution[] resolutions = values();
        return resolutions[(ordinal() + 1) % resolutions.length];
    }

    public CaptureSize captureSize(int sourceWidth, int sourceHeight) {
        return CaptureSize.fitInside(sourceWidth, sourceHeight, maximumWidth, maximumHeight);
    }
}
