package com.fluffybacon.observercam.recording;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecordingOutputPresetTest {
    @Test
    void currentResolutionOnlyPadsOddDimensions() {
        assertEquals("pad=ceil(iw/2)*2:ceil(ih/2)*2", RecordingResolution.CURRENT.videoFilter());
    }

    @Test
    void fixedResolutionPreservesAspectRatioAndPadsCanvas() {
        String filter = RecordingResolution.FULL_HD_1080.videoFilter();
        assertTrue(filter.contains("scale=1920:1080"));
        assertTrue(filter.contains("force_original_aspect_ratio=decrease"));
        assertTrue(filter.contains("pad=1920:1080"));
        assertTrue(filter.endsWith("setsar=1"));
    }

    @Test
    void qualityPresetsUseCodecAppropriateCrfValues() {
        assertEquals(18, RecordingQuality.HIGH.crf(RecordingVideoFormat.MP4));
        assertEquals(28, RecordingQuality.HIGH.crf(RecordingVideoFormat.WEBM));
        assertEquals(20, RecordingQuality.BALANCED.crf(RecordingVideoFormat.MKV));
        assertEquals(38, RecordingQuality.SMALL.crf(RecordingVideoFormat.WEBM));
    }
}
