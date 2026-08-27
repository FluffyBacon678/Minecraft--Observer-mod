package com.fluffybacon.observercam.client;

import com.fluffybacon.observercam.ObserverCam;
import com.fluffybacon.observercam.config.ObserverCamConfig;
import com.fluffybacon.observercam.entity.ObserverCameraEntity;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.TextureFilteringMethod;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** A low-rate, bounded Observer world preview rendered like an upgraded paper doll. */
public final class ObserverPictureInPicture {
    private static final Logger LOGGER = LoggerFactory.getLogger("ObserverCam/PiP");
    private static final Identifier TEXTURE_ID = Identifier.fromNamespaceAndPath(ObserverCam.MOD_ID, "pip/live");
    private static final long CAPTURE_INTERVAL_NANOS = 100_000_000L;
    private static final int MAX_TEXTURE_WIDTH = 480;
    private static final int MAX_TEXTURE_HEIGHT = 270;
    private static final AtomicBoolean CAPTURE_IN_FLIGHT = new AtomicBoolean();
    private static final AtomicLong GENERATION = new AtomicLong();

    private static DynamicTexture texture;
    private static int textureWidth;
    private static int textureHeight;
    private static long nextCaptureNanos;
    private static boolean renderingFeed;

    private ObserverPictureInPicture() {
    }

    public static void toggle() {
        setEnabled(!ObserverCamConfig.get().pictureInPictureEnabled);
    }

    public static void setEnabled(boolean enabled) {
        ObserverCamConfig config = ObserverCamConfig.get();
        config.pictureInPictureEnabled = enabled;
        config.save();
        if (!enabled) {
            reset();
        }
    }

    public static boolean isEnabled() {
        return ObserverCamConfig.get().pictureInPictureEnabled;
    }

    public static boolean isRenderingFeed() {
        return renderingFeed;
    }

    public static void captureBeforeMainWorld(GameRenderer renderer, DeltaTracker deltaTracker,
                                              boolean renderLevel) {
        Minecraft client = Minecraft.getInstance();
        if (!renderLevel || !isEnabled() || client == null || client.level == null || client.player == null
                || client.options.hideGui || ObserverCamClient.isViewingObserver()) {
            return;
        }
        long now = System.nanoTime();
        if (now < nextCaptureNanos || !CAPTURE_IN_FLIGHT.compareAndSet(false, true)) {
            return;
        }
        ObserverCameraEntity observer = ObserverPovController.findOwnedObserver(client);
        if (observer == null) {
            CAPTURE_IN_FLIGHT.set(false);
            return;
        }
        nextCaptureNanos = now + CAPTURE_INTERVAL_NANOS;
        long generation = GENERATION.get();
        var originalCamera = client.getCameraEntity();
        CameraType originalCameraType = client.options.getCameraType();
        try {
            renderingFeed = true;
            client.options.setCameraType(CameraType.FIRST_PERSON);
            client.setCameraEntity(observer);
            renderer.updateCamera(deltaTracker);
            updateGlobalUniforms(client, renderer, deltaTracker);
            renderer.renderLevel(deltaTracker);
            Screenshot.takeScreenshot(client.getMainRenderTarget(),
                    image -> acceptFrame(client, generation, image));
        } catch (Throwable throwable) {
            CAPTURE_IN_FLIGHT.set(false);
            LOGGER.error("Could not render the Observer picture-in-picture feed", throwable);
        } finally {
            client.setCameraEntity(originalCamera == null ? client.player : originalCamera);
            client.options.setCameraType(originalCameraType);
            renderer.updateCamera(deltaTracker);
            updateGlobalUniforms(client, renderer, deltaTracker);
            renderingFeed = false;
        }
    }

    /**
     * GameRenderer normally refreshes these values once, before the vanilla
     * world pass. A temporary second camera therefore has to refresh them for
     * both sides of the switch or its geometry is projected with the player's
     * stale camera matrices (usually leaving only the sky visible).
     */
    private static void updateGlobalUniforms(Minecraft client, GameRenderer renderer,
                                             DeltaTracker deltaTracker) {
        renderer.getGlobalSettingsUniform().update(
                client.getWindow().getWidth(),
                client.getWindow().getHeight(),
                client.options.glintStrength().get(),
                client.level == null ? 0L : client.level.getGameTime(),
                deltaTracker,
                client.options.getMenuBackgroundBlurriness(),
                renderer.getMainCamera(),
                client.options.textureFiltering().get() == TextureFilteringMethod.RGSS
        );
    }

