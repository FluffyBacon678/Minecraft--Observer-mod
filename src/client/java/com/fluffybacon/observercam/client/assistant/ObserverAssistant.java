package com.fluffybacon.observercam.client.assistant;

import com.fluffybacon.observercam.assistant.AssistantFactScheduler;
import com.fluffybacon.observercam.client.ObserverPovController;
import com.fluffybacon.observercam.config.ObserverCamConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Client-local, opt-in personality layer for the player's own Observer. */
public final class ObserverAssistant {
    private static final int OBSERVER_CHECK_INTERVAL_TICKS = 20;
    private static final AssistantFactScheduler FACT_SCHEDULER = new AssistantFactScheduler();
    private static final ObserverAssistantFacts FACTS = new ObserverAssistantFacts();
    private static int observerCheckTicks;
    private static boolean observerAvailable;

    private ObserverAssistant() {
    }

    public static void tick(Minecraft client) {
        ObserverCamConfig config = ObserverCamConfig.get();
        boolean enabledInWorld = config.assistantEnabled
                && config.assistantFactsEnabled
                && client != null && client.level != null && client.player != null;
        if (!enabledInWorld) {
            reset();
            return;
        }
        if (observerCheckTicks <= 0) {
            observerAvailable = ObserverPovController.findOwnedObserver(client) != null;
            observerCheckTicks = OBSERVER_CHECK_INTERVAL_TICKS;
        } else {
            observerCheckTicks--;
        }
        if (!observerAvailable) {
            FACT_SCHEDULER.reset();
            return;
        }

        // Do not speak over configuration, inventory, or pause screens. The
        // existing deadline remains intact and is checked after gameplay resumes.
        if (client.screen != null) {
            return;
        }
        if (!FACT_SCHEDULER.shouldSpeak(System.nanoTime(), true, config.assistantFactIntervalMinutes)) {
            return;
        }

        Component message = Component.literal("[").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.translatable("observercam.assistant.name")
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
                .append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY))
                .append(FACTS.next().copy().withStyle(ChatFormatting.GRAY));
        client.player.displayClientMessage(message, false);
    }

    public static void reset() {
        FACT_SCHEDULER.reset();
        observerCheckTicks = 0;
        observerAvailable = false;
    }
}
