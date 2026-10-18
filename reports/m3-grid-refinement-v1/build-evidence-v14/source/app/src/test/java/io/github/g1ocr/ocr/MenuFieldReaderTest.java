package io.github.g1ocr.ocr;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class MenuFieldReaderTest {
    @Test public void partialChildRequiresWholeLowerEdgeAndRecognizedMenuContext(){
        SceneFieldReaderTest.Image im=partialParent();Random random=new Random(821);
        for(String s:new String[]{"└","┘"}){long mask=random.nextLong()&random.nextLong();im.masks.put(s,mask);im.bank.add(new GlyphTemplate("unit","main",im.bank.size(),mask,Collections.singletonList(s)));}
        for(int x=4;x<=19;x++)im.cell(x==4?"└":x==19?"┘":"─",x,12);
        MenuFieldReader reader=new MenuFieldReader();
        for(MenuFieldReader.Layout layout:new MenuFieldReader.Layout[]{MenuFieldReader.Layout.START,MenuFieldReader.Layout.BACKPACK,MenuFieldReader.Layout.PC_ITEMS,MenuFieldReader.Layout.SHOP_BUY,MenuFieldReader.Layout.SHOP_SELL_LIST})assertTrue(reader.partialChildOutline(im.read(),layout));
        assertFalse(reader.partialChildOutline(im.read(),MenuFieldReader.Layout.NONE));
        assertFalse(reader.partialChildOutline(im.read(),MenuFieldReader.Layout.CHOICE));
        for(int y=192;y<208;y++)Arrays.fill(im.rgb,(y*320+144)*3,(y*320+160)*3,(byte)255);
        assertFalse(reader.partialChildOutline(im.read(),MenuFieldReader.Layout.PC_ITEMS));
    }
    @Test public void completeChildTopIsNotPartialEvenBeforeSelectionArrives(){
        SceneFieldReaderTest.Image im=partialParent();Random random=new Random(821);
        for(String s:new String[]{"└","┘"}){long mask=random.nextLong()&random.nextLong();im.masks.put(s,mask);im.bank.add(new GlyphTemplate("unit","main",im.bank.size(),mask,Collections.singletonList(s)));}
        box(im,4,2,19,12);im.text("AB",6,4);
        assertFalse(new MenuFieldReader().partialChildOutline(im.read(),MenuFieldReader.Layout.BACKPACK));
    }
    @Test public void merchantParentUsesScreenOrderAndKeepsMissingMoneyEmpty(){
        for(boolean amount:new boolean[]{false,true}){
            SceneFieldReaderTest.Image im=new SceneFieldReaderTest.Image();Random random=new Random(791);
            for(String s:new String[]{"か","い","に","き","た","う","り","べ","つ","で","す","お","ね","円"}){
                long mask=random.nextLong()&random.nextLong();im.masks.put(s,mask);im.bank.add(new GlyphTemplate("unit","main",im.bank.size(),mask,Collections.singletonList(s)));
            }
            im.text("かいに きた",2,1);im.text("うりに きた",2,3);im.text("べつに いです",2,5);im.text("おかね",14,0);
            if(amount)im.text("37円",16,1);
            List<JapaneseOcrEngine.Region> out=new ArrayList<>();assertEquals(MenuFieldReader.Layout.SHOP_PARENT,new MenuFieldReader().read(im.read(),Collections.emptyList(),out));
            assertEquals(amount?5:4,out.size());assertEquals("shop_money_label",out.get(0).role);assertEquals("shop_menu_choice",out.get(1).role);
            if(amount){assertEquals("money_amount",out.get(2).role);assertEquals("37円",out.get(2).text);}
            assertEquals("うりに きた",out.get(amount?3:2).text);
            for(int i=0;i<out.size();i++)assertEquals(i,out.get(i).readingOrder);
            assertEquals("dialogue_line",MenuFieldReader.messageRole(MenuFieldReader.Layout.SHOP_PARENT));
        }
    }
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
    @Test public void partiallyDrawnListDoesNotHideUndrawnParentRowsAndSeparatesQuantity(){
        SceneFieldReaderTest.Image im=new SceneFieldReaderTest.Image();Random random=new Random(313);
        for(String s:new String[]{"か","い","に","き","た","う","り","べ","つ","で","す","×","┌","─","┐","│","└","┘"}) {
            long mask=random.nextLong()&random.nextLong();im.masks.put(s,mask);im.bank.add(new GlyphTemplate("unit","main",im.bank.size(),mask,Collections.singletonList(s)));
        }
        im.text("かいに きた",2,1);im.text("うりに きた",2,3);im.text("べつに いです",2,5);
        im.cell("┌",4,2);im.cell("┐",19,2);im.cell("│",4,3);im.cell("│",4,4);im.cell("│",19,4);
        im.text("AB",6,4);im.cell("×",15,4);im.cell("7",17,4);
        PixelGlyphReader.Frame read=im.read();List<JapaneseOcrEngine.Region> out=new ArrayList<>();
        assertEquals(MenuFieldReader.Layout.SHOP_SELL_LIST,new MenuFieldReader().read(read,Collections.emptyList(),out));
        assertTrue(out.stream().anyMatch(r->r.role.equals("menu_label")&&r.text.equals("べつに いです")));
        assertTrue(out.stream().anyMatch(r->r.role.equals("quantity_label")&&r.text.equals("×")));
        assertTrue(out.stream().anyMatch(r->r.role.equals("item_quantity")&&r.text.equals("7")&&r.gridRect[0]==17));
    }
    @Test public void coveredBaseLeavesAnObservedMarkButVisibleKanaConsumesItsOwnMark(){
        SceneFieldReaderTest.Image im=new SceneFieldReaderTest.Image();Random random=new Random(112);
        for(String s:new String[]{"カ","゛","▶","┌","┐","│"}){long mask=random.nextLong()&random.nextLong();im.masks.put(s,mask);im.bank.add(new GlyphTemplate("unit","main",im.bank.size(),mask,Collections.singletonList(s)));}
        im.cell("カ",2,2);im.cell("゛",2,1);im.cell("゛",4,1);im.cell("┌",4,2);im.cell("┐",19,2);im.cell("│",4,4);im.cell("│",19,4);im.cell("▶",1,4);im.cell("▶",5,4);
        List<BoxTextDetector.Symbol> symbols=new MenuFieldReader().uncoveredParentSymbols(im.read(),MenuFieldReader.Layout.PC_ITEMS);
        assertEquals(2,symbols.size());assertTrue(symbols.stream().anyMatch(s->s.kind.equals("dakuten")&&s.column==4&&s.row==1));
        assertTrue(symbols.stream().anyMatch(s->s.kind.equals("filled_right")&&s.column==1&&s.row==4));
        assertFalse(symbols.stream().anyMatch(s->s.column==2||s.column==5));
    }
    @Test public void parentMenuRedrawReadsOnlyRowsWithCurrentSideBorders(){
        SceneFieldReaderTest.Image im=partialParent();im.text("AB",2,2);im.text("CD",2,4);
        // Unenclosed background letters must not become the not-yet-copied third menu row.
        im.text("CD",2,6);im.cell("▶",1,2);im.cell("▶",1,6);
        PixelGlyphReader.Frame read=im.read();MenuFieldReader reader=new MenuFieldReader();List<JapaneseOcrEngine.Region> out=new ArrayList<>();
        assertEquals(MenuFieldReader.Layout.PC_MAIN_PARTIAL,reader.read(read,new BoxTextDetector().detect(read),out));
        assertEquals(2,out.size());assertEquals("AB",out.get(0).text);assertEquals("CD",out.get(1).text);
        assertEquals("pc_menu_choice",out.get(0).role);
        List<BoxTextDetector.Symbol> symbols=reader.uncoveredParentSymbols(read,MenuFieldReader.Layout.PC_MAIN_PARTIAL);
        assertEquals(1,symbols.size());assertEquals(2,symbols.get(0).row);
    }
    @Test public void parentRedrawRequiresBothSidesAndACompleteSupportedTop(){
        SceneFieldReaderTest.Image im=partialParent();im.text("AB",2,2);im.cell("│",14,2);
        // Erase one side of the first text row: the top alone is insufficient evidence.
        for(int y=32;y<48;y++)Arrays.fill(im.rgb,(y*320+224)*3,(y*320+240)*3,(byte)255);
        List<JapaneseOcrEngine.Region> out=new ArrayList<>();assertEquals(MenuFieldReader.Layout.NONE,new MenuFieldReader().read(im.read(),Collections.emptyList(),out));assertTrue(out.isEmpty());
    }
    private static SceneFieldReaderTest.Image partialParent(){
        SceneFieldReaderTest.Image im=new SceneFieldReaderTest.Image();Random random=new Random(914);
        for(String s:new String[]{"┌","─","┐","│","▶"}){long mask=random.nextLong()&random.nextLong();im.masks.put(s,mask);im.bank.add(new GlyphTemplate("unit","main",im.bank.size(),mask,Collections.singletonList(s)));}
        for(int x=0;x<=14;x++)im.cell(x==0?"┌":x==14?"┐":"─",x,0);
        for(int y=1;y<=4;y++){im.cell("│",0,y);im.cell("│",14,y);}return im;
    }
    private static void box(SceneFieldReaderTest.Image im,int l,int t,int r,int b){
        for(int x=l;x<=r;x++){im.cell(x==l?"┌":x==r?"┐":"─",x,t);im.cell(x==l?"└":x==r?"┘":"─",x,b);}
        for(int y=t+1;y<b;y++){im.cell("│",l,y);im.cell("│",r,y);}
    }
}
