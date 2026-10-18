package io.github.g1ocr.ocr;

import io.github.g1ocr.capture.SourceFrame;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class MessageEraseTrackerTest {
    private final BoxTextDetectorTest fixture=new BoxTextDetectorTest();
    private SourceFrame frame(boolean marked,long pts){
        byte[] rgb=fixture.source(false,false,true).copyRgb();
        if(!marked)clearCell(rgb,3,13);
        clearCell(rgb,18,16);
        return new SourceFrame(rgb,320,288,pts,1,30);
    }
    private static void clearCell(byte[] rgb,int col,int row){
        for(int y=row*16;y<(row+1)*16;y++)for(int x=col*16;x<(col+1)*16;x++)
            Arrays.fill(rgb,(y*320+x)*3,(y*320+x)*3+3,(byte)160);
    }
    private boolean observe(MessageEraseTracker tracker,SourceFrame source,boolean complete,boolean scrolling){
        PixelGlyphReader.Frame read=new PixelGlyphReader(fixture.bank()).read(source,new PixelGlyphReader.Grid(0,0,2,2));
        return tracker.observe(source,read,new BoxTextDetector().detect(read).get(0).lines,complete,scrolling);
    }
    @Test public void removalAfterCompletePersistsWithoutRestoringMissingMarks(){
        MessageEraseTracker tracker=new MessageEraseTracker();
        assertFalse(observe(tracker,frame(true,0),true,false));
        assertTrue(observe(tracker,frame(false,1),false,false));
        assertTrue(observe(tracker,frame(false,2),false,false));
        assertFalse(observe(tracker,frame(true,3),false,false));
    }
    @Test public void loadingAndAddedMarksDoNotCertifyErasure(){
        MessageEraseTracker tracker=new MessageEraseTracker();
        assertFalse(observe(tracker,frame(true,0),false,false));
        assertFalse(observe(tracker,frame(false,1),false,false));
        assertFalse(observe(tracker,frame(true,2),true,false));
        byte[] replacement=fixture.source(false,false,false).copyRgb();
        assertFalse(observe(tracker,new SourceFrame(replacement,320,288,3,1,30),true,false));
    }
    @Test public void scrollResetAndTimeDiscontinuityDiscardEarlierEvidence(){
        for(int boundary=0;boundary<4;boundary++){
            MessageEraseTracker tracker=new MessageEraseTracker();observe(tracker,frame(true,10),true,false);
            if(boundary==0)tracker.reset();
            if(boundary==1)observe(tracker,frame(true,11),true,true);
            SourceFrame next=frame(false,boundary==2?9:12);
            if(boundary==3)next=new SourceFrame(next.copyRgb(),320,288,12,1,24);
            assertFalse(observe(tracker,next,false,false));
        }
    }
    @Test public void emptyMessageDiscardsEarlierEvidence(){
        MessageEraseTracker tracker=new MessageEraseTracker();observe(tracker,frame(true,0),true,false);
        SourceFrame empty=frame(false,1);PixelGlyphReader.Frame pixels=new PixelGlyphReader(fixture.bank()).read(empty,new PixelGlyphReader.Grid(0,0,2,2));
        assertFalse(tracker.observe(empty,pixels,Collections.emptyList(),false,false));
        assertFalse(observe(tracker,frame(false,2),false,false));
    }
    @Test public void pipelineLabelsObservedErasureAndKeepsCurrentRawText(){
        JapaneseOcrEngine engine=new JapaneseOcrEngine(fixture.bank());
        SourceFrame full=new SourceFrame(fixture.source(false,false,true).copyRgb(),320,288,0,1,30);
        assertEquals("message_complete",engine.process(full).visibleState.kind);
        JapaneseOcrEngine.Result erased=engine.process(frame(false,1));
        assertEquals("message_clearing",erased.visibleState.kind);
        assertEquals("A カ",erased.rawRegions.get(0).text);
        assertTrue(erased.rawRegions.get(0).markCells.isEmpty());
        assertEquals("message_clearing",engine.process(frame(false,2)).visibleState.kind);
        engine.reset();assertEquals("message_loading",engine.process(frame(false,3)).visibleState.kind);
    }
}
