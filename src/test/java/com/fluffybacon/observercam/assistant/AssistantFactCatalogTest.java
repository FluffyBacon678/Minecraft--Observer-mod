package com.fluffybacon.observercam.assistant;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssistantFactCatalogTest {
    @Test
    void catalogueContainsExactlyTheThreeCuratedFacts() {
        Set<String> ids = new HashSet<>();
        for (AssistantFactCatalog.Fact fact : AssistantFactCatalog.entries()) {
            ids.add(fact.id());
            assertTrue(fact.bubbleTranslationKey().endsWith(".bubble"));
            assertTrue(fact.messageTranslationKey().endsWith(".message"));
        }

        assertEquals(Set.of("octopus", "wombat", "cloud"), ids);
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
