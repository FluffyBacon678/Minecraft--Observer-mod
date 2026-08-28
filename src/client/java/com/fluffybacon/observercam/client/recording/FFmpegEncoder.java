package com.fluffybacon.observercam.client.recording;

import com.fluffybacon.observercam.recording.FFmpegCommand;
import com.fluffybacon.observercam.recording.RecordingAudio;
import com.fluffybacon.observercam.recording.RecordingQuality;
import com.fluffybacon.observercam.recording.RecordingResolution;
import com.fluffybacon.observercam.recording.RecordingTimeline;
import com.fluffybacon.observercam.recording.RecordingVideoFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

final class FFmpegEncoder {
    private static final Logger LOGGER = LoggerFactory.getLogger("ObserverCam/Recorder");
    private static final int QUEUE_CAPACITY = 3;
    private static final byte[] END_OF_STREAM = new byte[0];
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS");

    private final BlockingQueue<byte[]> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    private final AtomicBoolean accepting = new AtomicBoolean();
    private final AtomicLong acceptedFrames = new AtomicLong();
    private final AtomicLong writtenFrames = new AtomicLong();
    private final AtomicLong droppedFrames = new AtomicLong();
    private final AtomicLong paddedFrames = new AtomicLong();
    private final AtomicReference<Throwable> writerFailure = new AtomicReference<>();
    private final String executable;
    private final RecordingVideoFormat format;
    private final RecordingResolution resolution;
    private final RecordingQuality quality;
    private final RecordingAudio audio;
    private final int width;
    private final int height;
    private final int framesPerSecond;
    private final Path finalFile;
    private final Path partialFile;
    private final Path errorLog;

    private Process process;
    private Thread writerThread;
    private byte[] lastFrame;

    FFmpegEncoder(String executable, RecordingVideoFormat format, RecordingResolution resolution,
                  RecordingQuality quality, RecordingAudio audio, int width, int height,
                  int framesPerSecond, Path outputDirectory) throws IOException {
        this.executable = executable;
        this.format = format;
        this.resolution = resolution;
        this.quality = quality;
        this.audio = audio;
        this.width = width;
        this.height = height;
        this.framesPerSecond = framesPerSecond;
        Files.createDirectories(outputDirectory);
        String baseName = uniqueBaseName(outputDirectory);
        this.finalFile = outputDirectory.resolve(baseName + "." + format.id());
        this.partialFile = outputDirectory.resolve(baseName + ".partial." + format.id());
        this.errorLog = outputDirectory.resolve(baseName + ".ffmpeg.log");
    }

    void start() throws IOException {
        ProcessBuilder builder = new ProcessBuilder(FFmpegCommand.build(executable, format,
                resolution, quality, audio, width, height, framesPerSecond, partialFile));
        builder.redirectError(errorLog.toFile());
        try {
            process = builder.start();
            accepting.set(true);
            writerThread = new Thread(this::writeFrames, "ObserverCam-FFmpeg-Writer");
            writerThread.setDaemon(true);
            writerThread.start();
        } catch (IOException | RuntimeException exception) {
            cleanupFailedStart(exception);
            throw exception;
        }
    }

    private void cleanupFailedStart(Exception failure) {
        accepting.set(false);
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
            try {
                process.waitFor(2L, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                failure.addSuppressed(exception);
            }
        }
        try {
            Files.deleteIfExists(errorLog);
        } catch (IOException exception) {
            failure.addSuppressed(exception);
        }
    }

    boolean submit(byte[] frame) {
        if (!accepting.get()) {
            return false;
        }
        lastFrame = frame;
        if (!queue.offer(frame)) {
            droppedFrames.incrementAndGet();
            return false;
        }
        acceptedFrames.incrementAndGet();
        return true;
    }

    RecordingResult stop(long expectedFrameCount) {
        accepting.set(false);
        padTimeline(expectedFrameCount);
        signalEndOfStream();
        joinWriter();
        int exitCode = awaitProcess();
        Throwable failure = writerFailure.get();
        boolean successful = failure == null && exitCode == 0 && writtenFrames.get() > 0L;
        if (successful) {
            try {
                moveCompletedFile();
            } catch (IOException exception) {
                failure = exception;
            }
            if (failure == null) {
                try {
                    Files.deleteIfExists(errorLog);
                } catch (IOException exception) {
                    LOGGER.warn("Recording was saved, but its empty FFmpeg log could not be removed: {}",
                            errorLog, exception);
                }
                return new RecordingResult(true, finalFile, null, acceptedFrames.get(),
                        writtenFrames.get(), droppedFrames.get(), paddedFrames.get());
            }
        }

        String message = failure != null && failure.getMessage() != null
                ? failure.getMessage()
                : "FFmpeg exited with code " + exitCode;
        writeFailureSummary(message);
        return new RecordingResult(false, partialFile, message, acceptedFrames.get(),
                writtenFrames.get(), droppedFrames.get(), paddedFrames.get());
    }

