package com.fluffybacon.observercam.client;

import com.fluffybacon.observercam.config.ObserverCamConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class RecordingFolderActions {
    private static final Logger LOGGER = LoggerFactory.getLogger("ObserverCam/RecordingFolder");
    private static final SystemToast.SystemToastId TOAST_ID = new SystemToast.SystemToastId(5_000L);

    private RecordingFolderActions() {
    }

    public static void open() {
        Path outputDirectory = ObserverCamConfig.get().recordingOutputPath();
        try {
            Files.createDirectories(outputDirectory);
            Util.getPlatform().openPath(outputDirectory);
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Could not open the Observer Cam recording folder {}", outputDirectory, exception);
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.getToastManager() != null) {
                String detail = exception.getMessage() == null
                        ? exception.getClass().getSimpleName() : exception.getMessage();
                SystemToast.add(client.getToastManager(), TOAST_ID,
                        Component.translatable("observercam.recording.folder.error.title"),
                        Component.translatable("observercam.recording.folder.error.body", detail));
            }
        }
    }
}
