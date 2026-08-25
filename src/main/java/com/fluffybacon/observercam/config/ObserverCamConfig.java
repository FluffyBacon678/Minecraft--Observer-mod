package com.fluffybacon.observercam.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ObserverCamConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ObserverCamConfig instance = new ObserverCamConfig();

    public double outdoorDistance = 8.0;
    public double indoorDistance = 4.0;
    public double minimumDistance = 3.0;
    public double maximumDistance = 14.0;
    public double cameraHeight = 2.0;
    public double cameraFov = 70.0;

    public double maximumSpeed = 0.65;
    public double acceleration = 0.10;
    public double rotationSpeed = 7.0;
    public double positionSmoothing = 0.22;
    public double rotationSmoothing = 0.28;
    public double catchUpDistance = 14.0;
    public double emergencyTeleportDistance = 28.0;

    public double backgroundImportance = 0.75;
    public double playerVisibilityImportance = 1.0;
    public double shotStability = 0.75;
    public double reframeThreshold = 62.0;
    public double preferredPlayerScreenSize = 0.34;
    public double movementPredictionTicks = 5.0;

    public boolean followTargetAutomatically = true;
    public boolean allowFrontFacingShots = true;
    public boolean cameramanEnabled = false;

    public double recordingStorageLimitGb = 3.0;

    public boolean debugHud = false;
    public boolean showCandidatePositions = false;
    public boolean showRaycasts = false;
    public boolean showSelectedCameraPosition = false;
    public boolean showCollisionChecks = false;

    public static ObserverCamConfig get() {
        return instance;
    }

    public static void load() {
        Path path = configPath();
        if (Files.isRegularFile(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                ObserverCamConfig loaded = GSON.fromJson(reader, ObserverCamConfig.class);
                if (loaded != null) {
                    instance = loaded;
                }
            } catch (IOException | RuntimeException ignored) {
                instance = new ObserverCamConfig();
            }
        }
        instance.clamp();
    }

    public void save() {
        clamp();
        Path path = configPath();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException ignored) {
        }
    }

    public void reset() {
        instance = new ObserverCamConfig();
        instance.save();
    }

    private void clamp() {
        outdoorDistance = clamp(outdoorDistance, 4.0, 20.0);
        indoorDistance = clamp(indoorDistance, 2.5, 10.0);
        minimumDistance = clamp(minimumDistance, 2.0, 10.0);
        maximumDistance = clamp(maximumDistance, minimumDistance, 30.0);
        cameraHeight = clamp(cameraHeight, 0.0, 8.0);
        cameraFov = clamp(cameraFov, 35.0, 110.0);
        maximumSpeed = clamp(maximumSpeed, 0.1, 2.0);
        acceleration = clamp(acceleration, 0.01, 0.5);
        rotationSpeed = clamp(rotationSpeed, 1.0, 30.0);
        positionSmoothing = clamp(positionSmoothing, 0.05, 0.8);
        rotationSmoothing = clamp(rotationSmoothing, 0.05, 0.8);
        catchUpDistance = clamp(catchUpDistance, 6.0, 40.0);
        emergencyTeleportDistance = clamp(emergencyTeleportDistance, catchUpDistance + 2.0, 96.0);
        backgroundImportance = clamp(backgroundImportance, 0.0, 1.5);
        playerVisibilityImportance = clamp(playerVisibilityImportance, 0.5, 2.0);
        shotStability = clamp(shotStability, 0.0, 1.0);
        reframeThreshold = clamp(reframeThreshold, 20.0, 90.0);
        preferredPlayerScreenSize = clamp(preferredPlayerScreenSize, 0.15, 0.65);
        movementPredictionTicks = clamp(movementPredictionTicks, 0.0, 12.0);
        recordingStorageLimitGb = clamp(recordingStorageLimitGb, 0.5, 100.0);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Path configPath() {
        try {
            return FabricLoader.getInstance().getConfigDir().resolve("observercam.json");
        } catch (RuntimeException ignored) {
            return Path.of("config", "observercam.json");
        }
    }
}
