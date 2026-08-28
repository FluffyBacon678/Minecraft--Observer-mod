package com.fluffybacon.observercam.recording;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CaptureSizeTest {
    @Test
    void fitsWideSourcesInsideBoundsWithoutChangingAspect() {
        assertEquals(new CaptureSize(480, 270), CaptureSize.fitInside(2560, 1440, 480, 270));
        assertEquals(new CaptureSize(480, 200), CaptureSize.fitInside(1920, 800, 480, 270));
    }

    @Test
    void recordingResolutionControlsTheRealCaptureSurface() {
        assertEquals(new CaptureSize(1280, 720), RecordingResolution.HD_720.captureSize(3840, 2160));
        assertEquals(new CaptureSize(1920, 1080), RecordingResolution.FULL_HD_1080.captureSize(1280, 720));
        assertEquals(new CaptureSize(1918, 1078), RecordingResolution.CURRENT.captureSize(1919, 1079));
    }

    @Test
    void pictureInPicturePresetsUseBoundedTargets() {
        assertEquals(new CaptureSize(320, 180),
                PictureInPictureResolution.PERFORMANCE.captureSize(3840, 2160));
        assertEquals(new CaptureSize(640, 360),
                PictureInPictureResolution.SHARP.captureSize(3840, 2160));
    }
}
