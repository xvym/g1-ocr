package io.github.g1ocr.verification;

import com.google.gson.*;
import io.github.g1ocr.capture.SourceFrame;
import java.awt.image.BufferedImage;
import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.locks.LockSupport;
import javax.imageio.ImageIO;

/** Source association and answers stay in this driver; consumers receive SourceFrame only. */
final class ReplayTimeline {
    interface Consumer {
        void reset();
        void accept(SourceFrame frame) throws Exception;
    }
    interface Clock {
        long now();
        void until(long deadline) throws InterruptedException;
    }
    static final Clock SYSTEM_CLOCK=new Clock() {
        public long now(){return System.nanoTime();}
        public void until(long deadline) throws InterruptedException {
            long remaining; while((remaining=deadline-System.nanoTime())>0) {
                if(Thread.interrupted())throw new InterruptedException();
                LockSupport.parkNanos(Math.min(remaining,20000000L));
            }
        }
    };
    static long deltaNanos(long pts,long origin,long numerator,long denominator) {
        FrozenDataset.require(numerator>0 && denominator>0 && pts>=origin,"Invalid source time");
        return BigInteger.valueOf(pts).subtract(BigInteger.valueOf(origin)).multiply(BigInteger.valueOf(numerator)).multiply(BigInteger.valueOf(1000000000L)).divide(BigInteger.valueOf(denominator)).longValueExact();
    }
    static JsonObject replay(FrozenDataset.Clip item,Path ledger,boolean realtime,Consumer consumer,Clock clock,Path timelinePath) throws Exception {
        JsonObject clip=item.clip;
        String mediaId=clip.get("mediaId").getAsString(); JsonObject media=null;
        for(JsonObject m:FrozenDataset.rows(ledger.resolve("media.jsonl"))) if(mediaId.equals(m.get("id").getAsString()))media=m;
        FrozenDataset.require(media!=null && VideoTool.sha(Paths.get(media.get("path").getAsString())).equals(clip.get("mediaSha256").getAsString()),"Replay original identity mismatch");
        Path frameLedger=ledger.resolve(mediaId).resolve("frames.jsonl");
        FrozenDataset.require(VideoTool.sha(frameLedger).equals(media.get("framesSha256").getAsString()),"Replay frame ledger changed");
        List<JsonObject> frames=FrozenDataset.rows(frameLedger);
        int start=clip.get("startFrame").getAsInt(),end=clip.get("endFrameExclusive").getAsInt();
        int scoreStart=clip.getAsJsonArray("scoredRange").get(0).getAsInt(),scoreEnd=clip.getAsJsonArray("scoredRange").get(1).getAsInt();
        String[] tb=media.get("timeBase").getAsString().split("/"); long num=Long.parseLong(tb[0]),den=Long.parseLong(tb[1]);
        long firstPts=frames.get(start).get("pts").getAsLong(),origin=0,previousEnd=firstPts,maxLateness=0;
        consumer.reset();
        try(BufferedWriter out=Files.newBufferedWriter(timelinePath,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)) {
            for(int n=start;n<end;n++) {
                JsonObject f=frames.get(n); long pts=f.get("pts").getAsLong(),duration=f.get("durationPts").getAsLong();
                FrozenDataset.require(f.get("frameIndex").getAsInt()==n && f.getAsJsonArray("errors").size()==0 && pts==previousEnd && duration>0,"Replay gap/invalid source time");
                previousEnd=Math.addExact(pts,duration);
                BufferedImage image=ImageIO.read(item.frameDirectory.resolve(String.format(Locale.ROOT,"frame-%06d.png",n)).toFile());
                FrozenDataset.require(image!=null && image.getWidth()==f.get("width").getAsInt() && image.getHeight()==f.get("height").getAsInt(),"Replay PNG dimensions mismatch at "+n);
                byte[] rgb=rgb(image);
                FrozenDataset.require(VideoTool.hex(MessageDigest.getInstance("SHA-256").digest(rgb)).equals(f.get("rgbSha256").getAsString()),"Replay PNG/RGB mismatch at "+n);
                if(n==start)origin=clock.now(); // decoder preparation is outside the source timeline
                long delta=deltaNanos(pts,firstPts,num,den); if(realtime)clock.until(Math.addExact(origin,delta));
                long delivered=clock.now()-origin; if(realtime)maxLateness=Math.max(maxLateness,delivered-delta);
                // No frameIndex, clip ID, set label, geometry or GT crosses this boundary.
                consumer.accept(new SourceFrame(rgb,image.getWidth(),image.getHeight(),pts,num,den));
                JsonObject row=new JsonObject();row.addProperty("sourceFrameIndex",n);row.addProperty("sourcePts",pts);row.addProperty("timeBase",media.get("timeBase").getAsString());row.addProperty("durationPts",duration);
                row.addProperty("rgbSha256",f.get("rgbSha256").getAsString());row.addProperty("sourceOffsetNanos",delta);row.addProperty("deliveryOffsetNanos",delivered);row.addProperty("scored",n>=scoreStart && n<scoreEnd);
                out.write(row.toString());out.newLine();
            }
            // Keep the final frame's actual duration, including sub-200ms opportunities.
            if(realtime)clock.until(Math.addExact(origin,deltaNanos(previousEnd,firstPts,num,den)));
        }
        JsonObject report=new JsonObject();report.addProperty("status","source_frame_transport_checked_not_ocr_acceptance");report.addProperty("clipId",item.id);report.addProperty("frames",end-start);report.addProperty("scoredFrames",scoreEnd-scoreStart);report.addProperty("realtime",realtime);report.addProperty("sourceDurationNanos",deltaNanos(previousEnd,firstPts,num,den));report.addProperty("wallDurationNanos",clock.now()-origin);report.addProperty("maxDeliveryLatenessNanos",maxLateness);report.addProperty("uiLatency","pending_independent_ui_observation_required");report.addProperty("sourceMediaSha256",media.get("sha256").getAsString());report.addProperty("frameLedgerSha256",media.get("framesSha256").getAsString());return report;
    }
    static byte[] rgb(BufferedImage image) {
        int width=image.getWidth(),height=image.getHeight();int[] pixels=image.getRGB(0,0,width,height,null,0,width);byte[] rgb=new byte[Math.multiplyExact(pixels.length,3)];
        for(int n=0,k=0;n<pixels.length;n++){int pixel=pixels[n];rgb[k++]=(byte)(pixel>>16);rgb[k++]=(byte)(pixel>>8);rgb[k++]=(byte)pixel;}
        return rgb;
    }
    static void audit(Path frozen,String selection,Path output,boolean realtime) throws Exception {
        FrozenDataset data=new FrozenDataset(frozen);FrozenDataset.require(!Files.exists(output),"Use a new replay directory");Files.createDirectories(output);
        JsonArray reports=new JsonArray();
        for(FrozenDataset.Clip clip:data.selected(selection)) {
            final int[] received={0};
            Consumer sink=new Consumer() {public void reset(){received[0]=0;}public void accept(SourceFrame frame){received[0]++;}};
            JsonObject r=replay(clip,Paths.get("data/meta/m1-v1"),realtime,sink,SYSTEM_CLOCK,output.resolve(clip.id+"-timeline.jsonl"));
            FrozenDataset.require(received[0]==r.get("frames").getAsInt(),"Consumer delivery count mismatch");
            VideoTool.save(output.resolve(clip.id+"-report.json"),r);reports.add(r);System.out.println("Replay transport: "+clip.id+" frames="+received[0]);
        }
        JsonObject result=new JsonObject();result.addProperty("status","transport_audit_only_no_ocr_executed");result.addProperty("freezeSha256",VideoTool.sha(frozen.resolve("freeze.json")));result.add("clips",reports);result.add("scope",data.envelope.get("summary"));result.addProperty("consumerInput","RGB24,width,height,integerPTS,timeBaseNumerator,timeBaseDenominator only");VideoTool.save(output.resolve("report.json"),result);
    }
}
