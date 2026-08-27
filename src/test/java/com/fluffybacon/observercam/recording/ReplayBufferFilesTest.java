package com.fluffybacon.observercam.recording;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplayBufferFilesTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void recognizesOnlyMarkedDirectChildrenOfThePrivateRoot() throws Exception {
        Path root = ReplayBufferFiles.root(temporaryDirectory);
        Path owned = root.resolve("session-owned");
        Path unmarked = root.resolve("session-unmarked");
        Path nested = root.resolve("extra").resolve("session-nested");
        Files.createDirectories(owned);
        Files.createDirectories(unmarked);
        Files.createDirectories(nested);
        Files.writeString(ReplayBufferFiles.marker(owned), "owned");
        Files.writeString(ReplayBufferFiles.marker(nested), "owned");

        assertTrue(ReplayBufferFiles.isOwnedSession(root, owned));
        assertFalse(ReplayBufferFiles.isOwnedSession(root, unmarked));
        assertFalse(ReplayBufferFiles.isOwnedSession(root, nested));
        assertFalse(ReplayBufferFiles.isOwnedSession(root, temporaryDirectory));
    }
}
