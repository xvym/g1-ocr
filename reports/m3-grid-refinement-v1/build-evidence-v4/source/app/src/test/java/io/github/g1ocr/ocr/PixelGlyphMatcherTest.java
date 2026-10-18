package io.github.g1ocr.ocr;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class PixelGlyphMatcherTest {
    private GlyphTemplate glyph(int tile,long ink,String... aliases){return new GlyphTemplate("source","main",tile,ink,Arrays.asList(aliases));}
    @Test public void identicalKanaShapesRetainEveryAliasAndCandidate(){GlyphTemplate a=glyph(0xcd,123,"へ","ヘ"),b=glyph(0xd8,123,"り","リ");List<PixelGlyphMatcher.Candidate> result=new PixelGlyphMatcher(Arrays.asList(b,a)).rank(123,0);assertEquals(2,result.size());assertEquals(Arrays.asList("へ","ヘ"),result.get(0).getGlyph().getAliases());assertEquals(0,result.get(1).getDistance());}
    @Test public void oneChangedPixelDoesNotBecomeExact(){GlyphTemplate a=glyph(0x80,3,"ア");PixelGlyphMatcher matcher=new PixelGlyphMatcher(Arrays.asList(a));assertTrue(matcher.rank(7,0).isEmpty());assertEquals(1,matcher.rank(7,1).get(0).getDistance());}
    @Test public void all64BitsAndOppositePolarityAreSignificant(){assertEquals(64,glyph(1,0,"a").distance(-1L));assertEquals(1,glyph(1,Long.MIN_VALUE,"a").distance(0));}
    @Test public void callersCannotMutateTheBank(){List<String> aliases=new ArrayList<>(Arrays.asList("あ"));GlyphTemplate t=new GlyphTemplate("source","main",0x80,4,aliases);aliases.set(0,"い");assertEquals("あ",t.getAliases().get(0));}
    @Test(expected=UnsupportedOperationException.class) public void candidatesAreImmutable(){new PixelGlyphMatcher(Arrays.asList(glyph(0x80,3,"ア"))).rank(3,0).clear();}
    @Test(expected=IllegalArgumentException.class) public void decomposedAliasCannotSilentlyNormalize(){glyph(0x80,3,"か\u3099");}
}
