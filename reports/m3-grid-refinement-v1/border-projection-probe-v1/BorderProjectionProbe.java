import io.github.g1ocr.capture.SourceFrame;
import io.github.g1ocr.ocr.*;
import com.google.gson.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Offline hypothesis test. Known border tiles only; no GT or message answers. */
public class BorderProjectionProbe {
 static final Gson G=new GsonBuilder().setPrettyPrinting().create();
 static class Sample {double x,y,v;Sample(double x,double y,double v){this.x=x;this.y=y;this.v=v;}}
 static double pixel(double[] model,int x,int y){return x<0||y<0||x>=160||y>=144?-1:model[y*160+x];}
 static double value(double[] model,double x,double y){
  int l=(int)Math.floor(x),t=(int)Math.floor(y);double a=pixel(model,l,t),b=pixel(model,l+1,t),c=pixel(model,l,t+1),d=pixel(model,l+1,t+1);
  if(Math.min(Math.min(a,b),Math.min(c,d))<0)return -1;
  double fx=x-l,fy=y-t;return a*(1-fx)*(1-fy)+b*fx*(1-fy)+c*(1-fx)*fy+d*fx*fy;
 }
 static double cost(double[] model,List<Sample> samples,double[] g){
  double sx=0,sy=0,sxx=0,sxy=0,syy=0;int n=0;
  for(Sample p:samples){double ink=value(model,(p.x+.5-g[0])/g[2]-.5,(p.y+.5-g[1])/g[3]-.5);if(ink<0)return Double.POSITIVE_INFINITY;
   sx+=ink;sy+=p.v;sxx+=ink*ink;sxy+=ink*p.v;syy+=p.v*p.v;n++;}
  double var=sxx-sx*sx/n;if(var<=1)return Double.POSITIVE_INFINITY;
  double cov=sxy-sx*sy/n;if(cov>=0)return Double.POSITIVE_INFINITY;
  return (syy-sy*sy/n-cov*cov/var)/n;
 }
 public static void main(String[] args)throws Exception{
  Path out=Paths.get(args[1]);if(Files.exists(out))throw new IllegalArgumentException("new output only");
  List<GlyphTemplate> bank=new ArrayList<>();
  for(String name:Arrays.asList("red-green-jp","blue-jp","yellow-jp")){
   JsonObject b=JsonParser.parseString(new String(Files.readAllBytes(Paths.get(".cache/m3-japanese-glyph-banks-v2/"+name+"-glyphs.json")),StandardCharsets.UTF_8)).getAsJsonObject();
   for(JsonElement e:b.getAsJsonArray("templates")){JsonObject r=e.getAsJsonObject();List<String> aliases=new ArrayList<>();for(JsonElement a:r.getAsJsonArray("aliases"))aliases.add(a.getAsString());bank.add(new GlyphTemplate(r.get("sourceId").getAsString(),r.get("profile").getAsString(),r.get("tile").getAsInt(),Long.parseUnsignedLong(r.get("darkInkHex").getAsString(),16),aliases));}
  }
  BufferedImage im=ImageIO.read(Paths.get(args[0]).toFile());int w=im.getWidth(),h=im.getHeight();byte[] rgb=new byte[w*h*3];
  for(int y=0;y<h;y++)for(int x=0;x<w;x++){int p=im.getRGB(x,y),i=y*w+x;rgb[i*3]=(byte)(p>>16);rgb[i*3+1]=(byte)(p>>8);rgb[i*3+2]=(byte)p;}
  SourceFrame source=new SourceFrame(rgb,w,h,0,1,1);FrameGeometryEstimator.Estimate initial=new FrameGeometryEstimator().estimate(source);
  if(!initial.located)throw new IllegalArgumentException("No grid");PixelGlyphReader.Frame read=new PixelGlyphReader(bank).read(source,initial.grid);
  List<BoxTextDetector.Box> boxes=new BoxTextDetector().detect(read);double[] model=new double[160*144];Arrays.fill(model,-1);int cells=0;
  for(PixelGlyphReader.Cell c:read.cells()){
   boolean border=false;for(BoxTextDetector.Box box:boxes)if(c.column>=box.left&&c.column<box.right&&c.row>=box.top&&c.row<box.bottom&&(c.column==box.left||c.column==box.right-1||c.row==box.top||c.row==box.bottom-1))border=true;
   if(!border||c.candidates.size()!=1||!Arrays.asList("┌","┐","└","┘","─","│").contains(c.candidates.get(0)))continue;
   long mask=0;int best=65;Set<Long> tied=new HashSet<>();for(GlyphTemplate t:bank)if(t.getAliases().contains(c.candidates.get(0))){int d=t.distance(c.ink);if(d<best){best=d;tied.clear();}if(d==best)tied.add(t.getInk());}
   if(tied.size()!=1)continue;mask=tied.iterator().next();cells++;
   for(int y=0;y<8;y++)for(int x=0;x<8;x++)model[(c.row*8+y)*160+c.column*8+x]=(mask>>>(y*8+x))&1;
  }
  double[] start={initial.grid.originX,initial.grid.originY,initial.grid.scaleX,initial.grid.scaleY},best=start.clone();List<Sample> samples=new ArrayList<>();
  // Keep a fixed set whose bilinear neighborhoods remain known under search motion.
  for(int y=0;y<h;y+=2)for(int x=0;x<w;x+=2){double lx=(x+.5-start[0])/start[2]-.5,ly=(y+.5-start[1])/start[3]-.5;boolean safe=true;
   for(int dy=-2;dy<=2;dy++)for(int dx=-2;dx<=2;dx++)if(value(model,lx+dx,ly+dy)<0)safe=false;
   if(safe){int p=im.getRGB(x,y);samples.add(new Sample(x,y,(((p>>16)&255)+2*((p>>8)&255)+(p&255))/4.0));}}
  if(cells<8||samples.size()<50)throw new IllegalArgumentException("Insufficient border samples "+cells+"/"+samples.size());
  double initialCost=cost(model,samples,best),bestCost=initialCost;
  for(int pass=0;pass<3;pass++)for(int axis=0;axis<2;axis++){
   double[] anchor=best.clone();double originStep=.25/Math.pow(2,pass),spanStep=.25/Math.pow(2,pass);int logical=axis==0?160:144;
   for(int oi=-8;oi<=8;oi++)for(int si=-8;si<=8;si++){double[] candidate=anchor.clone();candidate[axis]+=oi*originStep;candidate[axis+2]+=si*spanStep/logical;double loss=cost(model,samples,candidate);if(loss<bestCost){bestCost=loss;best=candidate;}}
  }
  JsonObject result=new JsonObject();result.addProperty("status","diagnostic_bilinear_border_model_only");result.addProperty("input",args[0]);result.add("initial",G.toJsonTree(initial));result.addProperty("borderCells",cells);result.addProperty("fixedSourceSamples",samples.size());result.addProperty("initialResidual",initialCost);result.addProperty("bestResidual",bestCost);result.add("fittedGrid",G.toJsonTree(best));result.addProperty("limitation","Assumed bilinear source sampling, not certified. Fit uses only current matched border pixels, no GT. No snapping or product change.");
  Files.createDirectories(out.toAbsolutePath().getParent());Files.write(out,G.toJson(result).getBytes(StandardCharsets.UTF_8));System.out.println(G.toJson(result));
 }
}
