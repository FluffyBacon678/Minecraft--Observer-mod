package com.fluffybacon.observercam.config;

import com.fluffybacon.observercam.assistant.AssistantFactScheduler;
import com.fluffybacon.observercam.config.ObserverCamConfig.CameraSettings;
import com.fluffybacon.observercam.recording.InstantReplayLimitMode;
import com.fluffybacon.observercam.recording.PictureInPictureFrameStyle;
import com.fluffybacon.observercam.recording.PictureInPictureResolution;
import com.fluffybacon.observercam.recording.PictureInPictureSize;
import com.fluffybacon.observercam.recording.RecordingQuality;
import com.fluffybacon.observercam.recording.RecordingResolution;
import com.fluffybacon.observercam.recording.RecordingVideoFormat;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObserverCamConfigTest {
    @TempDir
    Path gameDirectory;

    @Test
    void clientCameraSettingsAreFiniteAndClampedBeforeServerUse() {
        CameraSettings untrusted = new CameraSettings(
                Double.NaN, 100.0, -20.0, -10.0, 99.0, Double.POSITIVE_INFINITY,
                99.0, -4.0, 400.0, 0.0, 4.0, 100.0, 1.0,
                99.0, -20.0, 4.0, 1_000.0, 0.0, 100.0,
                false, false
        );

        ObserverCamConfig config = ObserverCamConfig.fromCameraSettings(untrusted);

        assertEquals(8.0, config.outdoorDistance);
        assertEquals(10.0, config.indoorDistance);
        assertEquals(2.0, config.minimumDistance);
        assertEquals(2.0, config.maximumDistance);
        assertEquals(8.0, config.cameraHeight);
        assertEquals(70.0, config.cameraFov);
        assertEquals(2.0, config.maximumSpeed);
        assertEquals(0.01, config.acceleration);
        assertEquals(30.0, config.rotationSpeed);
        assertEquals(40.0, config.catchUpDistance);
        assertEquals(42.0, config.emergencyTeleportDistance);
        assertEquals(12.0, config.movementPredictionTicks);
        assertFalse(config.followTargetAutomatically);
        assertFalse(config.allowFrontFacingShots);
    }

    @Test
    void recordingDirectoryUsesSafeDefaultAndResolvesRelativeChoices() {
        assertEquals(gameDirectory.resolve("observercam").resolve("recordings"),
                ObserverCamConfig.resolveRecordingOutputDirectory("", gameDirectory));
        assertEquals(gameDirectory.resolve("captures"),
                ObserverCamConfig.resolveRecordingOutputDirectory("captures", gameDirectory));
        assertEquals(gameDirectory.resolve("observercam").resolve("recordings"),
                ObserverCamConfig.resolveRecordingOutputDirectory("bad\0path", gameDirectory));
    }

    @Test
    void recordingDefaultsAreConservative() {
        ObserverCamConfig config = new ObserverCamConfig();

        assertEquals(3.0, config.recordingStorageLimitGb);
        assertEquals(RecordingVideoFormat.MP4, config.recordingVideoFormat);
        assertEquals(RecordingResolution.CURRENT, config.recordingResolution);
        assertEquals(RecordingQuality.BALANCED, config.recordingQuality);
        assertEquals(30, config.recordingFrameRate);
        assertFalse(config.recordingIncludeHud);
        assertEquals(PictureInPictureSize.BIG, config.pictureInPictureSize);
        assertEquals(PictureInPictureFrameStyle.COMPACT, config.pictureInPictureFrameStyle);
        assertEquals(PictureInPictureResolution.BALANCED, config.pictureInPictureResolution);
        assertEquals(5, config.pictureInPictureFrameRate);
        assertEquals(1.0, config.pictureInPictureOpacity);
        assertEquals("ffmpeg", config.recordingFfmpegPath);
        assertFalse(config.instantReplayEnabled);
        assertEquals(InstantReplayLimitMode.TIME, config.instantReplayLimitMode);
        assertEquals(2.0, config.instantReplayDurationMinutes);
        assertEquals(1.0, config.instantReplayStorageLimitGb);
    }

    @Test
    void assistantDefaultsAreQuietAndFactTimingIsBounded() {
        ObserverCamConfig config = new ObserverCamConfig();

        assertFalse(config.assistantEnabled);
        assertTrue(config.assistantFactsEnabled);
        assertTrue(config.assistantSpeechBubbleEnabled);
        assertTrue(config.assistantShowFactsInChat);
        assertFalse(config.assistantReadFactsAloud);
        assertEquals(AssistantFactScheduler.DEFAULT_INTERVAL_MINUTES,
                config.assistantFactIntervalMinutes);

        config.assistantFactIntervalMinutes = 50.0;
        config.cameraSettings();
        assertEquals(AssistantFactScheduler.MAXIMUM_INTERVAL_MINUTES,
                config.assistantFactIntervalMinutes);
    }

    @Test
    void localConfigurationReplacesNonFiniteNumbersWithDefaults() {
        ObserverCamConfig config = new ObserverCamConfig();
        config.outdoorDistance = Double.NaN;
        config.cameraFov = Double.POSITIVE_INFINITY;
        config.maximumSpeed = Double.NEGATIVE_INFINITY;
        config.recordingStorageLimitGb = Double.NaN;
        config.pictureInPictureOpacity = Double.NaN;
        config.instantReplayDurationMinutes = Double.POSITIVE_INFINITY;
        config.instantReplayStorageLimitGb = Double.NaN;

        config.cameraSettings();

        assertEquals(8.0, config.outdoorDistance);
        assertEquals(70.0, config.cameraFov);
        assertEquals(0.65, config.maximumSpeed);
        assertEquals(3.0, config.recordingStorageLimitGb);
        assertEquals(1.0, config.pictureInPictureOpacity);
        assertEquals(2.0, config.instantReplayDurationMinutes);
        assertEquals(1.0, config.instantReplayStorageLimitGb);
    }

    @Test
    void recordingChoicesCyclePredictably() {
        assertEquals(RecordingVideoFormat.MKV, RecordingVideoFormat.MP4.next());
        assertEquals(RecordingVideoFormat.WEBM, RecordingVideoFormat.MKV.next());
        assertEquals(RecordingVideoFormat.MP4, RecordingVideoFormat.WEBM.next());
        assertEquals(InstantReplayLimitMode.SIZE, InstantReplayLimitMode.TIME.next());
        assertEquals(InstantReplayLimitMode.TIME, InstantReplayLimitMode.SIZE.next());
        assertEquals(PictureInPictureSize.MEDIUM, PictureInPictureSize.SMALL.next());
        assertEquals(PictureInPictureSize.BIG, PictureInPictureSize.MEDIUM.next());
        assertEquals(PictureInPictureSize.SMALL, PictureInPictureSize.BIG.next());
        assertEquals(PictureInPictureFrameStyle.LABELED, PictureInPictureFrameStyle.COMPACT.next());
        assertEquals(PictureInPictureFrameStyle.COMPACT, PictureInPictureFrameStyle.LABELED.next());
        assertFalse(PictureInPictureFrameStyle.COMPACT.statusBarVisible());
        assertTrue(PictureInPictureFrameStyle.LABELED.statusBarVisible());
        assertEquals(PictureInPictureResolution.BALANCED, PictureInPictureResolution.PERFORMANCE.next());
    }

    @Test
    void highEndFrameRatesRemainOptionalButBounded() {
        ObserverCamConfig config = new ObserverCamConfig();
        config.recordingFrameRate = 500;
        config.pictureInPictureFrameRate = 500;

        config.cameraSettings();

        assertEquals(120, config.recordingFrameRate);
        assertEquals(60, config.pictureInPictureFrameRate);
    }

    @Test
    void pictureInPictureOpacityRemainsVisibleAndBounded() {
        ObserverCamConfig config = new ObserverCamConfig();
        config.pictureInPictureOpacity = 0.01;

        config.cameraSettings();
        assertEquals(0.25, config.pictureInPictureOpacity);

        config.pictureInPictureOpacity = 4.0;
        config.cameraSettings();
        assertEquals(1.0, config.pictureInPictureOpacity);
    }

    @Test
    void olderConfigurationsReceiveSafePictureInPictureDisplayDefaults() {
        ObserverCamConfig migrated = new Gson().fromJson("{}", ObserverCamConfig.class);
        assertEquals(PictureInPictureSize.BIG, migrated.pictureInPictureSize);
        assertEquals(PictureInPictureFrameStyle.COMPACT, migrated.pictureInPictureFrameStyle);
        assertTrue(migrated.assistantSpeechBubbleEnabled);
        assertTrue(migrated.assistantShowFactsInChat);
        assertFalse(migrated.assistantReadFactsAloud);

        migrated.pictureInPictureSize = null;
        migrated.pictureInPictureFrameStyle = null;
        migrated.cameraSettings();
        assertEquals(PictureInPictureSize.BIG, migrated.pictureInPictureSize);
        assertEquals(PictureInPictureFrameStyle.COMPACT, migrated.pictureInPictureFrameStyle);
    }
}
