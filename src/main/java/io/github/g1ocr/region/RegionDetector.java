package io.github.g1ocr.region;

import io.github.g1ocr.image.GbFrame;
import io.github.g1ocr.ocr.PixelFont;
import java.util.ArrayList;
import java.util.List;

/** Only measured Japanese tile layouts, all coordinates in GB pixels. */
public final class RegionDetector {
    public record Rect(int x, int y, int width, int height) {}
    public record Grid(int x, int y, int columns, List<Integer> maskedColumns, long arrowPixels,
                       List<Integer> maskedMarkColumns) {
        public Grid(int x, int y, int columns) { this(x, y, columns, List.of()); }
        public Grid(int x, int y, int columns, List<Integer> maskedColumns) {
            this(x, y, columns, maskedColumns, 0);
        }
        public Grid(int x, int y, int columns, List<Integer> maskedColumns, long arrowPixels) {
            this(x, y, columns, maskedColumns, arrowPixels, List.of());
        }
        public long markPixels(GbFrame frame, int column) {
            return maskedMarkColumns.contains(column) ? 0 : frame.tile(x + column * 8, y - 8);
        }
        public long basePixels(GbFrame frame, int column) {
            long pixels = frame.tile(x + column * 8, y);
            return column == columns - 1 && pixels == arrowPixels ? 0 : pixels;
        }
    }
    public record Region(String id, String layout, String semanticType, Rect bounds,
                         List<Grid> rows, List<Grid> footerRows, Integer selectedItem) {
        /** Only the character cells and their full mark tiles participate in stability. */
        public long[] textPixels(GbFrame frame) {
            List<Long> pixels = new ArrayList<>();
            for (Grid row : java.util.stream.Stream.concat(rows.stream(), footerRows.stream()).toList()) {
                for (int col = 0; col < row.columns(); col++) {
                    if (row.maskedColumns().contains(col)) continue;
                    pixels.add(row.markPixels(frame, col));
                    pixels.add(row.basePixels(frame, col));
                }
            }
            return pixels.stream().mapToLong(Long::longValue).toArray();
        }
    }

    private final PixelFont font;
    public RegionDetector(PixelFont font) { this.font = font; }

    private boolean is(GbFrame frame, int x, int y, String token) {
        return font.matches(token, frame.tile(x, y));
    }

    private boolean textAt(GbFrame frame, int x, int y, String text) {
        for (int i = 0; i < text.length(); i++, x += 8) {
            if (!font.matches(text.substring(i, i + 1), frame.tile(x, y), frame.tile(x, y - 8))) return false;
        }
        return true;
    }

    public boolean continuationArrow(GbFrame frame, Region region) {
        Grid last = region.rows().getLast();
        return last.arrowPixels() != 0
                && frame.tile(last.x() + (last.columns() - 1) * 8, last.y()) == last.arrowPixels();
    }

    private boolean horizontal(GbFrame frame, int left, int right, int y, String start, String end) {
        if (!is(frame, left, y, start) || !is(frame, right, y, end)) return false;
        for (int x = left + 8; x < right; x += 8) if (!is(frame, x, y, "─")) return false;
        return true;
    }

    private boolean box(GbFrame frame, int right, int top, int bottom) {
        return box(frame, right, top, bottom, false);
    }

