package com.fluffybacon.observercam.client.recording;

import com.mojang.blaze3d.platform.NativeImage;
import com.fluffybacon.observercam.client.ObserverPictureInPicture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.network.chat.Component;
import com.fluffybacon.observercam.recording.ReusableFrame;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

public final class ObserverFrameCapture {
    private static final Logger LOGGER = LoggerFactory.getLogger("ObserverCam/Recorder");
    private static final AtomicBoolean CAPTURE_IN_FLIGHT = new AtomicBoolean();
    private static final FrameBufferPool BUFFER_POOL = new FrameBufferPool();

    private ObserverFrameCapture() {
    }

    public static void captureIfNeeded(boolean includesHud) {
        ObserverRecordingManager manager = ObserverRecordingManager.get();
        long session = manager.beginMainCapture(includesHud);
        if (session < 0L || !CAPTURE_IN_FLIGHT.compareAndSet(false, true)) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getMainRenderTarget() == null) {
            CAPTURE_IN_FLIGHT.set(false);
            return;
        }
        try {
            takeScreenshot(manager, session, client.getMainRenderTarget());
        } catch (RuntimeException exception) {
            CAPTURE_IN_FLIGHT.set(false);
            LOGGER.error("Could not submit an Observer Cam framebuffer readback", exception);
            manager.failCapture(Component.translatable("observercam.recording.error.capture"));
            ObserverPictureInPicture.onCaptureFinished();
        }
    }

    public static void captureSecondary(ObserverRecordingManager manager, long session, RenderTarget target) {
        if (session < 0L || target == null || !CAPTURE_IN_FLIGHT.compareAndSet(false, true)) {
            return;
        }
        try {
            takeScreenshot(manager, session, target);
        } catch (RuntimeException exception) {
            CAPTURE_IN_FLIGHT.set(false);
            LOGGER.error("Could not submit an Observer Cam secondary framebuffer readback", exception);
            manager.failCapture(Component.translatable("observercam.recording.error.capture"));
            ObserverPictureInPicture.onCaptureFinished();
        }
    }

    public static boolean isCaptureInFlight() {
        return CAPTURE_IN_FLIGHT.get();
    }

    static void dropCachedBuffers() {
        BUFFER_POOL.clear();
    }

    private static void takeScreenshot(ObserverRecordingManager manager, long session, RenderTarget target) {
        Screenshot.takeScreenshot(target, image -> handleImage(manager, session, image));
    }

    private static void handleImage(ObserverRecordingManager manager, long session, NativeImage image) {
        try (image) {
            if (image == null) {
                manager.failCapture(Component.translatable("observercam.recording.error.capture"));
                return;
            }
            int expectedWidth = manager.captureWidth();
            int expectedHeight = manager.captureHeight();
            NativeImage resized = null;
            try {
                NativeImage captured = image;
                if (image.getWidth() != expectedWidth || image.getHeight() != expectedHeight) {
                    resized = new NativeImage(expectedWidth, expectedHeight, false);
                    image.resizeSubRectTo(0, 0, image.getWidth(), image.getHeight(), resized);
                    captured = resized;
                }
                int byteCount = Math.multiplyExact(Math.multiplyExact(
                        captured.getWidth(), captured.getHeight()), 4);
                ReusableFrame frame = BUFFER_POOL.acquire(byteCount);
                try {
                    MemoryUtil.memByteBuffer(captured.getPointer(), byteCount).get(frame.bytes());
                    manager.submitFrame(session, captured.getWidth(), captured.getHeight(), frame);
                } finally {
                    frame.release();
                }
            } finally {
                if (resized != null) {
                    resized.close();
                }
            }
        } catch (RuntimeException exception) {
            LOGGER.error("Could not convert an Observer Cam frame", exception);
            manager.failCapture(Component.translatable("observercam.recording.error.capture"));
        } finally {
            CAPTURE_IN_FLIGHT.set(false);
            ObserverPictureInPicture.onCaptureFinished();
        }
    }

}
