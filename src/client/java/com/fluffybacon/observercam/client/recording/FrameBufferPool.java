package com.fluffybacon.observercam.client.recording;

import com.fluffybacon.observercam.recording.ReusableFrame;

import java.util.ArrayDeque;
import java.util.Deque;

/** Keeps a very small number of same-sized capture arrays off the garbage collector's hot path. */
final class FrameBufferPool {
    private static final int MAX_CACHED_BUFFERS = 2;

    private final Deque<byte[]> available = new ArrayDeque<>(MAX_CACHED_BUFFERS);
    private int bufferSize = -1;
    private long generation;

    synchronized ReusableFrame acquire(int requestedSize) {
        if (requestedSize <= 0) {
            throw new IllegalArgumentException("Frame buffer size must be positive");
        }
        if (bufferSize != requestedSize) {
            available.clear();
            bufferSize = requestedSize;
            generation++;
        }
        byte[] bytes = available.pollFirst();
        if (bytes == null) {
            bytes = new byte[requestedSize];
        }
        byte[] capturedBytes = bytes;
        long capturedGeneration = generation;
        return new ReusableFrame(bytes, () -> recycle(capturedBytes, capturedGeneration));
    }

    synchronized void clear() {
        available.clear();
        generation++;
    }

    private synchronized void recycle(byte[] bytes, long bufferGeneration) {
        if (bufferGeneration == generation && bytes.length == bufferSize
                && available.size() < MAX_CACHED_BUFFERS) {
            available.addFirst(bytes);
        }
    }
}
