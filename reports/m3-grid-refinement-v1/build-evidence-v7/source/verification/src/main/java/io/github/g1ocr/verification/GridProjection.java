package io.github.g1ocr.verification;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;

/** Source raster coordinates only. Does not rescale pixels or infer glyph/text content. */
final class GridProjection {
    static int[] rectangle(JsonObject layout, int left, int top, int right, int bottom) {
        if (left < 0 || top < 0 || right <= left || bottom <= top || right > 20 || bottom > 18)
            throw new IllegalArgumentException("Invalid canonical glyph envelope");
        return pixels(layout, left * 8, top * 8, right * 8, bottom * 8);
    }
    static int[] pixels(JsonObject layout, int left, int top, int right, int bottom) {
        if (left < 0 || top < 0 || right <= left || bottom <= top || right > 160 || bottom > 144)
            throw new IllegalArgumentException("Invalid canonical pixel envelope");
        if (layout.has("renderOffsetLogicalPixels")) {
            JsonArray offset=layout.getAsJsonArray("renderOffsetLogicalPixels");
            if(offset.size()!=2)throw new IllegalArgumentException("Invalid raster offset");
            for(int i=0;i<2;i++) if(offset.get(i).getAsDouble()!=offset.get(i).getAsInt())
                throw new IllegalArgumentException("Raster offset must use integer logical pixels");
            left=Math.addExact(left,offset.get(0).getAsInt());right=Math.addExact(right,offset.get(0).getAsInt());
            top=Math.addExact(top,offset.get(1).getAsInt());bottom=Math.addExact(bottom,offset.get(1).getAsInt());
            if(left<0||top<0||right>160||bottom>144)throw new IllegalArgumentException("Translated glyph clipped by game viewport; annotate visible portion separately");
        }
        boolean axis = layout.has("sourceScaleXNumerator") || layout.has("sourceScaleXDenominator")
                || layout.has("sourceScaleYNumerator") || layout.has("sourceScaleYDenominator");
        int xn = positive(layout, axis ? "sourceScaleXNumerator" : "sourceScaleNumerator");
        int xd = positive(layout, axis ? "sourceScaleXDenominator" : "sourceScaleDenominator");
        int yn = positive(layout, axis ? "sourceScaleYNumerator" : "sourceScaleNumerator");
        int yd = positive(layout, axis ? "sourceScaleYDenominator" : "sourceScaleDenominator");
        int ox = layout.getAsJsonArray("sourceOrigin").get(0).getAsInt();
        int oy = layout.getAsJsonArray("sourceOrigin").get(1).getAsInt();
        int[] rect = {project(ox, left, xn, xd, false), project(oy, top, yn, yd, false),
                project(ox, right, xn, xd, true), project(oy, bottom, yn, yd, true)};
        int width = layout.getAsJsonArray("sourceFrameSize").get(0).getAsInt();
        int height = layout.getAsJsonArray("sourceFrameSize").get(1).getAsInt();
        if (rect[0] < 0 || rect[1] < 0 || rect[2] > width || rect[3] > height
                || rect[2] <= rect[0] || rect[3] <= rect[1])
            throw new IllegalArgumentException("Glyph envelope outside original source raster");
        return rect;
    }
    private static int positive(JsonObject object, String field) {
        if (!object.has(field) || object.get(field).getAsInt() <= 0)
            throw new IllegalArgumentException("Missing/invalid rational projection field: " + field);
        return object.get(field).getAsInt();
    }
    private static int project(int origin, int pixel, int numerator, int denominator, boolean end) {
        long scaled = Math.multiplyExact((long) pixel, numerator);
        long ticks = scaled / denominator;
        if (end && scaled % denominator != 0) ticks = Math.addExact(ticks, 1);
        long value = Math.addExact(origin, ticks);
        return Math.toIntExact(value);
    }
}
