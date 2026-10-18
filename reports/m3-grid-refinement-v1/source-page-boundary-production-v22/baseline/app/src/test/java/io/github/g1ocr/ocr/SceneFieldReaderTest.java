package io.github.g1ocr.ocr;
import io.github.g1ocr.capture.SourceFrame;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class SceneFieldReaderTest {
    static final class Image {
        final List<GlyphTemplate> bank=new ArrayList<>();final Map<String,Long> masks=new HashMap<>();final byte[] rgb=new byte[320*288*3];
        final PixelGlyphReader.Grid grid=new PixelGlyphReader.Grid(0,0,2,2);
        Image(){Arrays.fill(rgb,(byte)255);Random random=new Random(1903);int tile=128;for(String s:new String[]{"A","B","C","D","0","1","2","3","4","5","6","7","8","9","/",".","PP","P","<LV>","№","<ID>"}){long mask=random.nextLong()&random.nextLong();masks.put(s,mask);bank.add(new GlyphTemplate("toy","main",tile++,mask,Collections.singletonList(s)));}
            bank.add(new GlyphTemplate("toy","battle-extra",0x71,0x6d939a6d00000000L,Collections.emptyList()));
            bank.add(new GlyphTemplate("toy","battle-extra",0x62,0x0803090300000000L,Collections.emptyList()));}
        void cell(String glyph,int x,int y){paint(masks.get(glyph),x*8,y*8,8,8);}
        void paint(long mask,int px,int py,int w,int h){for(int y=0;y<h;y++)for(int x=0;x<w;x++)if((mask&(1L<<(y*w+x)))!=0)for(int dy=0;dy<2;dy++)for(int dx=0;dx<2;dx++){int p=((py+y)*2+dy)*320+(px+x)*2+dx;rgb[p*3]=rgb[p*3+1]=rgb[p*3+2]=0;}}
        void text(String s,int x,int y){for(char c:s.toCharArray()){if(c!=' ')cell(String.valueOf(c),x,y);x++;}}
        void hp(int x,int y){paint(0x6d939a6d00000000L,x*8,y*8,8,8);paint(0x0803090300000000L,(x+1)*8,y*8,8,8);}
        SourceFrame source(){return new SourceFrame(rgb,320,288,0,1,30);}
        PixelGlyphReader.Frame read(){return new PixelGlyphReader(bank,0,30).read(source(),grid);}
    }
    private static JapaneseOcrEngine.Region role(List<JapaneseOcrEngine.Region> regions,String role){return regions.stream().filter(r->r.role.equals(role)).findFirst().get();}
    @Test public void readsDynamicStatusFieldsAndKeepsInternalFractionSpaces(){
        Image im=new Image();im.cell("<LV>",16,1);im.text("73",17,1);im.text("AB",11,1);im.cell("№",1,7);im.cell(".",2,7);im.text("007",3,7);im.hp(11,3);im.text("12/ 17",13,4);
        List<JapaneseOcrEngine.Region> regions=new ArrayList<>();assertEquals(SceneFieldReader.Scene.STATUS,new SceneFieldReader(im.bank).read(im.source(),im.read(),regions));
        assertEquals("AB",role(regions,"pokemon_name").text);assertEquals("73",role(regions,"pokemon_level").text);assertEquals("007",role(regions,"pokedex_number").text);
        assertEquals("12/ 17",role(regions,"hp_current_max").text);assertArrayEquals(new double[]{88,28,100,32},regions.stream().filter(r->r.text.equals("HP:")).findFirst().get().logicalRect,0);
        JapaneseOcrEngine.Region statusHp=regions.stream().filter(r->r.text.equals("HP:")).findFirst().get();
        assertEquals(1,statusHp.renderUnits.size());assertEquals("HP:",statusHp.renderUnits.get(0).text);
        Image other=new Image();other.cell("<LV>",16,1);other.text("24",17,1);other.text("CD",11,1);other.cell("№",1,7);other.cell(".",2,7);
        regions.clear();new SceneFieldReader(other.bank).read(other.source(),other.read(),regions);assertEquals("CD",role(regions,"pokemon_name").text);assertEquals("24",role(regions,"pokemon_level").text);
    }
    @Test public void hpRequiresTheWholeCurrentTwelveByFourPixelPattern(){
        Image im=new Image();im.hp(2,2);PackedGlyphReader reader=new PackedGlyphReader(im.bank);assertTrue(reader.hp(im.source(),im.grid,16,20));
        assertFalse(reader.hp(im.source(),im.grid,16,12));assertFalse(reader.hp(new Image().source(),im.grid,16,20));
        assertFalse(new PackedGlyphReader(Collections.emptyList()).hp(im.source(),im.grid,16,20));
    }
    @Test public void unrelatedLettersWithoutRendererAnchorsDoNotCreateStatusFields(){
        Image im=new Image();im.text("AB1234",11,1);List<JapaneseOcrEngine.Region> out=new ArrayList<>();
        assertEquals(SceneFieldReader.Scene.NONE,new SceneFieldReader(im.bank).read(im.source(),im.read(),out));assertTrue(out.isEmpty());
    }
    @Test public void partyHpRetainsSeparatePrintedHpAndColonUnitsAcrossDynamicRows(){
        Image im=new Image();int[] rows={1,7,11};String[] names={"AB","CD","BA"},levels={"24","5","73"};
        for(int i=0;i<rows.length;i++){int y=rows[i];im.text(names[i],3,y);im.cell("<LV>",8,y);im.text(levels[i],9,y);im.hp(11,y-1);im.text("12/ 17",13,y);}
        List<JapaneseOcrEngine.Region> out=new ArrayList<>();assertEquals(SceneFieldReader.Scene.PARTY,new SceneFieldReader(im.bank).read(im.source(),im.read(),out));
        List<JapaneseOcrEngine.Region> hp=new ArrayList<>();for(JapaneseOcrEngine.Region r:out)if(r.text.equals("HP:"))hp.add(r);assertEquals(3,hp.size());
        for(int i=0;i<hp.size();i++){
            JapaneseOcrEngine.Region r=hp.get(i);double top=(rows[i]-1)*8+4;
            assertEquals("compact_party_label",r.role);assertEquals(2,r.renderUnits.size());
            assertEquals("HP",r.renderUnits.get(0).text);assertEquals(":",r.renderUnits.get(1).text);
            assertArrayEquals(new double[]{88,top,96,top+4},r.renderUnits.get(0).logicalRect,0);
            assertArrayEquals(new double[]{96,top,100,top+4},r.renderUnits.get(1).logicalRect,0);
            assertArrayEquals(new double[]{88,top,100,top+4},r.logicalRect,0);
            assertArrayEquals(new int[]{176,(int)top*2,200,(int)(top+4)*2},r.sourceRect);
        }
        assertEquals("AB",role(out,"party_pokemon_name").text);assertEquals("24",role(out,"level").text);assertEquals("12/ 17",role(out,"hp_current_max").text);
    }
    @Test public void partyHpUnitsRequireCurrentColonPixelsNotJustHpLetters(){
        Image im=new Image();im.cell("<LV>",8,1);im.hp(11,0);im.cell("<LV>",8,3);
        im.paint(0x6d939a6d00000000L,88,16,8,8); // Later row has the HP tile, but no current colon tile.
        List<JapaneseOcrEngine.Region> out=new ArrayList<>();assertEquals(SceneFieldReader.Scene.PARTY,new SceneFieldReader(im.bank).read(im.source(),im.read(),out));
        assertEquals(1,out.stream().filter(r->r.text.equals("HP:")).count());
        assertFalse(new PackedGlyphReader(im.bank).hp(im.source(),im.grid,88,20));
    }
}
