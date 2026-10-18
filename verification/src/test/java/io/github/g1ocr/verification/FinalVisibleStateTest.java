package io.github.g1ocr.verification;
import com.google.gson.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class FinalVisibleStateTest {
    private JsonObject row(){return JsonParser.parseString("{\"visibleState\":{\"kind\":\"message_loading\",\"cursor\":{\"downArrowVisible\":false},\"detachedMarks\":[]}}").getAsJsonObject();}
    private JsonObject corrected(){JsonObject r=row(),v=r.getAsJsonObject("visibleState").deepCopy();v.addProperty("kind","message_complete");r.add("correctedVisibleState",v);JsonObject p=new JsonObject();p.addProperty("reason","optional_pinned_source_exact_current_two_line_page_boundary");p.addProperty("catalogSha256",new String(new char[64]).replace('\0','a'));JsonArray refs=new JsonArray();refs.add(new JsonObject());p.add("matchedSources",refs);r.add("stateCorrectionEvidence",p);return r;}
    @Test public void originalStateRemainsRawAndFinalInputIsExplicit(){JsonObject r=row();assertSame(r.get("visibleState"),OcrComparison.finalVisible(r));r=corrected();assertSame(r.get("correctedVisibleState"),OcrComparison.finalVisible(r));assertEquals("message_loading",r.getAsJsonObject("visibleState").get("kind").getAsString());assertNull(OcrComparison.finalVisible(null));}
    @Test public void missingEmptyAndWrongEvidenceAreRejected(){for(int variant=0;variant<4;variant++){JsonObject r=corrected();if(variant==0)r.remove("stateCorrectionEvidence");if(variant==1)r.getAsJsonObject("stateCorrectionEvidence").add("matchedSources",new JsonArray());if(variant==2)r.getAsJsonObject("stateCorrectionEvidence").addProperty("reason","just_stable");if(variant==3)r.getAsJsonObject("stateCorrectionEvidence").addProperty("catalogSha256","unknown");try{OcrComparison.finalVisible(r);fail("Accepted unsupported proof");}catch(IllegalArgumentException expected){}}}
    @Test public void sourceBoundaryCannotEraseSymbolsOrAcceptOtherKindCorrections(){for(int variant=0;variant<3;variant++){JsonObject r=corrected(),v=r.getAsJsonObject("correctedVisibleState");if(variant==0)v.getAsJsonObject("cursor").addProperty("downArrowVisible",true);if(variant==1)v.addProperty("kind","menu_complete");if(variant==2)r.getAsJsonObject("visibleState").addProperty("kind","message_clearing");try{OcrComparison.finalVisible(r);fail("Accepted field or other-kind correction");}catch(IllegalArgumentException expected){}}}
}
