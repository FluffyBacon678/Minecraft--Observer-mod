package com.fluffybacon.observercam.client;

import com.fluffybacon.observercam.ObserverCam;
import com.fluffybacon.observercam.client.recording.ObserverFrameCapture;
import com.fluffybacon.observercam.client.recording.ObserverRecordingManager;
import com.fluffybacon.observercam.config.ObserverCamConfig;
import com.fluffybacon.observercam.entity.ObserverCameraEntity;
import com.fluffybacon.observercam.recording.CaptureSize;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.TextureFilteringMethod;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Shared low-rate Observer feed for PiP, background recording, and replay. */
public final class ObserverPictureInPicture {
    private static final Logger LOGGER = LoggerFactory.getLogger("ObserverCam/Feed");
    private static final Identifier TEXTURE_ID = Identifier.fromNamespaceAndPath(ObserverCam.MOD_ID, "pip/live");

    private static TextureTarget feedTarget;
    private static RenderTargetTexture feedTexture;
    private static RenderTarget activeRenderTarget;
    private static long nextPictureInPictureNanos;
    private static boolean renderingFeed;
    private static boolean frameAvailable;

    private ObserverPictureInPicture() {
    }

    public static void toggle() {
        setEnabled(!ObserverCamConfig.get().pictureInPictureEnabled);
    }

