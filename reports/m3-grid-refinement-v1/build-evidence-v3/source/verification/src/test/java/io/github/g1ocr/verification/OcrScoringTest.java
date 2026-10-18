package io.github.g1ocr.verification;

import com.google.gson.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class OcrScoringTest {
    private JsonObject object(String s){return JsonParser.parseString(s).getAsJsonObject();}
    private JsonObject state(String text){JsonObject s=object("{\"kind\":\"loading\",\"regions\":[],\"cursor\":{\"menuCursors\":[],\"downArrowVisible\":false}}");s.getAsJsonArray("regions").add(region(text,0,1));return s;}
    private JsonObject region(String text,int order,int column){JsonObject r=object("{\"role\":\"dialogue_line\",\"readingOrder\":"+order+",\"gridRect\":["+column+",14,3,15],\"sourceRect\":[8,112,24,120],\"markCells\":[]}");r.addProperty("text",text);return r;}
    @Test public void unicodeSmallKanaDakutenAndSpacesRemainSignificant(){assertEquals(1,OcrScoring.edit("キョ","キヨ").errors());assertEquals(1,OcrScoring.edit("ポケモン","ホケモン").substitutions);assertEquals(1,OcrScoring.edit("5/ 5","5/5").deletions);assertEquals(2,OcrScoring.scalars("あ\uD83D\uDE00").length);assertEquals(2,OcrScoring.edit("が","か\u3099").errors());}
    @Test public void editReportsInsertionsAndDeletions(){assertEquals(1,OcrScoring.edit("206/206","206/20").deletions);assertEquals(1,OcrScoring.edit("","あ").insertions);assertEquals(2,OcrScoring.edit("あい","").deletions);}
    @Test(expected=IllegalArgumentException.class) public void invalidUnicodeRejected(){OcrScoring.edit("a","\uD800");}
    @Test public void missingOutputFailsEmptyStateToo(){JsonObject e=state("あ");e.add("regions",new JsonArray());OcrScoring.FrameScore r=OcrScoring.score(e,new JsonArray(),null,true);assertFalse(r.exact);assertEquals(0,r.characters);}
    @Test public void matchingDoesNotChooseTextSimilarity(){JsonObject e=state("あい");JsonArray actual=new JsonArray();actual.add(region("あい",0,2));OcrScoring.FrameScore r=OcrScoring.score(e,actual,e,false);assertEquals(2,r.deletions);assertEquals(2,r.insertions);assertFalse(r.regionGeometry);}
    @Test(expected=IllegalArgumentException.class) public void duplicateRegionKeysRejected(){JsonObject e=state("あい");JsonArray actual=e.getAsJsonArray("regions").deepCopy();actual.add(actual.get(0).deepCopy());OcrScoring.score(e,actual,e,false);}
    @Test public void geometryAndCursorFailuresIndependentOfText(){JsonObject e=state("あい"),a=e.deepCopy();a.getAsJsonArray("regions").get(0).getAsJsonObject().add("sourceRect",JsonParser.parseString("[9,112,25,120]"));a.getAsJsonObject("cursor").addProperty("downArrowVisible",true);OcrScoring.FrameScore r=OcrScoring.score(e,a.getAsJsonArray("regions"),a,false);assertEquals(0,r.errors());assertFalse(r.exact);assertFalse(r.regionGeometry);assertFalse(r.symbols);}
    @Test public void detachedMarkIsScoredWithZeroCharacters(){JsonObject e=state("あ");e.add("regions",new JsonArray());e.add("detachedMarks",JsonParser.parseString("[{\"kind\":\"dakuten\",\"gridRect\":[14,1,15,2],\"sourceRect\":[570,30,600,61]}]"));JsonObject a=e.deepCopy();a.remove("detachedMarks");OcrScoring.FrameScore r=OcrScoring.score(e,new JsonArray(),a,false);assertEquals(1,r.detachedExpected);assertEquals(0,r.detachedActual);assertFalse(r.exact);}
    @Test public void renderingEvidenceDoesNotAffectVisibleSymbolMatch(){JsonObject e=state("あい"),a=e.deepCopy();e.add("detachedMarks",JsonParser.parseString("[{\"kind\":\"dakuten\",\"gridRect\":[14,1,15,2],\"sourceRect\":[570,30,600,61],\"renderNote\":\"independent GT evidence\"}]"));a.add("detachedMarks",JsonParser.parseString("[{\"kind\":\"dakuten\",\"gridRect\":[14,1,15,2],\"sourceRect\":[570,30,600,61]}]"));assertTrue(OcrScoring.score(e,a.getAsJsonArray("regions"),a,false).exact);}
    @Test public void approvedLegacyEmptyCursorEquivalentToExplicitEmpty(){JsonObject e=state("あい"),a=e.deepCopy();e.add("cursor",object("{\"gridRect\":null,\"sourceRect\":null,\"downArrowVisible\":false}"));assertTrue(OcrScoring.score(e,a.getAsJsonArray("regions"),a,false).exact);}
    @Test public void approvedTriangleDescriptorAliasKeepsSameVisibleSymbol(){JsonObject a=object("{\"style\":\"outlined_right\",\"gridRect\":[1,1,2,2],\"sourceRect\":[8,8,16,16]}"),b=a.deepCopy();b.addProperty("style","outline_right");assertEquals(OcrScoring.symbolList(a),OcrScoring.symbolList(b));}
}
