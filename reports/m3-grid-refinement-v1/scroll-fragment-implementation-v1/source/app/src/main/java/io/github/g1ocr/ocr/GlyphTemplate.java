package io.github.g1ocr.ocr;

import java.text.Normalizer;
import java.util.*;

/** One 8x8 source glyph. Bits are row-major, bit (8*y+x) is dark ink. */
public final class GlyphTemplate {
    private final String sourceId,profile;
    private final int tile;
    private final long ink;
    private final List<String> aliases;
    public GlyphTemplate(String sourceId,String profile,int tile,long ink,List<String> aliases) {
        if(sourceId==null||sourceId.isEmpty()||profile==null||profile.isEmpty()||tile<0||tile>255||aliases==null)throw new IllegalArgumentException("Invalid glyph identity");
        LinkedHashSet<String> unique=new LinkedHashSet<>();
        for(String alias:aliases)if(alias==null||alias.isEmpty()||!Normalizer.isNormalized(alias,Normalizer.Form.NFC)||!unique.add(alias))throw new IllegalArgumentException("Invalid/duplicate source glyph alias");
        this.sourceId=sourceId;this.profile=profile;this.tile=tile;this.ink=ink;this.aliases=Collections.unmodifiableList(new ArrayList<>(unique));
    }
    public String getSourceId(){return sourceId;}
    public String getProfile(){return profile;}
    public int getTile(){return tile;}
    public long getInk(){return ink;}
    public List<String> getAliases(){return aliases;}
    public int distance(long observedInk){return Long.bitCount(ink^observedInk);}
}
