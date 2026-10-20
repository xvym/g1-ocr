package io.github.g1ocr.image;

import java.awt.image.BufferedImage;

/** Logical GB pixels. Thresholding is fixed per frame; no interpolation or denoising. */
public final class GbFrame {
    public record Mapping(int ox, int oy, int scale) {
        public Mapping {
            if (ox < 0 || oy < 0 || scale < 1) throw new IllegalArgumentException("Invalid upper-screen mapping");
        }
    }

    private final BufferedImage image;
    private final boolean[] ink = new boolean[160 * 144];
    private final int threshold;

    private GbFrame(BufferedImage image) {
        this.image = image;
        int min = 255, max = 0;
        int[] luminance = new int[ink.length];
        for (int y = 0; y < 144; y++) for (int x = 0; x < 160; x++) {
            int rgb = image.getRGB(x, y);
            int value = (((rgb >> 16) & 255) * 299 + ((rgb >> 8) & 255) * 587 + (rgb & 255) * 114) / 1000;
            luminance[y * 160 + x] = value;
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        threshold = (min + max) / 2;
        for (int i = 0; i < ink.length; i++) ink[i] = luminance[i] < threshold;
    }

    public static GbFrame normalize(BufferedImage source, Mapping mapping) {
        int k = mapping.scale();
        if (mapping.ox() + 400 * k > source.getWidth() || mapping.oy() + 240 * k > source.getHeight())
            throw new IllegalArgumentException("Upper screen lies outside input image");
        BufferedImage gb = new BufferedImage(160, 144, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 144; y++) for (int x = 0; x < 160; x++)
            gb.setRGB(x, y, source.getRGB(mapping.ox() + (120 + x) * k + k / 2,
                    mapping.oy() + (48 + y) * k + k / 2));
        return new GbFrame(gb);
    }

    public BufferedImage image() { return image; }
    public int threshold() { return threshold; }

    public long tile(int x, int y) {
        long bits = 0;
        for (int dy = 0; dy < 8; dy++) for (int dx = 0; dx < 8; dx++)
            if (ink[(y + dy) * 160 + x + dx]) bits |= 1L << (dy * 8 + dx);
        return bits;
    }
}
