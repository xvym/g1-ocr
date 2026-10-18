import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import java.util.*;
import com.google.gson.*;

/** Read-only original RGB statistics. No OCR, GT, frame-state labels or template input. */
public final class RawSignalProbe {
    public static void main(String[] args) throws Exception {
        Path input=Paths.get(args[0]),output=Paths.get(args[1]);
        if(Files.exists(output))throw new IllegalArgumentException("New report required");
        JsonArray result=new JsonArray();
        try(java.util.stream.Stream<Path> paths=Files.list(input)) {
            Path[] files=paths.filter(p->p.getFileName().toString().matches("frame-[0-9]+\\.png")).sorted().toArray(Path[]::new);
            for(Path file:files) {
                BufferedImage image=ImageIO.read(file.toFile());int width=image.getWidth(),height=image.getHeight();
                int[] rgb=image.getRGB(0,0,width,height,null,0,width);int nonzero=0,signal=0,min=255,max=0;
                for(int p:rgb){int r=(p>>16)&255,g=(p>>8)&255,b=p&255,v=(r+2*g+b)/4;
                    if((p&0xffffff)!=0)nonzero++;if(v>32)signal++;min=Math.min(min,v);max=Math.max(max,v);}
                JsonObject row=new JsonObject();row.addProperty("file",file.toString());row.addProperty("width",width);row.addProperty("height",height);
                row.addProperty("nonzeroRgbPixels",nonzero);row.addProperty("lumaAbove32Pixels",signal);row.addProperty("minimumLuma",min);row.addProperty("maximumLuma",max);
                row.addProperty("exactRgbBlack",nonzero==0);row.addProperty("locatorLowSignalPredicate",signal<rgb.length/1000+1);result.add(row);
            }
        }
        Files.write(output,(new GsonBuilder().setPrettyPrinting().create().toJson(result)+"\n").getBytes(StandardCharsets.UTF_8),StandardOpenOption.CREATE_NEW);
        System.out.println("Original RGB frames measured: "+result.size());
    }
}
