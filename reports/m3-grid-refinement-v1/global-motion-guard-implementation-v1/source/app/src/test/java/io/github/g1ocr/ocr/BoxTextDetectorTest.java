package io.github.g1ocr.ocr;
import io.github.g1ocr.capture.SourceFrame;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class BoxTextDetectorTest {
    private final String[] symbols={"┌","─","┐","│","└","┘","A","B","゛","カ","▼"};
    private final long[] masks={0x0000182424428100L,0x000000ffff000000L,0x0042241818244200L,0x1818181818181818L,0x0042241881422400L,0x0042241881244200L,0x183c66427e424200L,0x3e42423e42423e00L,0x6000000000000000L,0x1842241818244218L,0x00183c7e7e000000L};
    List<GlyphTemplate> bank(){List<GlyphTemplate> bank=new ArrayList<>();for(int i=0;i<symbols.length;i++)bank.add(new GlyphTemplate("unit","main",128+i,masks[i],Collections.singletonList(symbols[i])));return bank;}
    private PixelGlyphReader.Frame fixture(boolean open,boolean unknown,boolean marked) {
        return new PixelGlyphReader(bank()).read(source(open,unknown,marked),new PixelGlyphReader.Grid(0,0,2,2));
    }
    SourceFrame source(boolean open,boolean unknown,boolean marked) {
        Map<String,Long> bySymbol=new HashMap<>();for(int i=0;i<symbols.length;i++)bySymbol.put(symbols[i],masks[i]);
        byte[] rgb=new byte[320*288*3];Arrays.fill(rgb,(byte)160);
        for(int x=0;x<20;x++){draw(rgb,x,12,bySymbol.get(x==0?"┌":x==19?"┐":"─"));draw(rgb,x,17,bySymbol.get(x==0?"└":x==19?"┘":"─"));}
        for(int y=13;y<17;y++){draw(rgb,0,y,bySymbol.get("│"));draw(rgb,19,y,bySymbol.get("│"));}
        if(open)draw(rgb,19,17,0);
        draw(rgb,1,14,bySymbol.get("A"));draw(rgb,3,14,bySymbol.get(marked?"カ":"B"));
        if(unknown)draw(rgb,2,14,0x55aa55aa55aa55aaL);
        if(marked)draw(rgb,3,13,bySymbol.get("゛"));draw(rgb,18,16,bySymbol.get("▼"));
        return new SourceFrame(rgb,320,288,0,1,1);
    }
    private void draw(byte[] rgb,int col,int row,long mask) {
        for(int y=0;y<8;y++)for(int x=0;x<8;x++)for(int dy=0;dy<2;dy++)for(int dx=0;dx<2;dx++)for(int c=0;c<3;c++)rgb[(((row*8+y)*2+dy)*320+(col*8+x)*2+dx)*3+c]=(byte)((mask&(1L<<(y*8+x)))!=0?25:160);
    }
    @Test public void borderedLinePreservesInternalSpaceAndExcludesArrow(){
        List<BoxTextDetector.Box> boxes=new BoxTextDetector().detect(fixture(false,false,false));assertEquals(1,boxes.size());
        assertEquals(1,boxes.get(0).lines.size());assertEquals("A B",boxes.get(0).lines.get(0).rawText);assertEquals(1,boxes.get(0).symbols.size());
    }
    @Test public void unknownInteriorGlyphIsRetainedAsAnError(){assertEquals("A\ufffdB",new BoxTextDetector().detect(fixture(false,true,false)).get(0).lines.get(0).rawText);}
    @Test public void markOnlyRowDoesNotBecomeAnExtraLine(){
        BoxTextDetector.Box box=new BoxTextDetector().detect(fixture(false,false,true)).get(0);assertEquals(1,box.lines.size());assertEquals("A ガ",box.lines.get(0).rawText);
    }
    @Test public void incompleteOutlineCannotDeclareACompleteBox(){assertTrue(new BoxTextDetector().detect(fixture(true,false,false)).isEmpty());}
    @Test public void registeredApertureClippingRetainsVisibleTextWithoutInventingTheMissingCorner(){
        assertEquals("A B",new BoxTextDetector().detect(fixture(true,false,false),2).get(0).lines.get(0).rawText);
        assertTrue(new BoxTextDetector().detect(fixture(true,false,false),0).isEmpty());
    }
    @Test public void listCanCoverTheUpperRightCornerWithoutErasingTheMessage(){
        SourceFrame original=source(false,false,false);byte[] pixels=original.copyRgb();draw(pixels,19,12,masks[5]);
        PixelGlyphReader.Frame read=new PixelGlyphReader(bank()).read(new SourceFrame(pixels,320,288,0,1,1),new PixelGlyphReader.Grid(0,0,2,2));
        List<BoxTextDetector.Box> boxes=new BoxTextDetector().detect(read);assertEquals(1,boxes.size());assertEquals("A B",boxes.get(0).lines.get(0).rawText);
    }
}
