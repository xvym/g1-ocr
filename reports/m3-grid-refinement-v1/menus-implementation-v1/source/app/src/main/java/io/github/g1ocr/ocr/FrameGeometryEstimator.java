package io.github.g1ocr.ocr;

import io.github.g1ocr.capture.SourceFrame;
import java.util.*;

/** Normal-image geometry orchestration. A dark-surround fallback uses no file or session identity. */
public final class FrameGeometryEstimator {
    public static final class Estimate {
        public final boolean located;
        public final String method;
        public final PixelGlyphReader.Grid grid;
        public final RasterGridRefiner.Result hypotheses;
        private final int[] envelope;
        private Estimate(String method,int[] envelope,PixelGlyphReader.Grid grid,RasterGridRefiner.Result h) {
            this.method=method;this.envelope=envelope.clone();this.grid=grid;hypotheses=h;located=grid!=null;
        }
        public int[] copyEnvelope(){return envelope.clone();}
    }
    public Estimate estimate(SourceFrame frame) {
        GameViewportLocator.Result coarse=new GameViewportLocator().locate(frame);
        if(coarse.status==GameViewportLocator.Status.NO_SIGNAL)return new Estimate("no_signal",new int[0],null,null);
        int[] envelope=coarse.copyEnvelope();double ox=coarse.originX,oy=coarse.originY;
        String method="palette_and_phase";
        if(coarse.status!=GameViewportLocator.Status.LOCATED) {
            envelope=darkSurround(frame);
            if(envelope.length==0)return new Estimate("no_supported_grid",envelope,null,null);
            ox=envelope[0];oy=envelope[1];method="dark_surround_and_phase";
        }
        RasterGridRefiner.Result fit=new RasterGridRefiner().refine(frame,envelope,ox,oy);
        if(!fit.available) {
            if(coarse.status==GameViewportLocator.Status.LOCATED)return new Estimate("palette_coarse_only",envelope,new PixelGlyphReader.Grid(coarse.originX,coarse.originY,coarse.scaleX,coarse.scaleY),fit);
            return new Estimate("insufficient_lattice",envelope,null,fit);
        }
        RasterGridRefiner.Axis x=fit.xCandidates.get(0),y=fit.yCandidates.get(0);
        return new Estimate(method,envelope,new PixelGlyphReader.Grid(x.origin,y.origin,x.scale,y.scale),fit);
    }
    /** An enclosed rectangular active raster with a near-GB aspect; colored device bezels fail the aspect test. */
    static int[] darkSurround(SourceFrame frame) {
        byte[] rgb=frame.copyRgb();int w=frame.getWidth(),h=frame.getHeight();int l=w,t=h,r=0,b=0;
        int[] xs=new int[w],ys=new int[h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++) {
            int p=(y*w+x)*3,v=((rgb[p]&255)+2*(rgb[p+1]&255)+(rgb[p+2]&255))/4;
            if(v>=48){xs[x]++;ys[y]++;}
        }
        for(int x=0;x<w;x++)if(xs[x]>=h*.06){l=Math.min(l,x);r=Math.max(r,x+1);}
        for(int y=0;y<h;y++)if(ys[y]>=Math.max(16,(r-l)*.06)){t=Math.min(t,y);b=Math.max(b,y+1);}
        if(r-l<160||b-t<144)return new int[0];double aspect=(r-l)/(double)(b-t);
        // A dark ceiling/floor inside the game must not shorten an edge-clipped, full-height raster.
        double fullHeightAspect=(r-l)/(double)h;
        if((aspect<1.06||aspect>1.17)&&fullHeightAspect>=1.06&&fullHeightAspect<=1.17){t=0;b=h;aspect=fullHeightAspect;}
        if(aspect<1.06||aspect>1.17)return new int[0];
        // Require a genuinely dark surround on both horizontal sides, except an edge-clipped capture.
        if(l>0&&r<w) {
            int outside=0,bright=0;for(int y=t;y<b;y+=4)for(int x=0;x<w;x+=4)if(x<l-2||x>r+2){outside++;int p=(y*w+x)*3;if(((rgb[p]&255)+2*(rgb[p+1]&255)+(rgb[p+2]&255))/4>=32)bright++;}
            if(outside==0||bright/(double)outside>.02)return new int[0];
        }
        return new int[]{l,t,r,b};
    }
}
