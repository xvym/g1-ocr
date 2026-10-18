package io.github.g1ocr.ocr;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class JapaneseSourceAliasResolverTest {
    private JapaneseSourceAliasResolver resolver(String... lines){List<JapaneseSourceAliasResolver.Entry> e=new ArrayList<>();for(String s:lines)e.add(new JapaneseSourceAliasResolver.Entry(s,"source","commit","file",1,"hash"));return new JapaneseSourceAliasResolver(e);}
    private List<List<String>> cells(){return Arrays.asList(Collections.singletonList("あ"),Arrays.asList("へ","ヘ"));}
    @Test public void recordsOnlyCurrentAliasAndPreservesCandidates(){List<List<String>> c=cells();JapaneseSourceAliasResolver.Resolution r=resolver("あへ","あへ").resolve("あヘ",c);assertEquals("あへ",r.text);assertEquals("あヘ",r.rawText);assertEquals(2,r.sourceReferences.size());assertEquals(1,r.changes.get(0).index);assertEquals(Arrays.asList("へ","ヘ"),c.get(1));}
    @Test public void conflictingSourcesAbstain(){assertNull(resolver("あへ","あヘ").resolve("あヘ",cells()));}
    @Test public void unknownAndPartialLinesAbstain(){assertNull(resolver("いへ").resolve("あヘ",cells()));assertNull(resolver("あへあ").resolve("あヘ",cells()));}
    @Test public void missingDiacriticCannotBeFilled(){assertNull(resolver("あべ").resolve("あヘ",cells()));}
    @Test public void pageChangesHaveNoRememberedText(){JapaneseSourceAliasResolver r=resolver("あへ");assertNotNull(r.resolve("あヘ",cells()));assertNull(r.resolve("",Collections.<List<String>>emptyList()));assertNull(r.resolve("あ",Collections.singletonList(Collections.singletonList("あ"))));}
    @Test(expected=IllegalArgumentException.class) public void dynamicSourceRejected(){resolver("<PLAYER>へ");}
    @Test public void nonKanaAmbiguityCannotBeCorrected(){assertNull(resolver("？").resolve("?",Collections.singletonList(Arrays.asList("?","？"))));}
}
