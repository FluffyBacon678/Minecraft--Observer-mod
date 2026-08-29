package com.fluffybacon.observercam.assistant;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Translation-backed fact catalogue with no immediate repeats. */
public final class AssistantFactCatalog {
    private static final List<Fact> FACTS = List.of(
            fact("octopus"),
            fact("wombat"),
            fact("cloud"),
            fact("banana"),
            fact("honey"),
            fact("butterfly"),
            fact("flamingo"),
            fact("dolphin"),
            fact("horse"),
            fact("axolotl"),
            fact("jellyfish"),
            fact("wood_frog"),
            fact("penguin"),
            fact("snail"),
            fact("seahorse"),
            fact("woodpecker"),
            fact("polar_bear"),
            fact("blue_whale"),
            fact("fungus_ants"),
            fact("honeybee"),
            fact("cockroach"),
            fact("elephant"),
            fact("ballooning_spider"),
            fact("cattle"),
            fact("giraffe"),
            fact("shrimp"),
            fact("owl"),
            fact("sea_star"),
            fact("crow"),
            fact("horned_lizard"),
            fact("platypus_uv"),
            fact("platypus_stomach"),
            fact("flying_snake"),
            fact("mantis_shrimp"),
            fact("lobster"),
            fact("crocodile"),
            fact("frigatebird"),
            fact("ant_lungs"),
            fact("lightning")
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