    public static void render(GuiGraphics graphics) {
        Minecraft client = Minecraft.getInstance();
        if (!isEnabled() || ObserverCamClient.isViewingObserver() || texture == null
                || client == null || client.level == null || client.options.hideGui) {
            return;
        }
        int maximumWidth = Math.max(96, Math.min(200, graphics.guiWidth() / 3));
        int displayWidth = maximumWidth;
        int displayHeight = Math.max(54, Math.round(displayWidth * textureHeight / (float) textureWidth));
        int x = 8;
        int y = 8;
        int headerHeight = 13;
        graphics.fill(x, y, x + displayWidth + 4, y + displayHeight + headerHeight + 4, 0xD0080A0C);
        graphics.fill(x + 2, y + 2, x + displayWidth + 2, y + headerHeight + 1, 0xD0182028);
        graphics.fill(x + 6, y + 6, x + 10, y + 10, 0xFFFF2020);
        graphics.drawString(client.font, "OBSERVER  LIVE", x + 14, y + 4, 0xFFFFFFFF, true);
        graphics.blit(TEXTURE_ID,
                x + 2, y + headerHeight + 2,
                x + displayWidth + 2, y + displayHeight + headerHeight + 2,
                0.0F, 1.0F, 0.0F, 1.0F);
    }

    public static void reset() {
        GENERATION.incrementAndGet();
        CAPTURE_IN_FLIGHT.set(false);
        Minecraft client = Minecraft.getInstance();
        if (client != null && !RenderSystem.isOnRenderThread()) {
            client.execute(() -> retireTexture(client));
            return;
        }
        retireTexture(client);
    }

    private static void retireTexture(Minecraft client) {
        nextCaptureNanos = 0L;
        renderingFeed = false;
        DynamicTexture retiredTexture = texture;
        texture = null;
        textureWidth = 0;
        textureHeight = 0;
        if (retiredTexture != null && client != null) {
            if (client.getTextureManager().getTexture(TEXTURE_ID) == retiredTexture) {
                client.getTextureManager().release(TEXTURE_ID);
            }
        }
    }

    private static void acceptFrame(Minecraft client, long generation, NativeImage image) {
        NativeImage scaled = null;
        boolean uploadScheduled = false;
        try (image) {
            if (image == null || generation != GENERATION.get()) {
                return;
            }
            double scale = Math.min(1.0, Math.min(MAX_TEXTURE_WIDTH / (double) image.getWidth(),
                    MAX_TEXTURE_HEIGHT / (double) image.getHeight()));
            int width = Math.max(2, (int) Math.round(image.getWidth() * scale));
            int height = Math.max(2, (int) Math.round(image.getHeight() * scale));
            scaled = new NativeImage(width, height, false);
            image.resizeSubRectTo(0, 0, image.getWidth(), image.getHeight(), scaled);
            NativeImage completed = scaled;
            client.execute(() -> uploadFrame(client, generation, completed));
            uploadScheduled = true;
            scaled = null;
        } catch (Throwable throwable) {
            LOGGER.error("Could not prepare the Observer picture-in-picture frame", throwable);
        } finally {
            if (scaled != null) {
                scaled.close();
            }
            if (!uploadScheduled) {
                CAPTURE_IN_FLIGHT.set(false);
            }
        }
    }

    private static void uploadFrame(Minecraft client, long generation, NativeImage image) {
        try {
            if (generation != GENERATION.get() || !isEnabled() || client.level == null) {
                image.close();
                return;
            }
            if (texture == null || textureWidth != image.getWidth() || textureHeight != image.getHeight()) {
                if (texture != null) {
                    client.getTextureManager().release(TEXTURE_ID);
                }
                texture = new DynamicTexture(() -> "Observer Cam picture-in-picture",
                        image.getWidth(), image.getHeight(), false);
                texture.setPixels(image);
                textureWidth = image.getWidth();
                textureHeight = image.getHeight();
                client.getTextureManager().register(TEXTURE_ID, texture);
                texture.upload();
            } else {
                texture.setPixels(image);
                texture.upload();
            }
        } catch (Throwable throwable) {
            image.close();
            LOGGER.error("Could not upload the Observer picture-in-picture frame", throwable);
        } finally {
            CAPTURE_IN_FLIGHT.set(false);
        }
    }
}
