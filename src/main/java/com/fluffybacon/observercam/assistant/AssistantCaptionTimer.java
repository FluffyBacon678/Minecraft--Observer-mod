package com.fluffybacon.observercam.assistant;

/**
 * Active-gameplay timer for an assistant caption. Time spent in a menu does
 * not consume the caption, so a preview requested from Mod Menu remains
 * visible after the player returns to the world.
 */
public final class AssistantCaptionTimer {
    private long expiresAtNanos = Long.MIN_VALUE;
    private long pausedAtNanos = Long.MIN_VALUE;

    public void start(long nowNanos, long durationNanos, boolean activeGameplay) {
        expiresAtNanos = nowNanos + Math.max(0L, durationNanos);
        pausedAtNanos = activeGameplay ? Long.MIN_VALUE : nowNanos;
    }

    public boolean isActive(long nowNanos, boolean activeGameplay) {
        if (expiresAtNanos == Long.MIN_VALUE) {
            return false;
        }
        if (!activeGameplay) {
            if (pausedAtNanos == Long.MIN_VALUE) {
                pausedAtNanos = nowNanos;
            }
            return true;
        }
        if (pausedAtNanos != Long.MIN_VALUE) {
            expiresAtNanos += Math.max(0L, nowNanos - pausedAtNanos);
            pausedAtNanos = Long.MIN_VALUE;
        }
        if (nowNanos - expiresAtNanos >= 0L) {
            reset();
            return false;
        }
        return true;
    }

    public void reset() {
        expiresAtNanos = Long.MIN_VALUE;
        pausedAtNanos = Long.MIN_VALUE;
    }
}
