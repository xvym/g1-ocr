import com.google.gson.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.io.*;
import java.util.*;

/** Original-RGB support measurement only. No font, OCR, inferred text or model is loaded. */
public final class CoordinateInkProbe {
    static int[] rect(JsonArray a){int[] r=new int[4];for(int i=0;i<4;i++)r[i]=a.get(i).getAsInt();return r;}
    static boolean contains(int[] r,int x,int y){return x>=r[0]&&y>=r[1]&&x<r[2]&&y<r[3];}
    static String hex(byte[] b){StringBuilder s=new StringBuilder();for(byte v:b)s.append(String.format("%02x",v&255));return s.toString();}
    static int quantile(int[] h,int total,double q){int goal=Math.max(1,(int)Math.ceil(total*q)),n=0;for(int i=0;i<h.length;i++){n+=h[i];if(n>=goal)return i;}return 255;}
    static int otsu(int[] h,int total){long sum=0,leftSum=0;for(int i=0;i<256;i++)sum+=(long)i*h[i];int left=0,threshold=0;double best=-1;
        for(int i=0;i<255;i++){left+=h[i];leftSum+=(long)i*h[i];int right=total-left;if(left==0||right==0)continue;double d=leftSum/(double)left-(sum-leftSum)/(double)right,v=(double)left*right*d*d;if(v>best){best=v;threshold=i;}}return threshold;}
    static final class Target {
        JsonObject input,result;int[] expected,actual;List<Integer> core=new ArrayList<>();
    }
    static Target probe(JsonObject input,int[] luma,int width,int height){Target t=new Target();t.input=input;t.expected=rect(input.getAsJsonArray("expected"));t.actual=rect(input.getAsJsonArray("actual"));
        int[] r=t.expected;boolean valid=r[0]>=0&&r[1]>=0&&r[2]<=width&&r[3]<=height&&r[0]<r[2]&&r[1]<r[3];if(!valid)throw new IllegalArgumentException("Frozen support outside image: "+input.get("id"));
        BitSet mask=new BitSet(width*height);for(JsonElement e:input.getAsJsonArray("supportRects")){int[] p=rect(e.getAsJsonArray());for(int y=Math.max(0,p[1]);y<Math.min(height,p[3]);y++)for(int x=Math.max(0,p[0]);x<Math.min(width,p[2]);x++)if(contains(r,x,y))mask.set(y*width+x);}
        int[] histogram=new int[256];for(int p=mask.nextSetBit(0);p>=0;p=mask.nextSetBit(p+1))histogram[luma[p]]++;
        int area=mask.cardinality(),lo=quantile(histogram,area,.001),hi=quantile(histogram,area,.999),contrast=hi-lo,threshold=otsu(histogram,area),dark=0;
        for(int i=0;i<=threshold;i++)dark+=histogram[i];boolean darkForeground=dark<=area-dark;
        int coreThreshold=darkForeground?lo+contrast/4:hi-contrast/4,fg=0,omitted=0,omittedCore=0;
        int[] omittedBounds={width,height,0,0};
        for(int p=mask.nextSetBit(0);p>=0;p=mask.nextSetBit(p+1)){
            int x=p%width,y=p/width;boolean isForeground=darkForeground?luma[p]<=threshold:luma[p]>threshold;
            boolean isCore=isForeground&&(darkForeground?luma[p]<=coreThreshold:luma[p]>=coreThreshold);
            if(isForeground)fg++;if(isCore)t.core.add(p);
            if(!contains(t.actual,x,y)){if(isForeground)omitted++;if(isCore){omittedCore++;omittedBounds[0]=Math.min(omittedBounds[0],x);omittedBounds[1]=Math.min(omittedBounds[1],y);omittedBounds[2]=Math.max(omittedBounds[2],x+1);omittedBounds[3]=Math.max(omittedBounds[3],y+1);}}
        }
        JsonObject z=new JsonObject();z.addProperty("id",input.get("id").getAsString());z.addProperty("key",input.get("key").getAsString());z.addProperty("sourceChanged",input.get("sourceChanged").getAsBoolean());
        z.addProperty("supportPixels",area);z.addProperty("contrast",contrast);z.addProperty("threshold",threshold);z.addProperty("polarity",darkForeground?"dark":"light");z.addProperty("coreThreshold",coreThreshold);
        z.addProperty("foregroundPixels",fg);z.addProperty("corePixels",t.core.size());z.addProperty("omittedForeground",omitted);z.addProperty("omittedCore",omittedCore);
        if(omittedCore>0)z.add("omittedCoreBounds",new Gson().toJsonTree(omittedBounds));
        z.addProperty("supportClass",contrast<24||fg==0||t.core.isEmpty()?"low_contrast_unresolved":omittedCore>0?"visible_core_outside_reported_rect":omitted>0?"edge_foreground_outside_reported_rect":"no_foreground_omission");
        t.result=z;return t;
    }
    public static void main(String[] args)throws Exception{
        Path jobs=Paths.get(args[0]),output=Paths.get(args[1]);if(Files.exists(output))throw new IllegalArgumentException("New probe output required");
        Gson gson=new GsonBuilder().disableHtmlEscaping().create();int frames=0,targets=0;
        try(BufferedReader reader=Files.newBufferedReader(jobs,StandardCharsets.UTF_8);BufferedWriter writer=Files.newBufferedWriter(output,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)){
            String line;while((line=reader.readLine())!=null){JsonObject job=JsonParser.parseString(line).getAsJsonObject();BufferedImage im=ImageIO.read(Paths.get(job.get("image").getAsString()).toFile());int width=im.getWidth(),height=im.getHeight();int[] rgb=im.getRGB(0,0,width,height,null,0,width),luma=new int[rgb.length];
                MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] scanline=new byte[width*3];for(int y=0;y<height;y++){for(int x=0;x<width;x++){int c=rgb[y*width+x],r=(c>>>16)&255,g=(c>>>8)&255,b=c&255;scanline[x*3]=(byte)r;scanline[x*3+1]=(byte)g;scanline[x*3+2]=(byte)b;luma[y*width+x]=(r+2*g+b)/4;}digest.update(scanline);}
                String hash=hex(digest.digest());if(!hash.equals(job.get("rgbSha256").getAsString()))throw new IllegalArgumentException("Original RGB mismatch: "+job.get("sourceKey"));
                List<Target> list=new ArrayList<>();for(JsonElement e:job.getAsJsonArray("targets"))list.add(probe(e.getAsJsonObject(),luma,width,height));
                for(Target t:list){int other=0;JsonArray neighbors=new JsonArray();for(Target n:list){if(t==n||t.input.get("key").equals(n.input.get("key")))continue;if(n.expected[0]>=t.actual[2]||n.expected[2]<=t.actual[0]||n.expected[1]>=t.actual[3]||n.expected[3]<=t.actual[1])continue;
                        int count=0;for(int p:n.core){int x=p%width,y=p/width;if(contains(t.actual,x,y)&&!contains(t.expected,x,y))count++;}
                        if(count>0){JsonObject q=new JsonObject();q.addProperty("key",n.input.get("key").getAsString());q.addProperty("corePixels",count);neighbors.add(q);other+=count;}}
                    t.result.addProperty("neighborCoreInAddedMargin",other);t.result.add("neighbors",neighbors);
                }
                JsonObject result=new JsonObject();result.addProperty("sourceKey",job.get("sourceKey").getAsString());result.addProperty("image",job.get("image").getAsString());result.addProperty("rgbSha256",hash);result.addProperty("width",width);result.addProperty("height",height);JsonArray results=new JsonArray();for(Target t:list)results.add(t.result);result.add("targets",results);writer.write(gson.toJson(result));writer.newLine();frames++;targets+=list.size();
                if(frames%250==0){writer.flush();System.out.println("Original RGB support checked: "+frames+" frames");}
            }
        }
        System.out.println("Original RGB support completed: "+frames+" frames, "+targets+" target envelopes. Diagnostic only.");
    }
}
