package io.github.g1ocr.verification;

import com.google.gson.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Contract rejection/projection tests only; no simulated image is treated as video GT. */
public class GtGeometryTest {
    private JsonObject json(String literal){return JsonParser.parseString(literal).getAsJsonObject();}
    private JsonObject layout(){return json("{\"sourceOrigin\":[150,0],\"sourceFrameSize\":[960,544],\"sourceScaleXNumerator\":15,\"sourceScaleXDenominator\":4,\"sourceScaleYNumerator\":34,\"sourceScaleYDenominator\":9}");}
    private JsonObject packed(){return json("{\"text\":\"HP:\",\"gridRect\":[11,3.5,12.5,4],\"logicalRect\":[88,28,100,32],\"sourceRect\":[480,105,525,121],\"markCells\":[],\"renderEvidence\":\"unit test geometry only; no image reading claimed\",\"renderUnits\":[{\"kind\":\"compound_label\",\"text\":\"HP\",\"logicalRect\":[88,28,96,32]},{\"kind\":\"compound_label\",\"text\":\":\",\"logicalRect\":[96,28,100,32]}]}");}
    private JsonObject empty(){return json("{\"kind\":\"non_target\",\"completePage\":false,\"cursor\":{\"downArrowVisible\":false,\"menuCursors\":[]}}");}
    private JsonObject detached(){return json("{\"detachedMarks\":[{\"kind\":\"dakuten\",\"gridRect\":[14,1,15,2],\"sourceRect\":[570,30,600,61],\"renderNote\":\"base fully occluded; two visible upper strokes only\"}]}");}
    @Test public void packedLabelsCountLiteralCharactersWithoutInventingExtraCells(){assertEquals(3,GtGeometry.region(packed(),layout()));}
    @Test(expected=IllegalArgumentException.class) public void packedUnitsCannotOmitOrReplaceLiteralCharacters(){JsonObject r=packed();r.addProperty("text","HP");GtGeometry.region(r,layout());}
    @Test(expected=IllegalArgumentException.class) public void packedUnitGapsAreNotAccepted(){JsonObject r=packed();r.getAsJsonArray("renderUnits").get(1).getAsJsonObject().getAsJsonArray("logicalRect").set(0,new JsonPrimitive(97));GtGeometry.region(r,layout());}
    @Test(expected=IllegalArgumentException.class) public void packedLabelNeedsRawGeometryEvidence(){JsonObject r=packed();r.remove("renderEvidence");GtGeometry.region(r,layout());}
    @Test public void isolatedUpperMarksRemainSymbolsInsteadOfInvisibleKana(){assertEquals(1,GtGeometry.symbols(detached(),layout()));}
    @Test(expected=IllegalArgumentException.class) public void detachedMarkCannotRestoreAnOccludedWord(){JsonObject s=detached();s.getAsJsonArray("detachedMarks").get(0).getAsJsonObject().addProperty("restoredBaseText","ずかん");GtGeometry.symbols(s,layout());}
    @Test public void onlyExplicitNonTargetScopeAllowsNoPage(){assertFalse(GtGeometry.nonTarget(json("{}")));assertTrue(GtGeometry.nonTarget(json("{\"scoreKind\":\"non_target_only\"}")));GtGeometry.nonTargetState(empty(),0,0);}
    @Test(expected=IllegalArgumentException.class) public void nonTargetCannotHideExpectedText(){GtGeometry.nonTargetState(empty(),1,0);}
    @Test(expected=IllegalArgumentException.class) public void nonTargetCannotHideVisibleCursor(){JsonObject s=empty();s.getAsJsonObject("cursor").addProperty("downArrowVisible",true);GtGeometry.nonTargetState(s,0,0);}
    @Test(expected=IllegalArgumentException.class) public void unknownStateCannotBeRelabeledAsEmptySuccess(){JsonObject s=empty();s.addProperty("kind","unknown");GtGeometry.nonTargetState(s,0,0);}
    private JsonObject twoArrows(){return json("{\"cursor\":{\"downArrowVisible\":true,\"downArrow\":{\"gridRect\":[18,16,19,17],\"sourceRect\":[690,483,720,514]},\"additionalDownArrows\":[{\"gridRect\":[18,11,19,12],\"sourceRect\":[690,332,720,363],\"renderNote\":\"test geometry only; list and message arrows coexist\"}]}}");}
    @Test public void simultaneousListAndMessageArrowsHaveIndependentGeometry(){assertEquals(0,GtGeometry.symbols(twoArrows(),layout()));}
    @Test(expected=IllegalArgumentException.class) public void secondArrowCannotUseWrongSourceRectangle(){JsonObject s=twoArrows();s.getAsJsonObject("cursor").getAsJsonArray("additionalDownArrows").get(0).getAsJsonObject().getAsJsonArray("sourceRect").set(0,new JsonPrimitive(691));GtGeometry.symbols(s,layout());}
    @Test(expected=IllegalArgumentException.class) public void sameArrowCannotBeCountedTwice(){JsonObject s=twoArrows();JsonObject c=s.getAsJsonObject("cursor");c.getAsJsonArray("additionalDownArrows").get(0).getAsJsonObject().add("gridRect",c.getAsJsonObject("downArrow").get("gridRect").deepCopy());GtGeometry.symbols(s,layout());}
}
