package io.github.g1ocr.verification;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.Normalizer;
import java.util.*;

/** Candidate integrity only. Approval and independent image reading are external evidence. */
final class GtIntegrity {
    static void check(Path ledger, Path clipPath, Path gtPath, Path layoutPath, Path reportPath) throws Exception {
        JsonObject clip = VideoTool.readJson(clipPath), layout = VideoTool.readJson(layoutPath);
        if (Files.exists(reportPath) || !reportPath.toAbsolutePath().normalize().startsWith(Paths.get("").toAbsolutePath().normalize())) throw new IllegalArgumentException("Use a new report file inside the workspace");
        String id = clip.get("mediaId").getAsString(), hash = clip.get("mediaSha256").getAsString();
        JsonObject media = null;
        for (JsonObject row : rows(ledger.resolve("media.jsonl"))) if (id.equals(row.get("id").getAsString())) media = row;
        require(media != null && hash.equals(media.get("sha256").getAsString()), "Clip/source identity mismatch");
        require(hash.equals(VideoTool.sha(Paths.get(media.get("path").getAsString()))), "Original media changed");
        List<JsonObject> frames = rows(ledger.resolve(id).resolve("frames.jsonl"));
        require(VideoTool.sha(ledger.resolve(id).resolve("frames.jsonl")).equals(media.get("framesSha256").getAsString()), "Frame ledger changed");
        int scoreStart = clip.getAsJsonArray("scoredRange").get(0).getAsInt(), scoreEnd = clip.getAsJsonArray("scoredRange").get(1).getAsInt();
        int clipStart = clip.get("startFrame").getAsInt(), clipEnd = clip.get("endFrameExclusive").getAsInt();
        require(0 <= clipStart && clipStart <= scoreStart && scoreStart < scoreEnd && scoreEnd <= clipEnd && clipEnd <= frames.size(), "Invalid nonempty clip/scoring range");
        require(clip.getAsJsonArray("warmup").get(0).getAsInt() == clipStart && clip.getAsJsonArray("warmup").get(1).getAsInt() == scoreStart, "Warmup boundary mismatch");
        require(clip.getAsJsonArray("postContext").get(0).getAsInt() == scoreEnd && clip.getAsJsonArray("postContext").get(1).getAsInt() == clipEnd, "Post-context boundary mismatch");
        boolean nonTarget = GtGeometry.nonTarget(clip);
        require(!clip.has("pageOpportunityPolicy") || Arrays.asList("explicit_id","legacy_state_interval").contains(clip.get("pageOpportunityPolicy").getAsString()), "Unknown page opportunity policy");
        boolean explicitPages = clip.has("pageOpportunityPolicy") && "explicit_id".equals(clip.get("pageOpportunityPolicy").getAsString());
        PageOpportunities pages = new PageOpportunities(explicitPages);
        Set<String> ids = new HashSet<>(); int next = scoreStart, opportunities = 0, weightedCharacters = 0, weightedDetachedMarks = 0; JsonArray states = new JsonArray();
        List<JsonObject> gt = rows(gtPath); require(!gt.isEmpty(), "Empty GT rejected");
        for (JsonObject state : gt) {
            int start = state.get("startFrame").getAsInt(), end = state.get("endFrameExclusive").getAsInt();
            require(start == next && end > start && end <= scoreEnd, "GT gap/overlap/invalid interval"); next = end;
            require(ids.add(state.get("stateId").getAsString()), "Duplicate state ID");
            require(id.equals(state.get("mediaId").getAsString()) && hash.equals(state.get("mediaSha256").getAsString()), "GT source mismatch");
            require("agent_double_read_complete_candidate".equals(state.get("verificationStatus").getAsString()), "Unknown, conflict or unreviewed state rejected");
            require(state.get("timeBase").equals(media.get("timeBase")), "GT time base mismatch");
            require(state.get("startPts").getAsLong() == frames.get(start).get("pts").getAsLong(), "GT start PTS mismatch");
            JsonObject last = frames.get(end - 1); require(state.get("endPtsExclusive").getAsLong() == Math.addExact(last.get("pts").getAsLong(), last.get("durationPts").getAsLong()), "GT end PTS mismatch");
            JsonObject stateLayout=layout;
            if(state.has("renderOffsetLogicalPixels")) {
                stateLayout=layout.deepCopy();stateLayout.add("renderOffsetLogicalPixels",state.get("renderOffsetLogicalPixels"));
            }
            int characters = 0, ordinal = 0;
            for (JsonElement regionElement : state.getAsJsonArray("regions")) {
                JsonObject region = regionElement.getAsJsonObject(); String text = region.get("text").getAsString();
                require(region.get("readingOrder").getAsInt() == ordinal++, "Region reading order mismatch");
                characters += GtGeometry.region(region, stateLayout);
            }
            int detached = GtGeometry.symbols(state, stateLayout);
            if (nonTarget) GtGeometry.nonTargetState(state, characters, detached);
            weightedDetachedMarks = Math.addExact(weightedDetachedMarks, Math.multiplyExact(detached, end - start));
            for (int n = start; n < end; n++) require(frames.get(n).getAsJsonArray("errors").size() == 0 && frames.get(n).get("frameIndex").getAsInt() == n, "Scored source frame has errors/missing index");
            if (state.get("completePage").getAsBoolean()) { require(characters > 0, "Empty complete page"); pages.add(state); opportunities=pages.size(); }
            weightedCharacters = Math.addExact(weightedCharacters, Math.multiplyExact(characters, end - start)); states.add(state.get("stateId"));
        }
        require(next == scoreEnd && (opportunities > 0 || nonTarget), "Incomplete coverage or no complete page opportunity");
        JsonObject report = new JsonObject(); report.addProperty("status", "candidate_structural_integrity_checked_not_frozen"); report.addProperty("sourceFrames", scoreEnd - scoreStart); report.addProperty("states", gt.size()); report.addProperty("completePageOpportunities", opportunities);
        report.addProperty("completeStateIntervals", pages.intervals()); report.addProperty("pageOpportunityPolicy", explicitPages ? "explicit_id" : "legacy_state_interval");
        report.addProperty("frameWeightedExpectedCharacters", weightedCharacters); report.addProperty("frameWeightedDetachedMarks", weightedDetachedMarks); report.addProperty("scoreKind", nonTarget ? "non_target_only" : "text_and_state"); report.addProperty("gtSha256", VideoTool.sha(gtPath)); report.addProperty("clipSha256", VideoTool.sha(clipPath)); report.addProperty("layoutSha256", VideoTool.sha(layoutPath));
        report.addProperty("sourceMediaSha256", hash); report.addProperty("sourceFrameLedgerSha256", VideoTool.sha(ledger.resolve(id).resolve("frames.jsonl"))); report.add("stateIds", states);
        report.addProperty("limitations", "Structural checks do not establish image semantics, human approval, session independence, OCR performance or UI latency."); VideoTool.save(reportPath, report);
        System.out.println("Candidate integrity: " + (scoreEnd - scoreStart) + " frames; " + gt.size() + " states; " + opportunities + " complete page opportunity. Not frozen.");
    }
    private static List<JsonObject> rows(Path file) throws IOException {
        List<JsonObject> rows = new ArrayList<>(); try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { String line; while ((line = reader.readLine()) != null) { require(!line.trim().isEmpty(), "Blank JSONL record"); rows.add(JsonParser.parseString(line).getAsJsonObject()); } } return rows;
    }
    private static void require(boolean condition, String error) { if (!condition) throw new IllegalArgumentException(error); }
}
