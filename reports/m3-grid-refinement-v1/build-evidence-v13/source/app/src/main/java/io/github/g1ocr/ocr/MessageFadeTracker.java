package io.github.g1ocr.ocr;

import io.github.g1ocr.capture.SourceFrame;

/** Detects a coherent contraction toward the light background in both screen bands. */
final class MessageFadeTracker {
    private int[] previous;
    private String signature;
    private boolean fading;
    private long pts,num,den;
    private int width,height;
    void reset(){previous=null;signature=null;fading=false;}

    boolean observe(SourceFrame source,PixelGlyphReader.Grid grid,String text,boolean eligible){
        if(!eligible||text.isEmpty()){reset();return false;}
        if(previous!=null&&(source.getPts()<=pts||source.getWidth()!=width||source.getHeight()!=height
                ||source.getTimeBaseNumerator()!=num||source.getTimeBaseDenominator()!=den))reset();
        byte[] rgb=source.copyRgb();int[] current=new int[160*144];
        for(int y=0;y<144;y++)for(int x=0;x<160;x++){
            int sx=(int)Math.floor(grid.originX+(x+.5)*grid.scaleX),sy=(int)Math.floor(grid.originY+(y+.5)*grid.scaleY);
            if(sx<0||sx>=source.getWidth()||sy<0||sy>=source.getHeight()){reset();return false;}
            int at=(sy*source.getWidth()+sx)*3;current[y*160+x]=((rgb[at]&255)+2*(rgb[at+1]&255)+(rgb[at+2]&255))/4;
        }
        if(previous==null||!text.equals(signature))fading=false;
        else {
            boolean step=contracts(previous,current,0,160*96)&&contracts(previous,current,160*96,current.length);
            // Stable plateaus during a stepped palette fade retain the observed state.
            // A return to darker ink is not continuation of that fade.
            fading=step||(fading&&samePalette(previous,current,0,160*96)&&samePalette(previous,current,160*96,current.length));
        }
        previous=current;signature=text;pts=source.getPts();num=source.getTimeBaseNumerator();den=source.getTimeBaseDenominator();width=source.getWidth();height=source.getHeight();
        return fading;
    }
    private static int[] range(int[] pixels,int from,int to){
        int[] histogram=new int[256];for(int i=from;i<to;i++)histogram[pixels[i]]++;
        // Foreground can occupy less than five percent of the upper scene.
        // Trim a small tail without treating a sparse figure as its background.
        int low=0,high=255,total=0;for(int i=0;i<256;i++){total+=histogram[i];if(total>=(to-from)*.01){low=i;break;}}
        total=0;for(int i=0;i<256;i++){total+=histogram[i];if(total>=(to-from)*.95){high=i;break;}}
        return new int[]{low,high};
    }
    private static boolean contracts(int[] old,int[] now,int from,int to){
        int[] a=range(old,from,to),b=range(now,from,to);int contrast=a[1]-a[0];
        if(contrast<30||b[0]-a[0]<contrast*.15||Math.abs(b[1]-a[1])>contrast*.10)return false;
        int ink=0,bright=0,dark=0;
        for(int i=from;i<to;i++){
            if(old[i]<a[0]+contrast*.25){ink++;if(now[i]-old[i]>contrast*.15)bright++;}
            if(old[i]-now[i]>contrast*.15)dark++;
        }
        return ink>0&&bright>=ink*.8&&dark<=(to-from)*.01;
    }
    private static boolean samePalette(int[] old,int[] now,int from,int to){
        int[] a=range(old,from,to),b=range(now,from,to);
        return Math.abs(a[0]-b[0])<=4&&Math.abs(a[1]-b[1])<=4;
    }
}
