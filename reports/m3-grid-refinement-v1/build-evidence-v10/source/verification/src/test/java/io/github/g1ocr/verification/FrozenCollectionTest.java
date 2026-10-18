package io.github.g1ocr.verification;
import com.google.gson.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class FrozenCollectionTest {
    private JsonObject clip(){return JsonParser.parseString("{\"mediaId\":\"video\",\"mediaSha256\":\"hash\",\"timeBase\":\"1/24\",\"id\":\"stable-id\",\"startFrame\":10,\"endFrameExclusive\":20,\"scoredRange\":[12,18],\"warmup\":[10,12],\"postContext\":[18,20],\"set\":\"development\"}").getAsJsonObject();}
    @Test public void payloadPathMayChangeWithoutChangingSourceOrScope(){JsonObject a=clip(),b=a.deepCopy();a.addProperty("gt","old-approved.jsonl");b.addProperty("gt","new-approved.jsonl");FrozenDataset.sameSourceRange(a,b);}
    @Test(expected=IllegalArgumentException.class) public void shortenedScoringScopeRejected(){JsonObject a=clip(),b=a.deepCopy();b.add("scoredRange",JsonParser.parseString("[12,17]"));FrozenDataset.sameSourceRange(a,b);}
    @Test(expected=IllegalArgumentException.class) public void relabeledIndependentAcceptanceRejected(){JsonObject a=clip(),b=a.deepCopy();b.addProperty("set","acceptance");FrozenDataset.sameSourceRange(a,b);}
    @Test(expected=IllegalArgumentException.class) public void sourceIdentityChangeRejected(){JsonObject a=clip(),b=a.deepCopy();b.addProperty("mediaSha256","different");FrozenDataset.sameSourceRange(a,b);}
}
