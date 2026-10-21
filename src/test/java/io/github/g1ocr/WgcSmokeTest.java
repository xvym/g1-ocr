package io.github.g1ocr;

import io.github.g1ocr.capture.WindowCapture;
import io.github.g1ocr.image.GbFrame;
import io.github.g1ocr.image.ViewportResolver;
import io.github.g1ocr.ocr.PixelFont;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in desktop integration check; constructed input does not certify a 3DS capture application. */
@EnabledIfSystemProperty(named = "g1ocr.wgc.smoke", matches = "true")
class WgcSmokeTest {
    private static final String TITLE = "G1 WGC smoke fixture";
    private static final String[] IMAGES = {
            "testpic/Screenshot 2026-10-20 21-22-53.png",
            "testpic/Screenshot 2026-10-20 21-23-05.png"};

    @Test void capturesChangedPixelsAndCalibrates() throws Exception {
        Files.createDirectories(Path.of("output"));
        var fixture = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "javaw.exe").toString(),
                "-Dsun.java2d.uiScale=1", "-cp", "target/test-classes;target/classes", Fixture.class.getName())
                .redirectErrorStream(true).redirectOutput(Path.of("output/wgc-fixture.log").toFile()).start();
        List<String> evidence = new ArrayList<>();
        try {
            long deadline = System.nanoTime() + 20_000_000_000L;
            WindowCapture.Window window = null;
            while (window == null && System.nanoTime() < deadline) {
                window = WindowCapture.windows().stream()
                        .filter(w -> w.pid() == fixture.pid() && w.title().equals(TITLE)).findFirst().orElse(null);
                if (window == null) Thread.sleep(100);
            }
            assertNotNull(window, "Fixture window was not enumerated; log: output/wgc-fixture.log");
            evidence.add(window.toString());
            var resolver = new ViewportResolver(new PixelFont());
            var pipeline = new OcrPipeline();
            List<int[]> expected = new ArrayList<>();
            for (String path : IMAGES) expected.add(GbFrame.normalize(ImageIO.read(Path.of(path).toFile()),
                    new GbFrame.Mapping(0, 0, 3)).image().getRGB(0, 0, 160, 144, null, 0, 160));
            assertFalse(Arrays.equals(expected.get(0), expected.get(1)), "Fixture inputs must differ");
            boolean[] observed = new boolean[2];
            long[] frameIds = new long[2];
            try (var capture = new WindowCapture(window)) {
                while (!(observed[0] && observed[1]) && System.nanoTime() < deadline) {
                    var poll = capture.poll();
                    assertNotEquals(WindowCapture.Status.ERROR, poll.status(), poll.detail());
                    assertNotEquals(WindowCapture.Status.CLOSED, poll.status(), poll.detail());
                    if (poll.status() == WindowCapture.Status.RUNNING && poll.image() != null) {
                        assertTrue(poll.frameArrivalNanos() > 0 && poll.frameArrivalNanos() <= poll.sampleTimeNanos());
                        var resolved = resolver.resolve(poll.image(), poll.clientHints(), poll.sampleTimeNanos());
                        if (resolved.status() == ViewportResolver.Status.READY) {
                            int[] pixels = resolved.frame().image().getRGB(0, 0, 160, 144, null, 0, 160);
                            for (int i = 0; i < expected.size(); i++) {
                                if (!observed[i] && Arrays.equals(expected.get(i), pixels)) {
                                    // The second input is a map with no target text.
                                    assertEquals(i == 1, pipeline.recognize(resolved.frame()).isEmpty());
                                    observed[i] = true;
                                    frameIds[i] = poll.frameId();
                                    evidence.add("input=" + i + " frame=" + poll.frameId() + " arrival="
                                            + poll.frameArrivalNanos() + " sample=" + poll.sampleTimeNanos()
                                            + " size=" + poll.image().getWidth() + "x" + poll.image().getHeight()
                                            + " dpi=" + poll.dpi() + " mapping=" + resolved.mapping());
                                    ImageIO.write(poll.image(), "png", Path.of("output/wgc-input-" + i + ".png").toFile());
                                }
                            }
                        }
                    }
                    Thread.sleep(100);
                }
            }
            assertTrue(observed[0] && observed[1], "Both exact GB pixel images must arrive through WGC: " + evidence);
            assertNotEquals(frameIds[0], frameIds[1], "Changed pixels must come from distinct native frames");
        } finally {
            fixture.destroy();
            assertTrue(fixture.waitFor(5, java.util.concurrent.TimeUnit.SECONDS), "Fixture did not exit");
            Files.write(Path.of("output/wgc-smoke.txt"), evidence);
        }
    }

    /** Separate process supplies actual window pixels; no desktop automation or expected OCR text. */
    public static final class Fixture {
        public static void main(String[] args) throws Exception {
            BufferedImage[] images = new BufferedImage[2];
            for (int i = 0; i < images.length; i++) images[i] = ImageIO.read(Path.of(IMAGES[i]).toFile());
            SwingUtilities.invokeLater(() -> {
                int[] current = {0};
                JPanel panel = new JPanel() {
                    @Override protected void paintComponent(Graphics graphics) {
                        super.paintComponent(graphics);
                        graphics.drawImage(images[current[0]], 0, 0, null);
                    }
                };
                panel.setPreferredSize(new Dimension(1200, 720));
                JFrame frame = new JFrame(TITLE);
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.setContentPane(panel);
                frame.pack();
                frame.setVisible(true);
                new Timer(1500, event -> { current[0] = 1 - current[0]; panel.repaint(); }).start();
            });
        }
    }
}
