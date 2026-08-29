package com.fluffybacon.observercam.client.assistant;

import com.fluffybacon.observercam.assistant.AssistantFactCatalog;
import com.fluffybacon.observercam.assistant.AssistantFactScheduler;
import com.fluffybacon.observercam.client.ObserverCamClient;
import com.fluffybacon.observercam.client.ObserverPictureInPicture;
import com.fluffybacon.observercam.client.ObserverPovController;
import com.fluffybacon.observercam.config.ObserverCamConfig;
import com.fluffybacon.observercam.entity.ObserverCameraEntity;
import com.mojang.text2speech.Narrator;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Client-local, opt-in personality layer for the player's own Observer. */
public final class ObserverAssistant {
    private static final Logger LOGGER = LoggerFactory.getLogger("ObserverCam/Assistant");
    private static final int OBSERVER_CHECK_INTERVAL_TICKS = 20;
    private static final double MAXIMUM_BUBBLE_DISTANCE_SQUARED = 32.0 * 32.0;
    private static final long BUBBLE_DURATION_NANOS = TimeUnit.SECONDS.toNanos(8L);
    private static final SystemToast.SystemToastId NARRATOR_TOAST_ID = new SystemToast.SystemToastId(5_000L);
    private static final AssistantFactScheduler FACT_SCHEDULER = new AssistantFactScheduler();
    private static final AssistantFactCatalog FACTS = new AssistantFactCatalog();
    private static int observerCheckTicks;
    private static UUID observerUuid;
    private static UUID speakingObserverUuid;
    private static Component activeBubble;
    private static long bubbleExpiresAtNanos = Long.MIN_VALUE;
    private static boolean narratorWarningShown;

    private ObserverAssistant() {
    }

    public static void tick(Minecraft client) {
        long nowNanos = System.nanoTime();
        expireBubble(nowNanos);
        ObserverCamConfig config = ObserverCamConfig.get();
        boolean enabledInWorld = config.assistantEnabled
                && config.assistantFactsEnabled
                && client != null && client.level != null && client.player != null;
        if (!enabledInWorld) {
            reset();
            return;
        }
        if (observerCheckTicks <= 0) {
            ObserverCameraEntity observer = ObserverPovController.findOwnedObserver(client);
            observerUuid = observer == null ? null : observer.getUUID();
            observerCheckTicks = OBSERVER_CHECK_INTERVAL_TICKS;
        } else {
            observerCheckTicks--;
        }
        if (observerUuid == null) {
            FACT_SCHEDULER.reset();
            clearBubble();
            return;
        }

        boolean activeGameplay = client.screen == null;
        if (!FACT_SCHEDULER.shouldSpeak(nowNanos, true, activeGameplay,
                config.assistantFactIntervalMinutes)) {
            return;
        }
        emitFact(client, observerUuid, nowNanos);
    }

    public static void preview(Minecraft client) {
        ObserverCamConfig config = ObserverCamConfig.get();
        if (client == null || client.player == null || !config.assistantEnabled
                || !config.assistantFactsEnabled) {
            if (client != null && client.player != null) {
                client.player.displayClientMessage(
                        Component.translatable("observercam.assistant.preview.disabled"), true);
            }
            return;
        }
        ObserverCameraEntity observer = ObserverPovController.findOwnedObserver(client);
        if (observer == null) {
            client.player.displayClientMessage(
                    Component.translatable("observercam.assistant.preview.no_observer"), true);
            return;
        }
        observerUuid = observer.getUUID();
        emitFact(client, observerUuid, System.nanoTime());
    }

    private static void emitFact(Minecraft client, UUID sourceObserverUuid, long nowNanos) {
        ObserverCamConfig config = ObserverCamConfig.get();
        AssistantFactCatalog.Fact fact = FACTS.next();
        Component bubble = Component.translatable(fact.bubbleTranslationKey());
        Component spokenFact = Component.translatable(fact.messageTranslationKey());
        speakingObserverUuid = sourceObserverUuid;
        activeBubble = bubble;
        bubbleExpiresAtNanos = nowNanos + BUBBLE_DURATION_NANOS;

        Component message = Component.literal("[").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.translatable("observercam.assistant.name")
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
                .append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY))
                .append(spokenFact.copy().withStyle(ChatFormatting.GRAY));
        if (config.assistantShowFactsInChat) {
            if (config.assistantReadFactsAloud) {
                // Bypass ChatListener narration here so the explicit speech
                // path below remains the only voice for this message.
                client.gui.getChat().addMessage(message);
            } else {
                client.player.displayClientMessage(message, false);
            }
        }
        if (config.assistantReadFactsAloud) {
            speak(client, spokenFact);
        }
    }

    @Nullable
    public static Component bubbleFor(ObserverCameraEntity observer, double distanceToCameraSquared) {
        Minecraft client = Minecraft.getInstance();
        ObserverCamConfig config = ObserverCamConfig.get();
        if (!config.assistantEnabled || !config.assistantFactsEnabled
                || !config.assistantSpeechBubbleEnabled
                || client == null || client.options.hideGui || client.screen != null
                || ObserverCamClient.isViewingObserver() || ObserverPictureInPicture.isRenderingFeed()
                || activeBubble == null || speakingObserverUuid == null
                || !speakingObserverUuid.equals(observer.getUUID())
                || distanceToCameraSquared > MAXIMUM_BUBBLE_DISTANCE_SQUARED) {
            return null;
        }
        if (System.nanoTime() - bubbleExpiresAtNanos >= 0L) {
            clearBubble();
            return null;
        }
        return activeBubble;
    }

    private static void speak(Minecraft client, Component fact) {
        try {
            Narrator narrator = Narrator.getNarrator();
            if (!narrator.active()) {
                showNarratorWarning(client);
                return;
            }
            narrator.say(fact.getString(), false,
                    client.options.getFinalSoundSourceVolume(SoundSource.VOICE));
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not read an Observer fact aloud", exception);
            showNarratorWarning(client);
        }
    }

    private static void showNarratorWarning(Minecraft client) {
        if (narratorWarningShown) {
            return;
        }
        narratorWarningShown = true;
        SystemToast.add(client.getToastManager(), NARRATOR_TOAST_ID,
                Component.translatable("observercam.assistant.narrator_unavailable.title"),
                Component.translatable("observercam.assistant.narrator_unavailable.message"));
    }

    private static void expireBubble(long nowNanos) {
        if (activeBubble != null && nowNanos - bubbleExpiresAtNanos >= 0L) {
            clearBubble();
        }
    }

    private static void clearBubble() {
        speakingObserverUuid = null;
        activeBubble = null;
        bubbleExpiresAtNanos = Long.MIN_VALUE;
    }

    public static void reset() {
        FACT_SCHEDULER.reset();
        observerCheckTicks = 0;
        observerUuid = null;
        clearBubble();
    }
}
