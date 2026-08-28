package com.fluffybacon.observercam.recording;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/** Identifies Observer Cam-owned replay directories before any cleanup operation. */
public final class ReplayBufferFiles {
    public static final String ROOT_NAME = ".observercam-replay-buffer";
    public static final String OWNER_MARKER = ".observercam-owned-buffer";
    public static final String OWNER_MARKER_CONTENT = "Observer Cam instant replay buffer v1\n";
    private static final String SESSION_NAME_PATTERN =
            "session-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    private ReplayBufferFiles() {
    }

    public static Path root(Path outputDirectory) {
        if (outputDirectory == null) {
            throw new IllegalArgumentException("Output directory must not be null");
        }
        return outputDirectory.resolve(ROOT_NAME).toAbsolutePath().normalize();
    }

    public static Path marker(Path sessionDirectory) {
        return sessionDirectory.resolve(OWNER_MARKER);
    }

    public static boolean isOwnedSession(Path bufferRoot, Path sessionDirectory) {
        if (bufferRoot == null || sessionDirectory == null) {
            return false;
        }
        Path normalizedRoot = bufferRoot.toAbsolutePath().normalize();
        Path normalizedSession = sessionDirectory.toAbsolutePath().normalize();
        Path sessionName = normalizedSession.getFileName();
        Path ownerMarker = marker(normalizedSession);
        if (!normalizedRoot.equals(normalizedSession.getParent()) || sessionName == null
                || !sessionName.toString().matches(SESSION_NAME_PATTERN)
                || Files.isSymbolicLink(normalizedRoot) || Files.isSymbolicLink(normalizedSession)
                || Files.isSymbolicLink(ownerMarker)
                || !Files.isDirectory(normalizedSession, LinkOption.NOFOLLOW_LINKS)
                || !Files.isRegularFile(ownerMarker, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        try {
            byte[] expected = OWNER_MARKER_CONTENT.getBytes(StandardCharsets.UTF_8);
            return Files.size(ownerMarker) == expected.length
                    && Files.readString(ownerMarker, StandardCharsets.UTF_8).equals(OWNER_MARKER_CONTENT);
        } catch (IOException | SecurityException ignored) {
            return false;
        }
    }
}
