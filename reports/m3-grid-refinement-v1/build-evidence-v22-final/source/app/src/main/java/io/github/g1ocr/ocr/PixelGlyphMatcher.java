package io.github.g1ocr.ocr;

import java.util.*;

/** Image-only glyph candidate ranking. Ties and shared kana shapes are retained. */
public final class PixelGlyphMatcher {
    private final List<GlyphTemplate> templates;
    public PixelGlyphMatcher(List<GlyphTemplate> templates) {
        if(templates==null||templates.isEmpty()||templates.contains(null))throw new IllegalArgumentException("Nonempty glyph bank required");
        this.templates=Collections.unmodifiableList(new ArrayList<>(templates));
    }
    public List<Candidate> rank(long observedInk,int maximumDistance) {
        return rank(observedInk,-1L,maximumDistance);
    }
    public List<Candidate> rank(long observedInk,long knownPixelMask,int maximumDistance) {
        if(maximumDistance<0||maximumDistance>64)throw new IllegalArgumentException("Glyph distance must be 0..64");
        if(Long.bitCount(knownPixelMask)<48)throw new IllegalArgumentException("Insufficient observed glyph pixels");
        List<Candidate> result=new ArrayList<>();
        for(GlyphTemplate glyph:templates){int distance=Long.bitCount((glyph.getInk()^observedInk)&knownPixelMask);if(distance<=maximumDistance)result.add(new Candidate(glyph,distance));}
        result.sort(Comparator.comparingInt(Candidate::getDistance).thenComparing(c->c.glyph.getSourceId()).thenComparing(c->c.glyph.getProfile()).thenComparingInt(c->c.glyph.getTile()));
        return Collections.unmodifiableList(result);
    }
    public static final class Candidate {
        private final GlyphTemplate glyph;private final int distance;
        private Candidate(GlyphTemplate glyph,int distance){this.glyph=glyph;this.distance=distance;}
        public GlyphTemplate getGlyph(){return glyph;}
        public int getDistance(){return distance;}
    }
}
