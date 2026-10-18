package io.github.g1ocr.capture;

import java.math.BigInteger;

/** Normal pipeline input: RGB and source time only; no clip ID, split or answers. */
public final class SourceFrame {
    private final byte[] rgb;
    private final int width, height;
    private final long pts, timeBaseNumerator, timeBaseDenominator;

    public SourceFrame(byte[] rgb, int width, int height, long pts, long numerator, long denominator) {
        if (rgb == null || width <= 0 || height <= 0 || (long) width * height * 3 != rgb.length)
            throw new IllegalArgumentException("RGB24 length must equal width * height * 3");
        if (numerator <= 0 || denominator <= 0) throw new IllegalArgumentException("Invalid time base");
        this.rgb = rgb.clone(); this.width = width; this.height = height;
        this.pts = pts; this.timeBaseNumerator = numerator; this.timeBaseDenominator = denominator;
    }
    public byte[] copyRgb() { return rgb.clone(); }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public long getPts() { return pts; }
    public long getTimeBaseNumerator() { return timeBaseNumerator; }
    public long getTimeBaseDenominator() { return timeBaseDenominator; }
    public BigInteger sourceNanoseconds() {
        return BigInteger.valueOf(pts).multiply(BigInteger.valueOf(timeBaseNumerator))
                .multiply(BigInteger.valueOf(1000000000L)).divide(BigInteger.valueOf(timeBaseDenominator));
    }
}
