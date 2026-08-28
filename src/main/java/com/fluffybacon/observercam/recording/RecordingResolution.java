package com.fluffybacon.observercam.recording;

public enum RecordingResolution {
    CURRENT("current", 0, 0),
    HD_720("720p", 1280, 720),
    FULL_HD_1080("1080p", 1920, 1080);

    private final String id;
    private final int width;
    private final int height;

    RecordingResolution(String id, int width, int height) {
        this.id = id;
        this.width = width;
        this.height = height;
    }

    public String id() {
        return id;
    }

    public RecordingResolution next() {
        RecordingResolution[] resolutions = values();
        return resolutions[(ordinal() + 1) % resolutions.length];
    }

    public CaptureSize captureSize(int sourceWidth, int sourceHeight) {
        return this == CURRENT
                ? CaptureSize.even(sourceWidth, sourceHeight)
                : CaptureSize.fitInside(sourceWidth, sourceHeight, width, height);
    }

    public String videoFilter() {
        if (this == CURRENT) {
            return "pad=ceil(iw/2)*2:ceil(ih/2)*2";
        }
        return "scale=" + width + ":" + height
                + ":force_original_aspect_ratio=decrease:flags=lanczos,pad="
                + width + ":" + height + ":(ow-iw)/2:(oh-ih)/2,setsar=1";
    }
}
