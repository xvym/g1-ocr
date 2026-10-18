package io.github.g1ocr.verification;
import com.google.gson.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class ComparisonAggregateTest {
    private JsonObject score(int deletions,boolean exact){return JsonParser.parseString("{\"substitutions\":0,\"deletions\":"+deletions+",\"insertions\":0,\"exact\":"+exact+",\"regionGeometry\":true,\"symbols\":true,\"state\":true}").getAsJsonObject();}
    @Test public void overlapCannotHideWorseTextOrGeometry(){JsonObject a=score(2,false),b=score(0,true);b.addProperty("symbols",false);JsonObject r=ComparisonAggregate.worse(a,b);assertEquals(2,r.get("deletions").getAsInt());assertFalse(r.get("exact").getAsBoolean());assertFalse(r.get("symbols").getAsBoolean());assertEquals(r,ComparisonAggregate.worse(b,a));}
    @Test public void transitivelyOverlappingFrozenIntervalsShareOneDenominator(){ComparisonAggregate.Union u=new ComparisonAggregate.Union();u.join("a","b");u.join("c","b");assertEquals(u.root("a"),u.root("c"));assertNotEquals(u.root("a"),u.root("another_occurrence"));}
}
