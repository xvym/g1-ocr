package io.github.g1ocr.ocr;

import io.github.g1ocr.capture.SourceFrame;
import java.util.*;

/** Bounded image registration for a whole raster shake. Never stores or returns previous text. */
public final class RasterMotionTracker {
    private static final int[] NEIGHBORS={0,1,160};
    public static final class Alignment {
        public final PixelGlyphReader.Grid grid;
        public final int offsetLogicalX,offsetLogicalY,featureCount;
        public final double meanError,runnerUpError;
        public final boolean matched;
        public final String reason;
        private Alignment(PixelGlyphReader.Grid grid,int x,int y,int count,double error,double second,boolean matched,String reason) {
            this.grid=grid;offsetLogicalX=x;offsetLogicalY=y;featureCount=count;meanError=error;runnerUpError=second;this.matched=matched;this.reason=reason;
        }
    }
    private int[] previous;private int previousWidth,previousHeight;private double previousTime;
    public void reset(){previous=null;}
    public Alignment align(SourceFrame source,PixelGlyphReader.Grid base) {
        byte[] rgb=source.copyRgb();double time=source.getPts()*(double)source.getTimeBaseNumerator()/source.getTimeBaseDenominator();
        boolean history=previous!=null&&previousWidth==source.getWidth()&&previousHeight==source.getHeight()&&time>previousTime&&time-previousTime<1;
        PixelGlyphReader.Grid chosen=base;int bestX=0,bestY=0;double best=0,second=0;int count=0;boolean matched=false;String reason="new_image_history";
        if(history) {
            List<Integer> points=new ArrayList<>();
            for(int y=3;y<140;y++)for(int x=3;x<156;x++){int p=y*160+x;if(previous[p]>=0&&previous[p+1]>=0&&previous[p+160]>=0&&(Math.abs(previous[p]-previous[p+1])>=24||Math.abs(previous[p]-previous[p+160])>=24))points.add(p);}
            count=points.size();best=Double.POSITIVE_INFINITY;second=Double.POSITIVE_INFINITY;
            if(count>=32) {
                int stride=Math.max(1,(points.size()+2047)/2048);
                for(int dy=-3;dy<=3;dy++)for(int dx=-3;dx<=3;dx++) {
                    double sum=0;int used=0;
                    for(int n=0;n<points.size();n+=stride) {
                        int p=points.get(n),x=p%160,y=p/160;
                        for(int neighbor:NEIGHBORS) {
                            int px=x+(neighbor==1?1:0),py=y+(neighbor==160?1:0),current=sample(rgb,source,base,px+dx,py+dy);
                            if(current<0)continue;sum+=Math.min(80,Math.abs(current-previous[p+neighbor]));used++;
                        }
                    }
                    if(used<points.size()/(double)stride*2.7)continue;double error=sum/used;
                    if(error<best){second=best;best=error;bestX=dx;bestY=dy;}else if(error<second)second=error;
                }
                if(best<20&&second-best>=2) {
                    matched=true;reason="current_pixels_match_previous_raster_edges";
                    chosen=new PixelGlyphReader.Grid(base.originX+bestX*base.scaleX,base.originY+bestY*base.scaleY,base.scaleX,base.scaleY);
                } else {bestX=0;bestY=0;reason="no_unambiguous_image_registration";}
            } else {best=0;second=0;reason="insufficient_previous_edges";}
        }
        previous=new int[160*144];for(int y=0;y<144;y++)for(int x=0;x<160;x++)previous[y*160+x]=sample(rgb,source,chosen,x,y);
        previousWidth=source.getWidth();previousHeight=source.getHeight();previousTime=time;
        if(!Double.isFinite(best))best=-1;if(!Double.isFinite(second))second=-1;
        return new Alignment(chosen,bestX,bestY,count,best,second,matched,reason);
    }
    private static int sample(byte[] rgb,SourceFrame source,PixelGlyphReader.Grid grid,int x,int y) {
        int sx=(int)Math.floor(grid.originX+(x+.5)*grid.scaleX),sy=(int)Math.floor(grid.originY+(y+.5)*grid.scaleY);
        if(sx<0||sy<0||sx>=source.getWidth()||sy>=source.getHeight())return -1;
        int at=(sy*source.getWidth()+sx)*3;return ((rgb[at]&255)+2*(rgb[at+1]&255)+(rgb[at+2]&255))/4;
    }
}
