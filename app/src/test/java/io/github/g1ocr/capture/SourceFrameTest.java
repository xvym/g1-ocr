package io.github.g1ocr.capture;

import java.math.BigInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class SourceFrameTest {
    @Test public void inputAndOutputBuffersCannotMutateFrame() {
        byte[] rgb = {1, 2, 3};
        SourceFrame frame = new SourceFrame(rgb, 1, 1, 3003, 1, 90000);
        rgb[0] = 9; byte[] copy = frame.copyRgb(); copy[1] = 9;
        assertArrayEquals(new byte[] {1, 2, 3}, frame.copyRgb());
        assertEquals(new BigInteger("33366666"), frame.sourceNanoseconds());
    }
    @Test public void timestampDoesNotOverflowOrAssumeFps() {
        SourceFrame frame = new SourceFrame(new byte[3], 1, 1, Long.MAX_VALUE, 1001, 30000);
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(1001))
                .multiply(BigInteger.valueOf(1000000000)).divide(BigInteger.valueOf(30000)), frame.sourceNanoseconds());
    }
    @Test(expected = IllegalArgumentException.class) public void rejectsMissingPixels() { new SourceFrame(new byte[2], 1, 1, 0, 1, 30); }
    @Test(expected = IllegalArgumentException.class) public void rejectsUnknownTimeBase() { new SourceFrame(new byte[3], 1, 1, 0, 1, 0); }
}
