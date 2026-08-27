package com.fluffybacon.observercam.recording;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;

/** Resolves FFmpeg from an explicit setting or Winget's package folder without bundling it. */
public final class FFmpegExecutableResolver {
    private FFmpegExecutableResolver() {
    }

    public static String resolve(String configured) {
        String localAppData = System.getenv("LOCALAPPDATA");
        Path localRoot = localAppData == null || localAppData.isBlank() ? null : Path.of(localAppData);
        return resolve(configured, System.getProperty("os.name", ""), localRoot);
    }

    static String resolve(String configured, String operatingSystem, Path localAppData) {
        String requested = configured == null || configured.isBlank() ? "ffmpeg" : configured.trim();
        try {
            Path explicit = Path.of(requested);
            if (Files.isRegularFile(explicit)) {
                return explicit.toAbsolutePath().normalize().toString();
            }
        } catch (InvalidPathException ignored) {
            return requested;
        }
        if (!(requested.equalsIgnoreCase("ffmpeg") || requested.equalsIgnoreCase("ffmpeg.exe"))
                || operatingSystem == null
                || !operatingSystem.toLowerCase(Locale.ROOT).contains("win")
                || localAppData == null) {
            return requested;
        }

        Path packages = localAppData.resolve("Microsoft").resolve("WinGet").resolve("Packages");
        if (!Files.isDirectory(packages)) {
            return requested;
        }
        try (var children = Files.list(packages)) {
            for (Path packageDirectory : children
                    .filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().startsWith("Gyan.FFmpeg_"))
                    .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed())
                    .toList()) {
                try (var files = Files.find(packageDirectory, 6,
                        (path, attributes) -> attributes.isRegularFile()
                                && path.getFileName().toString().equalsIgnoreCase("ffmpeg.exe"))) {
                    Path found = files.sorted().findFirst().orElse(null);
                    if (found != null) {
                        return found.toAbsolutePath().normalize().toString();
                    }
                }
            }
        } catch (IOException | SecurityException ignored) {
        }
        return requested;
    }
}
