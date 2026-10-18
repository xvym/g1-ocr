package io.github.g1ocr.ocr;
import io.github.g1ocr.capture.SourceFrame;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class ShopMoneyTransitionTrackerTest {
    private SceneFieldReaderTest.Image panel(String amount){
        SceneFieldReaderTest.Image im=new SceneFieldReaderTest.Image();Random random=new Random(7821);
        for(String s:new String[]{"┌","┐","│","└","┘","─","お","か","ね","円"}){long mask=random.nextLong()&random.nextLong();im.masks.put(s,mask);im.bank.add(new GlyphTemplate("unit","main",im.bank.size(),mask,Collections.singletonList(s)));}
        for(int x=11;x<=19;x++){if(x<14||x>16)im.cell(x==11?"┌":x==19?"┐":"─",x,0);im.cell(x==11?"└":x==19?"┘":"─",x,2);}
        im.text("おかね",14,0);im.cell("│",11,1);im.cell("│",19,1);im.text(amount,19-amount.length(),1);return im;
    }
    private boolean observe(ShopMoneyTransitionTracker t,SceneFieldReaderTest.Image im,int pts,MenuFieldReader.Layout layout){return t.observe(new SourceFrame(im.rgb,320,288,pts,1,30),layout,im.read());}
    @Test public void amountMustHaveBeenSeenAndBlankPanelNeverInventsItsValue(){
        ShopMoneyTransitionTracker t=new ShopMoneyTransitionTracker();SceneFieldReaderTest.Image empty=panel("");
        assertFalse(observe(t,empty,0,MenuFieldReader.Layout.SHOP_PARENT));
        assertFalse(observe(t,panel("37円"),1,MenuFieldReader.Layout.SHOP_PARENT));
        assertTrue(observe(t,empty,2,MenuFieldReader.Layout.SHOP_PARENT));assertTrue(observe(t,empty,3,MenuFieldReader.Layout.SHOP_PARENT));
        assertFalse(observe(t,panel("102円"),4,MenuFieldReader.Layout.SHOP_PARENT));
        t.reset();assertFalse(observe(t,empty,5,MenuFieldReader.Layout.SHOP_PARENT));
    }
    @Test public void unknownInkMissingBorderLayoutChangeAndTimeRollbackDiscardHistory(){
        for(int fault=0;fault<4;fault++){
            ShopMoneyTransitionTracker t=new ShopMoneyTransitionTracker();observe(t,panel("9円"),10,MenuFieldReader.Layout.SHOP_PARENT);
            SceneFieldReaderTest.Image im=panel("");MenuFieldReader.Layout menu=MenuFieldReader.Layout.SHOP_PARENT;int pts=11;
            if(fault==0)im.paint(0x55aa55aa55aa55aaL,15*8,8,8,8);
            if(fault==1)for(int y=32;y<48;y++)Arrays.fill(im.rgb,(y*320+11*16)*3,(y*320+12*16)*3,(byte)255);
            if(fault==2)menu=MenuFieldReader.Layout.SHOP_SELL_LIST;
            if(fault==3)pts=9;
            assertFalse(observe(t,im,pts,menu));assertFalse(observe(t,panel(""),12,MenuFieldReader.Layout.SHOP_PARENT));
        }
    }
}
