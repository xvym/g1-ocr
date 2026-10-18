package io.github.g1ocr.verification;

import java.awt.image.BufferedImage;
import java.security.MessageDigest;
import org.junit.Test;
import static org.junit.Assert.*;

public class LedgerTest {
    @Test public void missingDurationIsNotFilledFromFrameRate() { assertTrue(Ledger.timeErrors(0L, null, null).contains("missing_or_invalid_duration")); }
    @Test public void detectsOneTickGapAndOverlap() {
        assertTrue(Ledger.timeErrors(513L, 512L, 512L).contains("timestamp_gap_or_overlap"));
        assertTrue(Ledger.timeErrors(511L, 512L, 512L).contains("timestamp_gap_or_overlap"));
        assertTrue(Ledger.timeErrors(512L, 512L, 512L).isEmpty());
    }
    @Test public void rejectsUnknownAndOverflowTimestamps() {
        assertTrue(Ledger.timeErrors(null, 1L, null).contains("missing_pts"));
        assertTrue(Ledger.timeErrors(Long.MAX_VALUE, 1L, null).contains("timestamp_overflow"));
    }
    @Test(expected = IllegalArgumentException.class) public void rejectsCorruptFrameHash() { Ledger.hashFields("0,0,0,1,3,abcd"); }
    @Test public void hashesRgbBytesRatherThanPngContainerOrAlpha() throws Exception {
        BufferedImage image = new BufferedImage(2, 1, BufferedImage.TYPE_INT_RGB); image.setRGB(0, 0, 0x123456); image.setRGB(1, 0, 0xabcdef);
        assertEquals(VideoTool.hex(MessageDigest.getInstance("SHA-256").digest(new byte[] {0x12,0x34,0x56,(byte)0xab,(byte)0xcd,(byte)0xef})), VideoTool.rgbHash(image));
    }
}
