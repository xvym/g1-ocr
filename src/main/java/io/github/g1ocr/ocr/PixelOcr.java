package io.github.g1ocr.ocr;

import io.github.g1ocr.image.GbFrame;
import io.github.g1ocr.region.RegionDetector.Grid;
import io.github.g1ocr.region.RegionDetector.Region;
import io.github.g1ocr.region.RegionDetector.Rect;
import java.util.ArrayList;
import java.util.List;

public final class PixelOcr {
    public record CharacterResult(int x, int y, String raw, List<String> candidates,
                                  int distance, int runnerUpDistance, boolean unknown) {}
    public record Line(String rawOcr, List<CharacterResult> characters) {}
    public record Result(String id, String layout, String semanticType, Rect bounds,
                         List<Grid> textGrids, List<Grid> footerGrids, List<String> rawOcr,
                         List<String> footerLines, Integer selectedItem, List<Line> diagnostics) {
        public boolean hasText() { return !rawOcr.isEmpty() || !footerLines.isEmpty(); }
    }

    private final PixelFont font;
    public PixelOcr(PixelFont font) { this.font = font; }

    private Line line(GbFrame frame, Grid grid) {
        StringBuilder text = new StringBuilder();
        List<CharacterResult> characters = new ArrayList<>();
        for (int col = 0; col < grid.columns(); col++) {
            if (grid.maskedColumns().contains(col)) { text.append(' '); continue; }
            int x = grid.x() + col * 8, y = grid.y();
            PixelFont.Match match = font.match(frame.tile(x, y), frame.tile(x, y - 8));
            String raw = match.distance() == 0 ? match.candidates().getFirst() : "�";
            text.append(raw);
            characters.add(new CharacterResult(x, y, raw, match.candidates(), match.distance(),
                    match.runnerUpDistance(), match.distance() != 0));
        }
        return new Line(text.toString().strip().replaceAll(" +", " "), List.copyOf(characters));
    }

    public Result read(GbFrame frame, Region region) {
        List<Line> lines = region.rows().stream().map(row -> line(frame, row)).toList();
        List<Line> footer = region.footerRows().stream().map(row -> line(frame, row)).toList();
        List<Line> diagnostics = new ArrayList<>(lines);
        diagnostics.addAll(footer);
        return new Result(region.id(), region.layout(), region.semanticType(), region.bounds(), region.rows(), region.footerRows(),
                lines.stream().map(Line::rawOcr).filter(s -> !s.isEmpty()).toList(),
                footer.stream().map(Line::rawOcr).filter(s -> !s.isEmpty()).toList(),
                region.selectedItem(), List.copyOf(diagnostics));
    }
}