    private boolean box(GbFrame frame, int right, int top, int bottom, boolean fourRowPc) {
        if (!horizontal(frame, 0, right, top, "┌", "┐")) return false;
        if (fourRowPc) {
            if (!is(frame, 0, bottom, "└") || !is(frame, right, bottom, "┘")) return false;
            long stroke = font.pixels("─");
            for (int x = 8; x < right; x += 8) {
                // The player sprite touches the last three pixel rows at x=16 and x=24.
                long mask = right == 88 ? 0x0000_ffff_ffff_ffffL : -1L;
                if (x == 16 || x == 24) mask = 0x0000_00ff_ffff_ffffL;
                if ((frame.tile(x, bottom) & mask) != (stroke & mask)) return false;
            }
        } else if (!horizontal(frame, 0, right, bottom, "└", "┘")) return false;
        for (int y = top + 8; y < bottom; y += 8)
            if (!is(frame, 0, y, "│") || !is(frame, right, y, "│")) return false;
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
        for (int count = 3; count <= 5; count++) {
            int bottom = count * 16 + 8;
            if (!box(frame, 112, 0, bottom, count == 4)
                    || !textAt(frame, 16, bottom - 8, "スイッチを きる")) continue;
            List<Grid> rows = new ArrayList<>();
            for (int row = 0; row < count; row++) rows.add(new Grid(16, 16 + row * 16, 12));
            regions.add(new Region("pc_select", "pc_select", "pc_select", new Rect(0, 0, 120, bottom + 8),
                    List.copyOf(rows), List.of(), selected(frame, 8, 16, count)));
            break;
        }

        if (box(frame, 136, 16, 88) && is(frame, 88, 0, "┌") && is(frame, 152, 0, "┐"))
            regions.add(new Region("game_corner_prizes", "game_corner_prizes", "game_corner_prizes",
                    new Rect(0, 16, 144, 80), List.of(new Grid(16, 32, 15), new Grid(16, 48, 15),
                    new Grid(16, 64, 15), new Grid(16, 80, 15)), List.of(), selected(frame, 8, 32, 4)));

        boolean vending = box(frame, 104, 24, 96) && is(frame, 96, 40, "円")
                && is(frame, 96, 56, "円") && is(frame, 96, 72, "円");
        if (vending)
            regions.add(new Region("vending_items", "vending_items", "vending_items", new Rect(0, 24, 112, 80),
                    List.of(new Grid(16, 40, 11), new Grid(16, 56, 11), new Grid(16, 72, 11), new Grid(16, 88, 11)),
                    List.of(), selected(frame, 8, 40, 4)));

        // Five-option Bills PC menu; require the whole border so an overlaid list hides it.
        boolean pcAction = horizontal(frame, 0, 104, 0, "┌", "┐")
                && horizontal(frame, 0, 104, 88, "└", "┘");
        for (int y = 8; y <= 80; y += 8)
            pcAction &= is(frame, 0, y, "│") && is(frame, 104, y, "│");
        if (pcAction) regions.add(new Region("pc_action", "pc_action", "pc_action", new Rect(0, 0, 112, 96),
                List.of(new Grid(16, 16, 11), new Grid(16, 32, 11), new Grid(16, 48, 11),
                        new Grid(16, 64, 11), new Grid(16, 80, 11)), List.of(), selected(frame, 8, 16, 5)));

        // Player's item PC, with a smaller four-row frame than Bill's PC.
        if (box(frame, 88, 0, 72, true)) regions.add(new Region("pc_item_action", "pc_item_action", "pc_item_action",
                new Rect(0, 0, 96, 80), List.of(new Grid(16, 16, 9), new Grid(16, 32, 9),
                new Grid(16, 48, 9), new Grid(16, 64, 9)), List.of(), selected(frame, 8, 16, 4)));

        boolean listTop = horizontal(frame, 32, 152, 16, "┌", "┐");
        // Shop buying uses the same grid, with a price ending in the currency column.
        boolean prices = false;
        for (int y = 32; y <= 80; y += 16) prices |= is(frame, 144, y, "円");
        boolean itemQuantityBox = listTop && !prices
                && horizontal(frame, 120, 152, 72, "┌", "┐")
                && horizontal(frame, 120, 152, 88, "└", "┘")
                && is(frame, 120, 80, "│") && is(frame, 152, 80, "│");
        boolean list = listTop && horizontal(frame, 32, 152, 96, "└", "┘");
        // A full-width message may replace the item list's bottom border.
        if (listTop && !list && !prices && horizontal(frame, 0, 152, 96, "┌", "┐")) {
            list = true;
            for (int y = 24; list && y <= 88; y += 8) {
                list = is(frame, 32, y, "│") && ((itemQuantityBox && y >= 72) || is(frame, 152, y, "│"));
            }
        }
        if (list) {
            List<Grid> rows = new ArrayList<>(List.of(new Grid(48, 32, 13), new Grid(48, 48, 13),
                    new Grid(48, 64, 13), new Grid(48, 80, 13)));
            // The buying quantity/total box covers the fourth item's text and mark band.
            if (prices && horizontal(frame, 56, 152, 72, "┌", "┐")
                    && horizontal(frame, 56, 152, 88, "└", "┘")) rows.removeLast();
            // The unpriced quantity box starts at the fourth row's quantity column.
            if (itemQuantityBox) rows.set(3, new Grid(48, 80, 9));
            List<Grid> footer = horizontal(frame, 96, 152, 120, "└", "┘")
                    && is(frame, 96, 112, "│") ? List.of(new Grid(112, 112, 5)) : List.of();
            boolean levels = rows.stream().anyMatch(row -> is(frame, 96, row.y(), "<Lv>"));
            regions.add(new Region("list", prices ? "shop_items" : "shared_list",
                    prices ? "shop_items" : levels ? "pc_pokemon" : "ambiguous",
                    new Rect(32, 16, 128, 88), List.copyOf(rows), footer, selected(frame, 40, 32, rows.size())));
        }
        boolean moves = horizontal(frame, 0, 72, 64, "┌", "┐") && is(frame, 0, 136, "└");
        // A new message can replace the lower half, including the left border at y=96.
        for (int y = 72; moves && y <= 128; y += 8) moves = is(frame, 0, y, "│");
        // The status page shares the outer geometry but writes attributes in the pointer column.
        for (int y = 80; moves && y <= 128; y += 16) {
            long pointer = frame.tile(8, y);
            moves = pointer == 0 || font.matches("▶", pointer) || font.matches("▷", pointer);
        }
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

        // Safari uses the dialogue border, with two fixed option names and its own cursor columns.
        boolean safari = is(frame, 72, 112, "×") && box(frame, 152, 96, 136)
                && textAt(frame, 16, 112, "サファリボール×")
                && textAt(frame, 104, 112, "エサをなげる");
        if (safari) {
            Integer selection = null;
            for (int row = 0; row < 2; row++) for (int col = 0; col < 2; col++)
                if (is(frame, 8 + col * 88, 112 + row * 16, "▶")) selection = row * 2 + col;
            regions.add(new Region("safari_action", "safari_action", "safari_action", new Rect(0, 96, 160, 48),
                    List.of(new Grid(16, 112, 10), new Grid(104, 112, 6),
                            new Grid(16, 128, 6), new Grid(104, 128, 3)), List.of(), selection));
        }

        // The list and PC status box may cover the message's top/bottom right edges.
        // g1-077 has a two-logical-pixel horizontal displacement, including text and frame.
        // Search only this measured local range; GB normalization itself stays unchanged.
        int dialogueX = -1;
        for (int x = 0; !safari && x <= 2; x++) {
            boolean dialogueTop = is(frame, x, 96, "┌") && is(frame, x + 8, 96, "─")
                    && is(frame, x + 16, 96, "─") && is(frame, x + 24, 96, "─");
            if (x == 0 && vending)
                dialogueTop = is(frame, 112, 96, "─") && is(frame, 120, 96, "─")
                        && is(frame, 128, 96, "─") && is(frame, 136, 96, "─")
                        && is(frame, 144, 96, "─") && is(frame, 152, 96, "┐");
            boolean dialogue = dialogueTop && is(frame, x, 136, "└");
            for (int y = 104; y <= 128; y += 8) dialogue &= is(frame, x, y, "│");
            if (dialogue) { dialogueX = x; break; }
        }
        if (dialogueX >= 0) {
            boolean status = horizontal(frame, 72, 152, 112, "┌", "┐");
            int columns = action || status ? 8 : 18;
            // The battle's Yes/No box overwrites the mark tiles above the unvoiced question ending.
            boolean battleConfirmation = dialogueX == 0 && columns == 18
                    && horizontal(frame, 104, 144, 72, "┌", "┐")
                    && horizontal(frame, 104, 144, 104, "└", "┘");
            for (int y = 80; battleConfirmation && y <= 96; y += 8)
                battleConfirmation = is(frame, 104, y, "│") && is(frame, 144, y, "│");
            List<Integer> maskedMarks = battleConfirmation ? List.of(12, 13, 14, 15, 16, 17) : List.of();
            // The last cell can hold either the continuation arrow or real text.
            long arrow = columns == 18 ? font.pixels("▼") : 0;
            regions.add(new Region("dialogue", "dialogue", "dialogue", new Rect(dialogueX, 96, 160 - dialogueX, 48),
                    List.of(new Grid(dialogueX + 8, 112, columns, List.of(), 0, maskedMarks),
                            new Grid(dialogueX + 8, 128, columns, List.of(), arrow)), List.of(), null));
        }
        return List.copyOf(regions);
    }
}
