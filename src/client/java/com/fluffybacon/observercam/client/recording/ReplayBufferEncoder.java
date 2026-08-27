package com.fluffybacon.observercam.client.recording;

import com.fluffybacon.observercam.recording.FFmpegCommand;
import com.fluffybacon.observercam.recording.InstantReplayLimitMode;
import com.fluffybacon.observercam.recording.RecordingAudio;
import com.fluffybacon.observercam.recording.RecordingVideoFormat;
import com.fluffybacon.observercam.recording.RecordingQuality;
import com.fluffybacon.observercam.recording.RecordingResolution;
import com.fluffybacon.observercam.recording.ReplayBufferPolicy;
import com.fluffybacon.observercam.recording.ReplayBufferFiles;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

final class ReplayBufferEncoder {
    private static final int QUEUE_CAPACITY = 3;
    private static final byte[] END_OF_STREAM = new byte[0];

    private final BlockingQueue<byte[]> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    private final AtomicBoolean accepting = new AtomicBoolean();
    private final AtomicLong acceptedFrames = new AtomicLong();
    private final AtomicLong writtenFrames = new AtomicLong();
    private final AtomicLong droppedFrames = new AtomicLong();
    private final AtomicReference<Throwable> writerFailure = new AtomicReference<>();
    private final String executable;
    private final RecordingVideoFormat format;
    private final RecordingResolution resolution;
    private final RecordingQuality quality;
    private final RecordingAudio audio;
    private final int width;
    private final int height;
    private final int framesPerSecond;
    private final Path bufferRoot;
    private final Path sessionDirectory;
    private final Path errorLog;
    private final Path segmentPattern;
    private final String segmentExtension;

    private Process process;
    private Thread writerThread;

    ReplayBufferEncoder(String executable, RecordingVideoFormat format, RecordingResolution resolution,
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
        bufferRoot = ReplayBufferFiles.root(outputDirectory);
        Files.createDirectories(bufferRoot);
        sessionDirectory = bufferRoot.resolve("session-" + UUID.randomUUID()).normalize();
        if (!sessionDirectory.getParent().equals(bufferRoot)) {
            throw new IOException("Unsafe replay buffer path");
        }
        Files.createDirectories(sessionDirectory);
        Files.writeString(ReplayBufferFiles.marker(sessionDirectory), "Observer Cam instant replay buffer v1\n");
        errorLog = sessionDirectory.resolve("buffer.ffmpeg.log");
        segmentExtension = FFmpegCommand.replaySegmentExtension(format);
        segmentPattern = sessionDirectory.resolve("segment-%08d." + segmentExtension);
    }

    void start() throws IOException {
        ProcessBuilder builder = new ProcessBuilder(FFmpegCommand.buildReplayBuffer(
                executable, format, resolution, quality, audio, width, height, framesPerSecond, segmentPattern));
        builder.redirectError(errorLog.toFile());
        try {
            process = builder.start();
        } catch (IOException exception) {
            deleteOwnedSession(sessionDirectory);
            throw exception;
        }
        accepting.set(true);
        writerThread = new Thread(this::writeFrames, "ObserverCam-Replay-Writer");
        writerThread.setDaemon(true);
        writerThread.start();
    }

    boolean submit(byte[] frame) {
        if (!accepting.get()) {
            return false;
        }
        if (!queue.offer(frame)) {
            droppedFrames.incrementAndGet();
            return false;
        }
        acceptedFrames.incrementAndGet();
        return true;
    }

    ReplaySnapshot stop() {
        accepting.set(false);
        signalEndOfStream();
        joinWriter();
        int exitCode = awaitProcess();
        Throwable failure = writerFailure.get();
        List<Path> segments;
        try {
            segments = listSegments();
        } catch (IOException exception) {
            segments = List.of();
            if (failure == null) {
                failure = exception;
            }
        }
        boolean successful = failure == null && exitCode == 0 && writtenFrames.get() > 0L && !segments.isEmpty();
        if (successful) {
            try {
                Files.deleteIfExists(errorLog);
            } catch (IOException ignored) {
            }
        }
        String message = failure != null && failure.getMessage() != null
                ? failure.getMessage()
                : successful ? null : "FFmpeg exited with code " + exitCode;
        if (!successful) {
            appendFailureSummary(message);
        }
        return new ReplaySnapshot(successful, sessionDirectory, segments, message,
                acceptedFrames.get(), writtenFrames.get(), droppedFrames.get());
    }

    boolean failedWhileBuffering() {
        return accepting.get() && (writerFailure.get() != null || process != null && !process.isAlive());
    }

