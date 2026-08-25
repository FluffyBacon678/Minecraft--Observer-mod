package com.fluffybacon.observercam.camera;

import com.fluffybacon.observercam.config.ObserverCamConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShotScorerTest {
    @Test
    void desiredDistanceScoresHigherThanExtremes() {
        assertTrue(ShotScorer.distanceScore(8, 8) > ShotScorer.distanceScore(3, 8));
        assertTrue(ShotScorer.distanceScore(8, 8) > ShotScorer.distanceScore(15, 8));
    }

    @Test
    void visibilityDominatesCandidateScore() {
        ObserverCamConfig config = new ObserverCamConfig();
        double hidden = ShotScorer.score(true, true, 1, 8, 8, 1, 1, 0, -1, false, config);
        double visible = ShotScorer.score(true, true, 4, 8, 8, 1, 1, 0, -1, false, config);
        assertTrue(visible > hidden + 20);
    }

    @Test
    void shotSwitchRequiresMeaningfulImprovementAndTime() {
        assertFalse(ShotSwitchPolicy.shouldSwitch(84, 86, 100, 60, 10, false));
        assertFalse(ShotSwitchPolicy.shouldSwitch(84, 96, 20, 60, 10, false));
        assertTrue(ShotSwitchPolicy.shouldSwitch(84, 96, 60, 60, 10, false));
        assertTrue(ShotSwitchPolicy.shouldSwitch(84, 85, 1, 60, 10, true));
    }
}
