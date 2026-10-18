package io.github.g1ocr.ocr;

import io.github.g1ocr.capture.SourceFrame;
import java.text.Normalizer;
import java.util.*;

/** Raw 8x8 image glyphs. This class does not classify scenes, complete words or consult scripts. */
public final class PixelGlyphReader {
    public static final class Grid {
        public final double originX,originY,scaleX,scaleY;
        public Grid(double x,double y,double sx,double sy) {
            if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(sx)||!Double.isFinite(sy)||sx<1||sy<1||sx>32||sy>32)
                throw new IllegalArgumentException("Invalid sampling grid");
            originX=x;originY=y;scaleX=sx;scaleY=sy;
        }
    }
    public static final class Cell {
        public final int column,row,contrast,distance,threshold;
        public final boolean valid,blank;
        public final long ink;
        public final long knownPixelMask;
        private final int[] sourceRect;
        public final List<String> candidates;
        public final List<PixelGlyphMatcher.Candidate> sourceCandidates;
        private Cell(int col,int row,int contrast,int distance,int threshold,boolean valid,boolean blank,long ink,long knownMask,int[] rect,List<String> chars,List<PixelGlyphMatcher.Candidate> sources) {
            column=col;this.row=row;this.contrast=contrast;this.distance=distance;this.threshold=threshold;this.valid=valid;this.blank=blank;this.ink=ink;
            sourceRect=rect.clone();candidates=Collections.unmodifiableList(new ArrayList<>(chars));sourceCandidates=Collections.unmodifiableList(new ArrayList<>(sources));
            knownPixelMask=knownMask;
        }
        public int[] copySourceRect(){return sourceRect.clone();}
    }
    public static final class Frame {
        public final long pts;
        public final Grid grid;
        private final List<Cell> cells;
        private Frame(long pts,Grid grid,List<Cell> cells){this.pts=pts;this.grid=grid;this.cells=Collections.unmodifiableList(cells);}
        public List<Cell> cells(){return cells;}
        public Cell cell(int column,int row){if(column<0||column>=20||row<0||row>=18)throw new IllegalArgumentException("Cell outside grid");return cells.get(row*20+column);}
        public KanaScriptResolver.Result contextualRow(int row) {
            List<List<String>> observed=new ArrayList<>();int[] distance=new int[20];
            for(int col=0;col<20;col++){observed.add(literals(col,row));distance[col]=cell(col,row).distance;}
            return new KanaScriptResolver().resolve(observed,distance);
        }
        /** Diacritics are read from this frame's preceding row only. All ambiguous aliases remain. */
        public List<String> literals(int column,int row) {
            Cell base=cell(column,row);if(!base.valid)return Collections.emptyList();
            if(row==0||base.blank)return base.candidates;
            Cell above=cell(column,row-1);String mark=above.candidates.size()==1?above.candidates.get(0):"";
            String combining="゛".equals(mark)?"\u3099":"゜".equals(mark)?"\u309a":"";
            if(combining.isEmpty())return base.candidates;
            LinkedHashSet<String> out=new LinkedHashSet<>();
            for(String text:base.candidates) {
                String combined=Normalizer.normalize(text+combining,Normalizer.Form.NFC);
                // Only actual single-character kana compositions; no detached mark is invented or discarded.
                out.add(combined.codePointCount(0,combined.length())==1?combined:text);
            }
            return Collections.unmodifiableList(new ArrayList<>(out));
        }
    }
    private final PixelGlyphMatcher matcher;
    private final int maxDistance,minContrast;
    public PixelGlyphReader(List<GlyphTemplate> glyphs){this(glyphs,5,30);}
    public PixelGlyphReader(List<GlyphTemplate> glyphs,int maxDistance,int minContrast) {
        if(maxDistance<0||maxDistance>16||minContrast<1||minContrast>255)throw new IllegalArgumentException("Invalid glyph reader configuration");
        matcher=new PixelGlyphMatcher(glyphs);this.maxDistance=maxDistance;this.minContrast=minContrast;
    }
    public Frame read(SourceFrame frame,Grid grid) {
        Objects.requireNonNull(frame);Objects.requireNonNull(grid);byte[] rgb=frame.copyRgb();int w=frame.getWidth(),h=frame.getHeight();
        List<Cell> cells=new ArrayList<>();
        boolean topCaptureLine=captureTopLine(rgb,w,h,grid);
        for(int row=0;row<18;row++)for(int col=0;col<20;col++) {
            int[] rect={(int)Math.floor(grid.originX+col*8*grid.scaleX+1e-7),(int)Math.floor(grid.originY+row*8*grid.scaleY+1e-7),
                    (int)Math.ceil(grid.originX+(col+1)*8*grid.scaleX-1e-7),(int)Math.ceil(grid.originY+(row+1)*8*grid.scaleY-1e-7)};
            int[] values=new int[64],hist=new int[256];boolean valid=true;int lo=255,hi=0;
            for(int y=0;y<8;y++)for(int x=0;x<8;x++) {
                int sx=(int)Math.floor(grid.originX+(col*8+x+.5)*grid.scaleX),sy=(int)Math.floor(grid.originY+(row*8+y+.5)*grid.scaleY);
                if(sx<0||sx>=w||sy<0||sy>=h){valid=false;continue;}
                int at=(sy*w+sx)*3,v=((rgb[at]&255)+2*(rgb[at+1]&255)+(rgb[at+2]&255))/4;
                values[y*8+x]=v;hist[v]++;lo=Math.min(lo,v);hi=Math.max(hi,v);
            }
            int contrast=hi-lo;
            if(!valid){cells.add(new Cell(col,row,0,-1,0,false,false,0,0,rect,Collections.emptyList(),Collections.emptyList()));continue;}
            if(contrast<minContrast){cells.add(new Cell(col,row,contrast,0,lo,true,true,0,-1L,rect,Collections.singletonList(" "),Collections.emptyList()));continue;}
            int cut=otsu(hist);long ink=0;for(int p=0;p<64;p++)if(values[p]<=cut)ink|=1L<<p;
            long knownMask=row==0&&topCaptureLine?~255L:-1L;
            List<PixelGlyphMatcher.Candidate> ranked=matcher.rank(ink,knownMask,maxDistance),best=new ArrayList<>();LinkedHashSet<String> aliases=new LinkedHashSet<>();
            int distance=ranked.isEmpty()?-1:ranked.get(0).getDistance();
            for(PixelGlyphMatcher.Candidate c:ranked) {
                if(c.getDistance()!=distance)break;best.add(c);for(String alias:c.getGlyph().getAliases())aliases.add(displayLiteral(alias));
            }
            cells.add(new Cell(col,row,contrast,distance,cut,true,false,ink,knownMask,rect,new ArrayList<>(aliases),best));
        }
        return new Frame(frame.getPts(),grid,cells);
    }
    /** A uniform black line at the physical capture edge is not a row of font strokes. Keep its pixels as uncertain. */
    private static boolean captureTopLine(byte[] rgb,int w,int h,Grid grid) {
        if(grid.originY<-2||grid.originY>2)return false;int y=(int)Math.floor(grid.originY+.5*grid.scaleY);
        if(y<0||y>2||y>=h)return false;int valid=0,dark=0;
        for(int x=1;x<159;x++){int sx=(int)Math.floor(grid.originX+(x+.5)*grid.scaleX);if(sx<0||sx>=w)continue;valid++;int p=(y*w+sx)*3;if(((rgb[p]&255)+2*(rgb[p+1]&255)+(rgb[p+2]&255))/4<=32)dark++;}
        return valid>=150&&dark>=valid*.98;
    }
    static int otsu(int[] hist) {
        long total=0,sum=0;for(int i=0;i<256;i++){total+=hist[i];sum+=(long)i*hist[i];}
        long left=0,leftSum=0;double best=-1;int threshold=0;
        for(int i=0;i<255;i++){left+=hist[i];leftSum+=(long)i*hist[i];if(left==0||left==total)continue;
            double diff=leftSum/(double)left-(sum-leftSum)/(double)(total-left),score=left*(double)(total-left)*diff*diff;
            if(score>best){best=score;threshold=i;}
        }
        return threshold;
    }
    /** Source encodes Latin/digits as fullwidth; the pre-frozen display contract uses ASCII for those glyphs. */
    static String displayLiteral(String value) {
        if("<LV>".equals(value))return ":L";
        if("<ID>".equals(value))return "ID";
        if("№".equals(value))return "No";
        StringBuilder out=new StringBuilder();value.codePoints().forEach(c->{
            if(c>=0xff10&&c<=0xff19||c>=0xff21&&c<=0xff3a||c>=0xff41&&c<=0xff5a)out.appendCodePoint(c-0xfee0);
            else if(c==0xff0f)out.append('/');
            else if(c==0xff1f)out.append('?');
            else if(c==0x22ef)out.appendCodePoint(0x2026);
            else if(c==0xb7)out.appendCodePoint(0x30fb);
            else if(c==0xff0e)out.append('.');
            else out.appendCodePoint(c==0x3000?' ':c);
        });return out.toString();
    }
}
