package io.github.g1ocr.ocr;

import io.github.g1ocr.capture.SourceFrame;
import java.util.*;

/** Pixel-period and phase hypotheses inside a supplied image envelope. No text or GT access. */
public final class RasterGridRefiner {
    public static final class Axis {
        public final double origin, scale, concentration, phase;
        private Axis(double origin,double scale,double strength,double phase) {
            this.origin=origin;this.scale=scale;concentration=strength;this.phase=phase;
        }
    }
    public static final class Result {
        public final boolean available;
        public final List<Axis> xCandidates,yCandidates;
        private Result(List<Axis> x,List<Axis> y) {
            xCandidates=Collections.unmodifiableList(x);yCandidates=Collections.unmodifiableList(y);
            available=!x.isEmpty()&&!y.isEmpty()&&x.get(0).concentration>=.12&&y.get(0).concentration>=.12;
        }
    }
    public Result refine(SourceFrame frame,int[] envelope,double approximateOriginX,double approximateOriginY) {
        Objects.requireNonNull(frame);
        int w=frame.getWidth(),h=frame.getHeight();
        if(envelope==null||envelope.length!=4||envelope[0]<0||envelope[1]<0||envelope[2]>w||envelope[3]>h
                ||envelope[2]-envelope[0]<160||envelope[3]-envelope[1]<144
                ||!Double.isFinite(approximateOriginX)||!Double.isFinite(approximateOriginY))
            throw new IllegalArgumentException("Invalid raster envelope or anchor");
        byte[] rgb=frame.copyRgb();int[] gray=new int[w*h];
        for(int i=0;i<gray.length;i++)gray[i]=((rgb[i*3]&255)+2*(rgb[i*3+1]&255)+(rgb[i*3+2]&255))/4;
        double[] x=new double[w],y=new double[h];
        for(int row=envelope[1]+3;row<envelope[3]-3;row++)for(int col=envelope[0]+3;col<envelope[2]-3;col++) {
            int p=row*w+col,dx=Math.abs(gray[p]-gray[p-1]),dy=Math.abs(gray[p]-gray[p-w]);
            if(dx>=12)x[col]+=dx-11;if(dy>=12)y[row]+=dy-11;
        }
        return new Result(fit(x,envelope[0],envelope[2],160,approximateOriginX),
                fit(y,envelope[1],envelope[3],144,approximateOriginY));
    }
    private static List<Axis> fit(double[] edge,int begin,int end,int logical,double anchor) {
        double sum=0;for(int i=begin+3;i<end-3;i++)sum+=edge[i];
        List<Axis> all=new ArrayList<>();if(sum<=0)return all;
        // Integral 1/8-pixel span search avoids a crop-dependent starting phase in the parameter grid.
        int low=(int)Math.ceil((end-begin)*logical/(logical+8.0)*8),high=(int)Math.floor((end-begin)*1.02*8);
        for(int tick=low;tick<=high;tick++) {
            double scale=tick/(8.0*logical),step=2*Math.PI/scale,cs=Math.cos(step),sn=Math.sin(step);
            double c=Math.cos(3*step),s=Math.sin(3*step),re=0,im=0;
            for(int i=begin+3;i<end-3;i++){re+=edge[i]*c;im+=edge[i]*s;double next=c*cs-s*sn;s=s*cs+c*sn;c=next;}
            double phase=begin+Math.atan2(im,re)/step;
            // A lattice alone is periodic: use the supplied geometric anchor only to select its nearest representative.
            double origin=phase+Math.rint((anchor-phase)/scale)*scale;
            all.add(new Axis(origin,scale,Math.hypot(re,im)/sum,phase));
        }
        all.sort(Comparator.comparingDouble((Axis a)->-a.concentration).thenComparingDouble(a->a.scale));
        List<Axis> selected=new ArrayList<>();
        for(Axis a:all) {
            boolean distinct=true;for(Axis b:selected)if(Math.abs(a.scale-b.scale)*logical<.75)distinct=false;
            if(distinct)selected.add(a);if(selected.size()==3)break;
        }
        return selected;
    }
}
