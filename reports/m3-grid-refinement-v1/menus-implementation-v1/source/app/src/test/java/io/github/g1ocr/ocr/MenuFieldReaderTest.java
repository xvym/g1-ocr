package io.github.g1ocr.ocr;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class MenuFieldReaderTest {
    @Test public void recognizesNoMenuFromUnrelatedLetters(){
        SceneFieldReaderTest.Image im=new SceneFieldReaderTest.Image();im.text("ABCD",6,4);List<JapaneseOcrEngine.Region> out=new ArrayList<>();
        assertEquals(MenuFieldReader.Layout.NONE,new MenuFieldReader().read(im.read(),Collections.emptyList(),out));assertTrue(out.isEmpty());
    }
    @Test public void layeredListSplitsCurrentItemAndPriceAndKeepsParentPrefixes(){
        SceneFieldReaderTest.Image im=new SceneFieldReaderTest.Image();Random random=new Random(997);
        for(String s:new String[]{"か","い","に","き","た","う","り","べ","つ","円","┌","─","┐","│","└","┘"}) {
            long mask=random.nextLong()&random.nextLong();im.masks.put(s,mask);im.bank.add(new GlyphTemplate("unit","main",im.bank.size(),mask,Collections.singletonList(s)));
        }
        box(im,4,2,19,12);im.text("かいに きた",2,1);im.text("うり",2,3);im.text("べつ",2,5);
        im.text("AB",6,4);im.text("123円",15,4);im.text("CD",6,6);im.text("7円",17,6);
        PixelGlyphReader.Frame read=im.read();List<JapaneseOcrEngine.Region> out=new ArrayList<>();
        assertEquals(MenuFieldReader.Layout.SHOP_BUY,new MenuFieldReader().read(read,new BoxTextDetector().detect(read),out));
        assertTrue(out.stream().anyMatch(r->r.role.equals("item_name")&&r.text.equals("AB")));
        assertTrue(out.stream().anyMatch(r->r.role.equals("item_price")&&r.text.equals("123円")&&r.gridRect[0]==15));
        assertTrue(out.stream().anyMatch(r->r.role.equals("occluded_menu_visible_prefix")&&r.text.equals("うり")));
    }
    private static void box(SceneFieldReaderTest.Image im,int l,int t,int r,int b){
        for(int x=l;x<=r;x++){im.cell(x==l?"┌":x==r?"┐":"─",x,t);im.cell(x==l?"└":x==r?"┘":"─",x,b);}
        for(int y=t+1;y<b;y++){im.cell("│",l,y);im.cell("│",r,y);}
    }
}
