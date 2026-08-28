package com.fluffybacon.observercam.client.recording;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ObserverFrameCapture {
    private static final Logger LOGGER = LoggerFactory.getLogger("ObserverCam/Recorder");
    private static final AtomicBoolean CAPTURE_IN_FLIGHT = new AtomicBoolean();

    private ObserverFrameCapture() {
    }

    public static void captureIfNeeded(boolean includesHud) {
        ObserverRecordingManager manager = ObserverRecordingManager.get();
        long session = manager.beginCapture(includesHud);
        if (session < 0L || !CAPTURE_IN_FLIGHT.compareAndSet(false, true)) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getMainRenderTarget() == null) {
            CAPTURE_IN_FLIGHT.set(false);
            return;
        }
        try {
            Screenshot.takeScreenshot(client.getMainRenderTarget(), image -> handleImage(manager, session, image));
        } catch (Throwable throwable) {
            CAPTURE_IN_FLIGHT.set(false);
            LOGGER.error("Could not submit an Observer Cam framebuffer readback", throwable);
            manager.failCapture(Component.translatable("observercam.recording.error.capture"));
        }
    }

    private static void handleImage(ObserverRecordingManager manager, long session, NativeImage image) {
        try (image) {
            if (image == null) {
                manager.failCapture(Component.translatable("observercam.recording.error.capture"));
                return;
            }
            int[] pixels = image.getPixelsABGR();
            byte[] rgba = new byte[Math.multiplyExact(Math.multiplyExact(image.getWidth(), image.getHeight()), 4)];
            ByteBuffer.wrap(rgba).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().put(pixels);
            manager.submitFrame(session, image.getWidth(), image.getHeight(), rgba);
        } catch (Throwable throwable) {
            LOGGER.error("Could not convert an Observer Cam frame", throwable);
            manager.failCapture(Component.translatable("observercam.recording.error.capture"));
        } finally {
            CAPTURE_IN_FLIGHT.set(false);
        }
    }

}
