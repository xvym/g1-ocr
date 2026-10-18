package io.github.g1ocr.verification;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Independent actual-output comparison. Accepts no claim of actual UI presentation. */
final class OcrComparison {
    static void compare(Path frozen,String selection,Path actualDirectory,Path output) throws Exception {
        FrozenDataset data=new FrozenDataset(frozen);FrozenDataset.require(!Files.exists(output),"Use a new comparison directory");Files.createDirectories(output);
        JsonArray reports=new JsonArray();
        List<FrozenDataset.Clip> selected=data.selected(selection);
        for(FrozenDataset.Clip item:selected) {
            Path actualPath=actualDirectory.resolve(item.id+".jsonl");
            FrozenDataset.require(Files.isRegularFile(actualPath),"Actual file missing (an empty existing file means every output missing): "+actualPath);
            JsonObject report=compareClip(item,actualPath,output.resolve(item.id+"-frames.jsonl"));
            VideoTool.save(output.resolve(item.id+"-report.json"),report);reports.add(report);
        }
        JsonObject report=new JsonObject();report.addProperty("status","development_content_comparison_only");report.addProperty("freezeSha256",VideoTool.sha(frozen.resolve("freeze.json")));report.add("clips",reports);report.add("frozenScope",data.envelope.get("summary"));report.add("aggregate",ComparisonAggregate.build(selected,output));report.addProperty("independentAcceptance","pending_no_proven_unused_independent_session");report.addProperty("uiPresentationAndLatency","pending_independent_ui_observation_required");report.addProperty("productPassed",false);VideoTool.save(output.resolve("report.json"),report);
    }
    static JsonObject compareClip(FrozenDataset.Clip item,Path actualPath,Path diffPath) throws Exception {
        List<JsonObject> states=FrozenDataset.rows(item.gtPath),source=FrozenDataset.rows(Paths.get("data/meta/m1-v1").resolve(item.clip.get("mediaId").getAsString()).resolve("frames.jsonl"));
        int start=item.clip.getAsJsonArray("scoredRange").get(0).getAsInt(),end=item.clip.getAsJsonArray("scoredRange").get(1).getAsInt();
        Map<Integer,JsonObject> actual=new HashMap<>();
        for(JsonObject row:FrozenDataset.rows(actualPath)) {
            int n=row.get("sourceFrameIndex").getAsInt();
            FrozenDataset.require(n>=item.clip.get("startFrame").getAsInt() && n<item.clip.get("endFrameExclusive").getAsInt() && actual.put(n,row)==null,"Actual duplicate/outside clip source frame");
            FrozenDataset.require(row.get("sourcePts").equals(source.get(n).get("pts")) && row.get("timeBase").equals(source.get(n).get("timeBase")),"Actual source association mismatch");
            FrozenDataset.require(row.has("rawRegions") && row.has("visibleState"),"Actual rawRegions/visibleState required");
            FrozenDataset.require(!row.has("correctedRegions") || row.has("correctionEvidence") && !row.get("correctionEvidence").isJsonNull() && !row.get("correctionEvidence").toString().equals("[]") && !row.get("correctionEvidence").toString().equals("\"\""),"Corrected regions need auditable correction evidence");
            finalVisible(row);
        }
        Totals raw=new Totals(),fin=new Totals();JsonArray stateReports=new JsonArray();Map<String,Boolean> rawPages=new LinkedHashMap<>(),finalPages=new LinkedHashMap<>();int missing=0;
        try(BufferedWriter differences=Files.newBufferedWriter(diffPath,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)) {
            for(JsonObject state:states) {
                int stateStart=state.get("startFrame").getAsInt(),stateEnd=state.get("endFrameExclusive").getAsInt();
                double rawErrors=0,finalErrors=0,characters=0;int failedRaw=0,failedFinal=0;
                for(int n=stateStart;n<stateEnd;n++) {
                    JsonObject row=actual.get(n);if(row==null)missing++;
                    JsonArray rawRegions=row==null?new JsonArray():row.getAsJsonArray("rawRegions"),finalRegions=row!=null && row.has("correctedRegions")?row.getAsJsonArray("correctedRegions"):rawRegions;
                    JsonObject visible=row==null?null:row.getAsJsonObject("visibleState");
                    OcrScoring.FrameScore r=OcrScoring.score(state,rawRegions,visible,row==null),f=OcrScoring.score(state,finalRegions,finalVisible(row),row==null);
                    raw.add(r);fin.add(f);rawErrors+=r.errors();finalErrors+=f.errors();characters+=r.characters;if(!r.exact)failedRaw++;if(!f.exact)failedFinal++;
                    JsonObject diff=new JsonObject();diff.addProperty("sourceFrameIndex",n);diff.addProperty("sourcePts",source.get(n).get("pts").getAsLong());diff.addProperty("stateId",state.get("stateId").getAsString());diff.add("raw",new Gson().toJsonTree(r));diff.add("final",new Gson().toJsonTree(f));differences.write(diff.toString());differences.newLine();
                }
                int count=stateEnd-stateStart;raw.equalErrors+=rawErrors/count;fin.equalErrors+=finalErrors/count;raw.equalCharacters+=characters/count;fin.equalCharacters+=characters/count;
                JsonObject s=new JsonObject();s.addProperty("stateId",state.get("stateId").getAsString());s.addProperty("kind",state.get("kind").getAsString());s.addProperty("frames",count);s.addProperty("rawFailedFrames",failedRaw);s.addProperty("finalFailedFrames",failedFinal);stateReports.add(s);
                if(state.get("completePage").getAsBoolean()) {
                    String id=state.has("pageOpportunityId")?state.get("pageOpportunityId").getAsString():state.get("stateId").getAsString();
                    rawPages.put(id,rawPages.getOrDefault(id,true)&&failedRaw==0);finalPages.put(id,finalPages.getOrDefault(id,true)&&failedFinal==0);
                }
            }
        }
        JsonObject report=new JsonObject();report.addProperty("clipId",item.id);report.addProperty("set","development");report.addProperty("frames",end-start);report.addProperty("missingOutputs",missing);report.addProperty("gtSha256",VideoTool.sha(item.gtPath));report.addProperty("contractSha256",VideoTool.sha(item.contractPath));report.addProperty("actualSha256",VideoTool.sha(actualPath));report.add("raw",raw.json(rawPages));report.add("final",fin.json(finalPages));report.add("states",stateReports);report.addProperty("fullPageAcceptance","pending_UI_observation_and_independent_session; contentExactPages require every complete source frame exact");report.addProperty("timingEvidenceConsumed",false);return report;
    }
    static JsonObject finalVisible(JsonObject row){
        if(row==null)return null;JsonObject raw=row.getAsJsonObject("visibleState");if(!row.has("correctedVisibleState"))return raw;
        FrozenDataset.require(row.has("stateCorrectionEvidence")&&row.get("stateCorrectionEvidence").isJsonObject(),"Final state needs auditable source evidence");
        JsonObject proof=row.getAsJsonObject("stateCorrectionEvidence");FrozenDataset.require(proof.has("reason")&&"optional_pinned_source_exact_current_two_line_page_boundary".equals(proof.get("reason").getAsString())&&proof.has("catalogSha256")&&proof.get("catalogSha256").getAsString().matches("[0-9a-f]{64}")&&proof.has("matchedSources")&&proof.getAsJsonArray("matchedSources").size()>0,"Incomplete source boundary proof");
        JsonObject corrected=row.getAsJsonObject("correctedVisibleState"),a=raw.deepCopy(),b=corrected.deepCopy();
        FrozenDataset.require("message_loading".equals(a.get("kind").getAsString())&&"message_complete".equals(b.get("kind").getAsString()),"Unsupported final state correction");a.remove("kind");b.remove("kind");FrozenDataset.require(a.equals(b),"Source boundary may not correct visible symbols or fields");return corrected;
    }
    private static final class Totals {
        long characters,substitutions,deletions,insertions,failedFrames,emptyFrames,emptyFalsePositiveFrames,detachedExpected,detachedActual,symbolErrorFrames,geometryErrorFrames,stateErrorFrames;
        double equalErrors,equalCharacters;
        void add(OcrScoring.FrameScore f) {characters+=f.characters;substitutions+=f.substitutions;deletions+=f.deletions;insertions+=f.insertions;if(!f.exact)failedFrames++;if(f.characters==0){emptyFrames++;if(f.insertions>0)emptyFalsePositiveFrames++;}detachedExpected+=f.detachedExpected;detachedActual+=f.detachedActual;if(!f.symbols)symbolErrorFrames++;if(!f.regionGeometry)geometryErrorFrames++;if(!f.state)stateErrorFrames++;}
        JsonObject json(Map<String,Boolean> pages) {
            JsonObject r=new Gson().toJsonTree(this).getAsJsonObject();long errors=substitutions+deletions+insertions;r.addProperty("editErrors",errors);
            if(characters>0)r.addProperty("frameCharacterAccuracy",OcrScoring.accuracy(errors,characters));else r.add("frameCharacterAccuracy",JsonNull.INSTANCE);
            if(equalCharacters>0)r.addProperty("equalStateCharacterAccuracy",OcrScoring.accuracy(equalErrors,equalCharacters));else r.add("equalStateCharacterAccuracy",JsonNull.INSTANCE);
            long correct=pages.values().stream().filter(Boolean::booleanValue).count();r.addProperty("pageOpportunities",pages.size());r.addProperty("contentExactPages",correct);r.add("contentPages",new Gson().toJsonTree(pages));
            if(!pages.isEmpty())r.addProperty("contentExactPageRate",(double)correct/pages.size());else r.add("contentExactPageRate",JsonNull.INSTANCE);return r;
        }
    }
}
