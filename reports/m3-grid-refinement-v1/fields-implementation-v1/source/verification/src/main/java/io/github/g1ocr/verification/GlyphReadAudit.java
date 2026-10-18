package io.github.g1ocr.verification;

import com.google.gson.*;
import io.github.g1ocr.capture.SourceFrame;
import io.github.g1ocr.ocr.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Raw-cell development diagnostic, explicitly not the frozen-contract scene/region OCR score. */
final class GlyphReadAudit {
    private static final Gson JSON=new Gson();
    static List<GlyphTemplate> auditedBank()throws Exception {
        Path auditPath=Paths.get("reports/m3-font-audit-v1/source-audit-v2/report.json");
        FrozenDataset.require(VideoTool.sha(auditPath).equals("7dbead23d979e368afde384493177d9d9c05fbbf9c39fcd82461ff5172f9c2a3"),"Pinned source audit changed");
        JsonObject audit=VideoTool.readJson(auditPath);JsonArray displayBindings=VideoTool.readJson(Paths.get("resources/glyph-display-bindings-v1.json")).getAsJsonArray("bindings");
        List<GlyphTemplate> bank=new ArrayList<>();
        for(JsonElement e:audit.getAsJsonArray("sources")) {
            JsonObject source=e.getAsJsonObject();Path file=Paths.get(source.get("localBank").getAsString());
            FrozenDataset.require(VideoTool.sha(file).equals(source.get("localBankSha256").getAsString()),"Audited source glyph bank changed");
            for(JsonElement t:VideoTool.readJson(file).getAsJsonArray("templates")) {
                JsonObject glyph=t.getAsJsonObject();List<String> aliases=renderAliases(glyph,displayBindings);
                bank.add(new GlyphTemplate(glyph.get("sourceId").getAsString(),glyph.get("profile").getAsString(),glyph.get("tile").getAsInt(),Long.parseUnsignedLong(glyph.get("darkInkHex").getAsString(),16),aliases));
            }
        }
        return bank;
    }
    static List<String> renderAliases(JsonObject glyph,JsonArray bindings) {
        List<String> aliases=new ArrayList<>();for(JsonElement a:glyph.getAsJsonArray("aliases"))aliases.add(a.getAsString());
        for(JsonElement b:bindings){JsonObject binding=b.getAsJsonObject();if(glyph.get("profile").equals(binding.get("profile"))&&glyph.get("tile").equals(binding.get("tile")))for(JsonElement a:binding.getAsJsonArray("displayAliases"))if(!aliases.contains(a.getAsString()))aliases.add(a.getAsString());}
        return aliases;
    }
    static void audit(Path frozen,String selection,Path output)throws Exception {
        audit(frozen,selection,output,false);
    }
    static void audit(Path frozen,String selection,Path output,final boolean enhanced)throws Exception {
        FrozenDataset data=new FrozenDataset(frozen);List<GlyphTemplate> bank=auditedBank();
        FrozenDataset.require(!Files.exists(output),"Use a new glyph diagnostic directory");Files.createDirectories(output);
        Map<GlyphTemplate,Integer> templateIds=new IdentityHashMap<>();JsonArray templateIndex=new JsonArray();
        for(int i=0;i<bank.size();i++){GlyphTemplate g=bank.get(i);templateIds.put(g,i);JsonObject v=new JsonObject();v.addProperty("sourceId",g.getSourceId());v.addProperty("profile",g.getProfile());v.addProperty("tile",g.getTile());v.add("aliases",new Gson().toJsonTree(g.getAliases()));templateIndex.add(v);}
        VideoTool.save(output.resolve("template-index.json"),templateIndex);JsonArray reports=new JsonArray();Map<String,JsonObject> unique=new TreeMap<>();long retained=0;
        for(FrozenDataset.Clip clip:data.selected(selection)) {
            Path actualPath=output.resolve(clip.id+"-cells.jsonl");
            try(final BufferedWriter actual=Files.newBufferedWriter(actualPath,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)) {
                ReplayTimeline.Consumer consumer=new ReplayTimeline.Consumer() {
                    GameViewportLocator locator;PixelGlyphReader reader;
                    public void reset(){locator=new GameViewportLocator();reader=new PixelGlyphReader(bank);}
                    public void accept(SourceFrame frame)throws Exception {
                        long begin=System.nanoTime();JsonObject row=new JsonObject();row.addProperty("pts",frame.getPts());PixelGlyphReader.Grid grid=null;
                        if(enhanced) {
                            FrameGeometryEstimator.Estimate estimate=new FrameGeometryEstimator().estimate(frame);
                            row.addProperty("geometryStatus",estimate.located?"LOCATED":"NO_GRID");row.addProperty("geometryMethod",estimate.method);row.add("paletteEnvelope",JSON.toJsonTree(estimate.copyEnvelope()));row.add("refinement",JSON.toJsonTree(estimate.hypotheses));grid=estimate.grid;
                        } else {
                            GameViewportLocator.Result coarse=locator.locate(frame);row.addProperty("geometryStatus",coarse.status.name());row.add("paletteEnvelope",JSON.toJsonTree(coarse.copyEnvelope()));
                            if(coarse.status==GameViewportLocator.Status.LOCATED) {
                                RasterGridRefiner.Result refine=new RasterGridRefiner().refine(frame,coarse.copyEnvelope(),coarse.originX,coarse.originY);row.add("refinement",JSON.toJsonTree(refine));
                                double ox=coarse.originX,oy=coarse.originY,sx=coarse.scaleX,sy=coarse.scaleY;
                                if(refine.available){RasterGridRefiner.Axis x=refine.xCandidates.get(0),y=refine.yCandidates.get(0);ox=x.origin;oy=y.origin;sx=x.scale;sy=y.scale;}
                                grid=new PixelGlyphReader.Grid(ox,oy,sx,sy);
                            }
                        }
                        JsonArray cells=new JsonArray();
                        if(grid!=null) {
                            row.addProperty("originX",grid.originX);row.addProperty("originY",grid.originY);row.addProperty("scaleX",grid.scaleX);row.addProperty("scaleY",grid.scaleY);
                            PixelGlyphReader.Frame read=reader.read(frame,grid);Map<Integer,KanaScriptResolver.Result> scripts=new HashMap<>();
                            if(enhanced){row.add("boxes",JSON.toJsonTree(new BoxTextDetector().detect(read)));for(int y=0;y<18;y++)scripts.put(y,read.contextualRow(y));}
                            for(PixelGlyphReader.Cell c:read.cells()) {
                                if(c.blank)continue;JsonObject item=new JsonObject();item.addProperty("column",c.column);item.addProperty("row",c.row);
                                item.addProperty("valid",c.valid);item.addProperty("contrast",c.contrast);item.addProperty("threshold",c.threshold);item.addProperty("distance",c.distance);item.addProperty("inkHex",Long.toUnsignedString(c.ink,16));
                                item.add("sourceRect",JSON.toJsonTree(c.copySourceRect()));item.add("baseCandidates",JSON.toJsonTree(c.candidates));item.add("literals",JSON.toJsonTree(read.literals(c.column,c.row)));
                                if(enhanced){KanaScriptResolver.Result script=scripts.get(c.row);item.add("selectedLiterals",JSON.toJsonTree(script.literals.get(c.column)));for(KanaScriptResolver.Resolution reason:script.resolutions)if(reason.cell==c.column)item.add("candidateResolution",JSON.toJsonTree(reason));}
                                JsonArray refs=new JsonArray();for(PixelGlyphMatcher.Candidate candidate:c.sourceCandidates)refs.add(templateIds.get(candidate.getGlyph()));item.add("templateRefs",refs);cells.add(item);
                            }
                        }
                        row.add("cells",cells);row.addProperty("processingNanos",System.nanoTime()-begin);actual.write(row.toString());actual.newLine();
                    }
                };
                JsonObject transport=ReplayTimeline.replay(clip,Paths.get("data/meta/m1-v1"),false,consumer,ReplayTimeline.SYSTEM_CLOCK,output.resolve(clip.id+"-timeline.jsonl"));
                VideoTool.save(output.resolve(clip.id+"-transport.json"),transport);retained+=transport.get("frames").getAsLong();
            }
            List<JsonObject> actual=FrozenDataset.rows(actualPath),timeline=FrozenDataset.rows(output.resolve(clip.id+"-timeline.jsonl")),states=FrozenDataset.rows(clip.gtPath);JsonObject layout=VideoTool.readJson(clip.layoutPath);
            Totals totals=new Totals();
            try(BufferedWriter differences=Files.newBufferedWriter(output.resolve(clip.id+"-glyph-differences.jsonl"),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)) {
                for(int i=0;i<timeline.size();i++) {
                    JsonObject t=timeline.get(i),a=actual.get(i);FrozenDataset.require(t.get("sourcePts").equals(a.get("pts")),"Glyph output PTS mismatch");
                    if(!t.get("scored").getAsBoolean())continue;int n=t.get("sourceFrameIndex").getAsInt();JsonObject state=null;
                    for(JsonObject s:states)if(n>=s.get("startFrame").getAsInt()&&n<s.get("endFrameExclusive").getAsInt())state=s;
                    FrozenDataset.require(state!=null,"Missing frozen glyph comparison state");JsonObject result=compare(state,a,enhanced);result.addProperty("preContextExactCharacters",compare(state,a).get("exactCharacters").getAsInt());
                    if(layout.has("sourceGameRect")&&!layout.get("sourceGameRect").isJsonNull()) {
                        int[] offset={0,0};if(state.has("renderOffsetLogicalPixels")){offset[0]=state.getAsJsonArray("renderOffsetLogicalPixels").get(0).getAsInt();offset[1]=state.getAsJsonArray("renderOffsetLogicalPixels").get(1).getAsInt();}
                        int error="LOCATED".equals(a.get("geometryStatus").getAsString())?ViewportCheck.boundaryError(a,layout,offset):-1;result.addProperty("gridBoundaryError",error);
                    }
                    totals.add(result);String key=clip.clip.get("mediaId").getAsString()+":"+n;
                    JsonObject old=unique.put(key,result.deepCopy());if(old!=null)FrozenDataset.require(old.equals(result),"Overlapping raw glyph runs differ");
                    result.addProperty("sourceFrameIndex",n);result.addProperty("pts",t.get("sourcePts").getAsLong());result.addProperty("rgbSha256",t.get("rgbSha256").getAsString());result.addProperty("stateId",state.get("stateId").getAsString());
                    result.addProperty("originalFrame",clip.frameDirectory.resolve(String.format(Locale.ROOT,"frame-%06d.png",n)).toString().replace('\\','/'));differences.write(result.toString());differences.newLine();
                }
            }
            JsonObject report=totals.json();report.addProperty("clipId",clip.id);report.addProperty("actualSha256",VideoTool.sha(actualPath));report.addProperty("gtSha256",VideoTool.sha(clip.gtPath));reports.add(report);
            System.out.println("Raw glyph diagnostic: "+clip.id+" exact="+totals.exact+"/"+totals.eligible+" unsupported="+totals.unsupported);
        }
        Totals total=new Totals();for(JsonObject frame:unique.values())total.add(frame);JsonObject result=total.json();
        result.addProperty("status","raw_8x8_cell_diagnostic_not_contract_OCR_acceptance");result.addProperty("freezeSha256",VideoTool.sha(frozen.resolve("freeze.json")));result.addProperty("fontAuditSha256",VideoTool.sha(Paths.get("reports/m3-font-audit-v1/source-audit-v2/report.json")));result.addProperty("retainedFrameReferences",retained);result.addProperty("uniqueScoredFrames",unique.size());result.add("clips",reports);result.add("frozenScope",data.envelope.get("summary"));
        result.addProperty("readerParameters","maxHammingDistance=5,minLocalContrast=30,per-cell Otsu,normal RGB grid center sampling; same-frame marks only");
        result.addProperty("enhancedGeometryAndCurrentFrameScriptSelection",enhanced);result.addProperty("glyphDisplayBindingsSha256",VideoTool.sha(Paths.get("resources/glyph-display-bindings-v1.json")));
        result.addProperty("limitations","All product cells are read blind. Comparison uses GT cell positions only afterwards. Compact/packed fields unsupported, scene/region/role/read-order classification not implemented; non-target/background candidates are counted, not silently discarded. These diagnostic ratios do NOT satisfy spec OCR thresholds. No Chinese or source-message correction.");
        VideoTool.save(output.resolve("report.json"),result);
    }
    static JsonObject compare(JsonObject state,JsonObject actual) {
        return compare(state,actual,false);
    }
    static JsonObject compare(JsonObject state,JsonObject actual,boolean selected) {
        Map<String,JsonObject> cells=new HashMap<>();for(JsonElement c:actual.getAsJsonArray("cells")){JsonObject v=c.getAsJsonObject();cells.put(v.get("column").getAsString()+":"+v.get("row").getAsString(),v);}
        Set<String> expectedKeys=new HashSet<>();int all=0,eligible=0,exact=0,contains=0,unsupported=0,ambiguous=0;JsonArray mismatches=new JsonArray();
        boolean located="LOCATED".equals(actual.get("geometryStatus").getAsString());
        for(JsonElement e:state.getAsJsonArray("regions")) {
            JsonObject region=e.getAsJsonObject();int[] text=region.get("text").getAsString().codePoints().toArray();all+=text.length;JsonArray grid=region.getAsJsonArray("gridRect");
            double l=grid.get(0).getAsDouble(),top=grid.get(1).getAsDouble(),right=grid.get(2).getAsDouble(),bottom=grid.get(3).getAsDouble();
            if(l!=Math.rint(l)||top!=Math.rint(top)||bottom-top!=1||right-l!=text.length||region.has("renderUnits")){unsupported+=text.length;continue;}
            for(int j=0;j<text.length;j++) {
                int col=(int)l+j,row=(int)top;String key=col+":"+row,truth=new String(Character.toChars(text[j]));expectedKeys.add(key);eligible++;
                JsonObject cell=cells.get(key);List<String> literals=new ArrayList<>();
                if(located && cell==null)literals.add(" ");
                else if(cell!=null&&cell.get("valid").getAsBoolean())for(JsonElement x:cell.getAsJsonArray(selected&&cell.has("selectedLiterals")?"selectedLiterals":"literals"))literals.add(x.getAsString());
                if(literals.contains(truth))contains++;if(literals.size()>1)ambiguous++;
                if(literals.size()==1&&truth.equals(literals.get(0)))exact++;
                else {JsonObject diff=new JsonObject();diff.addProperty("column",col);diff.addProperty("row",row);diff.addProperty("expected",truth);diff.add("actualCandidates",new Gson().toJsonTree(literals));mismatches.add(diff);}
            }
        }
        int outside=0;for(Map.Entry<String,JsonObject> cell:cells.entrySet())if(!expectedKeys.contains(cell.getKey())&&cell.getValue().getAsJsonArray("literals").size()>0)outside++;
        JsonObject result=new JsonObject();result.addProperty("expectedCharacters",all);result.addProperty("eligibleCharacters",eligible);result.addProperty("unsupportedCharacters",unsupported);result.addProperty("exactCharacters",exact);result.addProperty("candidateContainsExpected",contains);result.addProperty("ambiguousCells",ambiguous);result.addProperty("unclassifiedCandidatesOutsideEligibleText",outside);result.add("mismatches",mismatches);return result;
    }
    private static final class Totals {
        long frames,expected,eligible,unsupported,exact,contains,ambiguous,outside;
        void add(JsonObject f){frames++;expected+=f.get("expectedCharacters").getAsLong();eligible+=f.get("eligibleCharacters").getAsLong();unsupported+=f.get("unsupportedCharacters").getAsLong();exact+=f.get("exactCharacters").getAsLong();contains+=f.get("candidateContainsExpected").getAsLong();ambiguous+=f.get("ambiguousCells").getAsLong();outside+=f.get("unclassifiedCandidatesOutsideEligibleText").getAsLong();}
        JsonObject json(){JsonObject r=new JsonObject();r.addProperty("frames",frames);r.addProperty("expectedCharacters",expected);r.addProperty("eligibleCharacters",eligible);r.addProperty("unsupportedCharacters",unsupported);r.addProperty("exactCharacters",exact);r.addProperty("candidateContainsExpected",contains);r.addProperty("ambiguousCells",ambiguous);r.addProperty("unclassifiedCandidatesOutsideEligibleText",outside);if(eligible>0)r.addProperty("diagnosticExactCellRate",exact/(double)eligible);return r;}
    }
}
