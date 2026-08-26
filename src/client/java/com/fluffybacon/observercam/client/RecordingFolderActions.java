package com.fluffybacon.observercam.client;

import com.fluffybacon.observercam.config.ObserverCamConfig;
import net.minecraft.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class RecordingFolderActions {
    private static final Logger LOGGER = LoggerFactory.getLogger("ObserverCam/RecordingFolder");

    private RecordingFolderActions() {
    }

    public static void open() {
        Path outputDirectory = ObserverCamConfig.get().recordingOutputPath();
        try {
            Files.createDirectories(outputDirectory);
            Util.getPlatform().openPath(outputDirectory);
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Could not open the Observer Cam recording folder {}", outputDirectory, exception);
        }
    }
}
