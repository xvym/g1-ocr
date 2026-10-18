package io.github.g1ocr.ocr;
import org.junit.Test;
import static org.junit.Assert.*;
import static io.github.g1ocr.ocr.MenuFieldReader.Layout.*;

public class ShopMessageRoleTrackerTest {
    @Test public void overlayPreservesUnchangedPromptButReplacementUsesCurrentRole(){
        ShopMessageRoleTracker t=new ShopMessageRoleTracker();
        assertEquals("shop_prompt",t.resolve(SHOP_PARENT,"14:AB\n","shop_prompt"));
        assertEquals("shop_prompt",t.resolve(SHOP_SELL_LIST,"14:AB\n","shop_message"));
        assertEquals("shop_prompt",t.resolve(SHOP_SELL_LIST,"14:AB\n","shop_message"));
        assertEquals("shop_message",t.resolve(SHOP_SELL_LIST,"14:CD\n","shop_message"));
    }
    @Test public void clearOrNonShopOrResetDoesNotLeakAnEarlierPrompt(){
        for(int boundary=0;boundary<3;boundary++){
            ShopMessageRoleTracker t=new ShopMessageRoleTracker();t.resolve(SHOP_PARENT,"14:AB\n","shop_prompt");
            if(boundary==0)t.resolve(SHOP_PARENT,"","shop_prompt");
            if(boundary==1)t.resolve(NONE,"14:AB\n","dialogue");
            if(boundary==2)t.reset();
            assertEquals("shop_message",t.resolve(SHOP_SELL_LIST,"14:AB\n","shop_message"));
        }
    }
    @Test public void coldListAndMovedLineRequireCurrentContext(){
        ShopMessageRoleTracker t=new ShopMessageRoleTracker();
        assertEquals("shop_message",t.resolve(SHOP_SELL_LIST,"14:AB\n","shop_message"));
        t.reset();t.resolve(SHOP_PARENT,"14:AB\n","shop_prompt");
        assertEquals("shop_message",t.resolve(SHOP_SELL_LIST,"16:AB\n","shop_message"));
    }
}
