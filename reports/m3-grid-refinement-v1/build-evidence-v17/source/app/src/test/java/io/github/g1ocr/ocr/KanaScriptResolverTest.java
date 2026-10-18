package io.github.g1ocr.ocr;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class KanaScriptResolverTest {
    private List<String> one(String s){return Collections.singletonList(s);}
    @Test public void katakanaAnchorResolvesOnlyExistingPixelAlias(){
        KanaScriptResolver.Result r=new KanaScriptResolver().resolve(Arrays.asList(one("グ"),Arrays.asList("り","リ"),one("ー"),one("ン")),new int[]{0,0,0,0});
        assertEquals(one("リ"),r.literals.get(1));assertEquals(0,r.resolutions.get(0).anchor);
    }
    @Test public void hiraganaAnchorCanResolveComposedMarkAliases(){assertEquals(one("べ"),new KanaScriptResolver().resolve(Arrays.asList(one("し"),Arrays.asList("べ","ベ"),one("る")),new int[]{0,0,0}).literals.get(1));}
    @Test public void conflictingEquidistantScriptsRemainUnresolved(){List<String> pair=Arrays.asList("へ","ヘ");assertEquals(pair,new KanaScriptResolver().resolve(Arrays.asList(one("あ"),pair,one("ア")),new int[]{0,0,0}).literals.get(1));}
    @Test public void allSharedNameCannotBeGuessed(){List<String> pair=Arrays.asList("り","リ");assertEquals(pair,new KanaScriptResolver().resolve(Arrays.asList(pair,pair),new int[]{0,0}).literals.get(0));}
    @Test public void spacesAndWeakAnchorsDoNotSupplyEvidence(){List<String> pair=Arrays.asList("り","リ");assertEquals(pair,new KanaScriptResolver().resolve(Arrays.asList(one("ア"),one(" "),pair),new int[]{0,0,0}).literals.get(2));assertEquals(pair,new KanaScriptResolver().resolve(Arrays.asList(one("ア"),pair),new int[]{4,0}).literals.get(1));}
}
