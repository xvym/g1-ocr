package io.github.g1ocr.ocr;

import io.github.g1ocr.capture.SourceFrame;
import org.junit.Test;
import static org.junit.Assert.*;

public class GameViewportLocatorTest {
    @Test public void blackFrameCannotRetainCoordinates(){
        GameViewportLocator l=new GameViewportLocator();
        assertEquals(GameViewportLocator.Status.NO_SIGNAL,l.locate(new SourceFrame(new byte[300*300*3],300,300,27,1,30)).status);
    }
    @Test public void uniformBrightFrameHasNoGrid(){
        byte[] rgb=new byte[320*288*3];java.util.Arrays.fill(rgb,(byte)150);
        assertEquals(GameViewportLocator.Status.NO_GRID,new GameViewportLocator().locate(new SourceFrame(rgb,320,288,0,1,1)).status);
    }
    @Test(expected=IllegalArgumentException.class) public void invalidScaleIsRejected(){new GameViewportLocator.Config(22,.5,16);}
    @Test(expected=IllegalArgumentException.class) public void nonFiniteScaleIsRejected(){new GameViewportLocator.Config(22,Double.NaN,16);}
    @Test public void coloredBezelCannotBecomeTheLargerMonochromeViewport(){
        int w=420,h=350;byte[] rgb=new byte[w*h*3];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int p=(y*w+x)*3;rgb[p]=(byte)123;rgb[p+1]=(byte)96;rgb[p+2]=(byte)117;
            if(x>=50&&x<370&&y>=30&&y<318){int dark=((x-50)/2*17+(y-30)/2*7)%23<7?35:145;rgb[p]=(byte)dark;rgb[p+1]=(byte)dark;rgb[p+2]=(byte)(dark-18);}
        }
        GameViewportLocator.Result r=new GameViewportLocator().locate(new SourceFrame(rgb,w,h,0,1,1));
        assertEquals(GameViewportLocator.Status.LOCATED,r.status);assertTrue(Math.abs(r.originX-50)<=2);assertTrue(Math.abs(r.originY-30)<=2);
    }
    @Test public void darkPaletteChromaOffsetDoesNotRemoveTheScreen(){
        int w=420,h=350;byte[] rgb=new byte[w*h*3];
        for(int y=30;y<318;y++)for(int x=50;x<370;x++){
            int v=((x-50)/2*17+(y-30)/2*7)%23<11?32:145,p=(y*w+x)*3;
            rgb[p]=(byte)v;rgb[p+1]=(byte)v;rgb[p+2]=(byte)(v-24);
        }
        assertEquals(GameViewportLocator.Status.LOCATED,new GameViewportLocator().locate(new SourceFrame(rgb,w,h,0,1,1)).status);
    }
    @Test public void screenEnvelopeIncludesMultipleCompatiblePaletteLevels(){
        int w=420,h=350;byte[] rgb=new byte[w*h*3];
        for(int y=30;y<318;y++)for(int x=50;x<370;x++){
            int v=(x<58||x>=362||y<38||y>=310)?88:((x-50)/2*17+(y-30)/2*7)%23<7?58:145;
            int p=(y*w+x)*3;rgb[p]=(byte)v;rgb[p+1]=(byte)v;rgb[p+2]=(byte)(v-24);
        }
        GameViewportLocator.Result r=new GameViewportLocator().locate(new SourceFrame(rgb,w,h,0,1,1));
        assertEquals(GameViewportLocator.Status.LOCATED,r.status);assertArrayEquals(new int[]{50,30,370,318},r.copyEnvelope());
    }
    @Test public void syntheticPixelPatternFindsTranslatedIntegerGrid(){
        int w=420,h=350;byte[] rgb=new byte[w*h*3];
        for(int y=24;y<312;y++)for(int x=40;x<360;x++){
            int value=((x-40)/2*17+(y-24)/2*7)%23<7?45:150;
            for(int c=0;c<3;c++)rgb[(y*w+x)*3+c]=(byte)value;
        }
        GameViewportLocator.Result r=new GameViewportLocator().locate(new SourceFrame(rgb,w,h,123,1,30));
        assertEquals(GameViewportLocator.Status.LOCATED,r.status);assertEquals(123,r.pts);
        assertEquals(2,r.scaleX,.02);assertEquals(2,r.scaleY,.02);
        int[] rect=r.sourceRectangle();assertTrue(Math.abs(rect[0]-40)<=2);assertTrue(Math.abs(rect[1]-24)<=2);
        int[] copy=r.copyEnvelope();copy[0]=-99;assertNotEquals(-99,r.copyEnvelope()[0]);
    }
}
