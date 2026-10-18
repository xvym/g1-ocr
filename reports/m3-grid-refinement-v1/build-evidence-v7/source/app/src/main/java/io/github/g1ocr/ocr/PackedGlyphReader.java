package io.github.g1ocr.ocr;

import io.github.g1ocr.capture.SourceFrame;
import java.util.*;

/** Source DrawHPBar places tile 0x71 then 0x62. Only the printed lower 12x4 pixels are text. */
public final class PackedGlyphReader {
    private final Set<Long> hpMasks=new LinkedHashSet<>();
    public PackedGlyphReader(List<GlyphTemplate> bank) {
        Map<String,Map<Integer,Long>> bySource=new LinkedHashMap<>();
        for(GlyphTemplate glyph:bank)if("battle-extra".equals(glyph.getProfile()))
            bySource.computeIfAbsent(glyph.getSourceId(),k->new HashMap<>()).put(glyph.getTile(),glyph.getInk());
        for(Map<Integer,Long> tiles:bySource.values())if(tiles.containsKey(0x71)&&tiles.containsKey(0x62)) {
            long mask=0;
            for(int y=0;y<4;y++)for(int x=0;x<12;x++) {
                long tile=tiles.get(x<8?0x71:0x62);int bit=(y+4)*8+(x<8?x:x-8);
                if((tile&(1L<<bit))!=0)mask|=1L<<(y*12+x);
            }
            hpMasks.add(mask);
        }
    }
    public boolean hp(SourceFrame frame,PixelGlyphReader.Grid grid,int logicalX,int logicalY) {
        if(hpMasks.isEmpty())return false;
        byte[] rgb=frame.copyRgb();int[] values=new int[48],hist=new int[256];int lo=255,hi=0;
        for(int y=0;y<4;y++)for(int x=0;x<12;x++) {
            int sx=(int)Math.floor(grid.originX+(logicalX+x+.5)*grid.scaleX),sy=(int)Math.floor(grid.originY+(logicalY+y+.5)*grid.scaleY);
            if(sx<0||sy<0||sx>=frame.getWidth()||sy>=frame.getHeight())return false;
            int at=(sy*frame.getWidth()+sx)*3,v=((rgb[at]&255)+2*(rgb[at+1]&255)+(rgb[at+2]&255))/4;
            values[y*12+x]=v;hist[v]++;lo=Math.min(lo,v);hi=Math.max(hi,v);
        }
        if(hi-lo<30)return false;int threshold=PixelGlyphReader.otsu(hist);long mask=0;
        for(int i=0;i<48;i++)if(values[i]<=threshold)mask|=1L<<i;
        for(long expected:hpMasks)if(Long.bitCount(expected^mask)<=3)return true;
        return false;
    }
}
