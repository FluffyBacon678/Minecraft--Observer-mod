package com.fluffybacon.observercam.assistant;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssistantFactCatalogTest {
    @Test
    void catalogueContainsExactlyTheCuratedFacts() {
        Set<String> ids = new HashSet<>();
        for (AssistantFactCatalog.Fact fact : AssistantFactCatalog.entries()) {
            ids.add(fact.id());
            assertTrue(fact.bubbleTranslationKey().endsWith(".bubble"));
            assertTrue(fact.messageTranslationKey().endsWith(".message"));
        }

        Set<String> expectedIds = Set.of(
                "octopus", "wombat", "cloud", "banana", "honey", "butterfly", "flamingo",
                "dolphin", "horse", "axolotl", "jellyfish", "wood_frog", "penguin", "snail",
                "seahorse", "woodpecker", "polar_bear", "blue_whale", "fungus_ants", "honeybee",
                "cockroach", "elephant", "ballooning_spider", "cattle", "giraffe", "shrimp", "owl",
                "sea_star", "crow", "horned_lizard", "platypus_uv", "platypus_stomach",
                "flying_snake", "mantis_shrimp", "lobster", "crocodile", "frigatebird", "ant_lungs",
                "lightning"
        );
        assertEquals(expectedIds, ids);
        assertEquals(expectedIds.size(), AssistantFactCatalog.entries().size());
    }

    @Test
    void catalogueNeverReturnsTheSameFactTwiceInARow() {
        AssistantFactCatalog catalog = new AssistantFactCatalog();
        AssistantFactCatalog.Fact previous = catalog.next();

        for (int index = 0; index < 100; index++) {
            AssistantFactCatalog.Fact next = catalog.next();
            assertNotEquals(previous, next);
            previous = next;
        }
    }
}