    private void padTimeline(long expectedFrameCount) {
        byte[] frame = lastFrame;
        int requested = RecordingTimeline.finalPaddingFrames(
                expectedFrameCount, acceptedFrames.get(), framesPerSecond);
        if (frame == null || requested <= 0 || writerThread == null || !writerThread.isAlive()) {
            return;
        }
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3L);
        int enqueued = 0;
        while (enqueued < requested && System.nanoTime() < deadline) {
            try {
                if (!queue.offer(frame, 100L, TimeUnit.MILLISECONDS)) {
                    continue;
                }
                acceptedFrames.incrementAndGet();
                paddedFrames.incrementAndGet();
                enqueued++;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    boolean failedWhileRecording() {
        return accepting.get() && (writerFailure.get() != null || process != null && !process.isAlive());
    }

    long droppedFrames() {
        return droppedFrames.get();
    }

    long acceptedFrames() {
        return acceptedFrames.get();
    }

    long outputBytes() {
        try {
            return Files.isRegularFile(partialFile) ? Files.size(partialFile) : 0L;
        } catch (IOException ignored) {
            return 0L;
        }
    }

    Path partialFile() {
        return partialFile;
    }

    private void writeFrames() {
        try (OutputStream output = process.getOutputStream()) {
            while (true) {
                byte[] frame = queue.take();
                if (frame == END_OF_STREAM) {
                    break;
                }
                output.write(frame);
                writtenFrames.incrementAndGet();
            }
            output.flush();
        } catch (IOException exception) {
            writerFailure.compareAndSet(null, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            writerFailure.compareAndSet(null, exception);
        }
    }

    private void signalEndOfStream() {
        if (writerThread == null || !writerThread.isAlive()) {
            return;
        }
        try {
            if (!queue.offer(END_OF_STREAM, 2L, TimeUnit.SECONDS)) {
                writerFailure.compareAndSet(null, new IOException("Frame queue did not drain in time"));
                if (process != null) {
                    process.destroyForcibly();
                }
                queue.clear();
                queue.offer(END_OF_STREAM);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            writerFailure.compareAndSet(null, exception);
            if (process != null) {
                process.destroyForcibly();
            }
        }
    }

    private void joinWriter() {
        if (writerThread == null) {
            return;
        }
        try {
            writerThread.join(TimeUnit.SECONDS.toMillis(20));
            if (writerThread.isAlive()) {
                writerFailure.compareAndSet(null, new IOException("Frame writer did not stop in time"));
                writerThread.interrupt();
                if (process != null) {
                    process.destroyForcibly();
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            writerFailure.compareAndSet(null, exception);
        }
    }

    private int awaitProcess() {
        if (process == null) {
            return -1;
        }
        try {
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                writerFailure.compareAndSet(null, new IOException("FFmpeg did not finalize in time"));
                return -1;
            }
            return process.exitValue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            writerFailure.compareAndSet(null, exception);
            return -1;
        }
    }

    private void moveCompletedFile() throws IOException {
        try {
            Files.move(partialFile, finalFile, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(partialFile, finalFile);
        }
    }

    private void writeFailureSummary(String message) {
        try {
            String summary = System.lineSeparator() + "Observer Cam: " + message + System.lineSeparator();
            Files.writeString(errorLog, summary, java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }

    private static String uniqueBaseName(Path outputDirectory) {
        String stamp = LocalDateTime.now().format(FILE_STAMP);
        String base = "observercam_" + stamp;
        int suffix = 1;
        while (baseExists(outputDirectory, base)) {
            base = "observercam_" + stamp + "_" + suffix++;
        }
        return base;
    }

    private static boolean baseExists(Path outputDirectory, String base) {
        return Files.exists(outputDirectory.resolve(base + ".mp4"))
                || Files.exists(outputDirectory.resolve(base + ".mkv"))
                || Files.exists(outputDirectory.resolve(base + ".webm"))
                || Files.exists(outputDirectory.resolve(base + ".partial.mp4"))
                || Files.exists(outputDirectory.resolve(base + ".partial.mkv"))
                || Files.exists(outputDirectory.resolve(base + ".partial.webm"))
                || Files.exists(outputDirectory.resolve(base + ".ffmpeg.log"));
    }

    record RecordingResult(boolean successful, Path path, String error, long acceptedFrames,
                           long writtenFrames, long droppedFrames, long paddedFrames) {
    }
}
