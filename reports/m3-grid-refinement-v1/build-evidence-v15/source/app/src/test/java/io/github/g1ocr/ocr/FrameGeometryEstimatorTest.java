package io.github.g1ocr.ocr;
import io.github.g1ocr.capture.SourceFrame;
import org.junit.Test;
import static org.junit.Assert.*;
public class FrameGeometryEstimatorTest {
    private SourceFrame image(int w,int h,int l,int t,int r,int b) {
        byte[] rgb=new byte[w*h*3];for(int y=t;y<b;y++)for(int x=l;x<r;x++)for(int c=0;c<3;c++)rgb[(y*w+x)*3+c]=(byte)145;
        return new SourceFrame(rgb,w,h,0,1,1);
    }
    @Test public void darkSurroundCanEncloseAnAnamorphicRaster(){assertArrayEquals(new int[]{180,0,780,544},FrameGeometryEstimator.darkSurround(image(960,544,180,0,780,544)));}
    @Test public void deviceBezelAspectCannotBecomeGameFallback(){assertEquals(0,FrameGeometryEstimator.darkSurround(image(800,480,100,0,700,480)).length);}
    @Test public void darkCeilingInsideFullHeightRasterDoesNotShortenIt(){assertArrayEquals(new int[]{180,0,780,544},FrameGeometryEstimator.darkSurround(image(960,544,180,122,780,544)));}
    @Test public void blackFrameCannotKeepAPreviousRaster(){assertFalse(new FrameGeometryEstimator().estimate(image(320,288,0,0,0,0)).located);}
}
