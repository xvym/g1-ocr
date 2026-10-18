package io.github.g1ocr.verification;
import com.google.gson.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class GlyphReadAuditTest {
    private JsonObject state(String text,String rect){return JsonParser.parseString("{\"regions\":[{\"text\":\""+text+"\",\"gridRect\":"+rect+"}]}").getAsJsonObject();}
    private JsonObject actual(String status,String literals){return JsonParser.parseString("{\"geometryStatus\":\""+status+"\",\"cells\":[{\"column\":1,\"row\":2,\"valid\":true,\"literals\":"+literals+"}]}").getAsJsonObject();}
    @Test public void ambiguousCorrectCandidateIsNotAnExactGlyph(){
        JsonObject r=GlyphReadAudit.compare(state("へ","[1,2,2,3]"),actual("LOCATED","[\"へ\",\"ヘ\"]"));
        assertEquals(0,r.get("exactCharacters").getAsInt());assertEquals(1,r.get("candidateContainsExpected").getAsInt());
    }
    @Test public void compactLabelsCannotDisappearFromTheDenominator(){
        JsonObject r=GlyphReadAudit.compare(state("HP:","[1,2.5,2.5,3]"),actual("LOCATED","[]"));
        assertEquals(3,r.get("expectedCharacters").getAsInt());assertEquals(3,r.get("unsupportedCharacters").getAsInt());assertEquals(0,r.get("eligibleCharacters").getAsInt());
    }
    @Test public void MissingGridCannotCountAVisibleSpaceAsCorrect(){
        JsonObject r=GlyphReadAudit.compare(state(" ","[2,2,3,3]"),actual("NO_GRID","[]"));
        assertEquals(0,r.get("exactCharacters").getAsInt());assertEquals(1,r.get("eligibleCharacters").getAsInt());
    }
    @Test public void renderedPBindingAddsNoMessageTextAndDoesNotChangeOtherGlyphs(){
        JsonArray bindings=JsonParser.parseString("[{\"profile\":\"status-P\",\"tile\":114,\"displayAliases\":[\"P\"]}]").getAsJsonArray();
        assertEquals(java.util.Collections.singletonList("P"),GlyphReadAudit.renderAliases(JsonParser.parseString("{\"profile\":\"status-P\",\"tile\":114,\"aliases\":[]}").getAsJsonObject(),bindings));
        assertTrue(GlyphReadAudit.renderAliases(JsonParser.parseString("{\"profile\":\"main\",\"tile\":114,\"aliases\":[]}").getAsJsonObject(),bindings).isEmpty());
    }
}
