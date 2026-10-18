import com.google.gson.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Read-only original-frame review sheets. No GT, tested OCR or message candidate input. */
public final class RawRecheckSheets {
    static String hex(byte[] bytes){StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format("%02x",b&255));return s.toString();}
    static String sha(Path p)throws Exception{return hex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));}
    static String rgbHash(BufferedImage image)throws Exception{byte[] rgb=new byte[image.getWidth()*image.getHeight()*3];int at=0;for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){int p=image.getRGB(x,y);rgb[at++]=(byte)(p>>16);rgb[at++]=(byte)(p>>8);rgb[at++]=(byte)p;}return hex(MessageDigest.getInstance("SHA-256").digest(rgb));}
    public static void main(String[] args)throws Exception {
        if(args.length!=7)throw new IllegalArgumentException("inputDirectory newOutput cropX cropY cropWidth cropHeight scale");
        Path input=Paths.get(args[0]),output=Paths.get(args[1]);if(Files.exists(output))throw new IllegalArgumentException("New output only");
        int l=Integer.parseInt(args[2]),t=Integer.parseInt(args[3]),w=Integer.parseInt(args[4]),h=Integer.parseInt(args[5]),scale=Integer.parseInt(args[6]);
        if(l<0||t<0||w<1||h<1||scale<1||scale>4)throw new IllegalArgumentException("Invalid crop/scale");
        JsonObject verification=new JsonParser().parse(new String(Files.readAllBytes(input.resolve("verification.json")),StandardCharsets.UTF_8)).getAsJsonObject();
        if(verification.get("step").getAsInt()!=1||!verification.get("allRgbAndPngReadbackHashesMatch").getAsBoolean())throw new IllegalArgumentException("Continuous verified original frames required");
        Files.createDirectories(output);JsonArray frames=verification.getAsJsonArray("frames"),records=new JsonArray(),sheets=new JsonArray();int cellH=h*scale+26;
        for(int begin=0;begin<frames.size();begin+=6) {
            int count=Math.min(6,frames.size()-begin);BufferedImage sheet=new BufferedImage(w*scale*2,cellH*3,BufferedImage.TYPE_INT_RGB);Graphics2D g=sheet.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,sheet.getWidth(),sheet.getHeight());g.setColor(Color.BLACK);g.setFont(new Font("Monospaced",Font.PLAIN,16));
            java.util.List<BufferedImage> originals=new ArrayList<>();
            for(int k=0;k<count;k++) {
                JsonObject frame=frames.get(begin+k).getAsJsonObject();int n=frame.get("frameIndex").getAsInt(),tx=k%2*w*scale,ty=k/2*cellH;Path png=input.resolve(String.format("frame-%06d.png",n));BufferedImage raw=ImageIO.read(png.toFile());
                if(l+w>raw.getWidth()||t+h>raw.getHeight()||!rgbHash(raw).equals(frame.get("rgbSha256").getAsString()))throw new IllegalArgumentException("Original pixels or crop differ");originals.add(raw);
                for(int y=0;y<h*scale;y++)for(int x=0;x<w*scale;x++)sheet.setRGB(tx+x,ty+y,raw.getRGB(l+x/scale,t+y/scale));
                g.drawString("frame="+n+" pts="+frame.get("pts").getAsString(),tx+4,ty+h*scale+19);
                JsonObject record=frame.deepCopy();record.addProperty("png",png.toString());record.addProperty("pngSha256",sha(png));record.addProperty("sheet",String.format("sheet-%03d.png",begin/6));record.addProperty("tileX",tx);record.addProperty("tileY",ty);records.add(record);
            }
            g.dispose();Path target=output.resolve(String.format("sheet-%03d.png",begin/6));ImageIO.write(sheet,"png",target.toFile());BufferedImage check=ImageIO.read(target.toFile());
            for(int k=0;k<count;k++){BufferedImage raw=originals.get(k);int tx=k%2*w*scale,ty=k/2*cellH;for(int y=0;y<h*scale;y++)for(int x=0;x<w*scale;x++)if((check.getRGB(tx+x,ty+y)&0xffffff)!=(raw.getRGB(l+x/scale,t+y/scale)&0xffffff))throw new IllegalStateException("Nearest-neighbor sheet pixel mismatch");}
            JsonObject info=new JsonObject();info.addProperty("path",target.toString());info.addProperty("sha256",sha(target));info.addProperty("frames",count);sheets.add(info);
        }
        JsonObject report=new JsonObject();report.addProperty("status","verified_original_pixels_for_blind_recheck_not_annotations_or_OCR");report.addProperty("inputVerification",input.resolve("verification.json").toString());report.addProperty("inputVerificationSha256",sha(input.resolve("verification.json")));report.addProperty("helperSha256",sha(Paths.get("tools/RawRecheckSheets.java")));report.addProperty("frameCount",frames.size());report.add("crop",new Gson().toJsonTree(new int[]{l,t,w,h}));report.addProperty("nearestNeighborScale",scale);report.addProperty("allDisplayedPixelsMatchOriginal",true);report.add("sheets",sheets);report.add("frames",records);
        Files.write(output.resolve("report.json"),(new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n").getBytes(StandardCharsets.UTF_8),StandardOpenOption.CREATE_NEW);Files.copy(Paths.get("tools/RawRecheckSheets.java"),output.resolve("helper-source.java.txt"));
        System.out.println("Verified original recheck sheets: "+frames.size()+" frames, "+sheets.size()+" sheets");
    }
}
