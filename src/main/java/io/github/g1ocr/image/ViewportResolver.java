package io.github.g1ocr.image;

import io.github.g1ocr.capture.WindowCapture.Bounds;
import io.github.g1ocr.ocr.PixelFont;
import io.github.g1ocr.region.RegionDetector;
import io.github.g1ocr.region.StabilityDetector;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Upper-screen-only profile. Native client rectangles are hints, never trusted calibration. */
public final class ViewportResolver {
    public enum Status { WAITING, AMBIGUOUS, CALIBRATING, READY }
    public record Resolution(Status status, String detail, GbFrame.Mapping mapping, GbFrame frame, int candidates) {}
    private final RegionDetector detector;
    private final long dakuten;
    private final long handakuten;
    private GbFrame.Mapping locked;
    private GbFrame.Mapping pending;
    private int count;
    private long nextObservation;
    private int width;
    private int height;

    public ViewportResolver(PixelFont font) {
        detector = new RegionDetector(font);
        dakuten = font.pixels("゛");
        handakuten = font.pixels("゜");
    }

    public void reset() { locked = null; pending = null; count = 0; nextObservation = 0; width = 0; height = 0; }

    private boolean geometryEvidence(GbFrame frame) {
        var regions = detector.detect(frame);
        if (regions.isEmpty()) return false;
        for (var region : regions) {
            for (var row : java.util.stream.Stream.concat(region.rows().stream(), region.footerRows().stream()).toList()) {
                for (int col = 0; col < row.columns(); col++) {
                    if (row.maskedColumns().contains(col)) continue;
                    long mark = frame.tile(row.x() + col * 8, row.y() - 8);
                    if (mark != 0 && mark != dakuten && mark != handakuten) return false;
                }
            }
        }
        return true;
    }

    private Set<GbFrame.Mapping> candidates(BufferedImage image, List<Bounds> hints) {
        List<Bounds> bounds = new ArrayList<>(hints);
        bounds.add(new Bounds(0, 0, image.getWidth(), image.getHeight()));
        Set<GbFrame.Mapping> result = new LinkedHashSet<>();
        for (Bounds rect : bounds) {
            if (rect.x() < 0 || rect.y() < 0 || rect.x() + rect.width() > image.getWidth()
                    || rect.y() + rect.height() > image.getHeight()) continue;
            int maxScale = Math.min(rect.width() / 400, rect.height() / 240);
            for (int k = 1; k <= maxScale; k++) {
                int spareX = rect.width() - 400 * k, spareY = rect.height() - 240 * k;
                // Only edge/center alignments of one upper screen, not arbitrary pixel scanning.
                for (int dx : new int[]{0, spareX / 2, spareX}) for (int dy : new int[]{0, spareY / 2, spareY})
                    result.add(new GbFrame.Mapping(rect.x() + dx, rect.y() + dy, k));
            }
        }
        return result;
    }

    public Resolution resolve(BufferedImage image, List<Bounds> hints, long observationNanos) {
        if (width != image.getWidth() || height != image.getHeight()) {
            reset(); width = image.getWidth(); height = image.getHeight();
        }
        GbFrame existing = locked == null ? null : GbFrame.normalize(image, locked);
        if (existing != null && !detector.detect(existing).isEmpty())
            return new Resolution(Status.READY, "已校准", locked, existing, 1);
        List<GbFrame.Mapping> matches = new ArrayList<>();
        for (GbFrame.Mapping mapping : candidates(image, hints))
            if (geometryEvidence(GbFrame.normalize(image, mapping))) matches.add(mapping);
        if (matches.isEmpty()) {
            pending = null; count = 0;
            if (existing != null) return new Resolution(Status.READY, "已校准，当前无目标框", locked, existing, 0);
            return new Resolution(Status.WAITING, "等待校准：请显示对话框或目标列表，并保持上屏整数倍输出", null, null, 0);
        }
        locked = null;
        if (matches.size() != 1) {
            pending = null; count = 0;
            return new Resolution(Status.AMBIGUOUS, "校准候选不唯一，识别已暂停；请检查仅上屏和整数倍率设置", null, null, matches.size());
        }
        GbFrame.Mapping candidate = matches.getFirst();
        if (!candidate.equals(pending)) { pending = candidate; count = 0; nextObservation = 0; }
        if (observationNanos >= nextObservation) {
            count++; nextObservation = observationNanos + StabilityDetector.INTERVAL_NANOS;
        }
        if (count < 3) return new Resolution(Status.CALIBRATING, "正在确认上屏映射（" + count + "/3）", candidate, null, 1);
        locked = candidate;
        return new Resolution(Status.READY, "已校准", locked, GbFrame.normalize(image, locked), 1);
    }
}
