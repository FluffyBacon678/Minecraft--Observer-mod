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
    void existingFilesReduceRemainingBudget() throws IOException {
        ObserverCamConfig.get().recordingStorageLimitGb = 3.0;
        Files.write(temporaryDirectory.resolve("one.part"), new byte[4]);
        Files.createDirectories(temporaryDirectory.resolve("nested"));
        Files.write(temporaryDirectory.resolve("nested/two.part"), new byte[6]);

        assertEquals(10L, RecordingStorageBudget.usedBytes(temporaryDirectory));
        assertEquals(2_999_999_990L, RecordingStorageBudget.remainingBytes(temporaryDirectory));
    }

    @Test
    void allocationCannotCrossCap() throws IOException {
        ObserverCamConfig.get().recordingStorageLimitGb = 3.0;
        assertTrue(RecordingStorageBudget.canAllocate(temporaryDirectory, 3_000_000_000L));
        assertFalse(RecordingStorageBudget.canAllocate(temporaryDirectory, 3_000_000_001L));
        assertFalse(RecordingStorageBudget.canAllocate(temporaryDirectory, -1L));
    }
}
