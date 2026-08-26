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

public final class RecordingDirectoryPicker {
    private static final Logger LOGGER = LoggerFactory.getLogger("Observer Cam");
    private static final AtomicBoolean SELECTING = new AtomicBoolean();
    private static final String WINDOWS_PICKER_SCRIPT = """
            [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
            Add-Type -AssemblyName System.Windows.Forms
            $dialog = New-Object System.Windows.Forms.FolderBrowserDialog
            $dialog.Description = $env:OBSERVERCAM_DIALOG_TITLE
            $dialog.SelectedPath = $env:OBSERVERCAM_INITIAL_DIRECTORY
            if ($dialog.ShowDialog() -eq [System.Windows.Forms.DialogResult]::OK) {
                [Console]::Out.Write($dialog.SelectedPath)
            }
            $dialog.Dispose()
            """;

    private RecordingDirectoryPicker() {
    }

    public static void choose(Runnable onSelected) {
        if (!SELECTING.compareAndSet(false, true)) {
            return;
        }

        ObserverCamConfig config = ObserverCamConfig.get();
        Path initialDirectory = nearestExistingDirectory(config.recordingOutputPath());
        String title = Component.translatable("observercam.config.recording_output.choose").getString();
        if (isWindows()) {
            chooseOnWindows(config, initialDirectory, title, onSelected);
        } else if (!GraphicsEnvironment.isHeadless()) {
            chooseWithSwing(config, initialDirectory, title, onSelected);
        } else {
            SELECTING.set(false);
            LOGGER.warn("No supported graphical folder picker is available");
        }
    }

    private static void chooseOnWindows(ObserverCamConfig config, Path initialDirectory, String title,
                                        Runnable onSelected) {
        CompletableFuture.runAsync(() -> {
            try {
                String systemRoot = System.getenv().getOrDefault("SystemRoot", "C:\\Windows");
                Path executable = Path.of(systemRoot, "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
                ProcessBuilder builder = new ProcessBuilder(
                        executable.toString(), "-NoProfile", "-NonInteractive", "-STA",
                        "-WindowStyle", "Hidden", "-Command", WINDOWS_PICKER_SCRIPT);
                builder.redirectError(ProcessBuilder.Redirect.DISCARD);
                builder.environment().put("OBSERVERCAM_DIALOG_TITLE", title);
                builder.environment().put("OBSERVERCAM_INITIAL_DIRECTORY",
                        initialDirectory == null ? "" : initialDirectory.toString());
                Process process = builder.start();
                String selected = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                int exitCode = process.waitFor();
                if (exitCode == 0 && !selected.isBlank()) {
                    applySelection(config, selected, onSelected);
                }
            } catch (IOException exception) {
                LOGGER.warn("Could not open the Windows recording folder picker", exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                SELECTING.set(false);
            }
        });
    }

    private static void chooseWithSwing(ObserverCamConfig config, Path initialDirectory, String title,
                                        Runnable onSelected) {
        EventQueue.invokeLater(() -> {
            try {
                JFileChooser chooser = initialDirectory == null
                        ? new JFileChooser()
                        : new JFileChooser(initialDirectory.toFile());
                chooser.setDialogTitle(title);
                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                chooser.setAcceptAllFileFilterUsed(false);
                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    applySelection(config, chooser.getSelectedFile().toPath().toString(), onSelected);
                }
            } finally {
                SELECTING.set(false);
            }
        });
    }

    private static void applySelection(ObserverCamConfig config, String selected, Runnable onSelected) {
        try {
            Path selectedDirectory = Path.of(selected);
            if (!Files.isDirectory(selectedDirectory)) {
                return;
            }
            Minecraft.getInstance().execute(() -> {
                config.setRecordingOutputDirectory(selectedDirectory);
                config.save();
                onSelected.run();
            });
        } catch (InvalidPathException ignored) {
        }
    }

    static Path nearestExistingDirectory(Path path) {
        Path candidate = path;
        while (candidate != null && !Files.isDirectory(candidate)) {
            candidate = candidate.getParent();
        }
        return candidate;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
