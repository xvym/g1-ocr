package io.github.g1ocr.verification;

import com.google.gson.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;

/** Development-only deterministic decoder. This module has no application dependency. */
public final class VideoTool {
    private static final Gson JSON = new GsonBuilder().disableHtmlEscaping().serializeNulls().create();
    private static final String FILTER = "scale=in_color_matrix=bt709:in_range=tv:out_range=full:flags=bilinear+accurate_rnd+bitexact,format=rgb24";
    private static final Path ROOT = Paths.get("").toAbsolutePath().normalize();
    private static String FFMPEG, FFPROBE;
    private VideoTool() { }

    public static void main(String[] args) {
        try {
            FFMPEG = tool("ffmpeg"); FFPROBE = tool("ffprobe");
            if (args.length == 3 && "index".equals(args[0])) index(Paths.get(args[1]), Paths.get(args[2]));
            else if (args.length == 7 && "export".equals(args[0]))
                export(Paths.get(args[1]), args[2], Integer.parseInt(args[3]), Integer.parseInt(args[4]), Paths.get(args[5]), Integer.parseInt(args[6]));
            else if (args.length == 5 && "sheet".equals(args[0])) sheet(Paths.get(args[1]), Paths.get(args[2]), Integer.parseInt(args[3]), Integer.parseInt(args[4]));
            else if (args.length == 7 && "inspect".equals(args[0])) FrameInspection.inspect(Paths.get(args[1]), Paths.get(args[2]), Integer.parseInt(args[3]), Integer.parseInt(args[4]), Integer.parseInt(args[5]), Integer.parseInt(args[6]));
            else if (args.length == 6 && "gt-check".equals(args[0])) GtIntegrity.check(Paths.get(args[1]), Paths.get(args[2]), Paths.get(args[3]), Paths.get(args[4]), Paths.get(args[5]));
            else if (args.length == 5 && "replay-audit".equals(args[0])) {
                if (!Arrays.asList("realtime", "unpaced").contains(args[4])) throw new IllegalArgumentException("Replay mode must be realtime or unpaced");
                ReplayTimeline.audit(Paths.get(args[1]), args[2], Paths.get(args[3]), "realtime".equals(args[4]));
            }
            else if (args.length == 5 && "compare-ocr".equals(args[0])) OcrComparison.compare(Paths.get(args[1]), args[2], Paths.get(args[3]), Paths.get(args[4]));
            else if (args.length == 4 && "font-audit".equals(args[0])) FontReferenceAudit.audit(Paths.get(args[1]), Paths.get(args[2]), Paths.get(args[3]));
            else if (args.length == 4 && "viewport-check".equals(args[0])) ViewportCheck.check(Paths.get(args[1]), args[2], Paths.get(args[3]));
            else if (args.length == 4 && "grid-check".equals(args[0])) ViewportCheck.check(Paths.get(args[1]), args[2], Paths.get(args[3]),true);
            else if (args.length == 4 && "glyph-audit".equals(args[0])) GlyphReadAudit.audit(Paths.get(args[1]), args[2], Paths.get(args[3]));
            else if (args.length == 4 && "glyph-context-audit".equals(args[0])) GlyphReadAudit.audit(Paths.get(args[1]), args[2], Paths.get(args[3]),true);
            else if (args.length == 4 && "ocr-replay".equals(args[0])) OcrReplay.replay(Paths.get(args[1]), args[2], Paths.get(args[3]));
            else throw new IllegalArgumentException("Usage: index <input.json> <new-ledger-dir> | export <ledger-dir> <media-id> <start> <end-exclusive> <new-dir> <step (1=all)> | sheet <frame-dir> <new.png> <columns> <thumb-width> | inspect <continuous-export-dir> <new-report-dir> <crop-x> <crop-y> <crop-width> <crop-height> | gt-check <ledger-dir> <clip.json> <gt.jsonl> <layout.json> <new-report.json>");
        } catch (Exception e) {
            e.printStackTrace(System.err); System.exit(e instanceof IllegalArgumentException ? 3 : 4);
        }
    }
    private static String tool(String key) {
        try { return readJson(ROOT.resolve("config/video-tools.json")).get(key).getAsString(); }
        catch (IOException e) { throw new IllegalStateException(e); }
    }
    static JsonObject readJson(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { return JsonParser.parseReader(reader).getAsJsonObject(); }
    }
    static String sha(Path path) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(path)) { byte[] b = new byte[1024 * 1024]; int n; while ((n = in.read(b)) != -1) md.update(b, 0, n); }
        return hex(md.digest());
    }
    static String hex(byte[] data) { StringBuilder out = new StringBuilder(); for (byte b : data) out.append(String.format(Locale.ROOT, "%02x", b & 255)); return out.toString(); }
    static void save(Path path, Object data) throws IOException { Files.write(path, (JSON.toJson(data) + "\n").getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW); }
    private static Path newDir(Path path) throws IOException {
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(ROOT) || Files.exists(normalized)) throw new IllegalArgumentException("Output must be a new directory inside this workspace: " + path);
        Files.createDirectories(normalized); return normalized;
    }
    private static void run(List<String> command, Path stdout, Path stderr, Path commandFile) throws Exception {
        save(commandFile, command);
        int code = new ProcessBuilder(command).redirectOutput(stdout.toFile()).redirectError(stderr.toFile()).start().waitFor();
        if (code != 0) throw new IOException("Decoder/probe failed with exit=" + code + "; see " + stderr);
    }
    private static Long number(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsLong() : null;
    }
    private static List<String> decode(Path source) {
        return new ArrayList<>(Arrays.asList(FFMPEG, "-nostdin", "-v", "warning", "-xerror", "-err_detect", "explode", "-copyts", "-noautorotate", "-hwaccel", "none", "-threads", "1", "-c:v", "h264", "-i", source.toString(), "-map", "0:v:0", "-an", "-sn", "-dn"));
    }

    private static void index(Path manifest, Path output) throws Exception {
        JsonObject input = readJson(manifest);
        JsonArray media = input.getAsJsonArray("media");
        if (!input.get("readOnly").getAsBoolean() || media.size() != 4) throw new IllegalArgumentException("Exactly four read-only original recordings required");
        Set<String> seen = new HashSet<>(), seenPaths = new HashSet<>();
        for (JsonElement entry : media) {
            JsonObject item = entry.getAsJsonObject(); String id = item.get("id").getAsString();
            Path source = ROOT.resolve(item.get("path").getAsString()).normalize();
            if (!id.matches("[a-z0-9-]+") || !seen.add(id) || !seenPaths.add(source.toString()) || !source.startsWith(ROOT.resolve("testpic/video")) || !Files.isRegularFile(source))
                throw new IllegalArgumentException("Invalid, duplicate or missing original media: " + item);
        }
        Path out = newDir(output);
        save(out.resolve("input-manifest.json"), input);
        run(Arrays.asList(FFMPEG, "-version"), out.resolve("ffmpeg-version.txt"), out.resolve("ffmpeg-version.stderr"), out.resolve("ffmpeg-version.command.json"));
        run(Arrays.asList(FFPROBE, "-version"), out.resolve("ffprobe-version.txt"), out.resolve("ffprobe-version.stderr"), out.resolve("ffprobe-version.command.json"));
        JsonObject decoder = new JsonObject(); decoder.addProperty("ffmpegSha256", sha(Paths.get(FFMPEG))); decoder.addProperty("ffprobeSha256", sha(Paths.get(FFPROBE)));
        decoder.addProperty("rgbFormat", "rgb24; row-major; top-to-bottom; R,G,B; untransformed orientation"); decoder.addProperty("filter", FILTER);
        save(out.resolve("decoder.json"), decoder);
        int total = 0, errors = 0;
        try (BufferedWriter mediaWriter = Files.newBufferedWriter(out.resolve("media.jsonl"), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
             BufferedWriter allWriter = Files.newBufferedWriter(out.resolve("frames.jsonl"), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) {
            for (JsonElement entry : media) {
                JsonObject item = entry.getAsJsonObject().deepCopy(); String id = item.get("id").getAsString(); Path source = ROOT.resolve(item.get("path").getAsString());
                Path dir = newDir(out.resolve(id)); String sourceHash = sha(source); item.addProperty("sha256", sourceHash); item.addProperty("bytes", Files.size(source));
                System.out.println("Indexing " + id + " sha256=" + sourceHash);
                run(Arrays.asList(FFPROBE, "-v", "warning", "-select_streams", "v:0", "-show_streams", "-show_format", "-of", "json", source.toString()), dir.resolve("probe.json"), dir.resolve("probe.stderr"), dir.resolve("probe.command.json"));
                JsonObject probe = readJson(dir.resolve("probe.json")); JsonObject stream = probe.getAsJsonArray("streams").get(0).getAsJsonObject();
                if (!"h264".equals(stream.get("codec_name").getAsString()) || !"bt709".equals(stream.get("color_space").getAsString()) || !"tv".equals(stream.get("color_range").getAsString()))
                    throw new IllegalArgumentException("This frozen decode profile requires H264 / BT709 / limited-range; preserve evidence and add a new profile for other sources");
                String tb = stream.get("time_base").getAsString();
                run(Arrays.asList(FFPROBE, "-v", "warning", "-err_detect", "explode", "-threads", "1", "-select_streams", "v:0", "-show_frames", "-show_entries", "frame=pts,duration,pkt_duration,width,height,decode_error_flags", "-of", "json", source.toString()), dir.resolve("probe-frames.json"), dir.resolve("probe-frames.stderr"), dir.resolve("probe-frames.command.json"));
                List<String> cmd = decode(source);
                cmd.addAll(Arrays.asList("-vf", FILTER, "-c:v", "rawvideo", "-threads:v", "1", "-fps_mode", "passthrough", "-enc_time_base", "demux", "-f", "framehash", "-hash", "sha256", "-"));
                run(cmd, dir.resolve("rgb.framehash"), dir.resolve("decode.stderr"), dir.resolve("decode.command.json"));
                JsonArray frames = readJson(dir.resolve("probe-frames.json")).getAsJsonArray("frames");
                int n = 0, bad = 0; Long previousEnd = null; String decodedTb = null;
                try (BufferedReader hashes = Files.newBufferedReader(dir.resolve("rgb.framehash"), StandardCharsets.UTF_8);
                     BufferedWriter writer = Files.newBufferedWriter(dir.resolve("frames.jsonl"), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) {
                    String line;
                    while ((line = hashes.readLine()) != null) {
                        if (line.startsWith("#tb 0:")) decodedTb = line.substring(line.indexOf(':') + 1).trim();
                        if (line.startsWith("#") || line.trim().isEmpty()) continue;
                        if (!tb.equals(decodedTb)) throw new IOException("RGB time base differs from source time base");
                        String[] hash = Ledger.hashFields(line);
                        if (n >= frames.size()) throw new IOException("More RGB frames than source metadata");
                        JsonObject f = frames.get(n).getAsJsonObject(); Long pts = number(f, "pts"), duration = number(f, "duration");
                        // No inference from FPS or neighbours, even if a duration is absent.
                        List<String> issues = Ledger.timeErrors(pts, duration, previousEnd);
                        if (pts == null || pts != Long.parseLong(hash[2])) issues.add("rgb_pts_mismatch");
                        if (duration == null || duration != Long.parseLong(hash[3])) issues.add("rgb_duration_mismatch");
                        int width = f.get("width").getAsInt(), height = f.get("height").getAsInt();
                        if ((long) width * height * 3 != Long.parseLong(hash[4])) issues.add("rgb_byte_size_mismatch");
                        if (width != stream.get("width").getAsInt() || height != stream.get("height").getAsInt()) issues.add("dimensions_changed");
                        if (number(f, "decode_error_flags") != null && number(f, "decode_error_flags") != 0) issues.add("decode_error_flags");
                        JsonObject row = new JsonObject(); row.addProperty("mediaId", id); row.addProperty("frameIndex", n); row.add("pts", pts == null ? JsonNull.INSTANCE : new JsonPrimitive(pts));
                        row.add("durationPts", duration == null ? JsonNull.INSTANCE : new JsonPrimitive(duration)); row.addProperty("timeBase", tb); row.addProperty("width", width); row.addProperty("height", height);
                        row.addProperty("rgbSha256", hash[5]); row.add("errors", JSON.toJsonTree(issues));
                        String serialized = JSON.toJson(row); writer.write(serialized); writer.newLine(); allWriter.write(serialized); allWriter.newLine();
                        if (!issues.isEmpty()) bad++;
                        previousEnd = issues.contains("timestamp_overflow") || pts == null || duration == null ? null : Math.addExact(pts, duration); n++;
                    }
                }
                if (n != frames.size() || (number(stream, "nb_frames") != null && n != number(stream, "nb_frames"))) throw new IOException("Frame count mismatch for " + id);
                if (!sourceHash.equals(sha(source))) throw new IOException("Original media changed during read");
                item.addProperty("timeBase", tb); item.addProperty("frameCount", n); item.addProperty("durationSeconds", probe.getAsJsonObject("format").get("duration").getAsString());
                item.addProperty("anomalousFrames", bad); item.addProperty("gameVersion", "unverified"); item.addProperty("session", "unverified"); item.add("decoder", decoder);
                item.addProperty("framesSha256", sha(dir.resolve("frames.jsonl"))); item.addProperty("probeSha256", sha(dir.resolve("probe.json")));
                mediaWriter.write(JSON.toJson(item)); mediaWriter.newLine(); mediaWriter.flush(); total += n; errors += bad;
                System.out.println(id + ": " + n + " original frames; anomalies=" + bad);
            }
        }
        JsonObject report = new JsonObject(); report.addProperty("schemaVersion", 1); report.addProperty("frameCount", total); report.addProperty("anomalousFrames", errors);
        report.addProperty("mediaSha256", sha(out.resolve("media.jsonl"))); report.addProperty("framesSha256", sha(out.resolve("frames.jsonl")));
        report.addProperty("status", errors == 0 ? "full_decode_and_integer_timeline_checked" : "evidence_incomplete");
        report.addProperty("scope", "Decode identity only. No GT, OCR, game coverage or independent acceptance conclusion."); save(out.resolve("report.json"), report);
        if (errors != 0) throw new IllegalArgumentException("Evidence incomplete: " + errors + " anomalous frames; see preserved ledger");
    }

    private static JsonObject media(Path ledger, String id) throws IOException {
        try (BufferedReader in = Files.newBufferedReader(ledger.resolve("media.jsonl"), StandardCharsets.UTF_8)) {
            String line; while ((line = in.readLine()) != null) { JsonObject m = JsonParser.parseString(line).getAsJsonObject(); if (id.equals(m.get("id").getAsString())) return m; }
        }
        throw new IllegalArgumentException("Unknown media ID");
    }
    private static void export(Path ledger, String id, int start, int end, Path output, int step) throws Exception {
        JsonObject media = media(ledger, id); Path source = ROOT.resolve(media.get("path").getAsString());
        if (start < 0 || end <= start || end > media.get("frameCount").getAsInt() || step <= 0) throw new IllegalArgumentException("Invalid source interval");
        if (!media.get("sha256").getAsString().equals(sha(source))) throw new IOException("Source identity mismatch");
        JsonObject decoder = readJson(ledger.resolve("decoder.json"));
        if (!decoder.get("ffmpegSha256").getAsString().equals(sha(Paths.get(FFMPEG))) || !FILTER.equals(decoder.get("filter").getAsString())) throw new IOException("Decoder profile changed");
        Path out = newDir(output); List<JsonObject> frames = new ArrayList<>();
        try (BufferedReader in = Files.newBufferedReader(ledger.resolve(id).resolve("frames.jsonl"), StandardCharsets.UTF_8)) {
            String line; while ((line = in.readLine()) != null) { JsonObject row = JsonParser.parseString(line).getAsJsonObject(); int n = row.get("frameIndex").getAsInt(); if (n >= start && n < end && (n - start) % step == 0) frames.add(row); }
        }
        int width = frames.get(0).get("width").getAsInt(), height = frames.get(0).get("height").getAsInt();
        List<String> cmd = decode(source);
        cmd.addAll(Arrays.asList("-vf", FILTER + ",select=gte(n\\," + start + ")*lt(n\\," + end + ")*not(mod(n-" + start + "\\," + step + "))", "-c:v", "rawvideo", "-threads:v", "1", "-fps_mode", "passthrough", "-enc_time_base", "demux", "-frames:v", Integer.toString(frames.size()), "-f", "rawvideo", "-"));
        save(out.resolve("decode.command.json"), cmd);
        Process process = new ProcessBuilder(cmd).redirectError(out.resolve("decode.stderr").toFile()).start();
        boolean completed = false;
        try (InputStream stream = new BufferedInputStream(process.getInputStream(), 1024 * 1024)) {
            byte[] rgb = new byte[Math.multiplyExact(Math.multiplyExact(width, height), 3)];
            for (JsonObject frame : frames) {
                int offset = 0; while (offset < rgb.length) { int n = stream.read(rgb, offset, rgb.length - offset); if (n < 0) throw new EOFException("Missing original frame " + frame.get("frameIndex")); offset += n; }
                String expected = frame.get("rgbSha256").getAsString();
                if (!expected.equals(hex(MessageDigest.getInstance("SHA-256").digest(rgb)))) throw new IOException("Re-decode RGB mismatch at frame=" + frame.get("frameIndex"));
                BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                int[] pixels = new int[width * height]; for (int p = 0, k = 0; p < pixels.length; p++, k += 3) pixels[p] = ((rgb[k] & 255) << 16) | ((rgb[k + 1] & 255) << 8) | (rgb[k + 2] & 255);
                image.setRGB(0, 0, width, height, pixels, 0, width);
                Path png = out.resolve(String.format(Locale.ROOT, "frame-%06d.png", frame.get("frameIndex").getAsInt()));
                if (!ImageIO.write(image, "png", png.toFile())) throw new IOException("No lossless PNG writer");
                BufferedImage readback = ImageIO.read(png.toFile());
                if (readback.getWidth() != width || readback.getHeight() != height || !expected.equals(rgbHash(readback))) throw new IOException("PNG RGB readback mismatch");
            }
            if (stream.read() != -1) throw new IOException("Extra decoder output");
            if (process.waitFor() != 0) throw new IOException("Export decoder failed; see decode.stderr");
            completed = true;
        } finally { if (!completed) process.destroyForcibly(); }
        if (!media.get("sha256").getAsString().equals(sha(source))) throw new IOException("Source changed during export");
        JsonObject report = new JsonObject(); report.addProperty("mediaId", id); report.addProperty("mediaSha256", media.get("sha256").getAsString());
        report.addProperty("startFrame", start); report.addProperty("endFrameExclusive", end); report.addProperty("step", step); report.addProperty("exportedFrames", frames.size());
        report.addProperty("kind", step == 1 ? "continuous_original_frames" : "navigation_only_not_scoring_evidence");
        report.addProperty("allRgbAndPngReadbackHashesMatch", true); report.addProperty("sourceFrameLedgerSha256", sha(ledger.resolve(id).resolve("frames.jsonl"))); report.add("frames", JSON.toJsonTree(frames));
        save(out.resolve("verification.json"), report); System.out.println("Exported and verified " + frames.size() + " source frames to " + out);
    }
    static String rgbHash(BufferedImage image) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) { int p = image.getRGB(x, y); md.update((byte) (p >> 16)); md.update((byte) (p >> 8)); md.update((byte) p); }
        return hex(md.digest());
    }
    private static void sheet(Path directory, Path output, int columns, int thumbWidth) throws Exception {
        if (columns <= 0 || thumbWidth < 100 || Files.exists(output) || !output.toAbsolutePath().normalize().startsWith(ROOT)) throw new IllegalArgumentException("Invalid sheet output/size");
        List<Path> paths = new ArrayList<>(); try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory, "frame-*.png")) { for (Path p : entries) paths.add(p); }
        Collections.sort(paths); if (paths.isEmpty()) throw new IllegalArgumentException("Empty frames directory");
        BufferedImage first = ImageIO.read(paths.get(0).toFile()); int h = first.getHeight() * thumbWidth / first.getWidth();
        BufferedImage result = new BufferedImage(columns * thumbWidth, ((paths.size() + columns - 1) / columns) * (h + 24), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = result.createGraphics(); graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, result.getWidth(), result.getHeight()); graphics.setFont(new Font("Monospaced", Font.PLAIN, 14));
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        for (int i = 0; i < paths.size(); i++) { int x = i % columns * thumbWidth, y = i / columns * (h + 24); graphics.drawImage(ImageIO.read(paths.get(i).toFile()), x, y, thumbWidth, h, null); graphics.setColor(Color.BLACK); graphics.drawString(paths.get(i).getFileName().toString(), x + 4, y + h + 18); }
        graphics.dispose(); ImageIO.write(result, "png", output.toFile());
    }
}
