package com.fluffybacon.observercam.recording;

import java.util.List;

/** Pure retention calculations for the segmented instant-replay buffer. */
public final class ReplayBufferPolicy {
    private ReplayBufferPolicy() {
    }

    public static int segmentsToDelete(InstantReplayLimitMode mode, double durationMinutes,
                                       long sizeLimitBytes, int segmentSeconds, List<Long> segmentSizes) {
        validate(mode, durationMinutes, sizeLimitBytes, segmentSeconds, segmentSizes);
        int deletable = Math.max(0, segmentSizes.size() - 1);
        if (mode == InstantReplayLimitMode.TIME) {
            int retainedSegments = Math.max(1,
                    (int) Math.ceil(durationMinutes * 60.0 / segmentSeconds));
            return Math.min(deletable, Math.max(0, segmentSizes.size() - retainedSegments));
        }

        long total = saturatedSum(segmentSizes);
        int deleteCount = 0;
        while (deleteCount < deletable && total > sizeLimitBytes) {
            total = Math.max(0L, total - segmentSizes.get(deleteCount));
            deleteCount++;
        }
        return deleteCount;
    }

    public static int completedSegmentsToFree(List<Long> segmentSizes, long requestedBytes) {
        if (segmentSizes == null || segmentSizes.stream().anyMatch(size -> size == null || size < 0L)) {
            throw new IllegalArgumentException("Segment sizes must be non-negative");
        }
        if (requestedBytes <= 0L) {
            return 0;
        }
        int deletable = Math.max(0, segmentSizes.size() - 1);
        long freed = 0L;
        int deleteCount = 0;
        while (deleteCount < deletable && freed < requestedBytes) {
            freed = saturatedAdd(freed, segmentSizes.get(deleteCount));
            deleteCount++;
        }
        return deleteCount;
    }

    public static long saturatedSum(List<Long> sizes) {
        long total = 0L;
        for (long size : sizes) {
            total = saturatedAdd(total, size);
        }
        return total;
    }

    private static long saturatedAdd(long left, long right) {
        if (Long.MAX_VALUE - left < right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private static void validate(InstantReplayLimitMode mode, double durationMinutes, long sizeLimitBytes,
                                 int segmentSeconds, List<Long> segmentSizes) {
        if (mode == null || !Double.isFinite(durationMinutes) || durationMinutes <= 0.0
                || sizeLimitBytes < 0L || segmentSeconds <= 0 || segmentSizes == null
                || segmentSizes.stream().anyMatch(size -> size == null || size < 0L)) {
            throw new IllegalArgumentException("Invalid replay retention parameters");
        }
    }
}
