package com.fluffybacon.observercam.recording;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/** A frame buffer that returns to its owner after every encoder reference is released. */
public final class ReusableFrame {
    private final byte[] bytes;
    private final Runnable recycler;
    private final AtomicInteger references = new AtomicInteger(1);

    public ReusableFrame(byte[] bytes, Runnable recycler) {
        this.bytes = Objects.requireNonNull(bytes, "bytes");
        this.recycler = Objects.requireNonNull(recycler, "recycler");
    }

    public byte[] bytes() {
        return bytes;
    }

    public ReusableFrame retain() {
        int current;
        do {
            current = references.get();
            if (current <= 0) {
                throw new IllegalStateException("Cannot retain a released frame");
            }
            if (current == Integer.MAX_VALUE) {
                throw new IllegalStateException("Frame reference count overflow");
            }
        } while (!references.compareAndSet(current, current + 1));
        return this;
    }

    public void release() {
        int remaining = references.decrementAndGet();
        if (remaining == 0) {
            recycler.run();
        } else if (remaining < 0) {
            throw new IllegalStateException("Frame released more than it was retained");
        }
    }
}
