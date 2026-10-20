package io.github.g1ocr;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.g1ocr.image.GbFrame;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;

/** A single image and an explicit upper-screen mapping; never opens annotations. */
public final class Main {
    public static void main(String[] args) throws Exception {
        if (args.length != 5) {
            System.err.println("Usage: java -jar g1-ocr.jar <image.png> <upper-ox> <upper-oy> <scale> <output-dir>");
            System.exit(2);
        }
        Path input = Path.of(args[0]), output = Path.of(args[4]);
        var mapping = new GbFrame.Mapping(Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]));
        var source = ImageIO.read(input.toFile());
        if (source == null) throw new IllegalArgumentException("Unsupported image: " + input);
        GbFrame frame = GbFrame.normalize(source, mapping);
        var results = new OcrPipeline().recognize(frame);
        Files.createDirectories(output);
        ImageIO.write(frame.image(), "png", output.resolve("gb.png").toFile());
        int k = mapping.scale();
        ImageIO.write(source.getSubimage(mapping.ox() + 120 * k, mapping.oy() + 48 * k, 160 * k, 144 * k),
                "png", output.resolve("crop.png").toFile());
        var report = new LinkedHashMap<String, Object>();
        report.put("source", input.toString());
        report.put("mapping", mapping);
        report.put("threshold", frame.threshold());
        report.put("regions", results);
        report.put("resolvedJapanese", null);
        report.put("chinese", null);
        JsonMapper.builder().build().writerWithDefaultPrettyPrinter().writeValue(output.resolve("ocr.json").toFile(), report);
        System.out.println("OCR saved to " + output.resolve("ocr.json"));
    }
}
