package com.fluffybacon.observercam.client.assistant;

import net.minecraft.network.chat.Component;

import java.util.concurrent.ThreadLocalRandom;

/** Translation-backed catalogue; future assistant topics can live beside this source. */
final class ObserverAssistantFacts {
    private static final int FACT_COUNT = 12;

    private int previousIndex = -1;

    Component next() {
        int index;
        if (previousIndex < 0) {
            index = ThreadLocalRandom.current().nextInt(FACT_COUNT);
        } else {
            int offset = ThreadLocalRandom.current().nextInt(1, FACT_COUNT);
            index = (previousIndex + offset) % FACT_COUNT;
        }
        previousIndex = index;
        return Component.translatable("observercam.assistant.fact." + index);
    }
}
