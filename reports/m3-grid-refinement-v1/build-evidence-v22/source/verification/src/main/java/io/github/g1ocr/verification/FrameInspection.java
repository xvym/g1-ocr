package io.github.g1ocr.verification;

import com.google.gson.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Locale;
import javax.imageio.ImageIO;

/** Every original frame is shown; pixel deltas navigate changes, never assign text/state. */
final class FrameInspection {
    static void inspect(Path input, Path output, int x, int y, int width, int height) throws Exception {
        JsonObject verification = VideoTool.readJson(input.resolve("verification.json"));
        if (verification.get("step").getAsInt() != 1 || !verification.get("allRgbAndPngReadbackHashesMatch").getAsBoolean()) throw new IllegalArgumentException("Inspection requires a verified continuous export");
        if (Files.exists(output) || !output.toAbsolutePath().normalize().startsWith(Paths.get("").toAbsolutePath().normalize())) throw new IllegalArgumentException("Use a new report directory inside the workspace");
        Files.createDirectories(output);
        JsonArray frames = verification.getAsJsonArray("frames"); Gson json = new GsonBuilder().disableHtmlEscaping().create();
        BufferedImage previous = null, montage = null; Graphics2D graphics = null;
        int columns = 4, rows = 5, cellHeight = height + 24, perPage = columns * rows;
        try (BufferedWriter writer = Files.newBufferedWriter(output.resolve("pixel-scan.jsonl"), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) {
            for (int i = 0; i < frames.size(); i++) {
                JsonObject frame = frames.get(i).getAsJsonObject(); int n = frame.get("frameIndex").getAsInt();
                BufferedImage full = ImageIO.read(input.resolve(String.format(Locale.ROOT, "frame-%06d.png", n)).toFile());
                if (full == null || !frame.get("rgbSha256").getAsString().equals(VideoTool.rgbHash(full))) throw new IOException("Original pixel identity mismatch at " + n);
                if (x < 0 || y < 0 || width <= 0 || height <= 0 || x + width > full.getWidth() || y + height > full.getHeight()) throw new IllegalArgumentException("Invalid crop rectangle");
                BufferedImage crop = full.getSubimage(x, y, width, height); int exact = 0, high = 0;
                if (previous != null) for (int cy = 0; cy < height; cy++) for (int cx = 0; cx < width; cx++) {
                    int p = crop.getRGB(cx, cy), q = previous.getRGB(cx, cy);
                    if (p != q) exact++;
                    if (Math.max(Math.max(Math.abs(((p >> 16) & 255) - ((q >> 16) & 255)), Math.abs(((p >> 8) & 255) - ((q >> 8) & 255))), Math.abs((p & 255) - (q & 255))) > 16) high++;
                }
                JsonObject scan = frame.deepCopy(); scan.addProperty("exactChangedPixelsFromPrevious", exact); scan.addProperty("changedPixelsOver16FromPrevious", high);
                scan.addProperty("cropRgbSha256", VideoTool.rgbHash(crop)); scan.addProperty("stateClassification", "unassigned; changes are navigation only"); writer.write(json.toJson(scan)); writer.newLine();
                if (i % perPage == 0) {
                    montage = new BufferedImage(columns * width, rows * cellHeight, BufferedImage.TYPE_INT_RGB); graphics = montage.createGraphics(); graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, montage.getWidth(), montage.getHeight()); graphics.setFont(new Font("Monospaced", Font.PLAIN, 14));
                }
                int cell = i % perPage, tx = cell % columns * width, ty = cell / columns * cellHeight;
                graphics.drawImage(crop, tx, ty, null); graphics.setColor(Color.BLACK); graphics.drawString("frame=" + n + " pts=" + frame.get("pts") + " delta>16=" + high, tx + 2, ty + height + 18);
                if (cell == perPage - 1 || i == frames.size() - 1) { graphics.dispose(); graphics = null; ImageIO.write(montage, "png", output.resolve(String.format(Locale.ROOT, "all-frames-%03d.png", i / perPage)).toFile()); }
                previous = crop;
            }
        } finally { if (graphics != null) graphics.dispose(); }
        JsonObject report = new JsonObject(); report.addProperty("frameCount", frames.size()); report.addProperty("cropX", x); report.addProperty("cropY", y); report.addProperty("cropWidth", width); report.addProperty("cropHeight", height);
        report.addProperty("inputVerificationSha256", VideoTool.sha(input.resolve("verification.json"))); report.addProperty("pixelScanSha256", VideoTool.sha(output.resolve("pixel-scan.jsonl")));
        report.addProperty("scope", "Every exported original frame/hash checked and displayed in order. Delta thresholds are navigation only; no GT or OCR result."); VideoTool.save(output.resolve("report.json"), report);
        System.out.println("Inspected and displayed every original frame: " + frames.size());
    }
}