    public static void setEnabled(boolean enabled) {
        ObserverCamConfig config = ObserverCamConfig.get();
        boolean activating = enabled && !config.pictureInPictureEnabled;
        config.pictureInPictureEnabled = enabled;
        config.save();
        if (activating) {
            ObserverActivationPulse.pulse();
        }
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

    /** Used only by the render-target getter mixin during the guarded auxiliary pass. */
    public static RenderTarget activeRenderTarget() {
        return renderingFeed && RenderSystem.isOnRenderThread() ? activeRenderTarget : null;
    }

    public static void captureBeforeMainWorld(GameRenderer renderer, DeltaTracker deltaTracker,
                                              boolean renderLevel) {
        Minecraft client = Minecraft.getInstance();
        if (!renderLevel || client == null || client.level == null || client.player == null
                || ObserverCamClient.isViewingObserver()) {
            return;
        }

        ObserverRecordingManager manager = ObserverRecordingManager.get();
        boolean recordingFeed = manager.requiresSecondaryCapture();
        boolean pictureInPictureFeed = isEnabled() && !client.options.hideGui;
        if (!recordingFeed && !pictureInPictureFeed) {
            return;
        }

        RenderTarget mainTarget = client.getMainRenderTarget();
        long now = System.nanoTime();
        long recordingSession = -1L;
        CaptureSize captureSize;
        if (recordingFeed) {
            recordingSession = manager.beginSecondaryCapture();
            if (recordingSession < 0L) {
                return;
            }
            captureSize = CaptureSize.even(manager.captureWidth(), manager.captureHeight());
        } else {
            ObserverCamConfig config = ObserverCamConfig.get();
            if (now < nextPictureInPictureNanos) {
                return;
            }
            long interval = 1_000_000_000L / Math.max(2, config.pictureInPictureFrameRate);
            nextPictureInPictureNanos = now + interval;
            captureSize = config.pictureInPictureResolution.captureSize(mainTarget.width, mainTarget.height);
        }

        ObserverCameraEntity observer = ObserverPovController.findOwnedObserver(client);
        if (observer == null || !ensureTarget(client, captureSize)) {
            if (recordingSession >= 0L) {
                manager.failCapture(Component.translatable("observercam.recording.error.capture"));
            }
            return;
        }

        var originalCamera = client.getCameraEntity();
        CameraType originalCameraType = client.options.getCameraType();
        try {
            renderingFeed = true;
            activeRenderTarget = feedTarget;
            client.options.setCameraType(CameraType.FIRST_PERSON);
            client.setCameraEntity(observer);
            renderer.updateCamera(deltaTracker);
            updateGlobalUniforms(client, renderer, deltaTracker, feedTarget.width, feedTarget.height);
            renderer.renderLevel(deltaTracker);
            frameAvailable = true;
            if (recordingSession >= 0L) {
                ObserverFrameCapture.captureSecondary(manager, recordingSession, feedTarget);
            }
        } catch (Throwable throwable) {
            LOGGER.error("Could not render the shared Observer feed", throwable);
            if (recordingSession >= 0L) {
                manager.failCapture(Component.translatable("observercam.recording.error.capture"));
            } else {
                disableAfterFailure();
            }
        } finally {
            activeRenderTarget = null;
            renderingFeed = false;
            client.setCameraEntity(originalCamera == null ? client.player : originalCamera);
            client.options.setCameraType(originalCameraType);
            renderer.updateCamera(deltaTracker);
            updateGlobalUniforms(client, renderer, deltaTracker, mainTarget.width, mainTarget.height);
        }
    }

    private static void updateGlobalUniforms(Minecraft client, GameRenderer renderer,
                                             DeltaTracker deltaTracker, int width, int height) {
        renderer.getGlobalSettingsUniform().update(
                width,
                height,
                client.options.glintStrength().get(),
                client.level == null ? 0L : client.level.getGameTime(),
                deltaTracker,
                client.options.getMenuBackgroundBlurriness(),
                renderer.getMainCamera(),
                client.options.textureFiltering().get() == TextureFilteringMethod.RGSS
        );
    }

    private static boolean ensureTarget(Minecraft client, CaptureSize size) {
        if (feedTarget != null && feedTarget.width == size.width() && feedTarget.height == size.height()) {
            return true;
        }
        if (ObserverFrameCapture.isCaptureInFlight()) {
            return false;
        }
        try {
            if (feedTexture != null && client.getTextureManager().getTexture(TEXTURE_ID) == feedTexture) {
                client.getTextureManager().release(TEXTURE_ID);
            }
            if (feedTarget != null) {
                feedTarget.destroyBuffers();
            }
            feedTarget = new TextureTarget("Observer Cam feed", size.width(), size.height(), true);
            feedTexture = new RenderTargetTexture(feedTarget);
            client.getTextureManager().register(TEXTURE_ID, feedTexture);
            frameAvailable = false;
            return true;
        } catch (Throwable throwable) {
            LOGGER.error("Could not create the Observer feed render target", throwable);
            feedTarget = null;
            feedTexture = null;
            frameAvailable = false;
            return false;
        }
    }

    public static void render(GuiGraphics graphics) {
        Minecraft client = Minecraft.getInstance();
        if (!isEnabled() || ObserverCamClient.isViewingObserver() || !frameAvailable
                || feedTexture == null || feedTarget == null || client == null || client.level == null
                || client.options.hideGui) {
            return;
        }
        int displayWidth = Math.max(96, Math.min(200, graphics.guiWidth() / 3));
        int displayHeight = Math.max(54, Math.round(displayWidth * feedTarget.height / (float) feedTarget.width));
        int x = 4;
        int y = 4;
        int frameWidth = 1;
        int headerHeight = 11;
        int opacity = opacityAlpha(ObserverCamConfig.get().pictureInPictureOpacity);
        int imageX = x + frameWidth;
        int imageY = y + frameWidth + headerHeight;

        graphics.fill(x, y,
                x + displayWidth + frameWidth * 2,
                y + displayHeight + headerHeight + frameWidth * 2,
                withOpacity(0xD0080A0C, opacity));
        graphics.fill(imageX, y + frameWidth,
                imageX + displayWidth, imageY,
                withOpacity(0xD0182028, opacity));
        graphics.fill(x + 4, y + 4, x + 7, y + 7, withOpacity(0xFFFF2020, opacity));
        graphics.drawString(client.font, "OBSERVER  LIVE", x + 10, y + 2,
                withOpacity(0xFFFFFFFF, opacity), true);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE_ID,
                imageX, imageY,
                0.0F, feedTarget.height,
                displayWidth, displayHeight,
                feedTarget.width, -feedTarget.height,
                feedTarget.width, feedTarget.height,
                (opacity << 24) | 0x00FFFFFF);
    }

    private static int opacityAlpha(double opacity) {
        double safeOpacity = Double.isFinite(opacity) ? opacity : 1.0;
        return (int) Math.round(Math.max(0.25, Math.min(1.0, safeOpacity)) * 255.0);
    }

    private static int withOpacity(int color, int opacity) {
        int sourceAlpha = color >>> 24;
        int scaledAlpha = (sourceAlpha * opacity + 127) / 255;
        return (color & 0x00FFFFFF) | (scaledAlpha << 24);
    }

    public static void reset() {
        nextPictureInPictureNanos = 0L;
        frameAvailable = false;
        activeRenderTarget = null;
        renderingFeed = false;
    }

    private static void disableAfterFailure() {
        ObserverCamConfig config = ObserverCamConfig.get();
        config.pictureInPictureEnabled = false;
        config.save();
        reset();
    }

    private static final class RenderTargetTexture extends AbstractTexture {
        private RenderTargetTexture(RenderTarget target) {
            texture = target.getColorTexture();
            textureView = target.getColorTextureView();
        }

        @Override
        public void close() {
            // The owning TextureTarget releases these GPU objects when it is replaced.
            texture = null;
            textureView = null;
        }
    }
}
