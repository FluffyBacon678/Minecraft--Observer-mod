package com.fluffybacon.observercam.recording;

/** An even-sized render surface fitted to a source aspect ratio. */
public record CaptureSize(int width, int height) {
    public CaptureSize {
        if (width < 2 || height < 2) {
            throw new IllegalArgumentException("Capture dimensions must be at least 2x2");
        }
    }

    public static CaptureSize even(int width, int height) {
        return new CaptureSize(evenFloor(width), evenFloor(height));
    }

    public static CaptureSize fitInside(int sourceWidth, int sourceHeight, int maximumWidth, int maximumHeight) {
        if (sourceWidth < 1 || sourceHeight < 1 || maximumWidth < 2 || maximumHeight < 2) {
            throw new IllegalArgumentException("Invalid capture bounds");
        }
        double scale = Math.min(maximumWidth / (double) sourceWidth,
                maximumHeight / (double) sourceHeight);
        return new CaptureSize(evenFloor((int) Math.floor(sourceWidth * scale)),
                evenFloor((int) Math.floor(sourceHeight * scale)));
    }

    private static int evenFloor(int value) {
        return Math.max(2, value & ~1);
    }
}
