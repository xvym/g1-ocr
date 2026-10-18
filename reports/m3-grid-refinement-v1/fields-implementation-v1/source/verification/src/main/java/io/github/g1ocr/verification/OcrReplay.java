package io.github.g1ocr.verification;

import com.google.gson.*;
import io.github.g1ocr.capture.SourceFrame;
import io.github.g1ocr.ocr.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Runs the real image-only consumer, then hands its closed outputs to the frozen scorer. */
final class OcrReplay {
    static void replay(Path frozen,String selection,Path output)throws Exception {
        FrozenDataset data=new FrozenDataset(frozen);List<GlyphTemplate> bank=GlyphReadAudit.auditedBank();
        FrozenDataset.require(!Files.exists(output),"Use a new OCR replay directory");Files.createDirectories(output);Path actual=output.resolve("actual");Files.createDirectories(actual);JsonArray runs=new JsonArray();
        for(FrozenDataset.Clip clip:data.selected(selection)) {
            try(final BufferedWriter writer=Files.newBufferedWriter(actual.resolve(clip.id+".jsonl"),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)) {
                final int[] association={clip.clip.get("startFrame").getAsInt()};
                ReplayTimeline.Consumer consumer=new ReplayTimeline.Consumer() {
                    JapaneseOcrEngine engine;
                    public void reset(){engine=new JapaneseOcrEngine(bank);}
                    public void accept(SourceFrame frame)throws Exception {
                        // Source index is attached only after the consumer returned, outside product code.
                        JapaneseOcrEngine.Result result=engine.process(frame);
                        JsonObject row=new GsonBuilder().disableHtmlEscaping().create().toJsonTree(result).getAsJsonObject();row.addProperty("sourceFrameIndex",association[0]++);
                        writer.write(row.toString());writer.newLine();
                    }
                };
                JsonObject r=ReplayTimeline.replay(clip,Paths.get("data/meta/m1-v1"),false,consumer,ReplayTimeline.SYSTEM_CLOCK,output.resolve(clip.id+"-timeline.jsonl"));runs.add(r);VideoTool.save(output.resolve(clip.id+"-transport.json"),r);
            }
            System.out.println("Real OCR replay: "+clip.id);
        }
        OcrComparison.compare(frozen,selection,actual,output.resolve("comparison"));JsonObject report=new JsonObject();
        report.addProperty("status","real_image_OCR_development_baseline_not_accepted");report.addProperty("freezeSha256",VideoTool.sha(frozen.resolve("freeze.json")));report.addProperty("fontAuditSha256",VideoTool.sha(Paths.get("reports/m3-font-audit-v1/source-audit-v2/report.json")));report.add("runs",runs);
        report.addProperty("classificationScope","Initial standard bottom message box only. Other regions remain missing in the frozen denominator. Image/state/role/position failures remain counted; no GT copied into actual outputs.");
        report.addProperty("comparisonSha256",VideoTool.sha(output.resolve("comparison/report.json")));VideoTool.save(output.resolve("report.json"),report);
    }
}
