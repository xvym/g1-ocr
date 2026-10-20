package io.github.g1ocr.region;

import io.github.g1ocr.image.GbFrame;
import io.github.g1ocr.ocr.PixelFont;
import java.util.ArrayList;
import java.util.List;

/** Only measured Japanese tile layouts, all coordinates in GB pixels. */
public final class RegionDetector {
    public record Rect(int x, int y, int width, int height) {}
    public record Grid(int x, int y, int columns, List<Integer> maskedColumns) {
        public Grid(int x, int y, int columns) { this(x, y, columns, List.of()); }
    }
    public record Region(String id, String layout, String semanticType, Rect bounds,
                         List<Grid> rows, List<Grid> footerRows, Integer selectedItem) {
        /** Only the character cells and their full mark tiles participate in stability. */
        public long[] textPixels(GbFrame frame) {
            List<Long> pixels = new ArrayList<>();
            for (Grid row : java.util.stream.Stream.concat(rows.stream(), footerRows.stream()).toList()) {
                for (int col = 0; col < row.columns(); col++) {
                    if (row.maskedColumns().contains(col)) continue;
                    pixels.add(frame.tile(row.x() + col * 8, row.y() - 8));
                    pixels.add(frame.tile(row.x() + col * 8, row.y()));
                }
            }
            return pixels.stream().mapToLong(Long::longValue).toArray();
        }
    }

    private final PixelFont font;
    public RegionDetector(PixelFont font) { this.font = font; }

    private boolean is(GbFrame frame, int x, int y, String token) {
        return frame.tile(x, y) == font.pixels(token);
    }

    private boolean horizontal(GbFrame frame, int left, int right, int y, String start, String end) {
        if (!is(frame, left, y, start) || !is(frame, right, y, end)) return false;
        for (int x = left + 8; x < right; x += 8) if (!is(frame, x, y, "─")) return false;
        return true;
    }

    private Integer selected(GbFrame frame, int pointerX, int firstY, int count) {
        for (int row = 0; row < count; row++)
            if (is(frame, pointerX, firstY + row * 16, "▶")) return row;
        return null;
    }

    private boolean markBand(GbFrame frame, int x, int y, int columns) {
        for (int col = 0; col < columns; col++) {
            long tile = frame.tile(x + col * 8, y);
            if (tile != 0 && tile != font.pixels("゛") && tile != font.pixels("゜")) return false;
        }
        return true;
    }

    public List<Region> detect(GbFrame frame) {
        List<Region> regions = new ArrayList<>();
        boolean list = horizontal(frame, 32, 152, 16, "┌", "┐")
                && horizontal(frame, 32, 152, 96, "└", "┘");
        // Shop prices occupy the final currency column; this dedicated layout is outside the target scope.
        boolean prices = false;
        for (int y = 32; y <= 80; y += 16) prices |= is(frame, 144, y, "円");
        list &= !prices;
        if (list) {
            List<Grid> rows = List.of(new Grid(48, 32, 13), new Grid(48, 48, 13),
                    new Grid(48, 64, 13), new Grid(48, 80, 13));
            List<Grid> footer = horizontal(frame, 96, 152, 120, "└", "┘")
                    && is(frame, 96, 112, "│") ? List.of(new Grid(112, 112, 5)) : List.of();
            boolean levels = rows.stream().anyMatch(row -> is(frame, 96, row.y(), "<Lv>"));
            regions.add(new Region("list", "shared_list", levels ? "pc_pokemon" : "ambiguous",
                    new Rect(32, 16, 128, 88), rows, footer, selected(frame, 40, 32, 4)));
        }
        boolean moves = horizontal(frame, 0, 72, 64, "┌", "┐") && is(frame, 0, 136, "└");
        if (moves) regions.add(new Region("moves", "battle_moves", "battle_moves", new Rect(0, 64, 80, 80),
                List.of(new Grid(16, 80, 7), new Grid(16, 96, 7), new Grid(16, 112, 7),
                        new Grid(16, 128, 7)), List.of(), selected(frame, 8, 80, 4)));

        boolean action = horizontal(frame, 72, 152, 96, "┌", "┐")
                && horizontal(frame, 72, 152, 136, "└", "┘")
                && markBand(frame, 80, 104, 9) && markBand(frame, 80, 120, 9);
        if (action) {
            Integer selection = null;
            for (int row = 0; row < 2; row++) for (int col = 0; col < 2; col++)
                if (is(frame, 80 + col * 40, 112 + row * 16, "▶")) selection = row * 2 + col;
            regions.add(new Region("action", "battle_action", "battle_action", new Rect(72, 96, 88, 48),
                    List.of(new Grid(88, 112, 8, List.of(4)), new Grid(88, 128, 8, List.of(4))),
                    List.of(), selection));
        }

        // The list and PC status box may cover the message's top/bottom right edges.
        // g1-077 has a two-logical-pixel horizontal displacement, including text and frame.
        // Search only this measured local range; GB normalization itself stays unchanged.
        int dialogueX = -1;
        for (int x = 0; x <= 2; x++) {
            boolean dialogue = is(frame, x, 96, "┌") && is(frame, x + 8, 96, "─")
                    && is(frame, x + 16, 96, "─") && is(frame, x + 24, 96, "─") && is(frame, x, 136, "└");
            for (int y = 104; y <= 128; y += 8) dialogue &= is(frame, x, y, "│");
            if (dialogue) { dialogueX = x; break; }
        }
        if (dialogueX >= 0) {
            boolean status = horizontal(frame, 72, 152, 112, "┌", "┐");
            int columns = action || status ? 8 : 18;
            // The lower-right continuation arrow lives at x=144,y=128.
            List<Integer> mask = columns == 18 ? List.of(17) : List.of();
            regions.add(new Region("dialogue", "dialogue", "dialogue", new Rect(dialogueX, 96, 160 - dialogueX, 48),
                    List.of(new Grid(dialogueX + 8, 112, columns), new Grid(dialogueX + 8, 128, columns, mask)), List.of(), null));
        }
        return List.copyOf(regions);
    }
}
