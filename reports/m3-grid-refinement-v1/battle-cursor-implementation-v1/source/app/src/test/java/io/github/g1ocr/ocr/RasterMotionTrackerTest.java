package io.github.g1ocr.ocr;
import io.github.g1ocr.capture.SourceFrame;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class RasterMotionTrackerTest {
    private SourceFrame image(int dx,int dy,long pts,boolean changed) {
        byte[] rgb=new byte[320*288*3];Arrays.fill(rgb,(byte)210);Random random=new Random(changed?711:119);
        for(int y=10;y<130;y++)for(int x=10;x<145;x++)if(random.nextInt(7)==0)for(int yy=0;yy<2;yy++)for(int xx=0;xx<2;xx++) {
            int p=((y+dy)*2+yy)*320+(x+dx)*2+xx;rgb[p*3]=rgb[p*3+1]=rgb[p*3+2]=20;
        }
        return new SourceFrame(rgb,320,288,pts,1,30);
    }
    @Test public void registersTwoPixelShakeAndReturnWithoutAccumulatingDrift() {
        RasterMotionTracker tracker=new RasterMotionTracker();PixelGlyphReader.Grid base=new PixelGlyphReader.Grid(0,0,2,2);
        assertFalse(tracker.align(image(0,0,0,false),base).matched);
        RasterMotionTracker.Alignment shifted=tracker.align(image(2,1,1,false),base);assertTrue(shifted.matched);assertEquals(2,shifted.offsetLogicalX);assertEquals(1,shifted.offsetLogicalY);assertEquals(4,shifted.grid.originX,0);
        RasterMotionTracker.Alignment back=tracker.align(image(0,0,2,false),base);assertTrue(back.matched);assertEquals(0,back.offsetLogicalX);assertEquals(0,back.offsetLogicalY);
    }
    @Test public void changedImageDoesNotBorrowPreviousGeometryOrText() {
        RasterMotionTracker tracker=new RasterMotionTracker();PixelGlyphReader.Grid base=new PixelGlyphReader.Grid(0,0,2,2);tracker.align(image(0,0,0,false),base);
        RasterMotionTracker.Alignment changed=tracker.align(image(0,0,1,true),base);assertFalse(changed.matched);assertEquals(0,changed.offsetLogicalX);assertSame(base,changed.grid);
        tracker.reset();assertFalse(tracker.align(image(2,0,2,false),base).matched);
    }
    @Test public void uniformHistoryAndNonMonotonicTimeDoNotInferMotion() {
        RasterMotionTracker tracker=new RasterMotionTracker();PixelGlyphReader.Grid base=new PixelGlyphReader.Grid(0,0,2,2);
        tracker.align(new SourceFrame(new byte[320*288*3],320,288,0,1,30),base);assertFalse(tracker.align(image(2,0,1,false),base).matched);
        assertFalse(tracker.align(image(2,0,1,false),base).matched);
    }
}
