package io.github.g1ocr.verification;
import com.google.gson.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
public class SourceAliasConfigurationTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private Path catalog()throws Exception {
        Path p=temp.newFile().toPath();Files.write(p,("{\"schema\":\"japanese-source-aliases-v1\",\"catalog\":[{\"literal\":\"あへ\",\"source\":\"fixture\",\"commit\":\"fixture\",\"path\":\"fixture\",\"line\":1,\"sha256\":\"fixture\"}]}").getBytes(StandardCharsets.UTF_8));return p;
    }
    @Test public void copiedFinalRetainsAllRawAndStateEvidence()throws Exception {
        Path p=catalog();SourceAliasConfiguration config=new SourceAliasConfiguration(p,VideoTool.sha(p));
        JsonObject row=JsonParser.parseString("{\"rawRegions\":[{\"id\":\"line\",\"text\":\"あヘ\",\"glyphCandidates\":[[\"あ\"],[\"へ\",\"ヘ\"]],\"sourceRect\":[1,2,3,4]}],\"visibleState\":{\"kind\":\"loading\"}}").getAsJsonObject();
        JsonObject before=row.deepCopy();config.annotate(row);
        assertEquals(before.get("rawRegions"),row.get("rawRegions"));assertEquals(before.get("visibleState"),row.get("visibleState"));
        assertEquals("あへ",row.getAsJsonArray("correctedRegions").get(0).getAsJsonObject().get("text").getAsString());
        assertEquals(VideoTool.sha(p),row.getAsJsonArray("correctionEvidence").get(0).getAsJsonObject().get("catalogSha256").getAsString());
    }
    @Test public void wrongConfigurationHashFailsClosed()throws Exception {
        try{new SourceAliasConfiguration(catalog(),"wrong");fail("accepted modified catalog");}catch(IllegalArgumentException expected){}
    }
}
