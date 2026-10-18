import io.github.g1ocr.capture.SourceFrame;
import io.github.g1ocr.ocr.*;
import com.google.gson.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Diagnostic only: compares a discrete edge lattice against the Fourier phase.
 * Takes only a PNG and normal pixels, never GT, IDs or expected coordinates. */
public class GridEdgeProbe {
 static double cost(double[] e,int lo,int hi,double origin,double scale){
  double sum=0,score=0;
  for(int p=lo+3;p<hi-3;p++)if(e[p]>0){
   int k=(int)Math.round((p-origin)/scale);double distance=Double.POSITIVE_INFINITY;
   for(int d=-1;d<=1;d++)distance=Math.min(distance,Math.abs(p-Math.ceil(origin+(k+d)*scale-1e-7)));
   sum+=e[p];score+=e[p]*Math.min(distance*distance,scale*scale/4);
  }
  return score/Math.max(1,sum);
 }
 static JsonObject fit(double[] e,int lo,int hi,int logical,double anchor){
  double best=Double.POSITIVE_INFINITY,bo=0,bs=0;int ties=0;
  int low=(int)Math.ceil((hi-lo)*logical/(logical+8.0)*8),high=(int)Math.floor((hi-lo)*1.02*8);
  for(int tick=low;tick<=high;tick++){
   double scale=tick/(8.0*logical);
   for(int phase=-16;phase<=16;phase++){
    double origin=anchor+phase*scale/32,score=cost(e,lo,hi,origin,scale);
    if(score<best-1e-10){best=score;bo=origin;bs=scale;ties=1;}else if(Math.abs(score-best)<1e-10)ties++;
   }
  }
  JsonObject j=new JsonObject();j.addProperty("origin",bo);j.addProperty("scale",bs);j.addProperty("weightedSquaredEdgeDistance",best);j.addProperty("equalMinima",ties);return j;
 }
 public static void main(String[] args)throws Exception{
  Path out=Paths.get(args[1]);if(Files.exists(out))throw new IllegalArgumentException("new output only");
  BufferedImage im=ImageIO.read(Paths.get(args[0]).toFile());int w=im.getWidth(),h=im.getHeight();byte[] rgb=new byte[w*h*3];int[] gray=new int[w*h];
  for(int y=0;y<h;y++)for(int x=0;x<w;x++){int p=im.getRGB(x,y),i=y*w+x;rgb[i*3]=(byte)(p>>16);rgb[i*3+1]=(byte)(p>>8);rgb[i*3+2]=(byte)p;gray[i]=(((p>>16)&255)+2*((p>>8)&255)+(p&255))/4;}
  FrameGeometryEstimator.Estimate current=new FrameGeometryEstimator().estimate(new SourceFrame(rgb,w,h,0,1,1));
  if(!current.located)throw new IllegalArgumentException("no initial geometry");
  int[] b=current.copyEnvelope();double[] ex=new double[w],ey=new double[h];
  for(int y=b[1]+3;y<b[3]-3;y++)for(int x=b[0]+3;x<b[2]-3;x++){int i=y*w+x,dx=Math.abs(gray[i]-gray[i-1]),dy=Math.abs(gray[i]-gray[i-w]);if(dx>=12)ex[x]+=dx-11;if(dy>=12)ey[y]+=dy-11;}
  JsonObject report=new JsonObject();Gson g=new GsonBuilder().setPrettyPrinting().create();report.addProperty("status","diagnostic_candidate_not_product_geometry");report.addProperty("input",args[0]);report.add("current",g.toJsonTree(current));
  report.add("discreteX",fit(ex,b[0],b[2],160,b[0]));report.add("discreteY",fit(ey,b[1],b[3],144,b[1]));
  report.addProperty("limitation","Discrete ceil-edge model is a hypothesis. Compression, filtering and tied origins may invalidate exact projection. No product modification or acceptance claim.");
  Files.createDirectories(out.toAbsolutePath().getParent());Files.write(out,g.toJson(report).getBytes(StandardCharsets.UTF_8));System.out.println(g.toJson(report));
 }
}
