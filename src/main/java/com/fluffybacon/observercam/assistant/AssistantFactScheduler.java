package com.fluffybacon.observercam.assistant;

import java.util.concurrent.TimeUnit;

/** Pure timing policy for the optional, client-local Observer assistant. */
public final class AssistantFactScheduler {
    public static final double MINIMUM_INTERVAL_MINUTES = 1.0;
    public static final double MAXIMUM_INTERVAL_MINUTES = 5.0;
    public static final double DEFAULT_INTERVAL_MINUTES = 3.0;

    private long nextFactNanos = Long.MIN_VALUE;
    private double scheduledIntervalMinutes = Double.NaN;

    /**
     * Returns true once per configured interval while eligible. Becoming
     * eligible starts a fresh interval, so enabling the assistant never
     * produces an immediate unsolicited message.
     */
    public boolean shouldSpeak(long nowNanos, boolean eligible, double configuredIntervalMinutes) {
        if (!eligible) {
            reset();
            return false;
        }

        double intervalMinutes = sanitizeMinutes(configuredIntervalMinutes);
        if (nextFactNanos == Long.MIN_VALUE
                || Double.compare(intervalMinutes, scheduledIntervalMinutes) != 0) {
            scheduledIntervalMinutes = intervalMinutes;
            nextFactNanos = nowNanos + intervalNanos(intervalMinutes);
            return false;
        }
        if (nowNanos - nextFactNanos < 0L) {
            return false;
        }

        nextFactNanos = nowNanos + intervalNanos(intervalMinutes);
        return true;
    }

    public void reset() {
        nextFactNanos = Long.MIN_VALUE;
        scheduledIntervalMinutes = Double.NaN;
    }

    public static double sanitizeMinutes(double minutes) {
        if (!Double.isFinite(minutes)) {
            return DEFAULT_INTERVAL_MINUTES;
        }
        return Math.max(MINIMUM_INTERVAL_MINUTES, Math.min(MAXIMUM_INTERVAL_MINUTES, minutes));
    }

    static long intervalNanos(double minutes) {
        return Math.round(sanitizeMinutes(minutes) * TimeUnit.MINUTES.toNanos(1L));
    }
}
