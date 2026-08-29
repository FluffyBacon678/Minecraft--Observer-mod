package com.fluffybacon.observercam.celebration;

/** Pure post-interaction check used to avoid celebrating failed cake clicks. */
public final class CakeBiteDetector {
    private static final int LAST_BITE = 6;

    private CakeBiteDetector() {
    }

    public static boolean wasSliceEaten(int bitesBefore, boolean cakeRemains, int bitesAfter) {
        if (bitesBefore < 0 || bitesBefore > LAST_BITE) {
            return false;
        }
        if (bitesBefore == LAST_BITE) {
            return !cakeRemains;
        }
        return cakeRemains && bitesAfter == bitesBefore + 1;
    }
}
