package com.fluffybacon.observercam.recording;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplayBufferFilesTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void recognizesOnlyMarkedDirectChildrenOfThePrivateRoot() throws Exception {
        Path root = ReplayBufferFiles.root(temporaryDirectory);
        Path owned = root.resolve("session-" + UUID.randomUUID());
        Path unmarked = root.resolve("session-" + UUID.randomUUID());
        Path nested = root.resolve("extra").resolve("session-" + UUID.randomUUID());
        Path wronglyNamed = root.resolve("session-owned");
        Path wrongMarker = root.resolve("session-" + UUID.randomUUID());
        Files.createDirectories(owned);
        Files.createDirectories(unmarked);
        Files.createDirectories(nested);
        Files.createDirectories(wronglyNamed);
        Files.createDirectories(wrongMarker);
        Files.writeString(ReplayBufferFiles.marker(owned), ReplayBufferFiles.OWNER_MARKER_CONTENT);
        Files.writeString(ReplayBufferFiles.marker(nested), ReplayBufferFiles.OWNER_MARKER_CONTENT);
        Files.writeString(ReplayBufferFiles.marker(wronglyNamed), ReplayBufferFiles.OWNER_MARKER_CONTENT);
        Files.writeString(ReplayBufferFiles.marker(wrongMarker), "not an Observer Cam marker");

        assertTrue(ReplayBufferFiles.isOwnedSession(root, owned));
        assertFalse(ReplayBufferFiles.isOwnedSession(root, unmarked));
        assertFalse(ReplayBufferFiles.isOwnedSession(root, nested));
        assertFalse(ReplayBufferFiles.isOwnedSession(root, wronglyNamed));
        assertFalse(ReplayBufferFiles.isOwnedSession(root, wrongMarker));
        assertFalse(ReplayBufferFiles.isOwnedSession(root, temporaryDirectory));
    }
}