    long acceptedFrames() {
        return acceptedFrames.get();
    }

    long droppedFrames() {
        return droppedFrames.get();
    }

    long outputBytes() {
        try {
            return ReplayBufferPolicy.saturatedSum(segmentSizes(listSegments()));
        } catch (IOException ignored) {
            return 0L;
        }
    }

    PruneResult prune(InstantReplayLimitMode mode, double durationMinutes, long sizeLimitBytes,
                      long additionalBytesToFree) throws IOException {
        List<Path> segments = listSegments();
        List<Long> sizes = segmentSizes(segments);
        long initialBytes = ReplayBufferPolicy.saturatedSum(sizes);
        int retentionDeletes = ReplayBufferPolicy.segmentsToDelete(mode, durationMinutes, sizeLimitBytes,
                FFmpegCommand.REPLAY_SEGMENT_SECONDS, sizes);
        deleteFirst(segments, retentionDeletes);

        segments = listSegments();
        sizes = segmentSizes(segments);
        long retainedBytes = ReplayBufferPolicy.saturatedSum(sizes);
        long remainingRequest = Math.max(0L, additionalBytesToFree
                - Math.max(0L, initialBytes - retainedBytes));
        int headroomDeletes = ReplayBufferPolicy.completedSegmentsToFree(sizes, remainingRequest);
        deleteFirst(segments, headroomDeletes);

        List<Long> remainingSizes = segmentSizes(listSegments());
        return new PruneResult(ReplayBufferPolicy.saturatedSum(remainingSizes),
                retentionDeletes + headroomDeletes, !remainingSizes.isEmpty());
    }

    Path sessionDirectory() {
        return sessionDirectory;
    }

    static void cleanupStaleBuffers(Path outputDirectory) throws IOException {
        Path root = ReplayBufferFiles.root(outputDirectory);
        if (!Files.isDirectory(root)) {
            return;
        }
        try (var children = Files.list(root)) {
            for (Path child : children.filter(Files::isDirectory).toList()) {
                deleteOwnedSession(child);
            }
        }
        try (var children = Files.list(root)) {
            if (children.findAny().isEmpty()) {
                Files.deleteIfExists(root);
            }
        }
    }

    static boolean deleteOwnedSession(Path sessionDirectory) throws IOException {
        if (sessionDirectory == null) {
            return false;
        }
        Path normalized = sessionDirectory.toAbsolutePath().normalize();
        Path parent = normalized.getParent();
        if (parent == null || !ReplayBufferFiles.ROOT_NAME.equals(parent.getFileName().toString())
                || !ReplayBufferFiles.isOwnedSession(parent, normalized)) {
            return false;
        }
        try (var paths = Files.walk(normalized)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
        return true;
    }

    private List<Path> listSegments() throws IOException {
        if (!Files.isDirectory(sessionDirectory)) {
            return List.of();
        }
        String suffix = "." + segmentExtension;
        try (var paths = Files.list(sessionDirectory)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith("segment-"))
                    .filter(path -> path.getFileName().toString().endsWith(suffix))
                    .filter(path -> {
                        try {
                            return Files.size(path) > 0L;
                        } catch (IOException ignored) {
                            return false;
                        }
                    })
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
    }

    private static List<Long> segmentSizes(List<Path> segments) throws IOException {
        List<Long> sizes = new ArrayList<>(segments.size());
        for (Path segment : segments) {
            sizes.add(Files.size(segment));
        }
        return sizes;
    }

    private static void deleteFirst(List<Path> segments, int count) throws IOException {
        int safeCount = Math.min(count, Math.max(0, segments.size() - 1));
        for (int index = 0; index < safeCount; index++) {
            Files.deleteIfExists(segments.get(index));
        }
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
                writerFailure.compareAndSet(null, new IOException("Replay frame queue did not drain in time"));
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
                writerFailure.compareAndSet(null, new IOException("Replay frame writer did not stop in time"));
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
                writerFailure.compareAndSet(null, new IOException("Replay encoder did not finalize in time"));
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

    private void appendFailureSummary(String message) {
        try {
            Files.writeString(errorLog, System.lineSeparator() + "Observer Cam: " + message
                    + System.lineSeparator(), java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }

    record ReplaySnapshot(boolean successful, Path sessionDirectory, List<Path> segments, String error,
                          long acceptedFrames, long writtenFrames, long droppedFrames) {
    }

    record PruneResult(long remainingBytes, int deletedSegments, boolean hasActiveSegment) {
    }
}
