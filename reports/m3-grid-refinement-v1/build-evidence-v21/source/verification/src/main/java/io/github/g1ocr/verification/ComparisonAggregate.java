package io.github.g1ocr.verification;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;

/** Same original frame is counted once; repeated runs of an overlap must all be correct. */
final class ComparisonAggregate {
    static final class Union {
        private final Map<String,String> parents=new HashMap<>();
        String root(String key){String p=parents.get(key);if(p==null){parents.put(key,key);return key;}if(!p.equals(key))parents.put(key,p=root(p));return p;}
        void join(String a,String b){parents.put(root(a),root(b));}
    }
    private static final class Frame {
        String state,page; JsonObject expected,raw,fin;
        Frame(String state,String page,JsonObject expected,JsonObject raw,JsonObject fin){this.state=state;this.page=page;this.expected=expected;this.raw=raw;this.fin=fin;}
    }
    static JsonObject visible(JsonObject state) {
        JsonObject out=new JsonObject();out.add("kind",state.get("kind"));JsonArray regions=new JsonArray();
        for(JsonElement r:state.getAsJsonArray("regions"))regions.add(OcrScoring.fields(r.getAsJsonObject(),"role","readingOrder","text","gridRect","sourceRect","markCells","logicalRect","renderUnits"));
        out.add("regions",regions);out.add("cursor",OcrScoring.cursor(state));out.add("detachedMarks",OcrScoring.symbolList(state.get("detachedMarks")));return out;
    }
    static JsonObject worse(JsonObject a,JsonObject b) {
        int ae=errors(a),be=errors(b);JsonObject result=(ae>=be?a:b).deepCopy();
        for(String f:Arrays.asList("exact","regionGeometry","symbols","state"))result.addProperty(f,a.get(f).getAsBoolean() && b.get(f).getAsBoolean());
        return result;
    }
    private static int errors(JsonObject score){return score.get("substitutions").getAsInt()+score.get("deletions").getAsInt()+score.get("insertions").getAsInt();}
    static JsonObject build(List<FrozenDataset.Clip> clips,Path reports) throws Exception {
        Union states=new Union(),pages=new Union();Map<String,Frame> frames=new LinkedHashMap<>();int references=0;
        for(FrozenDataset.Clip clip:clips) {
            Map<Integer,JsonObject> gt=new HashMap<>();
            for(JsonObject s:FrozenDataset.rows(clip.gtPath))for(int n=s.get("startFrame").getAsInt();n<s.get("endFrameExclusive").getAsInt();n++)gt.put(n,s);
            for(JsonObject diff:FrozenDataset.rows(reports.resolve(clip.id+"-frames.jsonl"))) {
                references++;int n=diff.get("sourceFrameIndex").getAsInt();JsonObject expected=gt.get(n);String media=clip.clip.get("mediaId").getAsString();
                String key=media+":"+n,state=media+":"+clip.id+":"+expected.get("stateId").getAsString();
                String page=expected.get("completePage").getAsBoolean()?media+":"+clip.id+":"+(expected.has("pageOpportunityId")?expected.get("pageOpportunityId").getAsString():expected.get("stateId").getAsString()):null;
                states.root(state);if(page!=null)pages.root(page);Frame previous=frames.get(key);
                if(previous==null)frames.put(key,new Frame(state,page,visible(expected),diff.getAsJsonObject("raw"),diff.getAsJsonObject("final")));
                else {
                    FrozenDataset.require(previous.expected.equals(visible(expected)),"Conflicting frozen overlap at "+key);
                    states.join(previous.state,state);FrozenDataset.require((previous.page==null)==(page==null),"Conflicting complete-page overlap");if(page!=null)pages.join(previous.page,page);
                    previous.raw=worse(previous.raw,diff.getAsJsonObject("raw"));previous.fin=worse(previous.fin,diff.getAsJsonObject("final"));
                }
            }
        }
        JsonObject report=new JsonObject();report.addProperty("frameReferences",references);report.addProperty("uniqueSourceFrames",frames.size());report.addProperty("duplicateReferences",references-frames.size());
        report.addProperty("overlapRule","One original frame counted once. Max edit errors and conjunction of exact geometry/state/symbol correctness across every replay of overlapping ranges; never select a successful replay to hide a failure.");
        for(String stage:Arrays.asList("raw","final")) {
            long characters=0,errors=0,failed=0,empty=0,falsePositive=0;Map<String,double[]> stateTotals=new LinkedHashMap<>();Map<String,Boolean> pageCorrect=new LinkedHashMap<>();
            for(Frame frame:frames.values()) {
                JsonObject score="raw".equals(stage)?frame.raw:frame.fin;int c=score.get("characters").getAsInt(),e=errors(score);characters+=c;errors+=e;boolean exact=score.get("exact").getAsBoolean();if(!exact)failed++;
                if(c==0){empty++;if(score.get("insertions").getAsInt()>0)falsePositive++;}
                double[] totals=stateTotals.computeIfAbsent(states.root(frame.state),k->new double[3]);totals[0]+=e;totals[1]+=c;totals[2]++;
                if(frame.page!=null){String page=pages.root(frame.page);pageCorrect.put(page,pageCorrect.getOrDefault(page,true)&&exact);}
            }
            double equalErrors=0,equalCharacters=0;for(double[] s:stateTotals.values()){equalErrors+=s[0]/s[2];equalCharacters+=s[1]/s[2];}
            JsonObject totals=new JsonObject();totals.addProperty("characters",characters);totals.addProperty("editErrors",errors);totals.addProperty("failedFrames",failed);totals.addProperty("emptyFrames",empty);totals.addProperty("emptyFalsePositiveFrames",falsePositive);totals.addProperty("uniqueContinuousStates",stateTotals.size());totals.addProperty("equalStateErrors",equalErrors);totals.addProperty("equalStateCharacters",equalCharacters);totals.addProperty("pageOpportunities",pageCorrect.size());totals.addProperty("contentExactPages",pageCorrect.values().stream().filter(Boolean::booleanValue).count());
            if(characters>0)totals.addProperty("frameCharacterAccuracy",OcrScoring.accuracy(errors,characters));else totals.add("frameCharacterAccuracy",JsonNull.INSTANCE);
            if(equalCharacters>0)totals.addProperty("equalStateCharacterAccuracy",OcrScoring.accuracy(equalErrors,equalCharacters));else totals.add("equalStateCharacterAccuracy",JsonNull.INSTANCE);
            report.add(stage,totals);
        }
        return report;
    }
}
