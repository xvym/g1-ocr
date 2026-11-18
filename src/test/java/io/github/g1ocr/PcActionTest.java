package io.github.g1ocr;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.g1ocr.image.GbFrame;
import io.github.g1ocr.text.TextCorpus;
import io.github.g1ocr.text.TextResolver;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PcActionTest {
    @Test void itemPcMenuAndDialogueAreRecognizedSeparately() throws Exception {
        var image = ImageIO.read(getClass().getResource("/pc-item-action-20261107.png"));
        var upper = new BufferedImage(400, 240, BufferedImage.TYPE_INT_RGB);
        var graphics = upper.createGraphics();
        graphics.drawImage(image, 120, 48, null);
        graphics.dispose();
        var pipeline = new OcrPipeline();
        var results = pipeline.recognize(GbFrame.normalize(upper, new GbFrame.Mapping(0, 0, 1)));
        assertEquals(List.of("pc_item_action", "dialogue"), results.stream().map(r -> r.layout()).toList());
        var menu = results.getFirst();
        assertEquals(List.of("どうぐを ひきだす", "どうぐを あずける", "どうぐを すてる", "スイッチを きる"),
                menu.rawOcr());
        assertEquals(0, menu.selectedItem());
        var resolver = new TextResolver(new TextCorpus());
        assertEquals(List.of("取出道具", "寄放道具", "丢弃道具", "关机"),
                resolver.resolve(menu).stream().map(TextResolver.Unit::chinese).toList());
        assertEquals(List.of("なにを しますか？"), results.getLast().rawOcr());
    }

    @Test void userMenuAndDialogueMatchIndependentAnswersAndCoveredMenuIsNotRead() throws Exception {
        Path labels = Path.of("testpic/labels/pc-action.json");
        var answer = JsonMapper.builder().build().readTree(labels.toFile());
        var image = ImageIO.read(labels.getParent().resolve(answer.path("file").asText()).toFile());
        assertEquals(answer.path("imageWidth").asInt(), image.getWidth());
        assertEquals(answer.path("imageHeight").asInt(), image.getHeight());
        // Preserve the attached preview pixels; only place the GB area at the normal upper-screen origin.
        var upper = new BufferedImage(1200, 720, BufferedImage.TYPE_INT_RGB);
        var graphics = upper.createGraphics();
        graphics.drawImage(image, 360 - answer.path("gbCrop").path("x").asInt(),
                144 - answer.path("gbCrop").path("y").asInt(), null);
        graphics.dispose();
        var pipeline = new OcrPipeline();
        var mapping = new GbFrame.Mapping(0, 0, 3);
        var actual = pipeline.recognize(GbFrame.normalize(upper, mapping));
        var resolver = new TextResolver(new TextCorpus());
        assertEquals(answer.path("regions").size(), actual.size());
        for (int i = 0; i < actual.size(); i++) {
            var expected = answer.path("regions").get(i);
            var result = actual.get(i);
            var japanese = new ArrayList<String>();
            expected.path("raw").forEach(row -> japanese.add(row.asText()));
            assertEquals(expected.path("layout").asText(), result.layout());
            assertEquals(japanese, result.rawOcr());
            assertTrue(result.diagnostics().stream().flatMap(row -> row.characters().stream())
                    .noneMatch(character -> character.unknown()));
            if (expected.has("selectedItem")) assertEquals(expected.path("selectedItem").asInt(), result.selectedItem());
            if (expected.has("chinese")) {
                var chinese = new ArrayList<String>();
                expected.path("chinese").forEach(row -> chinese.add(row.asText()));
                var translated = resolver.resolve(result);
                assertEquals(chinese, translated.stream().map(TextResolver.Unit::chinese).toList());
                assertTrue(translated.stream().allMatch(unit -> unit.displayStatus().equals("ready")));
            }
        }
        // Real shared-list overlays must not produce the partially visible PC operation menu.
        for (String file : List.of("Screenshot 2026-10-20 22-42-43.png", "Screenshot 2026-10-20 22-42-47.png")) {
            var covered = GbFrame.normalize(ImageIO.read(Path.of("testpic", file).toFile()), mapping);
            assertTrue(pipeline.regions(covered).stream().noneMatch(region -> region.layout().equals("pc_action")), file);
        }
    }
}
