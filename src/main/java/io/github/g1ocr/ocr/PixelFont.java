package io.github.g1ocr.ocr;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class PixelFont {
    public record Glyph(String role, long base, long mark, List<String> candidates) {}
    public record Match(List<String> candidates, int distance, int runnerUpDistance) {}
    private final List<Glyph> glyphs = new ArrayList<>();

    public PixelFont() throws IOException {
        try (var reader = new BufferedReader(new InputStreamReader(
                PixelFont.class.getResourceAsStream("/font.tsv"), StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null;) {
                if (line.startsWith("#")) continue;
                String[] parts = line.split("\t", -1);
                glyphs.add(new Glyph(parts[0], Long.parseUnsignedLong(parts[1], 16),
                        Long.parseUnsignedLong(parts[2], 16), List.of(parts[3].split("\\|"))));
            }
        }
    }

    public long pixels(String token) {
        return glyphs.stream().filter(g -> g.mark() == 0 && g.candidates().contains(token))
                .findFirst().orElseThrow().base();
    }

    /** All equal visual candidates survive; non-exact matches remain unknown. */
    public Match match(long base, long mark) {
        int best = 129, second = 129;
        List<String> candidates = new ArrayList<>();
        for (Glyph glyph : glyphs) {
            if (!glyph.role().equals("text")) continue;
            int distance = Long.bitCount(base ^ glyph.base()) + Long.bitCount(mark ^ glyph.mark());
            if (distance < best) {
                second = best;
                best = distance;
                candidates = new ArrayList<>(glyph.candidates());
            } else if (distance == best) {
                candidates.addAll(glyph.candidates());
            } else second = Math.min(second, distance);
        }
        return new Match(List.copyOf(candidates), best, second);
    }
}
