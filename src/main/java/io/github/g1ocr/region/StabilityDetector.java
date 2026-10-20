package io.github.g1ocr.region;

import io.github.g1ocr.image.GbFrame;
import io.github.g1ocr.region.RegionDetector.Region;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Called with the latest healthy image and a monotonic sampling timestamp. */
public final class StabilityDetector {
    public static final long INTERVAL_NANOS = 100_000_000L;
    public record Key(String session, GbFrame.Mapping mapping, String regionId, long generation) {}
    public record Request(Key key, long sampleTimeNanos, GbFrame frame, Region region) {}
    private static final class State {
        Region region;
        long[] pixels;
        Key key;
        int count = 1;
        boolean requested;
        State(Region region, long[] pixels, Key key) { this.region = region; this.pixels = pixels; this.key = key; }
    }
    private final RegionDetector detector;
    private final Map<String, State> states = new HashMap<>();
    private String session;
    private GbFrame.Mapping mapping;
    private long generation;
    private Long nextSample;

    public StabilityDetector(RegionDetector detector) { this.detector = detector; }

    /** Capture failure, disappearance of the input, or minimization. Old requests expire. */
    public void invalidate() { states.clear(); nextSample = null; }

    public boolean isCurrent(Key key) {
        State state = states.get(key.regionId());
        return state != null && state.key.equals(key);
    }

    public List<Request> sample(String newSession, GbFrame.Mapping newMapping, long timeNanos, GbFrame frame) {
        if (!Objects.equals(session, newSession) || !Objects.equals(mapping, newMapping)) {
            invalidate();
            session = newSession;
            mapping = newMapping;
        }
        if (nextSample != null && timeNanos < nextSample) return List.of();
        nextSample = timeNanos + INTERVAL_NANOS; // Delayed calls never replay missed ticks.
        List<Request> requests = new ArrayList<>();
        var visible = new HashSet<String>();
        for (Region region : detector.detect(frame)) {
            long[] pixels = region.textPixels(frame);
            if (Arrays.stream(pixels).allMatch(p -> p == 0)) continue;
            visible.add(region.id());
            State state = states.get(region.id());
            if (state == null || !state.region.rows().equals(region.rows())
                    || !state.region.footerRows().equals(region.footerRows()) || !Arrays.equals(state.pixels, pixels)) {
                state = new State(region, pixels, new Key(session, mapping, region.id(), ++generation));
                states.put(region.id(), state);
            } else state.count++;
            if (state.count >= 3 && !state.requested) {
                requests.add(new Request(state.key, timeNanos, frame, region));
                state.requested = true;
            }
        }
        states.keySet().retainAll(visible);
        return List.copyOf(requests);
    }
}
