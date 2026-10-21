package io.github.g1ocr;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.g1ocr.image.GbFrame;
import io.github.g1ocr.ocr.PixelOcr;
import io.github.g1ocr.region.RegionDetector.Rect;
import io.github.g1ocr.text.TextCorpus;
import io.github.g1ocr.text.TextResolver;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.text.Normalizer;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TranslationTest {
    private static TextCorpus corpus;
    @BeforeAll static void load() throws Exception { corpus = new TextCorpus(); }

    @Test void independentScenarios() throws Exception {
        var json = JsonMapper.builder().build();
        var fixture = json.readTree(Path.of("src/test/resources/chinese-cases.json").toFile());
        var results = new ArrayList<Object>();
        var failures = new ArrayList<String>();
        int assertions = 0;
        for (var scenario : fixture.path("scenarios")) {
            var resolver = new TextResolver(corpus);
            for (var event : scenario.path("events")) {
                if (event.path("reset").asBoolean()) { resolver.reset(); continue; }
                if (event.path("disappear").asBoolean()) { resolver.retainRegions(Set.of()); continue; }
                var actual = resolver.dialogue("dialogue", event.path("raw").asText());
                boolean pass = actual.matchStatus().equals(event.path("match").asText())
                        && actual.displayStatus().equals(event.path("status").asText())
                        && actual.chinese().equals(event.path("chinese").asText())
                        && (!event.has("source") || actual.candidates().stream()
                        .allMatch(c -> c.recordId().endsWith(event.path("source").asText())))
                        && (!event.has("candidateCount") || actual.candidates().size() == event.path("candidateCount").asInt());
                results.add(java.util.Map.of("scenario", scenario.path("name").asText(), "expected", event, "actual", actual, "pass", pass));
                if (!pass) failures.add(scenario.path("name").asText() + ": " + json.writeValueAsString(actual));
                assertions++;
            }
        }
        var report = new LinkedHashMap<String, Object>();
        report.put("implementationVersion", System.getProperty("g1ocr.version"));
        report.put("scope", fixture.path("scope").asText());
        report.put("annotationMethod", fixture.path("method").asText());
        report.put("checks", assertions); report.put("passed", assertions - failures.size());
        report.put("realCaptureTested", false); report.put("finalChineseAcceptance", false);
        var hashes = new LinkedHashMap<String, String>();
        try (var paths = Files.walk(Path.of("src"))) {
            for (var path : paths.filter(Files::isRegularFile).sorted().toList())
                hashes.put(path.toString().replace('\\', '/'), HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))));
        }
        report.put("sha256", hashes); report.put("results", results);
        Files.createDirectories(Path.of("reports"));
        json.writerWithDefaultPrettyPrinter().writeValue(Path.of("reports/chinese-runtime.json").toFile(), report);
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    @Test void listFieldsAndVisualOcrRemainIndependent() throws Exception {
        var resolver = new TextResolver(corpus);
        var frame = GbFrame.normalize(ImageIO.read(Path.of("testpic/Screenshot 2026-10-20 21-22-46.png").toFile()),
                new GbFrame.Mapping(0, 0, 3));
        var original = new OcrPipeline().recognize(frame).getFirst();
        assertEquals(List.of("じてんしゃ", "ポケモンのふえ", "いいつりざお", "ボロのつりざお"), original.rawOcr());
        var translated = resolver.resolve(original);
        assertEquals(List.of("自行车", "宝可梦之笛", "好钓竿", "破旧钓竿"),
                translated.subList(0, 4).stream().map(TextResolver.Unit::chinese).toList());
        assertTrue(translated.subList(0, 4).stream().allMatch(u -> u.matchStatus().equals("equivalent") && u.candidates().size() == 3));
        assertEquals("关闭", translated.get(4).chinese());
        assertEquals(original.rawOcr(), new OcrPipeline().recognize(frame).getFirst().rawOcr());
        var items = new PixelOcr.Result("list", "shared_list", "ambiguous", new Rect(32, 16, 128, 88),
                List.of(), List.of(), List.of("キズぐすり ×12", "キズぐすり × 99"), List.of(), 0, List.of());
        var quantities = resolver.resolve(items);
        assertEquals(List.of("伤药 ×12", "伤药 × 99"), quantities.stream().map(TextResolver.Unit::chinese).toList());
        assertEquals(items.rawOcr(), quantities.stream().map(TextResolver.Unit::rawJapanese).toList());
        var nicknames = new PixelOcr.Result("list", "shared_list", "pc_pokemon", items.bounds(),
                List.of(), List.of(), List.of("やめる <Lv>12", "やめる"), List.of(), 0, List.of());
        var pc = resolver.resolve(nicknames);
        assertEquals("やめる <Lv>12", pc.getFirst().display());
        assertEquals("nickname_preserved", pc.getFirst().displayStatus());
        assertEquals("取消", pc.get(1).chinese());
        var action = new PixelOcr.Result("action", "battle_action", "battle_action", items.bounds(),
                List.of(), List.of(), List.of("たたかう どうぐ", "ポケモン にげる"), List.of(), 2, List.of());
        assertEquals(List.of("战斗", "道具", "宝可梦", "逃走"),
                resolver.resolve(action).stream().map(TextResolver.Unit::chinese).toList());
    }

    /** Coverage measurement is not a claim that every emitted translation is semantically correct. */
    @Test void allScreenshots() throws Exception {
        var json = JsonMapper.builder().build();
        Path labelPath = Path.of("testpic/labels/japanese.json");
        var labels = json.readTree(labelPath.toFile());
        var regressions = json.readTree(Path.of("src/test/resources/chinese-regressions.json").toFile());
        int regressionImages = 0, regressionUnits = 0;
        var samples = new ArrayList<Object>();
        var summary = new LinkedHashMap<String, Object>();
        var failures = new ArrayList<String>();
        var pipeline = new OcrPipeline();
        java.util.function.UnaryOperator<String> canonical = text -> Normalizer.normalize(text, Normalizer.Form.NFC)
                .replace('リ', 'り').replace('ヘ', 'へ').replace('ベ', 'べ').replace('ペ', 'ぺ');
        summary.put("images", 0); summary.put("noTargetImages", 0);
        summary.put("allUnitsReadyChineseImages", 0); summary.put("partialChineseImages", 0);
        summary.put("noChineseImages", 0); summary.put("units", 0); summary.put("unitsWithChinese", 0);
        summary.put("displayStatusCounts", new LinkedHashMap<String, Integer>());
        for (var sample : labels.path("samples")) {
            String id = sample.path("sample_id").asText();
            Path imagePath = labelPath.getParent().resolve(sample.path("file").asText()).normalize();
            var frame = GbFrame.normalize(ImageIO.read(imagePath.toFile()), new GbFrame.Mapping(0, 0, 3));
            var actual = pipeline.recognize(frame);
            var expected = new ArrayList<com.fasterxml.jackson.databind.JsonNode>();
            sample.path("regions").forEach(r -> { if (!r.path("empty").asBoolean()) expected.add(r); });
            boolean japanesePass = actual.size() == expected.size();
            for (int i = 0; i < Math.min(actual.size(), expected.size()); i++) {
                var answer = expected.get(i);
                var lines = new ArrayList<String>(); answer.path("lines").forEach(l -> lines.add(canonical.apply(l.asText())));
                var footer = new ArrayList<String>(); answer.path("footer_lines").forEach(l -> footer.add(canonical.apply(l.asText())));
                japanesePass &= lines.equals(actual.get(i).rawOcr().stream().map(canonical).toList())
                        && footer.equals(actual.get(i).footerLines().stream().map(canonical).toList());
            }
            if (!japanesePass) failures.add(id + " raw Japanese mismatch");
            // Every image is independent; filename order supplies no historical context.
            var resolver = new TextResolver(corpus);
            var regions = new ArrayList<Object>();
            var units = new ArrayList<TextResolver.Unit>();
            for (var region : actual) {
                var translated = resolver.resolve(region);
                units.addAll(translated);
                regions.add(java.util.Map.of("id", region.id(), "layout", region.layout(),
                        "semanticType", region.semanticType(), "units", translated));
            }
            int published = (int) units.stream().filter(u -> !u.chinese().isEmpty()).count();
            if (regressions.path("samples").has(id)) {
                regressionImages++;
                var answers = regressions.path("samples").path(id);
                if (units.size() != answers.size()) failures.add(id + " regression unit count mismatch");
                for (int i = 0; i < Math.min(units.size(), answers.size()); i++) {
                    regressionUnits++;
                    if (!units.get(i).chinese().equals(answers.get(i).path("chinese").asText())
                            || !units.get(i).displayStatus().equals(answers.get(i).path("status").asText()))
                        failures.add(id + " regression unit " + i + ": " + units.get(i));
                }
            }
            boolean allReady = !units.isEmpty() && units.stream()
                    .allMatch(u -> !u.chinese().isEmpty() && u.displayStatus().equals("ready"));
            String outcome = units.isEmpty() ? "no_target" : allReady ? "all_ready"
                    : published == 0 ? "no_chinese" : "published_partial";
            summary.put("images", (int) summary.get("images") + 1);
            String bucket = switch (outcome) {
                case "no_target" -> "noTargetImages";
                case "all_ready" -> "allUnitsReadyChineseImages";
                case "published_partial" -> "partialChineseImages";
                default -> "noChineseImages";
            };
            summary.put(bucket, (int) summary.get(bucket) + 1);
            summary.put("units", (int) summary.get("units") + units.size());
            summary.put("unitsWithChinese", (int) summary.get("unitsWithChinese") + published);
            @SuppressWarnings("unchecked") var statuses = (Map<String, Integer>) summary.get("displayStatusCounts");
            for (var unit : units) statuses.merge(unit.displayStatus(), 1, Integer::sum);
            if (sample.path("expected_no_target_text").asBoolean() && !units.isEmpty())
                failures.add(id + " negative sample published text");
            var row = new LinkedHashMap<String, Object>();
            row.put("sampleId", id); row.put("source", imagePath.toString().replace('\\', '/'));
            row.put("sourceSha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(imagePath))));
            row.put("expectedNoTargetText", sample.path("expected_no_target_text").asBoolean());
            row.put("rawJapanesePass", japanesePass); row.put("rawOcrRegions", actual);
            row.put("outcome", outcome); row.put("units", units.size()); row.put("unitsWithChinese", published); row.put("regions", regions);
            samples.add(row);
        }
        var hashes = new LinkedHashMap<String, String>();
        try (var paths = Files.walk(Path.of("src"))) {
            for (var path : paths.filter(Files::isRegularFile).sorted().toList()) hashes.put(path.toString().replace('\\', '/'),
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))));
        }
        hashes.put("testpic/labels/japanese.json", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(labelPath))));
        var report = new LinkedHashMap<String, Object>();
        report.put("implementationVersion", System.getProperty("g1ocr.version"));
        report.put("scope", "108 independent PNGs; all corpus sources; no game version selection or historical context");
        report.put("measurement", "nonempty Chinese fields measure publication, not semantic correctness; nickname passthrough is not Chinese");
        report.put("images", samples.size());
        report.put("rawJapanesePassedImages", samples.size() - failures.stream().filter(f -> f.contains("raw Japanese")).count());
        report.put("executionFailures", failures); report.put("finalChineseAcceptance", false);
        report.put("regressionImages", regressionImages); report.put("regressionUnits", regressionUnits);
        report.put("regressionMethod", regressions.path("method").asText());
        report.put("summary", summary); report.put("sha256", hashes); report.put("samples", samples);
        Files.createDirectories(Path.of("reports"));
        json.writerWithDefaultPrettyPrinter().writeValue(Path.of("reports/chinese-images.json").toFile(), report);
        assertEquals(108, samples.size());
        assertEquals(regressions.path("samples").size(), regressionImages);
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }
}
