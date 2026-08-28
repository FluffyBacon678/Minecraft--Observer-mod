package com.fluffybacon.observercam.client.recording;

import com.fluffybacon.observercam.client.ObserverCamClient;
import com.fluffybacon.observercam.client.ObserverRecordingState;
import com.fluffybacon.observercam.config.ObserverCamConfig;
import com.fluffybacon.observercam.recording.InstantReplayLimitMode;
import com.fluffybacon.observercam.recording.RecordingAudio;
import com.fluffybacon.observercam.recording.FFmpegExecutableResolver;
import com.fluffybacon.observercam.recording.RecordingStorageBudget;
import com.fluffybacon.observercam.recording.RecordingQuality;
import com.fluffybacon.observercam.recording.RecordingResolution;
import com.fluffybacon.observercam.recording.RecordingVideoFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class ObserverRecordingManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("ObserverCam/Recorder");
    private static final ObserverRecordingManager INSTANCE = new ObserverRecordingManager();
    private static final SystemToast.SystemToastId TOAST_ID = new SystemToast.SystemToastId(5_000L);
    private static final long MINIMUM_START_BUDGET = 128_000_000L;
    private static final long CAP_STOP_HEADROOM = 128_000_000L;
    private static final long DISK_FREE_RESERVE = 128_000_000L;
    private static final long STORAGE_CHECK_INTERVAL = TimeUnit.MILLISECONDS.toNanos(500L);
    private static final long LIVE_FINALIZER_SHUTDOWN_SECONDS = 60L;
    private static final long REPLAY_FINALIZER_SHUTDOWN_SECONDS = 120L;
    private static final long INTERRUPTED_FINALIZER_GRACE_SECONDS = 5L;

    private final AtomicReference<RecordingState> state = new AtomicReference<>(RecordingState.IDLE);
    private final AtomicReference<ReplayState> replayState = new AtomicReference<>(ReplayState.IDLE);
    private final AtomicLong sessionSequence = new AtomicLong();

    private volatile FFmpegEncoder encoder;
    private volatile ReplayBufferEncoder replayEncoder;
    private volatile long activeSession;
    private volatile long startNanos;
    private volatile long stoppedElapsedNanos;
    private volatile long replayStartNanos;
    private volatile long replayStoppedElapsedNanos;
    private volatile long nextCaptureNanos;
    private volatile long captureIntervalNanos;
    private volatile long timelineFrames;
    private volatile long lastStorageCheckNanos;
    private volatile long estimatedBytes;
    private volatile long replayEstimatedBytes;
    private volatile int sourceWidth;
    private volatile int sourceHeight;
    private volatile int framesPerSecond;
    private volatile boolean includeHud;
    private volatile boolean liveStartPending;
    private volatile boolean replayStartAnnounced;
    private volatile Path outputDirectory;
    private volatile Path cleanedReplayOutput;
    private volatile String activeExecutable;
    private volatile RecordingVideoFormat activeFormat;
    private volatile RecordingResolution activeResolution;
    private volatile RecordingQuality activeQuality;
    private volatile RecordingAudio activeAudio = RecordingAudio.disabled();
    private volatile Thread finalizerThread;
    private volatile Thread replayFinalizerThread;

    private ObserverRecordingManager() {
    }

    public static ObserverRecordingManager get() {
        return INSTANCE;
    }

    public RecordingState state() {
        return state.get();
    }

    public ReplayState replayState() {
        return replayState.get();
    }

    public boolean isRecording() {
        return state.get() == RecordingState.RECORDING;
    }

    public boolean isReplayBuffering() {
        return replayState.get() == ReplayState.BUFFERING;
    }

    public boolean isIdle() {
        return state.get() == RecordingState.IDLE;
    }

    public void toggle(Minecraft client) {
        if (isRecording()) {
            stop(client, Component.translatable("observercam.recording.stop.user"));
            return;
        }
        if (!isIdle()) {
            return;
        }
        if (isReplayBuffering()) {
            pauseReplayForLiveRecording(client);
        } else if (replayState.get() == ReplayState.IDLE) {
            start(client);
        }
    }

    public synchronized void start(Minecraft client) {
        if (replayState.get() != ReplayState.IDLE
                || !state.compareAndSet(RecordingState.IDLE, RecordingState.STARTING)) {
            return;
        }
        if (!canCaptureObserver(client)) {
            state.set(RecordingState.IDLE);
            toast(client, Component.translatable("observercam.recording.error.title"),
                    Component.translatable("observercam.recording.error.pov"));
            return;
        }

        ObserverCamConfig config = ObserverCamConfig.get();
        config.save();
        try {
            configureCapture(client, config);
            FFmpegEncoder created = new FFmpegEncoder(activeExecutable, activeFormat,
                    activeResolution, activeQuality, activeAudio, sourceWidth, sourceHeight,
                    framesPerSecond, outputDirectory);
            created.start();
            encoder = created;

            beginSession(false);
            state.set(RecordingState.RECORDING);
            updateObserverLight();
            toast(client, Component.translatable("observercam.recording.started.title"),
                    Component.translatable("observercam.recording.started.body", framesPerSecond,
                            activeFormat.id().toUpperCase(java.util.Locale.ROOT),
                            Component.translatable(activeAudio.enabled()
                                    ? "observercam.recording.audio.on" : "observercam.recording.audio.off")));
            LOGGER.info("Observer recording started: {}x{} at {} FPS -> {}", sourceWidth, sourceHeight,
                    framesPerSecond, created.partialFile());
        } catch (IOException | RuntimeException exception) {
            state.set(RecordingState.IDLE);
            encoder = null;
            updateObserverLight();
            reportStartFailure(client, exception, false);
        }
    }

    public void saveInstantReplay(Minecraft client) {
        ObserverCamConfig config = ObserverCamConfig.get();
        if (!config.instantReplayEnabled) {
            toast(client, Component.translatable("observercam.replay.error.title"),
                    Component.translatable("observercam.replay.error.disabled"));
            return;
        }
        if (!isReplayBuffering()) {
            toast(client, Component.translatable("observercam.replay.error.title"),
                    Component.translatable("observercam.replay.error.not_ready"));
            return;
        }
        finishReplay(client, true, false, null);
    }

    public void discardInstantReplay(Minecraft client) {
        if (isReplayBuffering()) {
            finishReplay(client, false, false, null);
        }
    }

    public void tick(Minecraft client) {
        if (isRecording()) {
            tickLiveRecording(client);
            return;
        }
        if (isReplayBuffering()) {
            tickReplayBuffer(client);
            return;
        }

        ObserverCamConfig config = ObserverCamConfig.get();
        if (!config.instantReplayEnabled) {
            replayStartAnnounced = false;
        }
        if (state.get() == RecordingState.IDLE && replayState.get() == ReplayState.IDLE
                && !liveStartPending && config.instantReplayEnabled && canCaptureObserver(client)) {
            startReplay(client);
        }
    }

    public long beginCapture(boolean captureIncludesHud) {
        if ((!isRecording() && !isReplayBuffering()) || includeHud != captureIncludesHud) {
            return -1L;
        }
        long now = System.nanoTime();
        if (now < nextCaptureNanos) {
            return -1L;
        }
        nextCaptureNanos += captureIntervalNanos;
        if (now - nextCaptureNanos > captureIntervalNanos * 4L) {
            nextCaptureNanos = now + captureIntervalNanos;
        }
        return activeSession;
    }

    public void submitFrame(long session, int width, int height, byte[] rgba) {
        if (session != activeSession || (!isRecording() && !isReplayBuffering())) {
            return;
        }
        if (width != sourceWidth || height != sourceHeight) {
            Minecraft client = Minecraft.getInstance();
            client.execute(() -> stopForCaptureProblem(client,
                    Component.translatable("observercam.recording.stop.resize")));
            return;
        }

        long expected = com.fluffybacon.observercam.recording.RecordingTimeline.expectedFrames(
                System.nanoTime() - startNanos, framesPerSecond);
        int copies = com.fluffybacon.observercam.recording.RecordingTimeline.copiesForCapture(
                expected, timelineFrames);
        timelineFrames += copies;
        FFmpegEncoder live = encoder;
        ReplayBufferEncoder replay = replayEncoder;
        for (int index = 0; index < copies; index++) {
            if (isRecording() && live != null) {
                live.submit(rgba);
            } else if (isReplayBuffering() && replay != null) {
                replay.submit(rgba);
            }
        }
    }

    public void failCapture(Component reason) {
        Minecraft client = Minecraft.getInstance();
        if (client != null) {
            client.execute(() -> stopForCaptureProblem(client, reason));
        }
    }

    public synchronized void stop(Minecraft client, Component reason) {
        if (!state.compareAndSet(RecordingState.RECORDING, RecordingState.FINALIZING)) {
            return;
        }
        invalidateCaptureSession();
        stoppedElapsedNanos = Math.max(0L, System.nanoTime() - startNanos);
        updateObserverLight();
        FFmpegEncoder finishing = encoder;
        toast(client, Component.translatable("observercam.recording.saving.title"), reason);

        Thread finalizer = new Thread(() -> finalizeRecording(client, finishing), "ObserverCam-Finalize");
        finalizer.setDaemon(true);
        finalizerThread = finalizer;
        try {
            finalizer.start();
        } catch (RuntimeException exception) {
            LOGGER.error("Could not start the recording finalizer thread; finalizing synchronously", exception);
            finalizer.run();
        }
    }

    public void shutdown(Minecraft client) {
        if (state.compareAndSet(RecordingState.RECORDING, RecordingState.FINALIZING)) {
            invalidateCaptureSession();
            stoppedElapsedNanos = Math.max(0L, System.nanoTime() - startNanos);
            updateObserverLight();
            finalizeRecording(client, encoder);
        } else {
            joinFinalizer(finalizerThread, LIVE_FINALIZER_SHUTDOWN_SECONDS, "recording");
        }

        if (replayState.compareAndSet(ReplayState.BUFFERING, ReplayState.STOPPING)) {
            invalidateCaptureSession();
            updateObserverLight();
            ReplayBufferEncoder finishing = replayEncoder;
            ReplayBufferEncoder.ReplaySnapshot snapshot = finishing == null ? null : finishing.stop();
            discardSnapshot(snapshot);
            replayEncoder = null;
            replayState.set(ReplayState.IDLE);
        } else {
            joinFinalizer(replayFinalizerThread, REPLAY_FINALIZER_SHUTDOWN_SECONDS, "instant replay");
        }
        ObserverRecordingState.setActive(false);
    }

    public long elapsedNanos() {
        return isRecording() ? Math.max(0L, System.nanoTime() - startNanos) : stoppedElapsedNanos;
    }

    public long acceptedFrames() {
        FFmpegEncoder current = encoder;
        return current == null ? 0L : current.acceptedFrames();
    }

    public long droppedFrames() {
        FFmpegEncoder current = encoder;
        return current == null ? 0L : current.droppedFrames();
    }

    public long estimatedBytes() {
        FFmpegEncoder current = encoder;
        return current == null ? estimatedBytes : Math.max(estimatedBytes, current.outputBytes());
    }

    public long replayElapsedNanos() {
        long elapsed = isReplayBuffering()
                ? Math.max(0L, System.nanoTime() - replayStartNanos) : replayStoppedElapsedNanos;
        ObserverCamConfig config = ObserverCamConfig.get();
        if (config.instantReplayLimitMode == InstantReplayLimitMode.TIME) {
            long limit = (long) (config.instantReplayDurationMinutes * 60.0 * 1_000_000_000L);
            return Math.min(elapsed, limit);
        }
        return elapsed;
    }

    public long replayEstimatedBytes() {
        ReplayBufferEncoder current = replayEncoder;
        return current == null ? replayEstimatedBytes : Math.max(replayEstimatedBytes, current.outputBytes());
    }

    public long replayDroppedFrames() {
        ReplayBufferEncoder current = replayEncoder;
        return current == null ? 0L : current.droppedFrames();
    }

    private void tickLiveRecording(Minecraft client) {
        if (!canCaptureObserver(client)) {
            stop(client, Component.translatable("observercam.recording.stop.pov"));
            return;
        }
        FFmpegEncoder current = encoder;
        if (current == null || current.failedWhileRecording()) {
            stop(client, Component.translatable("observercam.recording.stop.encoder"));
            return;
        }
        long now = System.nanoTime();
        if (now - lastStorageCheckNanos >= STORAGE_CHECK_INTERVAL) {
            lastStorageCheckNanos = now;
            estimatedBytes = current.outputBytes();
            try {
                long remainingBudget = RecordingStorageBudget.remainingBytes(outputDirectory);
                long usableDisk = Files.getFileStore(outputDirectory).getUsableSpace();
                if (remainingBudget <= CAP_STOP_HEADROOM || usableDisk <= DISK_FREE_RESERVE) {
                    stop(client, Component.translatable("observercam.recording.stop.storage"));
                }
            } catch (IOException exception) {
                LOGGER.warn("Could not verify recording storage; stopping safely", exception);
                stop(client, Component.translatable("observercam.recording.stop.storage_check"));
            }
        }
    }

    private void tickReplayBuffer(Minecraft client) {
        ObserverCamConfig config = ObserverCamConfig.get();
        if (!config.instantReplayEnabled || !canCaptureObserver(client)) {
            if (!config.instantReplayEnabled) {
                replayStartAnnounced = false;
            }
            finishReplay(client, false, false, null);
            return;
        }
        ReplayBufferEncoder current = replayEncoder;
        if (current == null || current.failedWhileBuffering()) {
            disableReplayAfterFailure(config);
            finishReplay(client, false, false,
                    Component.translatable("observercam.replay.error.encoder"));
            return;
        }

        long now = System.nanoTime();
        if (now - lastStorageCheckNanos < STORAGE_CHECK_INTERVAL) {
            return;
        }
        lastStorageCheckNanos = now;
        try {
            long remainingBudget = RecordingStorageBudget.remainingBytes(outputDirectory);
            long usableDisk = Files.getFileStore(outputDirectory).getUsableSpace();
            long bytesToFree = Math.max(Math.max(0L, CAP_STOP_HEADROOM - remainingBudget),
                    Math.max(0L, DISK_FREE_RESERVE - usableDisk));
            long replayLimit = Math.round(config.instantReplayStorageLimitGb
                    * RecordingStorageBudget.BYTES_PER_GB);
            ReplayBufferEncoder.PruneResult pruned = current.prune(config.instantReplayLimitMode,
                    config.instantReplayDurationMinutes, replayLimit, bytesToFree);
            replayEstimatedBytes = pruned.remainingBytes();

            remainingBudget = RecordingStorageBudget.remainingBytes(outputDirectory);
            usableDisk = Files.getFileStore(outputDirectory).getUsableSpace();
            boolean localSizeExceeded = config.instantReplayLimitMode == InstantReplayLimitMode.SIZE
                    && pruned.remainingBytes() > replayLimit;
            if (!pruned.hasActiveSegment() || localSizeExceeded
                    || remainingBudget < CAP_STOP_HEADROOM || usableDisk < DISK_FREE_RESERVE) {
                disableReplayAfterFailure(config);
                finishReplay(client, false, false,
                        Component.translatable("observercam.replay.error.storage"));
            }
        } catch (IOException exception) {
            LOGGER.warn("Could not maintain the instant replay buffer", exception);
            disableReplayAfterFailure(config);
            finishReplay(client, false, false,
                    Component.translatable("observercam.replay.error.storage"));
        }
    }

    private synchronized void startReplay(Minecraft client) {
        if (state.get() != RecordingState.IDLE || liveStartPending
                || !replayState.compareAndSet(ReplayState.IDLE, ReplayState.STARTING)) {
            return;
        }
        ObserverCamConfig config = ObserverCamConfig.get();
        if (!config.instantReplayEnabled || !canCaptureObserver(client)) {
            replayState.set(ReplayState.IDLE);
            return;
        }
        config.save();
        try {
            configureCapture(client, config);
            if (!outputDirectory.equals(cleanedReplayOutput)) {
                ReplayBufferEncoder.cleanupStaleBuffers(outputDirectory);
                cleanedReplayOutput = outputDirectory;
            }
            ReplayBufferEncoder created = new ReplayBufferEncoder(activeExecutable, activeFormat,
                    activeResolution, activeQuality, activeAudio, sourceWidth, sourceHeight,
                    framesPerSecond, outputDirectory);
            created.start();
            replayEncoder = created;
            beginSession(true);
            replayState.set(ReplayState.BUFFERING);
            updateObserverLight();
            if (!replayStartAnnounced) {
                replayStartAnnounced = true;
                toast(client, Component.translatable("observercam.replay.started.title"),
                        Component.translatable("observercam.replay.started.body"));
            }
            LOGGER.info("Observer instant replay buffer started: {}x{} at {} FPS in {}", sourceWidth,
                    sourceHeight, framesPerSecond, created.sessionDirectory());
        } catch (IOException | RuntimeException exception) {
            replayEncoder = null;
            replayState.set(ReplayState.IDLE);
            disableReplayAfterFailure(config);
            updateObserverLight();
            reportStartFailure(client, exception, true);
        }
    }

    private void pauseReplayForLiveRecording(Minecraft client) {
        liveStartPending = true;
        finishReplay(client, false, true, null);
    }

    private void finishReplay(Minecraft client, boolean save, boolean startLiveAfter, Component failureToast) {
        ReplayState target = save ? ReplayState.SAVING : ReplayState.STOPPING;
        if (!replayState.compareAndSet(ReplayState.BUFFERING, target)) {
            if (startLiveAfter) {
                liveStartPending = false;
            }
            return;
        }
        invalidateCaptureSession();
        replayStoppedElapsedNanos = Math.max(0L, System.nanoTime() - replayStartNanos);
        updateObserverLight();
        ReplayBufferEncoder finishing = replayEncoder;
        Path finishingOutput = outputDirectory;
        String executable = activeExecutable;
        RecordingVideoFormat format = activeFormat;
        if (save) {
            toast(client, Component.translatable("observercam.replay.saving.title"),
                    Component.translatable("observercam.replay.saving.body"));
        }

        Thread finalizer = new Thread(() -> {
            Component completionFailure = failureToast;
            ReplayExporter.ExportResult export = null;
            try {
                ReplayBufferEncoder.ReplaySnapshot snapshot = finishing == null ? null : finishing.stop();
                if (save) {
                    export = ReplayExporter.export(executable, format, finishingOutput, snapshot);
                } else {
                    discardSnapshot(snapshot);
                }
            } catch (RuntimeException exception) {
                LOGGER.error("Unexpected error while finalizing Observer instant replay", exception);
                completionFailure = Component.translatable("observercam.recording.error.generic",
                        safeMessage(exception));
                if (save) {
                    export = new ReplayExporter.ExportResult(false,
                            finishing == null ? null : finishing.sessionDirectory(), safeMessage(exception));
                }
            }
            replayEncoder = null;
            replayState.set(ReplayState.IDLE);
            replayFinalizerThread = null;
            updateObserverLight();

            ReplayExporter.ExportResult completedExport = export;
            Component reportedFailure = completionFailure;
            boolean resumeLiveRecording = startLiveAfter && completionFailure == null;
            if (client != null) {
                Runnable notifyCompletion = () -> {
                    if (reportedFailure != null) {
                        toast(client, Component.translatable("observercam.replay.error.title"), reportedFailure);
                    } else if (save && completedExport != null && completedExport.successful()) {
                        toast(client, Component.translatable("observercam.replay.saved.title"),
                                Component.translatable("observercam.replay.saved.body",
                                        completedExport.path().getFileName()));
                    } else if (save) {
                        String error = completedExport == null ? "Replay buffer was unavailable"
                                : completedExport.error();
                        toast(client, Component.translatable("observercam.replay.error.title"),
                                Component.translatable("observercam.replay.error.export", error));
                    }
                    if (resumeLiveRecording) {
                        liveStartPending = false;
                        start(client);
                    } else if (startLiveAfter) {
                        liveStartPending = false;
                    }
                };
                try {
                    client.execute(notifyCompletion);
                } catch (RuntimeException exception) {
                    liveStartPending = false;
                    LOGGER.warn("Could not show the instant replay completion message", exception);
                }
            } else if (startLiveAfter) {
                liveStartPending = false;
            }

            if (save && completedExport != null && completedExport.successful()) {
                LOGGER.info("Observer instant replay saved to {}", completedExport.path());
            } else if (save) {
                LOGGER.error("Observer instant replay export failed: {}",
                        completedExport == null ? "buffer unavailable" : completedExport.error());
            }
        }, save ? "ObserverCam-Replay-Save" : "ObserverCam-Replay-Stop");
        finalizer.setDaemon(true);
        replayFinalizerThread = finalizer;
        try {
            finalizer.start();
        } catch (RuntimeException exception) {
            LOGGER.error("Could not start the replay finalizer thread; finalizing synchronously", exception);
            finalizer.run();
        }
    }

    private void stopForCaptureProblem(Minecraft client, Component reason) {
        if (isRecording()) {
            stop(client, reason);
        } else if (isReplayBuffering()) {
            disableReplayAfterFailure(ObserverCamConfig.get());
            finishReplay(client, false, false, reason);
        }
    }

    private void configureCapture(Minecraft client, ObserverCamConfig config) throws IOException {
        outputDirectory = config.recordingOutputPath();
        Files.createDirectories(outputDirectory);
        verifyStartCapacity(outputDirectory);
        sourceWidth = Math.max(2, client.getMainRenderTarget().width);
        sourceHeight = Math.max(2, client.getMainRenderTarget().height);
        framesPerSecond = config.recordingFrameRate;
        includeHud = config.recordingIncludeHud;
        activeExecutable = FFmpegExecutableResolver.resolve(config.recordingFfmpegPath);
        if (!activeExecutable.equals(config.recordingFfmpegPath)) {
            config.recordingFfmpegPath = activeExecutable;
            config.save();
        }
        activeFormat = config.recordingVideoFormat;
        activeResolution = config.recordingResolution;
        activeQuality = config.recordingQuality;
        if (config.recordingAudioEnabled && config.recordingAudioDevice.isBlank()) {
            throw new IOException(Component.translatable("observercam.recording.error.audio_device").getString());
        }
        activeAudio = config.recordingAudioEnabled
                ? RecordingAudio.directShow(config.recordingAudioDevice)
                : RecordingAudio.disabled();
    }

    private void beginSession(boolean replay) {
        activeSession = sessionSequence.incrementAndGet();
        startNanos = System.nanoTime();
        if (replay) {
            replayStartNanos = startNanos;
            replayStoppedElapsedNanos = 0L;
            replayEstimatedBytes = 0L;
        } else {
            stoppedElapsedNanos = 0L;
            estimatedBytes = 0L;
        }
        nextCaptureNanos = startNanos;
        captureIntervalNanos = TimeUnit.SECONDS.toNanos(1L) / framesPerSecond;
        timelineFrames = 0L;
        lastStorageCheckNanos = startNanos;
    }

    private void invalidateCaptureSession() {
        activeSession = sessionSequence.incrementAndGet();
    }

    private void finalizeRecording(Minecraft client, FFmpegEncoder finishing) {
        FFmpegEncoder.RecordingResult result;
        try {
            result = finishing == null
                    ? new FFmpegEncoder.RecordingResult(false, null, "Encoder was unavailable", 0L, 0L, 0L)
                    : finishing.stop();
        } catch (RuntimeException exception) {
            LOGGER.error("Unexpected error while finalizing Observer recording", exception);
            result = new FFmpegEncoder.RecordingResult(false,
                    finishing == null ? null : finishing.partialFile(), safeMessage(exception), 0L, 0L, 0L);
        }
        encoder = null;
        state.set(RecordingState.IDLE);
        finalizerThread = null;
        updateObserverLight();
        FFmpegEncoder.RecordingResult completedResult = result;
        if (client != null) {
            Runnable notifyCompletion = () -> {
                if (completedResult.successful()) {
                    toast(client, Component.translatable("observercam.recording.saved.title"),
                            Component.translatable("observercam.recording.saved.body",
                                    completedResult.path().getFileName()));
                } else {
                    toast(client, Component.translatable("observercam.recording.error.title"),
                            Component.translatable("observercam.recording.error.finalize",
                                    completedResult.error()));
                }
            };
            try {
                client.execute(notifyCompletion);
            } catch (RuntimeException exception) {
                LOGGER.warn("Could not show the recording completion message", exception);
            }
        }
        if (result.successful()) {
            LOGGER.info("Observer recording saved to {} ({} written, {} dropped)", result.path(),
                    result.writtenFrames(), result.droppedFrames());
        } else {
            LOGGER.error("Observer recording failed; partial output: {} ({})", result.path(), result.error());
        }
    }

    private static void discardSnapshot(ReplayBufferEncoder.ReplaySnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        try {
            if (!ReplayBufferEncoder.deleteOwnedSession(snapshot.sessionDirectory())) {
                LOGGER.warn("A replay buffer failed the ownership check and was preserved: {}",
                        snapshot.sessionDirectory());
            }
        } catch (IOException exception) {
            LOGGER.warn("Could not remove a private instant replay buffer", exception);
        }
    }

    private static boolean canCaptureObserver(Minecraft client) {
        return client != null && client.level != null && client.player != null
                && ObserverCamClient.isViewingObserver();
    }

    private static void verifyStartCapacity(Path directory) throws IOException {
        long remainingBudget = RecordingStorageBudget.remainingBytes(directory);
        long usableDisk = Files.getFileStore(directory).getUsableSpace();
        if (remainingBudget < MINIMUM_START_BUDGET) {
            throw new IOException("Less than 128 MB remains under the configured recording cap");
        }
        if (usableDisk < DISK_FREE_RESERVE + MINIMUM_START_BUDGET) {
            throw new IOException("The selected disk does not have enough free space");
        }
    }

    private static void disableReplayAfterFailure(ObserverCamConfig config) {
        config.instantReplayEnabled = false;
        config.save();
    }

    private void reportStartFailure(Minecraft client, Exception exception, boolean replay) {
        LOGGER.error("Could not start Observer Cam {}", replay ? "instant replay" : "recording", exception);
        Component message = isMissingExecutable(exception)
                ? Component.translatable("observercam.recording.error.ffmpeg")
                : Component.translatable("observercam.recording.error.generic", safeMessage(exception));
        toast(client, Component.translatable(replay
                ? "observercam.replay.error.title" : "observercam.recording.error.title"), message);
    }

    private void updateObserverLight() {
        ObserverRecordingState.setActive(isRecording() || isReplayBuffering());
    }

    private static void joinFinalizer(Thread thread, long seconds, String description) {
        if (thread == null || thread == Thread.currentThread()) {
            return;
        }
        try {
            thread.join(TimeUnit.SECONDS.toMillis(seconds));
            if (thread.isAlive()) {
                LOGGER.warn("{} finalization exceeded {} seconds; interrupting it for safe shutdown",
                        description, seconds);
                thread.interrupt();
                thread.join(TimeUnit.SECONDS.toMillis(INTERRUPTED_FINALIZER_GRACE_SECONDS));
                if (thread.isAlive()) {
                    LOGGER.error("{} finalizer did not stop after interruption; partial diagnostics are preserved",
                            description);
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            thread.interrupt();
        }
    }

    private static boolean isMissingExecutable(Exception exception) {
        String message = safeMessage(exception).toLowerCase(java.util.Locale.ROOT);
        return message.contains("cannot run program") || message.contains("no such file")
                || message.contains("createprocess error=2");
    }

    private static String safeMessage(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    private static void toast(Minecraft client, Component title, Component body) {
        if (client != null && client.getToastManager() != null) {
            SystemToast.add(client.getToastManager(), TOAST_ID, title, body);
        }
    }
}
