package io.github.g1ocr.ocr;

import io.github.g1ocr.capture.SourceFrame;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Arrays;

public class RasterGridRefinerTest {
    private SourceFrame pattern(int w,int h,int ox,int oy,double sx,double sy) {
        byte[] rgb=new byte[w*h*3];
        for(int y=oy;y<Math.ceil(oy+144*sy);y++)for(int x=ox;x<Math.ceil(ox+160*sx);x++) {
            int lx=(int)((x-ox)/sx),ly=(int)((y-oy)/sy);
            int v=(lx*31+ly*17)%37<13?28:152;
            for(int c=0;c<3;c++)rgb[(y*w+x)*3+c]=(byte)v;
        }
        return new SourceFrame(rgb,w,h,1,1,30);
    }
    @Test public void knownIntegerLatticeRetainsCorrectPeriodAndPhase() {
        RasterGridRefiner.Result r=new RasterGridRefiner().refine(pattern(400,340,32,24,2,2),new int[]{32,24,352,312},32.4,23.8);
        assertTrue(r.available);assertEquals(2,r.xCandidates.get(0).scale,.002);assertEquals(2,r.yCandidates.get(0).scale,.002);
        assertEquals(32,r.xCandidates.get(0).origin,.05);assertEquals(24,r.yCandidates.get(0).origin,.05);
    }
    @Test public void independentAxisPeriodsDoNotCollapseToSquarePixels() {
        RasterGridRefiner.Result r=new RasterGridRefiner().refine(pattern(660,580,24,16,3.75,34.0/9),new int[]{24,16,624,560},24,16);
        assertTrue(r.available);assertEquals(3.75,r.xCandidates.get(0).scale,.025);assertEquals(34.0/9,r.yCandidates.get(0).scale,.025);
    }
    @Test public void uniformFrameCannotSupplyAPhase() {
        byte[] rgb=new byte[320*288*3];Arrays.fill(rgb,(byte)145);
        RasterGridRefiner.Result r=new RasterGridRefiner().refine(new SourceFrame(rgb,320,288,0,1,1),new int[]{0,0,320,288},0,0);
        assertFalse(r.available);assertTrue(r.xCandidates.isEmpty());assertTrue(r.yCandidates.isEmpty());
    }
    @Test(expected=IllegalArgumentException.class) public void invalidEnvelopeCannotReadOutsidePixels() {
        new RasterGridRefiner().refine(pattern(400,340,32,24,2,2),new int[]{-1,0,320,288},0,0);
    }
}
