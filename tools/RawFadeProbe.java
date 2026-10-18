import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.imageio.ImageIO;
import java.util.*;
import com.google.gson.*;

/** Original-pixel measurements only. No OCR, labels, GT, or templates. */
public final class RawFadeProbe {
    static String hash(byte[] data)throws Exception{StringBuilder s=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(data))s.append(String.format("%02x",b&255));return s.toString();}
    static int percentile(int[] h,int n,double q){int sum=0;for(int i=0;i<256;i++){sum+=h[i];if(sum>=n*q)return i;}return 255;}
    public static void main(String[] args)throws Exception{
        Path input=Paths.get(args[0]),out=Paths.get(args[1]);if(Files.exists(out))throw new IllegalArgumentException("New output required");
        int left=Integer.parseInt(args[2]),top=Integer.parseInt(args[3]),width=Integer.parseInt(args[4]),height=Integer.parseInt(args[5]);
        JsonObject report=new JsonObject();report.addProperty("meaning","original_pixel_statistics_not_state_labels");report.addProperty("input",input.toString());
        JsonArray rect=new JsonArray();for(int v:new int[]{left,top,width,height})rect.add(v);report.add("measurementRectXYWH",rect);JsonArray rows=new JsonArray();int[] previous=null;
        try(java.util.stream.Stream<Path> stream=Files.list(input)){
            Path[] paths=stream.filter(p->p.getFileName().toString().matches("frame-[0-9]+\\.png")).sorted().toArray(Path[]::new);
            for(Path p:paths){BufferedImage image=ImageIO.read(p.toFile());int[] rgb=image.getRGB(left,top,width,height,null,0,width),luma=new int[rgb.length],hist=new int[256];byte[] pixels=new byte[rgb.length*3];
                int changed=0,brighter=0,darker=0,stableDark=0;long absolute=0,sum=0,oldDarkDelta=0;
                for(int i=0;i<rgb.length;i++){int r=(rgb[i]>>16)&255,g=(rgb[i]>>8)&255,b=rgb[i]&255,v=(r+2*g+b)/4;luma[i]=v;hist[v]++;sum+=v;pixels[i*3]=(byte)r;pixels[i*3+1]=(byte)g;pixels[i*3+2]=(byte)b;
                    if(previous!=null){int d=v-previous[i];if(d!=0)changed++;if(d>4)brighter++;if(d< -4)darker++;absolute+=Math.abs(d);if(previous[i]<80){stableDark++;oldDarkDelta+=d;}}}
                JsonObject row=new JsonObject();row.addProperty("file",p.toString());row.addProperty("pngSha256",hash(Files.readAllBytes(p)));row.addProperty("cropRgbSha256",hash(pixels));row.addProperty("mean",(double)sum/rgb.length);
                row.addProperty("p05",percentile(hist,rgb.length,.05));row.addProperty("p50",percentile(hist,rgb.length,.5));row.addProperty("p95",percentile(hist,rgb.length,.95));
                JsonArray bands=new JsonArray();for(int band=0;band<2;band++){int from=band==0?0:width*height*2/3,to=band==0?width*height*2/3:rgb.length;int[] h=new int[256];for(int i=from;i<to;i++)h[luma[i]]++;JsonObject stats=new JsonObject();stats.addProperty("p01",percentile(h,to-from,.01));stats.addProperty("p05",percentile(h,to-from,.05));stats.addProperty("p95",percentile(h,to-from,.95));bands.add(stats);}row.add("bands",bands);
                row.addProperty("lumaChangedPixels",changed);row.addProperty("brightenedByOver4",brighter);row.addProperty("darkenedByOver4",darker);row.addProperty("absoluteLumaDelta",absolute);row.addProperty("previousBelow80Count",stableDark);if(stableDark>0)row.addProperty("previousBelow80MeanDelta",(double)oldDarkDelta/stableDark);rows.add(row);previous=luma;
            }
        }
        report.add("frames",rows);Files.write(out,(new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n").getBytes(StandardCharsets.UTF_8),StandardOpenOption.CREATE_NEW);System.out.println("Measured original frames: "+rows.size());
    }
}
