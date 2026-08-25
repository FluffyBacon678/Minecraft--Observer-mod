package com.fluffybacon.observercam.recording;

import com.fluffybacon.observercam.config.ObserverCamConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Disk-budget guard reserved for the future recording subsystem.
 * Observer Cam does not record video yet; future writers must check this guard before allocating files.
 */
public final class RecordingStorageBudget {
    public static final long BYTES_PER_GB = 1_000_000_000L;

    private RecordingStorageBudget() {
    }

    public static long configuredLimitBytes() {
        return Math.round(ObserverCamConfig.get().recordingStorageLimitGb * BYTES_PER_GB);
    }

    public static long usedBytes(Path recordingDirectory) throws IOException {
        if (!Files.isDirectory(recordingDirectory)) {
            return 0L;
        }
        try (Stream<Path> paths = Files.walk(recordingDirectory)) {
            long total = 0L;
            var files = paths.filter(Files::isRegularFile).iterator();
            while (files.hasNext()) {
                Path path = files.next();
                total = Math.addExact(total, Files.size(path));
            }
            return total;
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    public static long remainingBytes(Path recordingDirectory) throws IOException {
        return Math.max(0L, configuredLimitBytes() - usedBytes(recordingDirectory));
    }

    public static boolean canAllocate(Path recordingDirectory, long requestedBytes) throws IOException {
        return requestedBytes >= 0L && requestedBytes <= remainingBytes(recordingDirectory);
    }
}
