package com.fluffybacon.observercam.recording;

/** On-screen chrome around the live PiP feed. */
public enum PictureInPictureFrameStyle {
    COMPACT("compact", false),
    LABELED("labeled", true);

    private final String id;
    private final boolean statusBarVisible;

    PictureInPictureFrameStyle(String id, boolean statusBarVisible) {
        this.id = id;
        this.statusBarVisible = statusBarVisible;
    }

    public String id() {
        return id;
    }

    public boolean statusBarVisible() {
        return statusBarVisible;
    }

    public PictureInPictureFrameStyle next() {
        PictureInPictureFrameStyle[] styles = values();
        return styles[(ordinal() + 1) % styles.length];
    }
}
