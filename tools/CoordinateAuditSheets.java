import com.google.gson.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Deterministic original crops/nearest-neighbor contact sheets for agent diagnostic review. */
public final class CoordinateAuditSheets {
    static int[] rect(JsonArray a){int[] r=new int[4];for(int i=0;i<4;i++)r[i]=a.get(i).getAsInt();return r;}
    public static void main(String[] args)throws Exception {
        JsonObject data=JsonParser.parseString(new String(Files.readAllBytes(Paths.get(args[0])),StandardCharsets.UTF_8)).getAsJsonObject();Path out=Paths.get(args[1]);if(Files.exists(out))throw new IllegalArgumentException("New sheet directory required");Files.createDirectories(out);
        for(JsonElement element:data.getAsJsonArray("sheets")){JsonObject sheet=element.getAsJsonObject();boolean overview=sheet.get("mode").getAsString().equals("overview");int cols=overview?4:2,cw=overview?332:700,ch=overview?320:170;JsonArray entries=sheet.getAsJsonArray("entries");BufferedImage canvas=new BufferedImage(cols*cw,((entries.size()+cols-1)/cols)*ch,BufferedImage.TYPE_INT_RGB);Graphics2D g=canvas.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,canvas.getWidth(),canvas.getHeight());g.setFont(new Font("Dialog",Font.PLAIN,12));g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            int index=0;for(JsonElement en:entries){JsonObject e=en.getAsJsonObject();BufferedImage original=ImageIO.read(Paths.get(e.get("image").getAsString()).toFile());int[] r=rect(e.getAsJsonArray("rect"));BufferedImage crop=original.getSubimage(r[0],r[1],r[2]-r[0],r[3]-r[1]);int left=(index%cols)*cw,top=(index/cols)*ch;g.setColor(Color.BLACK);String label=e.get("label").getAsString();g.drawString(label.substring(0,Math.min(label.length(),overview?44:96)),left+5,top+15);
                if(overview){g.drawImage(crop,left+5,top+25,320,288,null);}else {double scale=Math.min(2,Math.min(330.0/crop.getWidth(),120.0/crop.getHeight()));int w=(int)Math.round(crop.getWidth()*scale),h=(int)Math.round(crop.getHeight()*scale);g.drawImage(crop,left+5,top+25,w,h,null);g.drawImage(crop,left+355,top+25,w,h,null);
                    for(String type:new String[]{"expected","actual"})if(e.has(type)&&e.get(type).isJsonArray()){int[] b=rect(e.getAsJsonArray(type));g.setColor(type.equals("expected")?new Color(215,35,30):new Color(0,100,220));int x=left+355+(int)Math.round((b[0]-r[0])*scale),y=top+25+(int)Math.round((b[1]-r[1])*scale),rr=left+355+(int)Math.round((b[2]-r[0])*scale),bb=top+25+(int)Math.round((b[3]-r[1])*scale);g.drawRect(x,y,rr-x,bb-y);}
                    g.setColor(Color.BLACK);g.drawString("Original crop (left) | GT red / actual blue (right)",left+5,top+160);
                }index++;
            }g.dispose();Path file=out.resolve(sheet.get("id").getAsString()+".png");ImageIO.write(canvas,"png",file.toFile());System.out.println(file.toString());
        }
    }
}
