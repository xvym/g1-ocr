package io.github.g1ocr.verification;

import com.google.gson.*;
import io.github.g1ocr.ocr.JapaneseSourceAliasResolver;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Explicit external source configuration, separate from frozen data and diagnostics. */
final class SourceAliasConfiguration {
    final JapaneseSourceAliasResolver resolver;
    final String sha256;
    SourceAliasConfiguration(Path path,String expectedSha)throws Exception {
        sha256=VideoTool.sha(path);FrozenDataset.require(sha256.equals(expectedSha),"Source catalog hash mismatch");
        JsonObject config=JsonParser.parseString(new String(Files.readAllBytes(path),StandardCharsets.UTF_8)).getAsJsonObject();
        FrozenDataset.require("japanese-source-aliases-v1".equals(config.get("schema").getAsString()),"Source catalog schema");
        List<JapaneseSourceAliasResolver.Entry> entries=new ArrayList<>();
        for(JsonElement value:config.getAsJsonArray("catalog")) {
            JsonObject e=value.getAsJsonObject();entries.add(new JapaneseSourceAliasResolver.Entry(e.get("literal").getAsString(),e.get("source").getAsString(),e.get("commit").getAsString(),e.get("path").getAsString(),e.get("line").getAsInt(),e.get("sha256").getAsString()));
        }
        resolver=new JapaneseSourceAliasResolver(entries);
    }
    void annotate(JsonObject row) {
        JsonArray corrected=row.getAsJsonArray("rawRegions").deepCopy(),evidence=new JsonArray();
        for(JsonElement value:corrected) {
            JsonObject region=value.getAsJsonObject();List<List<String>> candidates=new ArrayList<>();
            for(JsonElement cell:region.getAsJsonArray("glyphCandidates")){List<String> chars=new ArrayList<>();for(JsonElement c:cell.getAsJsonArray())chars.add(c.getAsString());candidates.add(chars);}
            JapaneseSourceAliasResolver.Resolution resolution=resolver.resolve(region.get("text").getAsString(),candidates);
            if(resolution==null)continue;
            JsonObject proof=new Gson().toJsonTree(resolution).getAsJsonObject();proof.add("regionId",region.get("id"));proof.addProperty("catalogSha256",sha256);evidence.add(proof);region.addProperty("text",resolution.text);
        }
        if(evidence.size()>0){row.add("correctedRegions",corrected);row.add("correctionEvidence",evidence);}
    }
}
