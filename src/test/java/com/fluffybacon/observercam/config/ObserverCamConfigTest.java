package com.fluffybacon.observercam.config;

import com.fluffybacon.observercam.config.ObserverCamConfig.CameraSettings;
import com.fluffybacon.observercam.recording.InstantReplayLimitMode;
import com.fluffybacon.observercam.recording.RecordingVideoFormat;
import com.fluffybacon.observercam.recording.RecordingQuality;
import com.fluffybacon.observercam.recording.RecordingResolution;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
        assertEquals("ffmpeg", config.recordingFfmpegPath);
        assertFalse(config.instantReplayEnabled);
        assertEquals(InstantReplayLimitMode.TIME, config.instantReplayLimitMode);
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
    }
}
