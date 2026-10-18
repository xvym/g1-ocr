package io.github.g1ocr.verification;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Verifier-only access to the immutable approved envelope. Never passed to a consumer. */
final class FrozenDataset {
    final Path directory;
    final JsonObject envelope, batch;
    final List<Clip> clips = new ArrayList<>();
    FrozenDataset(Path directory) throws Exception { this(directory,new HashSet<Path>()); }
    private FrozenDataset(Path directory,Set<Path> visiting) throws Exception {
        this.directory = directory.toAbsolutePath().normalize();
        require(visiting.add(this.directory),"Cyclic frozen collection reference");
        envelope = VideoTool.readJson(this.directory.resolve("freeze.json"));
        if("approved_frozen_collection".equals(envelope.get("status").getAsString())) {
            Path baseFile=checkedReference(envelope.getAsJsonObject("baseFreeze"));
            JsonObject replacement=envelope.getAsJsonObject("replacement");Path amendmentFile=checkedReference(replacement.getAsJsonObject("envelope"));
            FrozenDataset base=new FrozenDataset(baseFile.getParent(),visiting),amendment=new FrozenDataset(amendmentFile.getParent(),visiting);
            String replacementId=replacement.get("id").getAsString();Clip old=base.selected(replacementId).get(0),updated=amendment.selected(replacementId).get(0);
            sameSourceRange(old.clip,updated.clip);
            require(VideoTool.sha(old.gtPath).equals(replacement.get("oldGtSha256").getAsString()) && VideoTool.sha(updated.gtPath).equals(replacement.get("newGtSha256").getAsString()),"Replacement payload binding mismatch");
            require(VideoTool.sha(old.layoutPath).equals(VideoTool.sha(updated.layoutPath)) && VideoTool.sha(old.contractPath).equals(VideoTool.sha(updated.contractPath)),"Metadata-only collection cannot change layout or scoring contract");
            require(base.envelope.get("summary").equals(envelope.get("summary")),"Collection cannot shrink or alter the original frozen denominator");
            Path batchPath=checkedReference(envelope.getAsJsonObject("batchIdentity"));require(batchPath.equals(this.directory.resolve("batch.json")),"Collection batch path mismatch");batch=VideoTool.readJson(batchPath);
            checkedReference(envelope.getAsJsonObject("overlapIntegrity"));
            for(Clip clip:base.clips)clips.add(clip.id.equals(replacementId)?updated:clip);
            require(envelope.getAsJsonArray("items").size()==clips.size() && batch.getAsJsonArray("items").size()==clips.size(),"Collection cannot add/remove approved items");
            Set<String> ids=new HashSet<>();
            for(JsonElement element:envelope.getAsJsonArray("items")) {
                JsonObject declared=element.getAsJsonObject();String id=declared.get("id").getAsString();require(ids.add(id),"Duplicate collection item");Clip actual=selected(id).get(0);
                require(declared.getAsJsonArray("payloads").size()==actual.payloads.size(),"Collection payload count changed");Set<String> kinds=new HashSet<>();
                for(JsonElement p:declared.getAsJsonArray("payloads")) {JsonObject payload=p.getAsJsonObject();String kind=payload.get("kind").getAsString();Path source=checkedReference(payload);require(kinds.add(kind) && actual.payloads.containsKey(kind) && source.equals(actual.payloads.get(kind).toAbsolutePath().normalize()),"Collection payload does not come from its approved envelope");}
                JsonObject definition=null;for(JsonElement d:batch.getAsJsonArray("items"))if(id.equals(d.getAsJsonObject().get("id").getAsString())){require(definition==null,"Duplicate collection batch item");definition=d.getAsJsonObject();}
                require(definition!=null && Paths.get(definition.get("frames").getAsString()).toAbsolutePath().normalize().equals(actual.frameDirectory.toAbsolutePath().normalize()),"Collection frame source changed");
            }
            validateOverlaps(clips,envelope.getAsJsonObject("summary").get("uniqueScoredOriginalFrames").getAsInt());visiting.remove(this.directory);return;
        }
        require("frozen_approved_scope_only".equals(envelope.get("status").getAsString()), "Missing approved frozen envelope");
        JsonObject approved=envelope.getAsJsonObject("approvedBundle"),check=envelope.getAsJsonObject("identityCheck");
        Path approvedPath=Paths.get(approved.get("path").getAsString());
        require(VideoTool.sha(approvedPath).equals(approved.get("sha256").getAsString()),"Approved bundle manifest changed");
        require(VideoTool.sha(Paths.get(check.get("path").getAsString())).equals(check.get("sha256").getAsString()),"Freeze identity evidence changed");
        JsonObject manifest=VideoTool.readJson(approvedPath);
        for(String name:Arrays.asList("batch.json","summary.json")) {
            String expected=null;for(JsonElement f:manifest.getAsJsonArray("files"))if(name.equals(f.getAsJsonObject().get("path").getAsString()))expected=f.getAsJsonObject().get("sha256").getAsString();
            require(expected!=null && expected.equals(VideoTool.sha(this.directory.resolve(name))),"Frozen metadata differs from reviewed bytes: "+name);
        }
        batch = VideoTool.readJson(this.directory.resolve("batch.json"));
        require(batch.get("reviewId").equals(envelope.getAsJsonObject("approval").get("reviewId")), "Frozen review mismatch");
        for (JsonElement element : envelope.getAsJsonArray("items")) {
            JsonObject item = element.getAsJsonObject();
            JsonObject original = null;
            for (JsonElement b : batch.getAsJsonArray("items")) if (b.getAsJsonObject().get("id").equals(item.get("id"))) original = b.getAsJsonObject();
            require(original != null, "Missing frozen clip metadata");
            Map<String,Path> payloads = new HashMap<>();
            for (JsonElement p : item.getAsJsonArray("payloads")) {
                JsonObject payload = p.getAsJsonObject();
                Path file = payload.has("file") ? inside(this.directory, payload.get("file").getAsString()) : Paths.get(payload.get("approvedSource").getAsString());
                require(VideoTool.sha(file).equals(payload.get("sha256").getAsString()) && Files.size(file)==payload.get("bytes").getAsLong(), "Frozen payload changed: " + file);
                require(payloads.put(payload.get("kind").getAsString(), file)==null, "Duplicate payload kind");
            }
            if (item.has("priorFreeze")) {
                JsonObject prior = item.getAsJsonObject("priorFreeze");
                require(VideoTool.sha(Paths.get(prior.get("path").getAsString())).equals(prior.get("sha256").getAsString()), "Prior approved reference envelope changed");
            }
            clips.add(new Clip(item.get("id").getAsString(), payloads, original));
        }
        require(!clips.isEmpty() && clips.size()==batch.getAsJsonArray("items").size(), "Empty/incomplete frozen dataset");
        Set<String> ids = new HashSet<>(); for (Clip c:clips) require(ids.add(c.id), "Duplicate frozen clip ID");
        visiting.remove(this.directory);
    }
    static Path checkedReference(JsonObject ref) throws Exception {
        String field=ref.has("path")?"path":"approvedSource";Path file=Paths.get(ref.get(field).getAsString()).toAbsolutePath().normalize();
        require(file.startsWith(Paths.get("").toAbsolutePath().normalize()) && VideoTool.sha(file).equals(ref.get("sha256").getAsString()),"Frozen reference identity mismatch: "+file);
        if(ref.has("bytes"))require(Files.size(file)==ref.get("bytes").getAsLong(),"Frozen reference byte count mismatch");return file;
    }
    static void sameSourceRange(JsonObject a,JsonObject b) {
        for(String field:Arrays.asList("mediaId","mediaSha256","timeBase","id","startFrame","endFrameExclusive","scoredRange","warmup","postContext","set"))require(a.has(field) && a.get(field).equals(b.get(field)),"Metadata amendment changed source/range: "+field);
    }
    static void validateOverlaps(List<Clip> clips,int expectedFrames) throws Exception {
        Map<String,JsonObject> seen=new HashMap<>();
        for(Clip clip:clips)for(JsonObject state:rows(clip.gtPath))for(int n=state.get("startFrame").getAsInt();n<state.get("endFrameExclusive").getAsInt();n++) {
            String key=clip.clip.get("mediaId").getAsString()+":"+n;JsonObject value=ComparisonAggregate.visible(state);value.add("completePage",state.get("completePage"));JsonObject prior=seen.put(key,value);require(prior==null || prior.equals(value),"Conflicting approved collection overlap: "+key);
        }
        require(seen.size()==expectedFrames,"Approved collection source-frame denominator mismatch");
    }
    List<Clip> selected(String id) {
        List<Clip> result = new ArrayList<>();
        for (Clip c:clips) if ("all".equals(id) || c.id.equals(id)) result.add(c);
        require(!result.isEmpty(), "Unknown frozen clip: " + id);
        result.sort(Comparator.comparing((Clip c)->c.clip.get("mediaId").getAsString()).thenComparingInt(c->c.clip.get("startFrame").getAsInt()));
        return result;
    }
    static Path inside(Path parent, String child) {
        Path p=parent.resolve(child).normalize(); require(p.startsWith(parent) && !p.equals(parent), "Path escapes frozen directory"); return p;
    }
    static List<JsonObject> rows(Path path) throws IOException {
        List<JsonObject> rows=new ArrayList<>();
        try (BufferedReader in=Files.newBufferedReader(path,StandardCharsets.UTF_8)) {
            String line; while ((line=in.readLine())!=null) { require(!line.trim().isEmpty(), "Blank JSONL record"); rows.add(JsonParser.parseString(line).getAsJsonObject()); }
        }
        return rows;
    }
    static void require(boolean ok,String why) { if(!ok) throw new IllegalArgumentException(why); }
    static final class Clip {
        final String id;
        final Path gtPath, layoutPath, contractPath, frameDirectory;
        final Map<String,Path> payloads;
        final JsonObject clip;
        Clip(String id,Map<String,Path> paths,JsonObject original) throws Exception {
            this.id=id; require(paths.keySet().equals(new HashSet<>(Arrays.asList("gt","clip","layout","contract"))), "Missing frozen payload");payloads=Collections.unmodifiableMap(new HashMap<>(paths));
            gtPath=paths.get("gt"); layoutPath=paths.get("layout"); contractPath=paths.get("contract"); clip=VideoTool.readJson(paths.get("clip"));
            frameDirectory=Paths.get(original.get("frames").getAsString());
            require("development".equals(clip.get("set").getAsString()), "Current corpus has no proven independent acceptance session");
        }
    }
}
