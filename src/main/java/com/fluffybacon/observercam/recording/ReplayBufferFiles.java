package com.fluffybacon.observercam.recording;

import java.nio.file.Files;
import java.nio.file.Path;

/** Identifies Observer Cam-owned replay directories before any cleanup operation. */
public final class ReplayBufferFiles {
    public static final String ROOT_NAME = ".observercam-replay-buffer";
    public static final String OWNER_MARKER = ".observercam-owned-buffer";

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
        return normalizedRoot.equals(normalizedSession.getParent())
                && Files.isRegularFile(marker(normalizedSession));
    }
}
