package com.fluffybacon.observercam.recording;

import com.fluffybacon.observercam.config.ObserverCamConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Disk-budget guard shared by the live recorder and future instant-replay buffer.
 * Writers check this guard before starting and while allocating recording data.
 */
public final class RecordingStorageBudget {
    public static final long BYTES_PER_GB = 1_000_000_000L;
    private static final Pattern OWNED_RECORDING_NAME = Pattern.compile(
            "observercam_(?:replay_)?\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}-\\d{3}"
                    + "(?:_\\d+)?(?:\\.partial)?\\.(?:mp4|mkv|webm)|"
                    + "observercam_(?:replay_)?\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}-\\d{3}"
                    + "(?:_\\d+)?\\.ffmpeg\\.log");

    private RecordingStorageBudget() {
    }

    public static long configuredLimitBytes() {
        return Math.round(ObserverCamConfig.get().recordingStorageLimitGb * BYTES_PER_GB);
    }

    public static long usedBytes(Path recordingDirectory) throws IOException {
        if (!Files.isDirectory(recordingDirectory)) {
            return 0L;
        }
        try {
            long total = 0L;
            try (var children = Files.list(recordingDirectory)) {
                for (Path child : children.toList()) {
                    if (isOwnedRecording(child)) {
                        total = Math.addExact(total, Files.size(child));
                    } else if (isReplayRoot(child)) {
                        total = Math.addExact(total, replayBytes(child));
                    }
                }
            }
            return total;
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    private static boolean isOwnedRecording(Path file) {
        Path name = file == null ? null : file.getFileName();
        return name != null
                && !Files.isSymbolicLink(file)
                && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                && OWNED_RECORDING_NAME.matcher(name.toString()).matches();
    }

    private static boolean isReplayRoot(Path directory) {
        Path name = directory == null ? null : directory.getFileName();
        return name != null
                && ReplayBufferFiles.ROOT_NAME.equals(name.toString())
                && !Files.isSymbolicLink(directory)
                && Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS);
    }

    private static long replayBytes(Path replayRoot) throws IOException {
        long total = 0L;
        try (var sessions = Files.list(replayRoot)) {
            for (Path session : sessions.toList()) {
                if (!ReplayBufferFiles.isOwnedSession(replayRoot, session)) {
                    continue;
                }
                try (var paths = Files.walk(session)) {
                    for (Path file : paths.toList()) {
                        if (!Files.isSymbolicLink(file)
                                && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                            total = Math.addExact(total, Files.size(file));
                        }
                    }
                }
            }
        }
        return total;
    }

    public static long remainingBytes(Path recordingDirectory) throws IOException {
        return Math.max(0L, configuredLimitBytes() - usedBytes(recordingDirectory));
    }

    public static boolean canAllocate(Path recordingDirectory, long requestedBytes) throws IOException {
        return requestedBytes >= 0L && requestedBytes <= remainingBytes(recordingDirectory);
    }
}
