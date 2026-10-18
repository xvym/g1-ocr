package io.github.g1ocr.ocr;
import io.github.g1ocr.capture.SourceFrame;
import org.junit.Test;
import static org.junit.Assert.*;

public class MessageFadeTrackerTest {
    private SourceFrame image(int upper,int lower,int background,long pts){
        byte[] rgb=new byte[160*144*3];for(int y=0;y<144;y++)for(int x=0;x<160;x++){
            int v=x%8==0?(y<96?upper:lower):background;int at=(y*160+x)*3;
            rgb[at]=rgb[at+1]=rgb[at+2]=(byte)v;
        }return new SourceFrame(rgb,160,144,pts,1,30);
    }
    private boolean see(MessageFadeTracker tracker,int upper,int lower,long pts,String text,boolean eligible){
        return tracker.observe(image(upper,lower,160,pts),new PixelGlyphReader.Grid(0,0,1,1),text,eligible);
    }
    @Test public void coherentStepsAndPlateausKeepOnlyCurrentlyVisibleTextState(){
        MessageFadeTracker t=new MessageFadeTracker();assertFalse(see(t,20,20,0,"arbitrary",true));
        assertTrue(see(t,70,70,1,"arbitrary",true));assertTrue(see(t,70,70,2,"arbitrary",true));
        assertTrue(see(t,110,110,3,"arbitrary",true));assertTrue(see(t,110,110,4,"arbitrary",true));
        assertFalse(see(t,160,160,5,"",true));
    }
    @Test public void localAnimationNoiseAndGlobalBrightnessDoNotImplyPaletteFade(){
        MessageFadeTracker t=new MessageFadeTracker();see(t,20,20,0,"text",true);
        assertFalse(see(t,70,20,1,"text",true));t.reset();see(t,20,20,0,"text",true);
        assertFalse(see(t,20,70,1,"text",true));t.reset();see(t,20,20,0,"text",true);
        assertFalse(see(t,22,22,1,"text",true));
        assertFalse(t.observe(image(70,70,210,2),new PixelGlyphReader.Grid(0,0,1,1),"text",true));
    }
    @Test public void ReplacementScrollResetAndReversedTimeDiscardHistory(){
        for(int boundary=0;boundary<4;boundary++){
            MessageFadeTracker t=new MessageFadeTracker();see(t,20,20,10,"first",true);
            if(boundary==0)t.reset();if(boundary==1)see(t,20,20,11,"first",false);
            assertFalse(see(t,70,70,boundary==2?9:12,boundary==3?"replacement":"first",true));
        }
    }
    @Test public void ReturningContrastEndsFadeInsteadOfHoldingAnOldLabel(){
        MessageFadeTracker t=new MessageFadeTracker();see(t,20,20,0,"text",true);assertTrue(see(t,70,70,1,"text",true));
        assertFalse(see(t,20,20,2,"text",true));assertFalse(see(t,20,20,3,"text",true));
    }
    @Test public void sparseUpperForegroundStillMustFadeTogetherWithTheMessage(){
        MessageFadeTracker t=new MessageFadeTracker();
        for(int phase=0;phase<3;phase++){
            int ink=new int[]{20,70,110}[phase];byte[] rgb=image(ink,ink,160,phase).copyRgb();
            for(int y=0;y<96;y++)for(int x=0;x<160;x++)if(x%32!=0){int at=(y*160+x)*3;rgb[at]=rgb[at+1]=rgb[at+2]=(byte)160;}
            assertEquals(phase>0,t.observe(new SourceFrame(rgb,160,144,phase,1,30),new PixelGlyphReader.Grid(0,0,1,1),"sparse",true));
        }
    }
}
