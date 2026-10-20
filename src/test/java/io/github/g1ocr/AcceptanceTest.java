package io.github.g1ocr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.g1ocr.image.GbFrame;
import io.github.g1ocr.ocr.PixelOcr;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Independent answers enter only here, after the production pipeline returns. */
class AcceptanceTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.asText()));
        return values;
    }
    private static String canonical(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFC)
                .replace('リ', 'り').replace('ヘ', 'へ').replace('ベ', 'べ').replace('ペ', 'ぺ');
    }
    private static List<String> tokens(String text) {
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < text.length();) {
            if (text.startsWith("<Lv>", i)) { tokens.add("<Lv>"); i += 4; }
            else { int cp = text.codePointAt(i); tokens.add(new String(Character.toChars(cp))); i += Character.charCount(cp); }
        }
        return tokens;
    }
    private static int distance(String expected, String actual) {
        List<String> a = tokens(expected), b = tokens(actual);
        int[] row = new int[b.size() + 1];
        for (int j = 0; j < row.length; j++) row[j] = j;
        for (int i = 1; i <= a.size(); i++) {
            int diagonal = row[0]; row[0] = i;
            for (int j = 1; j <= b.size(); j++) {
                int previous = row[j];
                row[j] = Math.min(Math.min(row[j] + 1, row[j - 1] + 1), diagonal + (a.get(i - 1).equals(b.get(j - 1)) ? 0 : 1));
                diagonal = previous;
            }
        }
        return row[b.size()];
    }
    private static String hash(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    @Test void allVisibleJapanese() throws Exception {
        String group = System.getProperty("ocr.group", "all");
        Path labelPath = Path.of("testpic/labels/japanese.json");
        JsonNode labels = JSON.readTree(labelPath.toFile());
        OcrPipeline pipeline = new OcrPipeline();
        List<Object> samples = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        int passed = 0, strictPassed = 0, characters = 0, errors = 0, strictErrors = 0, lines = 0, unknowns = 0;
        List<String> manifest = Files.readAllLines(Path.of("testpic/manifest.csv"));
        assertEquals(109, manifest.size());
        assertEquals(108, labels.path("samples").size());
        for (JsonNode sample : labels.path("samples")) {
            String id = sample.path("sample_id").asText();
            int index = Integer.parseInt(id.substring(3));
            // Entire Cinnabar Lab conversations are reserved together, before first OCR run.
            boolean holdout = index >= 96;
            if (group.equals("development") && holdout || group.equals("holdout") && !holdout) continue;
            assertTrue(sample.path("uncertainties").isEmpty(), id + " unresolved annotation");
            Path imagePath = labelPath.getParent().resolve(sample.path("file").asText()).normalize();
            assertTrue(manifest.get(index).contains(imagePath.getFileName().toString()), id + " manifest mismatch");
            var image = ImageIO.read(imagePath.toFile());
            assertEquals(1200, image.getWidth(), id);
            assertEquals(720, image.getHeight(), id);
            GbFrame frame = GbFrame.normalize(image, new GbFrame.Mapping(0, 0, 3));
            List<PixelOcr.Result> actual = pipeline.recognize(frame);
            List<JsonNode> expected = new ArrayList<>();
            sample.path("regions").forEach(r -> { if (!r.path("empty").asBoolean(false)) expected.add(r); });
            List<Object> regions = new ArrayList<>();
            boolean imagePass = actual.size() == expected.size();
            boolean strictPass = imagePass;
            for (int n = 0; n < Math.max(expected.size(), actual.size()); n++) {
                JsonNode answer = n < expected.size() ? expected.get(n) : JSON.createObjectNode();
                PixelOcr.Result found = n < actual.size() ? actual.get(n) : null;
                List<String> e = strings(answer.path("lines")), ef = strings(answer.path("footer_lines"));
                List<String> a = found == null ? List.of() : found.rawOcr(), af = found == null ? List.of() : found.footerLines();
                List<String> allE = new ArrayList<>(e), allA = new ArrayList<>(a);
                allE.addAll(ef); allA.addAll(af);
                boolean equal = e.stream().map(AcceptanceTest::canonical).toList().equals(a.stream().map(AcceptanceTest::canonical).toList())
                        && ef.stream().map(AcceptanceTest::canonical).toList().equals(af.stream().map(AcceptanceTest::canonical).toList());
                boolean strict = e.equals(a) && ef.equals(af);
                int regionErrors = 0, regionStrictErrors = 0;
                for (int line = 0; line < Math.max(allE.size(), allA.size()); line++) {
                    String el = line < allE.size() ? allE.get(line) : "", al = line < allA.size() ? allA.get(line) : "";
                    characters += tokens(el).size();
                    regionErrors += distance(canonical(el), canonical(al));
                    regionStrictErrors += distance(el, al);
                }
                lines += allE.size();
                errors += regionErrors; strictErrors += regionStrictErrors;
                imagePass &= equal; strictPass &= strict;
                var record = new LinkedHashMap<String, Object>();
                record.put("expectedType", answer.path("type").asText());
                record.put("detectedLayout", found == null ? null : found.layout());
                record.put("semanticType", found == null ? null : found.semanticType());
                record.put("bounds", found == null ? null : found.bounds());
                record.put("textGrids", found == null ? List.of() : found.textGrids());
                record.put("footerGrids", found == null ? List.of() : found.footerGrids());
                record.put("expectedLines", e); record.put("rawOcr", a);
                record.put("expectedFooter", ef); record.put("footerLines", af);
                record.put("pass", equal); record.put("strictUnicodePass", strict);
                record.put("errors", regionErrors); record.put("strictUnicodeErrors", regionStrictErrors);
                if (answer.has("selected_row") || answer.has("selected_item")) {
                    int selected = answer.has("selected_row") ? answer.path("selected_row").asInt() : answer.path("selected_item").asInt();
                    record.put("expectedSelection", selected); record.put("actualSelection", found == null ? null : found.selectedItem());
                    boolean selectionPass = found != null && Integer.valueOf(selected).equals(found.selectedItem());
                    record.put("selectionPass", selectionPass); imagePass &= selectionPass; strictPass &= selectionPass;
                }
                if (found != null) {
                    var diagnostics = found.diagnostics().stream().flatMap(line -> line.characters().stream()).toList();
                    record.put("ambiguousCharacters", diagnostics.stream().filter(c -> c.candidates().size() > 1 && !c.unknown()).toList());
                    var unknown = diagnostics.stream().filter(PixelOcr.CharacterResult::unknown).toList();
                    unknowns += unknown.size(); record.put("unknownCharacters", unknown);
                    record.put("maximumMatchDistance", diagnostics.stream().mapToInt(PixelOcr.CharacterResult::distance).max().orElse(0));
                }
                record.put("resolvedJapanese", null); record.put("chinese", null);
                regions.add(record);
            }
            if (imagePass) passed++; else failures.add(id + " " + JSON.writeValueAsString(regions));
            if (strictPass) strictPassed++;
            var record = new LinkedHashMap<String, Object>();
            record.put("sampleId", id); record.put("source", imagePath.toString().replace('\\', '/'));
            record.put("sourceSha256", hash(imagePath)); record.put("group", holdout ? "holdout_lab" : "development");
            record.put("threshold", frame.threshold()); record.put("pass", imagePass); record.put("strictUnicodePass", strictPass);
            record.put("expectedNoTargetText", sample.path("expected_no_target_text").asBoolean());
            record.put("regions", regions); samples.add(record);
        }
        var report = new LinkedHashMap<String, Object>();
        report.put("implementationVersion", "0.1.0"); report.put("java", System.getProperty("java.runtime.version"));
        report.put("group", group); report.put("annotationVersion", labels.path("annotation_version").asInt());
        report.put("annotationSha256", hash(labelPath));
        report.put("comparison", "NFC; only pixel-identical り/リ,へ/ヘ,べ/ベ,ぺ/ペ equivalent; <Lv> counts as one token; spaces count, line breaks compared structurally");
        report.put("mapping", new GbFrame.Mapping(0, 0, 3)); report.put("images", samples.size());
        report.put("passedImages", passed); report.put("strictUnicodePassedImages", strictPassed);
        report.put("expectedTextLines", lines); report.put("expectedCharacterTokens", characters);
        report.put("characterErrors", errors); report.put("strictUnicodeCharacterErrors", strictErrors);
        report.put("cer", (double) errors / characters); report.put("unknownCharacters", unknowns);
        var sourceHashes = new LinkedHashMap<String, String>();
        try (var paths = Files.walk(Path.of("src"))) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList())
                sourceHashes.put(path.toString().replace('\\', '/'), hash(path));
        }
        sourceHashes.put("pom.xml", hash(Path.of("pom.xml")));
        report.put("implementationSha256", sourceHashes); report.put("samples", samples);
        Files.createDirectories(Path.of("reports"));
        JSON.writerWithDefaultPrettyPrinter().writeValue(Path.of("reports/japanese-" + group + ".json").toFile(), report);
        System.out.printf("%s: %d/%d images; %d/%d character errors; strict Unicode %d/%d images%n",
                group, passed, samples.size(), errors, characters, strictPassed, samples.size());
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }
}
