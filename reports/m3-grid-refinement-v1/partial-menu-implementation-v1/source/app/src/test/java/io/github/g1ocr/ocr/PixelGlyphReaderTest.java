package io.github.g1ocr.ocr;
import io.github.g1ocr.capture.SourceFrame;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PixelGlyphReaderTest {
    private final long base=0x1842241818244218L,mark=0x6000000000000000L;
    private List<GlyphTemplate> bank(String... aliases){return Arrays.asList(new GlyphTemplate("unit","main",128,base,Arrays.asList(aliases)),new GlyphTemplate("unit","main",229,mark,Collections.singletonList("゛")));}
    private SourceFrame frame(long baseInk,boolean hasMark,int scale) {
        int w=160*scale,h=144*scale;byte[] rgb=new byte[w*h*3];Arrays.fill(rgb,(byte)160);
        for(int y=0;y<16;y++)for(int x=0;x<8;x++) {
            long bits=y<8?hasMark?mark:0:baseInk;int py=y%8;
            if((bits&(1L<<(py*8+x)))!=0)for(int yy=0;yy<scale;yy++)for(int xx=0;xx<scale;xx++)for(int c=0;c<3;c++)rgb[((y*scale+yy)*w+x*scale+xx)*3+c]=25;
        }
        return new SourceFrame(rgb,w,h,77,1,30);
    }
    @Test public void exactImageGlyphIsNotAnAnswerLookup() {
        PixelGlyphReader.Frame f=new PixelGlyphReader(bank("カ")).read(frame(base,false,2),new PixelGlyphReader.Grid(0,0,2,2));
        assertEquals(77,f.pts);assertEquals(Collections.singletonList("カ"),f.literals(0,1));assertEquals(base,f.cell(0,1).ink);
        assertEquals(Collections.singletonList(" "),f.literals(1,1));assertEquals(360,f.cells().size());
    }
    @Test public void sameFrameMarkComposesButAbsentMarkNeverUsesHistory() {
        PixelGlyphReader reader=new PixelGlyphReader(bank("カ"));
        assertEquals(Collections.singletonList("ガ"),reader.read(frame(base,true,2),new PixelGlyphReader.Grid(0,0,2,2)).literals(0,1));
        assertEquals(Collections.singletonList("カ"),reader.read(frame(base,false,2),new PixelGlyphReader.Grid(0,0,2,2)).literals(0,1));
    }
    @Test public void sharedKanaAliasesAreNotResolvedByListOrder() {
        assertEquals(Arrays.asList("へ","ヘ"),new PixelGlyphReader(bank("へ","ヘ")).read(frame(base,false,2),new PixelGlyphReader.Grid(0,0,2,2)).literals(0,1));
    }
    @Test public void clippedCellsAreInvalidInsteadOfPaddedWithInventedInk() {
        PixelGlyphReader.Frame f=new PixelGlyphReader(bank("カ")).read(frame(base,false,2),new PixelGlyphReader.Grid(-4,0,2,2));
        assertFalse(f.cell(0,1).valid);assertTrue(f.literals(0,1).isEmpty());
    }
    @Test public void displayNormalizationPreservesJapanesePunctuationAndKanaSize() {
        assertEquals("12AZaz ！ァア",PixelGlyphReader.displayLiteral("１２ＡＺａｚ　！ァア"));
    }
    @Test public void sourceGlyphNamesUseThePredeclaredDisplayCharactersWithoutDroppingCells(){assertEquals("/?…・！",PixelGlyphReader.displayLiteral("／？⋯·！"));}
    @Test public void uniformCaptureEdgeIsUncertainButInteriorFontPixelsRemainObserved(){
        SourceFrame original=frame(base,true,2);byte[] rgb=original.copyRgb();Arrays.fill(rgb,0,320*2*3,(byte)0);
        PixelGlyphReader.Frame read=new PixelGlyphReader(bank("カ")).read(new SourceFrame(rgb,320,288,0,1,30),new PixelGlyphReader.Grid(0,0,2,2));
        assertEquals(Collections.singletonList("ガ"),read.literals(0,1));assertEquals(~255L,read.cell(0,0).knownPixelMask);assertEquals(-1L,read.cell(0,1).knownPixelMask);
        Arrays.fill(rgb,0,320*2*3,(byte)114);
        read=new PixelGlyphReader(bank("カ")).read(new SourceFrame(rgb,320,288,0,1,30),new PixelGlyphReader.Grid(0,0,2,2));assertEquals(~255L,read.cell(0,0).knownPixelMask);
        // A dark row inside the picture does not trigger the capture-edge rule.
        rgb=original.copyRgb();Arrays.fill(rgb,320*16*3,320*18*3,(byte)0);
        read=new PixelGlyphReader(bank("カ")).read(new SourceFrame(rgb,320,288,0,1,30),new PixelGlyphReader.Grid(0,0,2,2));assertEquals(-1L,read.cell(0,1).knownPixelMask);
    }
}
