package com.fluffybacon.observercam.recording;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReusableFrameTest {
    @Test
    void recyclesOnlyAfterEveryReferenceIsReleased() {
        AtomicInteger recycled = new AtomicInteger();
        ReusableFrame frame = new ReusableFrame(new byte[16], recycled::incrementAndGet);

        frame.retain();
        frame.retain();
        frame.release();
        frame.release();
        assertEquals(0, recycled.get());

        frame.release();
        assertEquals(1, recycled.get());
    }

    @Test
    void rejectsUseAfterReleaseAndDoubleRelease() {
        ReusableFrame frame = new ReusableFrame(new byte[1], () -> { });
        frame.release();

        assertThrows(IllegalStateException.class, frame::retain);
        assertThrows(IllegalStateException.class, frame::release);
    }
}
