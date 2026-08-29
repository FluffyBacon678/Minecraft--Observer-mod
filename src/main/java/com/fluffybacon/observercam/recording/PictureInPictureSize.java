package com.fluffybacon.observercam.recording;

/** Display-size presets for the PiP HUD. Big preserves the original layout. */
public enum PictureInPictureSize {
    SMALL("small", 0.60),
    MEDIUM("medium", 0.80),
    BIG("big", 1.00);

    private static final int MINIMUM_BIG_WIDTH = 96;
    private static final int MAXIMUM_BIG_WIDTH = 200;
    private static final int MINIMUM_SCALED_WIDTH = 64;

    private final String id;
    private final double scale;

    PictureInPictureSize(String id, double scale) {
        this.id = id;
        this.scale = scale;
    }

    public String id() {
        return id;
    }

    public PictureInPictureSize next() {
        PictureInPictureSize[] sizes = values();
        return sizes[(ordinal() + 1) % sizes.length];
    }

    public int displayWidth(int guiWidth) {
        int bigWidth = Math.max(MINIMUM_BIG_WIDTH,
                Math.min(MAXIMUM_BIG_WIDTH, Math.max(0, guiWidth) / 3));
        return Math.max(MINIMUM_SCALED_WIDTH, (int) Math.round(bigWidth * scale));
    }
}
