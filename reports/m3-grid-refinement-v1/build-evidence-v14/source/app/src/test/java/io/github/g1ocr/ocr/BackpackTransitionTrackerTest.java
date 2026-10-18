package io.github.g1ocr.ocr;

import io.github.g1ocr.capture.SourceFrame;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class BackpackTransitionTrackerTest {
    @Test public void bottomOnlyChildTransferPreservesExposedParentText(){
        SceneFieldReaderTest.Image im=image(0,false,false,true);Arrays.fill(im.rgb,(byte)255);
        box(im,12,0,19,15);im.text("AB",14,2);im.text("CD",14,14);
        for(int y=192;y<208;y++)Arrays.fill(im.rgb,(y*320+64)*3,(y*320+320)*3,(byte)255);
        for(int x=4;x<=19;x++)im.cell(x==4?"└":x==19?"┘":"─",x,12);
        JapaneseOcrEngine.Result result=new JapaneseOcrEngine(im.bank).process(im.source());
        assertEquals("submenu_partial_redraw",result.visibleState.kind);
        assertEquals(2,result.rawRegions.size());assertEquals("AB",result.rawRegions.get(0).text);assertEquals("CD",result.rawRegions.get(1).text);
    }
    private SceneFieldReaderTest.Image image(int cursors,boolean residue,boolean parent,boolean closed){
        SceneFieldReaderTest.Image im=new SceneFieldReaderTest.Image();Random random=new Random(517);
        for(String s:new String[]{"┌","─","┐","│","└","┘","▶","゛"}){
            long mask=random.nextLong()&random.nextLong();im.masks.put(s,mask);im.bank.add(new GlyphTemplate("unit","main",im.bank.size(),mask,Collections.singletonList(s)));
        }
        box(im,12,0,19,15);
        // Drawing the child window replaces the covered parent pixels.
        for(int y=32;y<208;y++)Arrays.fill(im.rgb,(y*320+64)*3,(y*320+320)*3,(byte)255);
        box(im,4,2,19,12);
        im.text("AB",6,4);im.text("AB",6,6);im.text("CD",6,8);im.text("CD",6,10);im.text("AB",14,14);
        if(cursors>0)im.cell("▶",5,4);if(cursors>1)im.cell("▶",5,6);
        if(residue)im.cell("゛",8,5);if(parent)im.cell("゛",14,1);
        if(!closed)for(int y=192;y<208;y++)Arrays.fill(im.rgb,(y*320+64)*3,(y*320+80)*3,(byte)255);
        return im;
    }
    private void box(SceneFieldReaderTest.Image im,int l,int t,int r,int b){
        for(int x=l;x<=r;x++){im.cell(x==l?"┌":x==r?"┐":"─",x,t);im.cell(x==l?"└":x==r?"┘":"─",x,b);}
        for(int y=t+1;y<b;y++){im.cell("│",l,y);im.cell("│",r,y);}
    }
    private JapaneseOcrEngine.Result run(JapaneseOcrEngine engine,int cursors,boolean residue,long pts){
        return engine.process(new SourceFrame(image(cursors,residue,true,true).rgb,320,288,pts,1,30));
    }
    @Test public void openingThenMissingSelectionIsDifferentFromInitialLoading(){
        JapaneseOcrEngine engine=new JapaneseOcrEngine(image(0,false,true,true).bank);
        assertEquals("submenu_cursor_loading",run(engine,0,false,0).visibleState.kind);
        assertEquals("menu_complete",run(engine,1,false,1).visibleState.kind);
        assertEquals("menu_redraw_or_cursor_transition",run(engine,0,false,2).visibleState.kind);
        assertEquals("menu_complete",run(engine,1,false,3).visibleState.kind);
    }
    @Test public void duplicateRowsAndParentMarkAreNotRedrawButTwoSelectionsAndListResidueAre(){
        JapaneseOcrEngine engine=new JapaneseOcrEngine(image(1,false,true,true).bank);
        JapaneseOcrEngine.Result stable=run(engine,1,false,0);
        assertEquals("menu_complete",stable.visibleState.kind);
        JapaneseOcrEngine.Result dual=run(engine,2,false,1);
        assertEquals("menu_redraw_or_cursor_transition",dual.visibleState.kind);assertEquals(2,dual.visibleState.cursor.menuCursors.size());
        JapaneseOcrEngine.Result residue=run(engine,1,true,2);
        assertEquals("menu_redraw_or_cursor_transition",residue.visibleState.kind);
        assertEquals(stable.rawRegions.get(0).text,residue.rawRegions.get(0).text);
        assertEquals("menu_complete",run(engine,1,false,3).visibleState.kind);
    }
    @Test public void explicitResetReverseTimeAndTimeUnitChangeDiscardSelectionHistory(){
        for(int boundary=0;boundary<3;boundary++){
            JapaneseOcrEngine engine=new JapaneseOcrEngine(image(1,false,true,true).bank);run(engine,1,false,10);
            if(boundary==0)engine.reset();
            SourceFrame next=new SourceFrame(image(0,false,true,true).rgb,320,288,boundary==1?9:11,1,boundary==2?24:30);
            assertEquals("submenu_cursor_loading",engine.process(next).visibleState.kind);
        }
    }
    @Test public void MissingListBoundaryDiscardsSelectionHistory(){
        JapaneseOcrEngine engine=new JapaneseOcrEngine(image(1,false,true,true).bank);run(engine,1,false,0);
        engine.process(new SourceFrame(image(0,false,true,false).rgb,320,288,1,1,30));
        assertEquals("submenu_cursor_loading",run(engine,0,false,2).visibleState.kind);
    }
}
