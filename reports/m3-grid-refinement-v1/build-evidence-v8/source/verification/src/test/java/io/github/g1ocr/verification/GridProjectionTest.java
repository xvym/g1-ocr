package io.github.g1ocr.verification;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;
import static org.junit.Assert.*;

public class GridProjectionTest {
    private JsonObject legacy() {
        return JsonParser.parseString("{\"sourceOrigin\":[300,108],\"sourceFrameSize\":[960,544],\"sourceScaleNumerator\":9,\"sourceScaleDenominator\":4}").getAsJsonObject();
    }
    private JsonObject anisotropic() {
        return JsonParser.parseString("{\"sourceOrigin\":[180,0],\"sourceFrameSize\":[960,544],\"sourceScaleXNumerator\":15,\"sourceScaleXDenominator\":4,\"sourceScaleYNumerator\":34,\"sourceScaleYDenominator\":9}").getAsJsonObject();
    }
    @Test public void approvedTrialGeometryRemainsByteEquivalentInMeaning() {
        assertArrayEquals(new int[] {318,342,534,378}, GridProjection.rectangle(legacy(),1,13,13,15));
    }
    @Test public void sourceRasterAxesAreIndependentAndNeverApplySarAgain() {
        assertArrayEquals(new int[] {210,392,600,454}, GridProjection.rectangle(anisotropic(),1,13,14,15));
        assertArrayEquals(new int[] {210,90,240,121}, GridProjection.rectangle(anisotropic(),1,3,2,4));
        assertArrayEquals(new int[] {180,0,780,544}, GridProjection.rectangle(anisotropic(),0,0,20,18));
    }
    @Test(expected=IllegalArgumentException.class) public void partialAxisProfileDoesNotFallBackOrGuess() {
        JsonObject profile=legacy(); profile.addProperty("sourceScaleXNumerator",15);
        GridProjection.rectangle(profile,1,13,13,15);
    }
    @Test(expected=IllegalArgumentException.class) public void invisibleUpperMarkOutsideScreenCannotBeInvented() {
        GridProjection.rectangle(anisotropic(),1,-1,2,1);
    }
    @Test(expected=IllegalArgumentException.class) public void projectionCannotClaimCoordinatesOutsideInput() {
        JsonObject profile=anisotropic(); profile.addProperty("sourceScaleYNumerator",40);
        GridProjection.rectangle(profile,1,16,4,18);
    }
    @Test public void battleShakeMovesBothPackedAndRegularEnvelopesBeforeRationalRounding() {
        JsonObject profile=anisotropic();profile.add("renderOffsetLogicalPixels",JsonParser.parseString("[2,0]"));
        assertArrayEquals(new int[]{277,0,398,61},GridProjection.rectangle(profile,3,0,7,2));
        assertArrayEquals(new int[]{247,75,293,91},GridProjection.pixels(profile,16,20,28,24));
        profile.add("renderOffsetLogicalPixels",JsonParser.parseString("[-2,0]"));
        assertArrayEquals(new int[]{262,0,383,61},GridProjection.rectangle(profile,3,0,7,2));
    }
    @Test(expected=IllegalArgumentException.class) public void shakeCannotPretendAClippedGlyphIsFullyVisible() {
        JsonObject profile=anisotropic();profile.add("renderOffsetLogicalPixels",JsonParser.parseString("[-2,0]"));
        GridProjection.rectangle(profile,0,0,1,1);
    }
    @Test(expected=IllegalArgumentException.class) public void fractionalLogicalShakeNeedsANewContractRatherThanSilentRounding() {
        JsonObject profile=anisotropic();profile.add("renderOffsetLogicalPixels",JsonParser.parseString("[0.5,0]"));
        GridProjection.rectangle(profile,3,0,7,2);
    }
}
