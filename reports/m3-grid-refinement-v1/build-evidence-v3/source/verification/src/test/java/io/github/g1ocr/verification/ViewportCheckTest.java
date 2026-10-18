package io.github.g1ocr.verification;

import com.google.gson.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ViewportCheckTest {
    private JsonObject layout(){return JsonParser.parseString("{\"sourceOrigin\":[10,20],\"sourceScaleXNumerator\":15,\"sourceScaleXDenominator\":4,\"sourceScaleYNumerator\":34,\"sourceScaleYDenominator\":9}").getAsJsonObject();}
    private JsonObject actual(double x,double y){JsonObject a=new JsonObject();a.addProperty("originX",x);a.addProperty("originY",y);a.addProperty("scaleX",15.0/4);a.addProperty("scaleY",34.0/9);return a;}
    @Test public void independentRationalBoundaryComparisonIsExact(){assertEquals(0,ViewportCheck.boundaryError(actual(10,20),layout(),new int[]{0,0}));}
    @Test public void shiftCannotBeHiddenByCorrectNominalViewport(){assertEquals(8,ViewportCheck.boundaryError(actual(10,20),layout(),new int[]{2,0}));}
    @Test public void correctOffsetMustBeExpressedInActualPixelOrigin(){assertEquals(0,ViewportCheck.boundaryError(actual(17.5,20),layout(),new int[]{2,0}));}
}
