package com.fluffybacon.observercam.assistant;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Small translation-backed fact catalogue with no immediate repeats. */
public final class AssistantFactCatalog {
    private static final List<Fact> FACTS = List.of(
            fact("octopus"),
            fact("wombat"),
            fact("cloud")
    );

    private int previousIndex = -1;

    public Fact next() {
        int index;
        if (previousIndex < 0) {
            index = ThreadLocalRandom.current().nextInt(FACTS.size());
        } else {
            int offset = ThreadLocalRandom.current().nextInt(1, FACTS.size());
            index = (previousIndex + offset) % FACTS.size();
        }
        previousIndex = index;
        return FACTS.get(index);
    }

    public static List<Fact> entries() {
        return FACTS;
    }

    private static Fact fact(String id) {
        return new Fact(
                id,
                "observercam.assistant.fact." + id + ".bubble",
                "observercam.assistant.fact." + id + ".message"
        );
    }

    public record Fact(String id, String bubbleTranslationKey, String messageTranslationKey) {
    }
}
