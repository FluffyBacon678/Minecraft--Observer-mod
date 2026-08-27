package com.fluffybacon.observercam.client;

import com.fluffybacon.observercam.config.ObserverCamConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JFileChooser;
import java.awt.EventQueue;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FFmpegExecutablePicker {
    private static final Logger LOGGER = LoggerFactory.getLogger("ObserverCam/Recorder");
    private static final AtomicBoolean SELECTING = new AtomicBoolean();
    private static final String WINDOWS_PICKER_SCRIPT = """
            [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
            Add-Type -AssemblyName System.Windows.Forms
            $dialog = New-Object System.Windows.Forms.OpenFileDialog
            $dialog.Title = $env:OBSERVERCAM_DIALOG_TITLE
            $dialog.Filter = 'FFmpeg executable (ffmpeg.exe)|ffmpeg.exe|Executables (*.exe)|*.exe'
            $dialog.CheckFileExists = $true
            if ($env:OBSERVERCAM_INITIAL_DIRECTORY) { $dialog.InitialDirectory = $env:OBSERVERCAM_INITIAL_DIRECTORY }
            if ($dialog.ShowDialog() -eq [System.Windows.Forms.DialogResult]::OK) {
                [Console]::Out.Write($dialog.FileName)
            }
            $dialog.Dispose()
            """;

    private FFmpegExecutablePicker() {
    }

    public static void choose(Runnable onSelected) {
        if (!SELECTING.compareAndSet(false, true)) {
            return;
        }
        String title = Component.translatable("observercam.config.ffmpeg.choose").getString();
        Path initialDirectory = configuredParent();
        if (isWindows()) {
            chooseOnWindows(initialDirectory, title, onSelected);
        } else if (!GraphicsEnvironment.isHeadless()) {
            chooseWithSwing(initialDirectory, title, onSelected);
        } else {
            SELECTING.set(false);
            LOGGER.warn("No graphical FFmpeg file picker is available");
        }
    }

    private static void chooseOnWindows(Path initialDirectory, String title, Runnable onSelected) {
        CompletableFuture.runAsync(() -> {
            try {
                Path executable = Path.of(System.getenv().getOrDefault("SystemRoot", "C:\\Windows"),
                        "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
                ProcessBuilder builder = new ProcessBuilder(executable.toString(), "-NoProfile", "-NonInteractive",
                        "-STA", "-WindowStyle", "Hidden", "-Command", WINDOWS_PICKER_SCRIPT);
                builder.redirectError(ProcessBuilder.Redirect.DISCARD);
                builder.environment().put("OBSERVERCAM_DIALOG_TITLE", title);
                builder.environment().put("OBSERVERCAM_INITIAL_DIRECTORY",
                        initialDirectory == null ? "" : initialDirectory.toString());
                Process process = builder.start();
                String selected = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                if (process.waitFor() == 0 && !selected.isBlank()) {
                    applySelection(selected, onSelected);
                }
            } catch (IOException exception) {
                LOGGER.warn("Could not open the FFmpeg executable picker", exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                SELECTING.set(false);
            }
        });
    }

    private static void chooseWithSwing(Path initialDirectory, String title, Runnable onSelected) {
        EventQueue.invokeLater(() -> {
            try {
                JFileChooser chooser = initialDirectory == null
                        ? new JFileChooser()
                        : new JFileChooser(initialDirectory.toFile());
                chooser.setDialogTitle(title);
                chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    applySelection(chooser.getSelectedFile().toPath().toString(), onSelected);
                }
            } finally {
                SELECTING.set(false);
            }
        });
    }

    private static void applySelection(String selected, Runnable onSelected) {
        try {
            Path path = Path.of(selected).toAbsolutePath().normalize();
            if (!Files.isRegularFile(path)) {
                return;
            }
            Minecraft.getInstance().execute(() -> {
                ObserverCamConfig.get().recordingFfmpegPath = path.toString();
                ObserverCamConfig.get().save();
                onSelected.run();
            });
        } catch (InvalidPathException ignored) {
        }
    }

    private static Path configuredParent() {
        try {
            Path configured = Path.of(ObserverCamConfig.get().recordingFfmpegPath);
            return Files.isRegularFile(configured) ? configured.toAbsolutePath().getParent() : null;
        } catch (InvalidPathException ignored) {
            return null;
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
