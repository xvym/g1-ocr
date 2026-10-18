package io.github.g1ocr.verification;

import io.github.g1ocr.capture.SourceFrame;
import io.github.g1ocr.ocr.*;
import com.google.gson.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Isolated authorized experiment. Current/past pixels and pinned glyph masks only; no GT/actual answers. */
public final class JointGridProbe {
    static final class Point {double logical,source;Point(double l,double s){logical=l;source=s;}}
    static final class Fit {double origin,scale,rms;int points;Fit(double o,double s,double r,int n){origin=o;scale=s;rms=r;points=n;}}
    static Fit fit(List<Point> points,double origin,double scale){
        if(points.size()<12)return null;List<Point> selected=new ArrayList<>(points);double o=origin,s=scale;
        for(int pass=0;pass<4;pass++){
            double x=0,y=0,xx=0,xy=0;for(Point p:selected){x+=p.logical;y+=p.source;xx+=p.logical*p.logical;xy+=p.logical*p.source;}
            int n=selected.size();double spread=xx-x*x/n;if(n<12||spread/n<16)return null;
            s=(xy-x*y/n)/spread;o=(y-s*x)/n;
            List<Double> residuals=new ArrayList<>();for(Point p:selected)residuals.add(Math.abs(p.source-o-p.logical*s));Collections.sort(residuals);
            double limit=Math.max(.30,3*residuals.get(residuals.size()/2));List<Point> next=new ArrayList<>();for(Point p:points)if(Math.abs(p.source-o-p.logical*s)<=limit)next.add(p);selected=next;
        }
        if(Math.abs(s/scale-1)>.025||Math.abs(o-origin)>scale*1.5)return null;
        double error=0;for(Point p:selected)error+=Math.pow(p.source-o-p.logical*s,2);return new Fit(o,s,Math.sqrt(error/selected.size()),selected.size());
    }
    static int bit(long mask,int x,int y){return(int)(mask>>>(y*8+x))&1;}
    static int gray(byte[] rgb,int w,int h,int x,int y){if(x<0||x>=w||y<0||y>=h)return -1;int at=(y*w+x)*3;return((rgb[at]&255)+2*(rgb[at+1]&255)+(rgb[at+2]&255))/4;}
    static void runs(List<Point> out,long mask,int cx,int cy,boolean vertical,byte[] rgb,int w,int h,PixelGlyphReader.Grid grid){
        double origin=vertical?grid.originY:grid.originX,scale=vertical?grid.scaleY:grid.scaleX;
        for(int cross=1;cross<7;cross++)for(int begin=1;begin<7;begin++){
            if((vertical?bit(mask,cross,begin):bit(mask,begin,cross))==0||(vertical?bit(mask,cross,begin-1):bit(mask,begin-1,cross))!=0)continue;
            int end=begin+1;while(end<8&&(vertical?bit(mask,cross,end):bit(mask,end,cross))!=0)end++;
            if(end>=8||end-begin<1)continue;boolean stable=true;
            for(int pos=begin-1;pos<=end;pos++)for(int dc=-1;dc<=1;dc++)if((vertical?bit(mask,cross+dc,pos):bit(mask,pos,cross+dc))!=(vertical?bit(mask,cross,pos):bit(mask,pos,cross)))stable=false;
            if(!stable)continue;
            double start=(vertical?cy:cx)*8+begin,finish=(vertical?cy:cx)*8+end;
            int lo=(int)Math.ceil(origin+(start-.75)*scale-.5),hi=(int)Math.floor(origin+(finish+.75)*scale-.5);
            int fixed=(int)Math.floor((vertical?grid.originX:grid.originY)+((vertical?cx:cy)*8+cross+.5)*(vertical?grid.scaleX:grid.scaleY));
            int background=0,dark=255;for(int at=lo;at<=hi;at++){int value=vertical?gray(rgb,w,h,fixed,at):gray(rgb,w,h,at,fixed);if(value<0){stable=false;break;}background=Math.max(background,value);dark=Math.min(dark,value);}
            if(!stable||background-dark<30)continue;double mass=0,center=0;
            for(int at=lo;at<=hi;at++){int value=vertical?gray(rgb,w,h,fixed,at):gray(rgb,w,h,at,fixed);double ink=Math.max(0,background-value-2);mass+=ink;center+=(at+.5)*ink;}
            if(mass>30)out.add(new Point((start+finish)/2,center/mass));begin=end;
        }
    }
    public static void main(String[] args)throws Exception{
        Path list=Paths.get(args[0]),output=Paths.get(args[1]);if(Files.exists(output))throw new IllegalArgumentException("New output required");
        JsonArray input=JsonParser.parseString(new String(Files.readAllBytes(list),StandardCharsets.UTF_8)).getAsJsonArray(),results=new JsonArray();
        List<GlyphTemplate> bank=GlyphReadAudit.auditedBank();PixelGlyphReader reader=new PixelGlyphReader(bank);Deque<List<Point>> hx=new ArrayDeque<>(),hy=new ArrayDeque<>();
        int oldWidth=0,oldHeight=0;PixelGlyphReader.Grid oldGrid=null;
        for(JsonElement element:input){String file=element.getAsString();BufferedImage image=ImageIO.read(Paths.get(file).toFile());int w=image.getWidth(),h=image.getHeight();byte[] rgb=new byte[w*h*3];
            for(int y=0;y<h;y++)for(int x=0;x<w;x++){int v=image.getRGB(x,y),at=(y*w+x)*3;rgb[at]=(byte)(v>>16);rgb[at+1]=(byte)(v>>8);rgb[at+2]=(byte)v;}
            SourceFrame source=new SourceFrame(rgb,w,h,results.size(),1,30);FrameGeometryEstimator.Estimate estimate=new FrameGeometryEstimator().estimate(source);JsonObject row=new JsonObject();row.addProperty("input",file);row.add("initial",new Gson().toJsonTree(estimate));
            if(!estimate.located){hx.clear();hy.clear();oldGrid=null;results.add(row);continue;}
            PixelGlyphReader.Grid grid=estimate.grid;if(oldGrid!=null&&(w!=oldWidth||h!=oldHeight||Math.abs(grid.originX-oldGrid.originX)>grid.scaleX*1.5||Math.abs(grid.originY-oldGrid.originY)>grid.scaleY*1.5||Math.abs(grid.scaleX/oldGrid.scaleX-1)>.02||Math.abs(grid.scaleY/oldGrid.scaleY-1)>.02)){hx.clear();hy.clear();}
            PixelGlyphReader.Frame read=reader.read(source,grid);List<Point> x=new ArrayList<>(),y=new ArrayList<>();int cells=0;
            for(PixelGlyphReader.Cell c:read.cells()){
                if(!c.valid||c.blank||c.distance>1||c.candidates.size()!=1)continue;Set<Long> masks=new HashSet<>();int best=65;
                for(GlyphTemplate t:bank)if(t.getAliases().contains(c.candidates.get(0))){int d=t.distance(c.ink);if(d<best){best=d;masks.clear();}if(d==best)masks.add(t.getInk());}
                if(masks.size()!=1)continue;long mask=masks.iterator().next();cells++;runs(x,mask,c.column,c.row,false,rgb,w,h,grid);runs(y,mask,c.column,c.row,true,rgb,w,h,grid);
            }
            hx.addLast(x);hy.addLast(y);if(hx.size()>8){hx.removeFirst();hy.removeFirst();}List<Point> allX=new ArrayList<>(),allY=new ArrayList<>();for(List<Point> p:hx)allX.addAll(p);for(List<Point> p:hy)allY.addAll(p);
            row.add("x",new Gson().toJsonTree(fit(allX,grid.originX,grid.scaleX)));row.add("y",new Gson().toJsonTree(fit(allY,grid.originY,grid.scaleY)));row.addProperty("historyFrames",hx.size());row.addProperty("currentMatchedCells",cells);row.addProperty("currentXPoints",x.size());row.addProperty("currentYPoints",y.size());
            if(args.length>2){JsonArray inspect=new JsonArray();for(int cy=0;cy<18;cy++){JsonArray line=new JsonArray();for(int cx=0;cx<20;cx++)line.add(String.join("/",read.cell(cx,cy).candidates));inspect.add(line);}row.add("cells",inspect);}
            results.add(row);oldGrid=grid;oldWidth=w;oldHeight=h;
        }
        Files.write(output,(new GsonBuilder().setPrettyPrinting().create().toJson(results)+"\n").getBytes(StandardCharsets.UTF_8),StandardOpenOption.CREATE_NEW);System.out.println("Measured continuous inputs "+results.size());
    }
}
