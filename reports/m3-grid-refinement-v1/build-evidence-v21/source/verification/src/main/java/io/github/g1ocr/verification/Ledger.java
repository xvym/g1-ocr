package io.github.g1ocr.verification;

import java.util.ArrayList;
import java.util.List;

/** Timestamp checks use integer source ticks, never a guessed frame rate. */
final class Ledger {
    static List<String> timeErrors(Long pts, Long duration, Long previousEnd) {
        List<String> errors = new ArrayList<>();
        if (pts == null) errors.add("missing_pts");
        if (duration == null || duration <= 0) errors.add("missing_or_invalid_duration");
        if (pts != null && previousEnd != null && !pts.equals(previousEnd)) errors.add("timestamp_gap_or_overlap");
        if (pts != null && duration != null && duration > 0) {
            try { Math.addExact(pts, duration); } catch (ArithmeticException e) { errors.add("timestamp_overflow"); }
        }
        return errors;
    }
    static String[] hashFields(String line) {
        String[] fields = line.split(",");
        if (fields.length != 6) throw new IllegalArgumentException("Malformed framehash row: " + line);
        for (int i = 0; i < fields.length; i++) fields[i] = fields[i].trim();
        if (!fields[5].matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid RGB hash");
        return fields;
    }
}
