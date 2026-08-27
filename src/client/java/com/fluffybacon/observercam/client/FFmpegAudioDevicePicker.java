package com.fluffybacon.observercam.client;

import com.fluffybacon.observercam.config.ObserverCamConfig;
import com.fluffybacon.observercam.recording.FFmpegAudioDevices;
import com.fluffybacon.observercam.recording.FFmpegExecutableResolver;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JOptionPane;
import java.awt.EventQueue;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Discovers FFmpeg DirectShow audio inputs and lets the player explicitly select one. */
public final class FFmpegAudioDevicePicker {
    private static final Logger LOGGER = LoggerFactory.getLogger("ObserverCam/Recorder");
    private static final AtomicBoolean SELECTING = new AtomicBoolean();

    private FFmpegAudioDevicePicker() {
    }

    public static void choose(Runnable onSelected) {
        if (!SELECTING.compareAndSet(false, true)) {
            return;
        }
        if (!isWindows() || GraphicsEnvironment.isHeadless()) {
            SELECTING.set(false);
            showMessage(Component.translatable("observercam.config.audio.unsupported").getString(), true);
            return;
        }

        String executable = FFmpegExecutableResolver.resolve(ObserverCamConfig.get().recordingFfmpegPath);
        CompletableFuture.supplyAsync(() -> discover(executable)).thenAccept(devices -> {
            if (devices.isEmpty()) {
                SELECTING.set(false);
                showMessage(Component.translatable("observercam.config.audio.none_found").getString(), true);
                return;
            }
            EventQueue.invokeLater(() -> showChooser(devices, onSelected));
        }).exceptionally(exception -> {
            SELECTING.set(false);
            LOGGER.warn("Could not discover FFmpeg audio devices", exception);
            showMessage(Component.translatable("observercam.config.audio.scan_failed").getString(), true);
            return null;
        });
    }

    static List<String> discover(String executable) {
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(executable, "-hide_banner", "-list_devices", "true",
                    "-f", "dshow", "-i", "dummy");
            builder.redirectErrorStream(true);
            process = builder.start();
            process.getOutputStream().close();
            String listing = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(10L, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("FFmpeg audio-device scan timed out");
            }
            List<String> all = FFmpegAudioDevices.parseDirectShowListing(listing);
            return FFmpegAudioDevices.likelyGameAudioDevices(all);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private static void showChooser(List<String> devices, Runnable onSelected) {
        try {
            ObserverCamConfig config = ObserverCamConfig.get();
            Object initial = devices.contains(config.recordingAudioDevice)
                    ? config.recordingAudioDevice : devices.getFirst();
            Object selected = JOptionPane.showInputDialog(null,
                    Component.translatable("observercam.config.audio.instructions").getString(),
                    Component.translatable("observercam.config.audio.choose").getString(),
                    JOptionPane.PLAIN_MESSAGE, null, devices.toArray(), initial);
            if (selected instanceof String device && !device.isBlank()) {
                Minecraft.getInstance().execute(() -> {
                    ObserverCamConfig current = ObserverCamConfig.get();
                    current.recordingAudioDevice = device;
                    current.recordingAudioEnabled = true;
                    current.save();
                    onSelected.run();
                });
            }
        } finally {
            SELECTING.set(false);
        }
    }

    private static void showMessage(String message, boolean warning) {
        EventQueue.invokeLater(() -> JOptionPane.showMessageDialog(null, message,
                Component.translatable("observercam.config.audio.choose").getString(),
                warning ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE));
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
