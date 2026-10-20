package io.github.g1ocr;

import io.github.g1ocr.image.GbFrame;
import io.github.g1ocr.ocr.PixelFont;
import io.github.g1ocr.region.RegionDetector;
import io.github.g1ocr.region.StabilityDetector;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic inputs check rules only; these are not real capture/timing evidence. */
class PipelineRulesTest {
    private static final GbFrame.Mapping SCREEN = new GbFrame.Mapping(0, 0, 3);
    private static BufferedImage source(String time) throws Exception {
        return ImageIO.read(Path.of("testpic/Screenshot 2026-10-20 " + time + ".png").toFile());
    }
    private static void paint(BufferedImage image, int x, int y, long tile) {
        for (int dy = 0; dy < 8; dy++) for (int dx = 0; dx < 8; dx++)
            for (int ky = 0; ky < 3; ky++) for (int kx = 0; kx < 3; kx++)
                image.setRGB(360 + (x + dx) * 3 + kx, 144 + (y + dy) * 3 + ky,
                        (tile & 1L << (dy * 8 + dx)) == 0 ? 0x94947b : 0x181810);
    }

    @Test void offsetAndIntegerScaleRecoverEveryPixel() {
        for (int k = 1; k <= 4; k++) {
            int ox = 11, oy = 7;
            BufferedImage screen = new BufferedImage(ox + 400 * k + 5, oy + 240 * k + 9, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < 144; y++) for (int x = 0; x < 160; x++) {
                int color = (x * 17011 + y * 9137) & 0xffffff;
                for (int dy = 0; dy < k; dy++) for (int dx = 0; dx < k; dx++)
                    screen.setRGB(ox + (120 + x) * k + dx, oy + (48 + y) * k + dy, color);
            }
            GbFrame normalized = GbFrame.normalize(screen, new GbFrame.Mapping(ox, oy, k));
            for (int y = 0; y < 144; y++) for (int x = 0; x < 160; x++)
                assertEquals((x * 17011 + y * 9137) & 0xffffff, normalized.image().getRGB(x, y) & 0xffffff);
        }
    }

    @Test void stabilityMasksGenerationsAndInvalidation() throws Exception {
        PixelFont font = new PixelFont();
        var detector = new StabilityDetector(new RegionDetector(font));
        BufferedImage image = source("21-22-53");
        GbFrame a = GbFrame.normalize(image, SCREEN);
        OcrPipeline pipeline = new OcrPipeline();
        assertTrue(pipeline.sample("s1", SCREEN, 0, a).isEmpty());
        assertTrue(pipeline.sample("s1", SCREEN, 100_000_000L, a).isEmpty());
        var published = pipeline.sample("s1", SCREEN, 200_000_000L, a).getFirst();
        assertEquals(new OcrPipeline().recognize(a), java.util.List.of(published.result()));
        assertTrue(pipeline.isCurrent(published.key()));
        pipeline.invalidate();
        assertFalse(pipeline.isCurrent(published.key()));
        assertTrue(detector.sample("s1", SCREEN, 0, a).isEmpty());
        assertTrue(detector.sample("s1", SCREEN, 50_000_000L, a).isEmpty());
        assertTrue(detector.sample("s1", SCREEN, 100_000_000L, a).isEmpty());
        var request = detector.sample("s1", SCREEN, 200_000_000L, a).getFirst();
        assertTrue(detector.isCurrent(request.key()));
        assertTrue(detector.sample("s1", SCREEN, 300_000_000L, a).isEmpty());
        paint(image, 144, 128, 0); // Blinking continuation arrow.
        assertTrue(detector.sample("s1", SCREEN, 400_000_000L, GbFrame.normalize(image, SCREEN)).isEmpty());
        assertTrue(detector.isCurrent(request.key()));
        paint(image, 8, 104, font.pixels("゛")); // A single mark must reset, even if it is an unknown combination.
        GbFrame b = GbFrame.normalize(image, SCREEN);
        assertTrue(detector.sample("s1", SCREEN, 500_000_000L, b).isEmpty());
        assertFalse(detector.isCurrent(request.key()));
        assertTrue(detector.sample("s1", SCREEN, 600_000_000L, b).isEmpty());
        assertEquals(1, detector.sample("s1", SCREEN, 700_000_000L, b).size());
        assertTrue(detector.sample("s1", SCREEN, 800_000_000L, a).isEmpty());
        assertTrue(detector.sample("s1", SCREEN, 900_000_000L, a).isEmpty());
        var returned = detector.sample("s1", SCREEN, 1_000_000_000L, a).getFirst();
        assertNotEquals(request.key(), returned.key()); // A -> B -> A.
        GbFrame empty = GbFrame.normalize(source("22-58-44"), SCREEN);
        assertTrue(detector.sample("s1", SCREEN, 1_100_000_000L, empty).isEmpty());
        assertFalse(detector.isCurrent(returned.key()));
        assertTrue(detector.sample("s1", SCREEN, 1_200_000_000L, a).isEmpty());
        // One delayed observation counts once; no catch-up ticks.
        assertTrue(detector.sample("s1", SCREEN, 9_000_000_000L, a).isEmpty());
        var delayed = detector.sample("s1", SCREEN, 9_100_000_000L, a).getFirst();
        detector.invalidate();
        assertFalse(detector.isCurrent(delayed.key()));
        assertTrue(detector.sample("s1", SCREEN, 9_200_000_000L, a).isEmpty());
        assertTrue(detector.sample("s2", SCREEN, 9_300_000_000L, a).isEmpty());
        assertTrue(detector.sample("s2", new GbFrame.Mapping(1, 0, 3), 9_400_000_000L, a).isEmpty());
    }

    @Test void cursorMotionDoesNotRepeatListOcrAndUnknownStaysUnknown() throws Exception {
        PixelFont font = new PixelFont();
        var detector = new StabilityDetector(new RegionDetector(font));
        BufferedImage image = source("21-22-46");
        GbFrame frame = GbFrame.normalize(image, SCREEN);
        detector.sample("s", SCREEN, 0, frame);
        detector.sample("s", SCREEN, 100_000_000L, frame);
        var request = detector.sample("s", SCREEN, 200_000_000L, frame).getFirst();
        paint(image, 40, 32, 0);
        paint(image, 40, 48, font.pixels("▶"));
        var moved = GbFrame.normalize(image, SCREEN);
        assertTrue(detector.sample("s", SCREEN, 300_000_000L, moved).isEmpty());
        assertTrue(detector.isCurrent(request.key()));
        var list = new OcrPipeline().recognize(moved).getFirst();
        assertEquals(1, list.selectedItem());
        paint(image, 48, 32, -1L);
        var bad = new OcrPipeline().recognize(GbFrame.normalize(image, SCREEN)).getFirst();
        assertTrue(bad.rawOcr().getFirst().startsWith("�"));
        assertTrue(bad.diagnostics().getFirst().characters().getFirst().unknown());
    }
}
