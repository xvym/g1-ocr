package io.github.g1ocr.verification;
import io.github.g1ocr.capture.SourceFrame;
import org.junit.Test;
import java.lang.reflect.*;
import java.util.*;
import java.awt.image.BufferedImage;
import static org.junit.Assert.*;
public class ReplayTimelineTest {
    @Test public void originalRationalPtsNotGuessedFps(){assertEquals(41666666L,ReplayTimeline.deltaNanos(512,0,1,12288));assertEquals(52631578L,ReplayTimeline.deltaNanos(1024,0,1,19456));assertEquals(1000000000L,ReplayTimeline.deltaNanos(12288,0,1,12288));}
    @Test public void subtractBeforeMultiplicationAvoidsOverflow(){assertEquals(1000000000L,ReplayTimeline.deltaNanos(Long.MAX_VALUE,Long.MAX_VALUE-1,1,1));}
    @Test(expected=IllegalArgumentException.class) public void backwardsTimeRejected(){ReplayTimeline.deltaNanos(4,5,1,30);}
    @Test public void consumerBoundaryHasOnlyImmutablePixelsDimensionsAndTime(){Set<String> fields=new HashSet<>();for(Field f:SourceFrame.class.getDeclaredFields()){assertTrue(Modifier.isPrivate(f.getModifiers()));assertTrue(Modifier.isFinal(f.getModifiers()));fields.add(f.getName());}assertEquals(new HashSet<>(Arrays.asList("rgb","width","height","pts","timeBaseNumerator","timeBaseDenominator")),fields);}
    @Test public void bulkDecodePreservesRgbByteOrderAndOldLedgerHash() throws Exception {BufferedImage image=new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB);image.setRGB(0,0,0x123456);image.setRGB(1,0,0xabcdef);image.setRGB(0,1,0xff0080);image.setRGB(1,1,0x010203);byte[] bytes=ReplayTimeline.rgb(image);assertArrayEquals(new byte[]{0x12,0x34,0x56,(byte)0xab,(byte)0xcd,(byte)0xef,(byte)0xff,0,(byte)0x80,1,2,3},bytes);assertEquals(VideoTool.rgbHash(image),VideoTool.hex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));}
}
