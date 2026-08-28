package com.fluffybacon.observercam.client.recording;

import com.fluffybacon.observercam.recording.FFmpegCommand;
import com.fluffybacon.observercam.recording.RecordingVideoFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;

final class ReplayExporter {
    private static final Logger LOGGER = LoggerFactory.getLogger("ObserverCam/Recorder");
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS");

    private ReplayExporter() {
    }

    static ExportResult export(String executable, RecordingVideoFormat format, Path outputDirectory,
                               ReplayBufferEncoder.ReplaySnapshot snapshot) {
        if (snapshot == null || !snapshot.successful() || snapshot.segments().isEmpty()) {
            return new ExportResult(false, snapshot == null ? null : snapshot.sessionDirectory(),
                    snapshot == null ? "Replay buffer was unavailable" : snapshot.error());
        }

        Process process = null;
        Path errorLog = null;
        try {
            Files.createDirectories(outputDirectory);
            String base = uniqueBaseName(outputDirectory, format);
            Path finalFile = outputDirectory.resolve(base + "." + format.id());
            Path partialFile = outputDirectory.resolve(base + ".partial." + format.id());
            errorLog = outputDirectory.resolve(base + ".ffmpeg.log");
            Path concatList = snapshot.sessionDirectory().resolve("segments.ffconcat");
            writeConcatList(concatList, snapshot.segments());

            ProcessBuilder builder = new ProcessBuilder(FFmpegCommand.buildReplayExport(
                    executable, format, concatList, partialFile));
            builder.redirectError(errorLog.toFile());
            process = builder.start();
            int exitCode;
            if (!process.waitFor(60L, TimeUnit.SECONDS)) {
                terminate(process);
                appendFailure(errorLog, "Replay export did not finish in time");
                return new ExportResult(false, snapshot.sessionDirectory(),
                        "Replay export did not finish in time");
            }
            exitCode = process.exitValue();
            if (exitCode != 0 || !Files.isRegularFile(partialFile) || Files.size(partialFile) == 0L) {
                String message = "FFmpeg replay export exited with code " + exitCode;
                appendFailure(errorLog, message);
                return new ExportResult(false, snapshot.sessionDirectory(), message);
            }

            moveCompletedFile(partialFile, finalFile);
            try {
                Files.deleteIfExists(errorLog);
            } catch (IOException exception) {
                LOGGER.warn("Replay was saved, but its empty FFmpeg log could not be removed: {}",
                        errorLog, exception);
            }
            try {
                if (!ReplayBufferEncoder.deleteOwnedSession(snapshot.sessionDirectory())) {
                    LOGGER.warn("Replay was saved, but its source buffer failed the ownership check and was preserved: {}",
                            snapshot.sessionDirectory());
                }
            } catch (IOException exception) {
                LOGGER.warn("Replay was saved, but its private source buffer could not be removed: {}",
                        snapshot.sessionDirectory(), exception);
            }
            return new ExportResult(true, finalFile, null);
        } catch (IOException | InterruptedException exception) {
            terminate(process);
            String message = safeMessage(exception);
            appendFailure(errorLog, message);
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new ExportResult(false, snapshot.sessionDirectory(), message);
        }
    }

    private static void terminate(Process process) {
        if (process == null || !process.isAlive()) {
            return;
        }
        process.destroy();
        try {
            if (!process.waitFor(2L, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(2L, TimeUnit.SECONDS);
            }
        } catch (InterruptedException exception) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }

    private static void writeConcatList(Path target, List<Path> segments) throws IOException {
        StringBuilder text = new StringBuilder("ffconcat version 1.0\n");
        for (Path segment : segments) {
            String name = segment.getFileName().toString();
            if (!name.matches("segment-[0-9]{8}\\.(ts|webm)")) {
                throw new IOException("Unexpected replay segment name");
            }
            text.append("file '").append(name).append("'\n");
        }
        Files.writeString(target, text);
    }

    private static String uniqueBaseName(Path outputDirectory, RecordingVideoFormat format) {
        String stamp = LocalDateTime.now().format(FILE_STAMP);
        String base = "observercam_replay_" + stamp;
        int suffix = 1;
        while (Files.exists(outputDirectory.resolve(base + "." + format.id()))
                || Files.exists(outputDirectory.resolve(base + ".partial." + format.id()))
                || Files.exists(outputDirectory.resolve(base + ".ffmpeg.log"))) {
            base = "observercam_replay_" + stamp + "_" + suffix++;
        }
        return base;
    }

    private static void moveCompletedFile(Path partialFile, Path finalFile) throws IOException {
        try {
            Files.move(partialFile, finalFile, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(partialFile, finalFile);
        }
    }

    private static void appendFailure(Path errorLog, String message) {
        if (errorLog == null) {
            return;
        }
        try {
            Files.writeString(errorLog, System.lineSeparator() + "Observer Cam: " + message
                    + System.lineSeparator(), java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }

    private static String safeMessage(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    record ExportResult(boolean successful, Path path, String error) {
    }
}
