package io.github.g1ocr;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.g1ocr.image.GbFrame;
import io.github.g1ocr.ocr.PixelFont;
import io.github.g1ocr.ocr.PixelOcr;
import io.github.g1ocr.text.TextCorpus;
import io.github.g1ocr.text.TextResolver;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShopTest {
    private static final GbFrame.Mapping SCREEN = new GbFrame.Mapping(0, 0, 3);

    @Test void userAttachmentMatchesIndependentAnswers() throws Exception {
        var json = JsonMapper.builder().build();
        Path labels = Path.of("testpic/labels/shop-buy.json");
        var answer = json.readTree(labels.toFile());
        Path source = labels.getParent().resolve(answer.path("file").asText());
        var image = ImageIO.read(source.toFile());
        assertEquals(answer.path("imageWidth").asInt(), image.getWidth());
        assertEquals(answer.path("imageHeight").asInt(), image.getHeight());
        // The attachment contains the GB crop with a small outer margin, not a complete upper screen.
        // Place its unchanged pixels at the normal GB origin; production geometry is not relaxed.
        var upper = new BufferedImage(1200, 720, BufferedImage.TYPE_INT_RGB);
        var graphics = upper.createGraphics();
        graphics.drawImage(image, 360 - answer.path("gbCrop").path("x").asInt(),
                144 - answer.path("gbCrop").path("y").asInt(), null);
        graphics.dispose();
        var frame = GbFrame.normalize(upper, SCREEN);
        var actual = new OcrPipeline().recognize(frame);
        var resolver = new TextResolver(new TextCorpus());
        var results = new ArrayList<Object>();
        assertEquals(answer.path("regions").size(), actual.size());
        for (int i = 0; i < actual.size(); i++) {
            var expected = answer.path("regions").get(i);
            var region = actual.get(i);
            List<String> japanese = new ArrayList<>(), chinese = new ArrayList<>();
            expected.path("raw").forEach(n -> japanese.add(n.asText()));
            expected.path("chinese").forEach(n -> chinese.add(n.asText()));
            assertEquals(expected.path("layout").asText(), region.layout());
            assertEquals(japanese, region.rawOcr());
            if (expected.has("selectedItem")) assertEquals(expected.path("selectedItem").asInt(), region.selectedItem());
            assertTrue(region.diagnostics().stream().flatMap(l -> l.characters().stream()).noneMatch(PixelOcr.CharacterResult::unknown));
            var translated = resolver.resolve(region);
            assertEquals(chinese, translated.stream().map(TextResolver.Unit::chinese).toList());
            assertTrue(translated.stream().allMatch(u -> u.displayStatus().equals("ready")));
            results.add(java.util.Map.of("expected", expected, "ocr", region, "translation", translated));
        }
        var report = new LinkedHashMap<String, Object>();
        report.put("implementationVersion", System.getProperty("g1ocr.version"));
        report.put("scope", "user-provided GB crop; preserved pixels placed into an upper-screen canvas; not real capture acceptance");
        report.put("annotationMethod", answer.path("method").asText());
        report.put("source", source.toString().replace('\\', '/'));
        report.put("sourceSha256", java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source))));
        report.put("gbCrop", answer.path("gbCrop"));
        report.put("passed", true); report.put("results", results);
        report.put("realCaptureTested", false); report.put("finalChineseAcceptance", false);
        Files.createDirectories(Path.of("output/verify-shop-items/user"));
        ImageIO.write(upper, "png", Path.of("output/verify-shop-items/user/upper.png").toFile());
        ImageIO.write(frame.image(), "png", Path.of("output/verify-shop-items/user/gb.png").toFile());
        json.writerWithDefaultPrettyPrinter().writeValue(Path.of("reports/shop-user-image.json").toFile(), report);
    }

    private static void paint(BufferedImage image, int x, int y, long pixels) {
        for (int dy = 0; dy < 8; dy++) for (int dx = 0; dx < 8; dx++)
            for (int ky = 0; ky < 3; ky++) for (int kx = 0; kx < 3; kx++)
                image.setRGB(360 + (x + dx) * 3 + kx, 144 + (y + dy) * 3 + ky,
                        (pixels & 1L << (dy * 8 + dx)) == 0 ? 0x94947b : 0x181810);
    }

    @Test void pricesInvalidateWhileCursorAndArrowDoNotAndQuantityBoxOccludesLastRow() throws Exception {
        var image = ImageIO.read(Path.of("testpic/Screenshot 2026-10-20 22-44-11.png").toFile());
        var font = new PixelFont();
        var pipeline = new OcrPipeline();
        var frame = GbFrame.normalize(image, SCREEN);
        pipeline.sample("shop", SCREEN, 0, frame);
        pipeline.sample("shop", SCREEN, 100_000_000L, frame);
        var request = pipeline.sample("shop", SCREEN, 200_000_000L, frame).stream()
                .filter(r -> r.result().layout().equals("shop_items")).findFirst().orElseThrow();
        paint(image, 40, 32, 0);
        paint(image, 40, 48, font.pixels("▶"));
        var moved = GbFrame.normalize(image, SCREEN);
        assertTrue(pipeline.sample("shop", SCREEN, 300_000_000L, moved).isEmpty());
        assertTrue(pipeline.isCurrent(request.key()));
        assertEquals(1, new OcrPipeline().recognize(moved).getFirst().selectedItem());
        paint(image, 136, 32, font.pixels("1"));
        var changed = GbFrame.normalize(image, SCREEN);
        assertTrue(pipeline.sample("shop", SCREEN, 400_000_000L, changed).isEmpty());
        assertFalse(pipeline.isCurrent(request.key()));
        assertTrue(pipeline.sample("shop", SCREEN, 500_000_000L, changed).isEmpty());
        var updated = pipeline.sample("shop", SCREEN, 600_000_000L, changed);
        assertEquals(1, updated.size());
        assertEquals("ハイパーボール 1201円", updated.getFirst().result().rawOcr().getFirst());
        paint(image, 144, 88, 0);
        assertTrue(pipeline.sample("shop", SCREEN, 700_000_000L, GbFrame.normalize(image, SCREEN)).isEmpty());
        var tm = new PixelOcr.Result("list", "shop_items", "shop_items", request.result().bounds(),
                List.of(), List.of(), List.of("わざマシン02 3000円"), List.of(), 0, List.of());
        var translated = new TextResolver(new TextCorpus()).resolve(tm).getFirst();
        assertEquals("わざマシン02 3000円", translated.rawJapanese());
        assertEquals("TM02 3000円", translated.chinese());
        for (int x = 56; x <= 152; x += 8) {
            paint(image, x, 72, font.pixels(x == 56 ? "┌" : x == 152 ? "┐" : "─"));
            paint(image, x, 88, font.pixels(x == 56 ? "└" : x == 152 ? "┘" : "─"));
        }
        paint(image, 56, 80, font.pixels("│"));
        paint(image, 152, 80, font.pixels("│"));
        var covered = new OcrPipeline().recognize(GbFrame.normalize(image, SCREEN)).getFirst();
        assertEquals("shop_items", covered.layout());
        assertEquals(3, covered.rawOcr().size());
        assertTrue(covered.diagnostics().stream().flatMap(l -> l.characters().stream()).noneMatch(PixelOcr.CharacterResult::unknown));
    }
}
