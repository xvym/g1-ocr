package io.github.g1ocr;

import io.github.g1ocr.image.GbFrame;
import io.github.g1ocr.ocr.PixelFont;
import io.github.g1ocr.ocr.PixelOcr;
import io.github.g1ocr.region.RegionDetector;
import io.github.g1ocr.region.StabilityDetector;
import java.io.IOException;
import java.util.List;

/** Shared offline/realtime entry after geometric normalization. No text dictionary. */
public final class OcrPipeline {
    private final RegionDetector detector;
    private final PixelOcr ocr;
    private final StabilityDetector stability;
    public record StableResult(StabilityDetector.Key key, long sampleTimeNanos, PixelOcr.Result result) {}

    public OcrPipeline() throws IOException {
        PixelFont font = new PixelFont();
        detector = new RegionDetector(font);
        ocr = new PixelOcr(font);
        stability = new StabilityDetector(detector);
    }

    public List<PixelOcr.Result> recognize(GbFrame frame) {
        return detector.detect(frame).stream().map(region -> ocr.read(frame, region))
                .filter(PixelOcr.Result::hasText).toList();
    }

    public List<StableResult> sample(String session, GbFrame.Mapping mapping, long sampleTimeNanos, GbFrame frame) {
        return stability.sample(session, mapping, sampleTimeNanos, frame).stream()
                .map(request -> new StableResult(request.key(), request.sampleTimeNanos(), ocr.read(request.frame(), request.region())))
                .toList();
    }

    public boolean isCurrent(StabilityDetector.Key key) { return stability.isCurrent(key); }
    public void invalidate() { stability.invalidate(); }
}
