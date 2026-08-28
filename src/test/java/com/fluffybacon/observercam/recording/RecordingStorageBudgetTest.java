package com.fluffybacon.observercam.recording;

import com.fluffybacon.observercam.config.ObserverCamConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecordingStorageBudgetTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void defaultLimitIsThreeGigabytes() {
        ObserverCamConfig.get().recordingStorageLimitGb = 3.0;
        assertEquals(3_000_000_000L, RecordingStorageBudget.configuredLimitBytes());
    }

    @Test
    void ownedRecordingsReduceRemainingBudget() throws IOException {
        ObserverCamConfig.get().recordingStorageLimitGb = 3.0;
        Files.write(temporaryDirectory.resolve("observercam_2026-08-28_19-00-00-000.mp4"), new byte[4]);
        Files.write(temporaryDirectory.resolve("observercam_replay_2026-08-28_19-00-00-001.partial.mkv"),
                new byte[6]);

        assertEquals(10L, RecordingStorageBudget.usedBytes(temporaryDirectory));
        assertEquals(2_999_999_990L, RecordingStorageBudget.remainingBytes(temporaryDirectory));
    }

    @Test
    void unrelatedCaptureFilesNeverConsumeTheObserverCamBudget() throws IOException {
        ObserverCamConfig.get().recordingStorageLimitGb = 3.0;
        Files.write(temporaryDirectory.resolve("War Thunder 2026-08-12.mp4"), new byte[20]);
        Files.write(temporaryDirectory.resolve("observercam_notes.txt"), new byte[10]);
        Path nested = Files.createDirectories(temporaryDirectory.resolve("another-recorder"));
        Files.write(nested.resolve("observercam_2026-08-28_19-00-00-000.mp4"), new byte[30]);

        assertEquals(0L, RecordingStorageBudget.usedBytes(temporaryDirectory));
        assertEquals(3_000_000_000L, RecordingStorageBudget.remainingBytes(temporaryDirectory));
    }

    @Test
    void ownedReplayBufferConsumesTheSharedBudget() throws IOException {
        ObserverCamConfig.get().recordingStorageLimitGb = 3.0;
        Path root = Files.createDirectories(ReplayBufferFiles.root(temporaryDirectory));
        Path session = Files.createDirectories(root.resolve("session-01234567-89ab-cdef-0123-456789abcdef"));
        Files.writeString(ReplayBufferFiles.marker(session), ReplayBufferFiles.OWNER_MARKER_CONTENT);
        Files.write(session.resolve("segment-00000000.ts"), new byte[25]);

        long markerBytes = ReplayBufferFiles.OWNER_MARKER_CONTENT.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        assertEquals(markerBytes + 25L, RecordingStorageBudget.usedBytes(temporaryDirectory));
    }

    @Test
    void allocationCannotCrossCap() throws IOException {
        ObserverCamConfig.get().recordingStorageLimitGb = 3.0;
        assertTrue(RecordingStorageBudget.canAllocate(temporaryDirectory, 3_000_000_000L));
        assertFalse(RecordingStorageBudget.canAllocate(temporaryDirectory, 3_000_000_001L));
        assertFalse(RecordingStorageBudget.canAllocate(temporaryDirectory, -1L));
    }
}
