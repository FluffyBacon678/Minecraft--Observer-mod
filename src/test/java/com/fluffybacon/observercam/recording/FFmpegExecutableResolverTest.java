package com.fluffybacon.observercam.recording;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FFmpegExecutableResolverTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void keepsAnExplicitExecutable() throws IOException {
        Path executable = Files.createFile(temporaryDirectory.resolve("my-ffmpeg.exe"));
        assertEquals(executable.toAbsolutePath().normalize().toString(),
                FFmpegExecutableResolver.resolve(executable.toString(), "Windows 11", temporaryDirectory));
    }

    @Test
    void findsWingetFfmpegWhenPathCommandIsUnavailable() throws IOException {
        Path executable = temporaryDirectory.resolve("Microsoft/WinGet/Packages")
                .resolve("Gyan.FFmpeg_TestSource/ffmpeg-9-full_build/bin/ffmpeg.exe");
        Files.createDirectories(executable.getParent());
        Files.createFile(executable);

        assertEquals(executable.toAbsolutePath().normalize().toString(),
                FFmpegExecutableResolver.resolve("ffmpeg", "Windows 11", temporaryDirectory));
    }

    @Test
    void leavesCommandAloneWhenNoSafeInstallIsFound() {
        assertEquals("ffmpeg", FFmpegExecutableResolver.resolve("", "Windows 11", temporaryDirectory));
        assertEquals("custom-command",
                FFmpegExecutableResolver.resolve("custom-command", "Windows 11", temporaryDirectory));
    }
}
