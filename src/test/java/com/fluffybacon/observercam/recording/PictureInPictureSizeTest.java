package com.fluffybacon.observercam.recording;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PictureInPictureSizeTest {
    @Test
    void bigPreservesTheExistingResponsiveWindowSize() {
        assertEquals(200, PictureInPictureSize.BIG.displayWidth(1920));
        assertEquals(142, PictureInPictureSize.BIG.displayWidth(426));
        assertEquals(96, PictureInPictureSize.BIG.displayWidth(200));
    }

    @Test
    void smallerPresetsScaleDownWithoutBecomingUnreadable() {
        assertEquals(160, PictureInPictureSize.MEDIUM.displayWidth(1920));
        assertEquals(120, PictureInPictureSize.SMALL.displayWidth(1920));
        assertEquals(77, PictureInPictureSize.MEDIUM.displayWidth(200));
        assertEquals(64, PictureInPictureSize.SMALL.displayWidth(200));
    }
}
