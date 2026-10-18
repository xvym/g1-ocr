package io.github.g1ocr.ocr;
import io.github.g1ocr.capture.SourceFrame;
import org.junit.Test;
import static org.junit.Assert.*;

public class JapaneseOcrEngineTest {
    @Test public void emptyStartMenuRequiresItsCurrentCompleteOutlineAndClearsOldText(){
        SceneFieldReaderTest.Image im=new SceneFieldReaderTest.Image();java.util.Random random=new java.util.Random(1021);
        for(String s:new String[]{"┌","─","┐","│","└","┘"}){
            long mask=random.nextLong()&random.nextLong();im.masks.put(s,mask);
            im.bank.add(new GlyphTemplate("unit","main",im.bank.size(),mask,java.util.Collections.singletonList(s)));
        }
        for(int x=12;x<20;x++){im.cell(x==12?"┌":x==19?"┐":"─",x,0);im.cell(x==12?"└":x==19?"┘":"─",x,15);}
        for(int y=1;y<15;y++){im.cell("│",12,y);im.cell("│",19,y);}
        byte[] empty=im.rgb.clone();im.text("AB",14,2);
        JapaneseOcrEngine engine=new JapaneseOcrEngine(im.bank);
        assertFalse(engine.process(im.source()).rawRegions.isEmpty());
        JapaneseOcrEngine.Result cleared=engine.process(new SourceFrame(empty,320,288,1,1,30));
        assertEquals("menu_empty",cleared.visibleState.kind);assertTrue(cleared.rawRegions.isEmpty());
        // Without the bottom corners, an incomplete outline cannot certify an empty menu.
        for(int y=240;y<256;y++)java.util.Arrays.fill(empty,y*320*3,(y+1)*320*3,(byte)255);
        JapaneseOcrEngine.Result partial=engine.process(new SourceFrame(empty,320,288,2,1,30));
        assertEquals("no_textbox",partial.visibleState.kind);assertTrue(partial.rawRegions.isEmpty());
    }
    @Test public void overlappingMovePanelKeepsOnlyTheCurrentExposedCursor(){
        for(int selected:new int[]{-1,10,12,16}) {
            SceneFieldReaderTest.Image im=new SceneFieldReaderTest.Image();java.util.Random random=new java.util.Random(882);
            for(String s:new String[]{"わ","ざ","タ","イ","プ","▶"}) {
                long mask=random.nextLong()&random.nextLong();im.masks.put(s,mask);
                im.bank.add(new GlyphTemplate("unit","main",im.bank.size(),mask,java.util.Collections.singletonList(s)));
            }
            im.cell("<LV>",15,8);im.text("50",16,8);im.text("AB",10,8);im.hp(10,9);im.text("12/34",11,10);
            im.text("わざタイプ",10,15);im.text("AB",2,10);im.text("CD",2,12);im.text("AB",2,16);
            if(selected>=0)im.cell("▶",1,selected);
            // A cursor-shaped background tile outside the identified list is not a menu cursor.
            im.cell("▶",1,4);
            JapaneseOcrEngine.Result result=new JapaneseOcrEngine(im.bank).process(im.source());
            assertEquals(3,result.rawRegions.stream().filter(r->r.role.equals("move_name")).count());
            assertEquals(selected<0?0:1,result.visibleState.cursor.menuCursors.size());
            assertEquals(selected<0?"menu_cursor_loading":"menu_complete",result.visibleState.kind);
            if(selected>=0)assertArrayEquals(new int[]{1,selected,2,selected+1},result.visibleState.cursor.menuCursors.get(0).gridRect);
        }
    }
    @Test public void imagePipelineOutputsItsOwnTextAndRegionCoordinates(){
        BoxTextDetectorTest scene=new BoxTextDetectorTest();JapaneseOcrEngine.Result r=new JapaneseOcrEngine(scene.bank()).process(scene.source(false,false,false));
        assertEquals(1,r.rawRegions.size());assertEquals("A B",r.rawRegions.get(0).text);assertEquals("dialogue",r.rawRegions.get(0).role);
        assertArrayEquals(new double[]{1,14,4,15},r.rawRegions.get(0).gridRect,0);assertArrayEquals(new int[]{16,224,64,240},r.rawRegions.get(0).sourceRect);
        assertTrue(r.visibleState.cursor.downArrowVisible);
    }
    @Test public void failedFrameClearsPriorImageTextInsteadOfReusingIt(){
        BoxTextDetectorTest scene=new BoxTextDetectorTest();JapaneseOcrEngine engine=new JapaneseOcrEngine(scene.bank());engine.process(scene.source(false,false,false));
        JapaneseOcrEngine.Result invalid=engine.process(new SourceFrame(new byte[320*288*3],320,288,1,1,1));
        assertTrue(invalid.rawRegions.isEmpty());assertEquals("black_frame",invalid.visibleState.kind);assertFalse(invalid.visibleState.cursor.downArrowVisible);
    }
    @Test public void worldCameraMotionCannotShiftAStationaryMessageBox(){
        BoxTextDetectorTest scene=new BoxTextDetectorTest();JapaneseOcrEngine engine=new JapaneseOcrEngine(scene.bank());
        for(int step=0;step<2;step++) {
            byte[] rgb=scene.source(false,false,false).copyRgb();java.util.Random random=new java.util.Random(224);
            for(int y=4;y<88;y++)for(int x=4;x<150;x++)if(random.nextInt(5)==0)for(int yy=0;yy<2;yy++)for(int xx=0;xx<2;xx++) {
                int at=(((y+step)*2+yy)*320+x*2+xx)*3;rgb[at]=rgb[at+1]=rgb[at+2]=25;
            }
            JapaneseOcrEngine.Result result=engine.process(new SourceFrame(rgb,320,288,step,1,30));
            assertEquals("A B",result.rawRegions.get(0).text);assertEquals(0,result.imageRegistration.offsetLogicalY);
        }
    }
}
