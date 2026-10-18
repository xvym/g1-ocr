package io.github.g1ocr.verification;

import com.google.gson.*;
import io.github.g1ocr.ocr.GlyphTemplate;
import io.github.g1ocr.ocr.PixelGlyphMatcher;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;
import javax.imageio.ImageIO;

/** Source-asset audit only. Does not load media/GT, run an OCR pipeline or execute repository code. */
final class FontReferenceAudit {
    static List<JsonObject> parseCharmap(String source) {
        Pattern section=Pattern.compile("^\\s*; Actual characters \\(from ([^)]+)\\)"),entry=Pattern.compile("^\\s*charmap \\\"([^\\\"]*)\\\",\\s*\\$([0-9a-fA-F]{2})(?:\\s*;.*)?\\s*$");
        List<JsonObject> entries=new ArrayList<>();String current="control_and_diacritic_codes";int lineNumber=0;
        for(String line:source.split("\\r?\\n")) {
            lineNumber++;Matcher s=section.matcher(line);if(s.find())current=s.group(1);
            Matcher e=entry.matcher(line);if(e.matches()) {
                String literal=e.group(1);FrozenDataset.require(!literal.isEmpty() && Normalizer.isNormalized(literal,Normalizer.Form.NFC),"Invalid source charmap literal");
                JsonObject row=new JsonObject();row.addProperty("literal",literal);row.addProperty("code",Integer.parseInt(e.group(2),16));row.addProperty("section",current);row.addProperty("sourceLine",lineNumber);entries.add(row);
            }else FrozenDataset.require(!line.matches("\\s*charmap\\s+.*"),"Unsupported source charmap expression at line "+lineNumber);
        }
        FrozenDataset.require(!entries.isEmpty(),"Empty source charmap");return entries;
    }
    static long ink(BufferedImage image,int left,int top,int darkest) {
        FrozenDataset.require(left>=0&&top>=0&&left+8<=image.getWidth()&&top+8<=image.getHeight(),"Incomplete source tile");long bits=0;
        for(int y=0;y<8;y++)for(int x=0;x<8;x++)if((image.getRGB(left+x,top+y)&0xffffff)==darkest)bits|=1L<<(8*y+x);return bits;
    }
    static int loadedTiles(JsonObject profile,int imageTiles) {
        int loaded=profile.has("loadedTiles")?profile.get("loadedTiles").getAsInt():imageTiles;
        FrozenDataset.require(loaded>0&&loaded<=imageTiles,"Audited copy extent exceeds source image");return loaded;
    }
    private static byte[] git(Path checkout,String... args) throws Exception {
        List<String> command=new ArrayList<>(Arrays.asList("git","-c","http.sslBackend=openssl","-C",checkout.toString()));command.addAll(Arrays.asList(args));
        Process process=new ProcessBuilder(command).start();ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(InputStream in=process.getInputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)bytes.write(buffer,0,n);}
        ByteArrayOutputStream errors=new ByteArrayOutputStream();try(InputStream in=process.getErrorStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)errors.write(buffer,0,n);}
        FrozenDataset.require(process.waitFor()==0,"Read-only Git source read failed: "+new String(errors.toByteArray(),StandardCharsets.UTF_8));return bytes.toByteArray();
    }
    private static String hash(byte[] bytes) throws Exception {return VideoTool.hex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    static void audit(Path manifestPath,Path output,Path bankDirectory) throws Exception {
        Path root=Paths.get("").toAbsolutePath().normalize();FrozenDataset.require(!Files.exists(output)&&!Files.exists(bankDirectory)&&output.toAbsolutePath().normalize().startsWith(root)&&bankDirectory.toAbsolutePath().normalize().startsWith(root.resolve(".cache")),"Use new workspace report and ignored .cache bank directories");
        Files.createDirectories(output);Files.createDirectories(bankDirectory);JsonObject manifest=VideoTool.readJson(manifestPath);JsonArray sources=new JsonArray();
        for(JsonElement sourceElement:manifest.getAsJsonArray("sources")) {
            JsonObject source=sourceElement.getAsJsonObject();String id=source.get("id").getAsString(),commit=source.get("commit").getAsString();Path checkout=Paths.get(source.get("checkout").getAsString());
            FrozenDataset.require(commit.matches("[a-f0-9]{40}")&&commit.equals(new String(git(checkout,"rev-parse","HEAD"),StandardCharsets.UTF_8).trim()),"Reference source commit changed");
            JsonArray identities=new JsonArray();
            for(JsonElement fileElement:manifest.getAsJsonArray("files")) {
                String file=fileElement.getAsString();byte[] original=git(checkout,"show",commit+":"+file),working=Files.readAllBytes(checkout.resolve(file));boolean exact=Arrays.equals(original,working);
                boolean text=file.endsWith(".asm")||file.endsWith(".md")||file.equals("Makefile");
                FrozenDataset.require(exact || text&&new String(original,StandardCharsets.UTF_8).replace("\r\n","\n").equals(new String(working,StandardCharsets.UTF_8).replace("\r\n","\n")),"Reference checkout differs from pinned commit: "+file);
                JsonObject row=new JsonObject();row.addProperty("file",file);row.addProperty("url",source.get("url").getAsString()+"/blob/"+commit+"/"+file);row.addProperty("gitBlob",new String(git(checkout,"rev-parse",commit+":"+file),StandardCharsets.UTF_8).trim());row.addProperty("repositoryBytesSha256",hash(original));row.addProperty("checkoutBytesSha256",hash(working));row.addProperty("checkoutNewlinesOnly",!exact);identities.add(row);
            }
            List<JsonObject> charmap=parseCharmap(new String(git(checkout,"show",commit+":constants/charmap.asm"),StandardCharsets.UTF_8));
            List<GlyphTemplate> templates=new ArrayList<>();JsonArray profiles=new JsonArray(),bank=new JsonArray();
            for(JsonElement profileElement:manifest.getAsJsonArray("profiles")) {
                JsonObject profile=profileElement.getAsJsonObject();String profileId=profile.get("id").getAsString(),file=profile.get("png").getAsString();int first=profile.get("firstTile").getAsInt();BufferedImage image=ImageIO.read(checkout.resolve(file).toFile());
                FrozenDataset.require(image!=null&&image.getWidth()%8==0&&image.getHeight()%8==0,"Source image is not an exact tile sheet");
                SortedSet<Integer> colors=new TreeSet<>();for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){int pixel=image.getRGB(x,y);int r=(pixel>>16)&255,g=(pixel>>8)&255,b=pixel&255;FrozenDataset.require((pixel>>>24)==255&&r==g&&g==b,"Source font requires an audited opaque grayscale palette");colors.add(pixel&0xffffff);}
                FrozenDataset.require(colors.size()>=2&&colors.size()<=4,"Unexpected source grayscale palette");int darkest=colors.first(),imageTiles=image.getWidth()/8*(image.getHeight()/8),tileCount=loadedTiles(profile,imageTiles);FrozenDataset.require(first+tileCount<=256,"Source tile code overflow");JsonArray aliases=new JsonArray();
                for(int n=0;n<tileCount;n++) {
                    List<String> literals=new ArrayList<>();if(!profile.get("mappingSection").isJsonNull())for(JsonObject mapping:charmap)if(mapping.get("section").equals(profile.get("mappingSection"))&&mapping.get("code").getAsInt()==first+n)literals.add(mapping.get("literal").getAsString());
                    GlyphTemplate glyph=new GlyphTemplate(id,profileId,first+n,ink(image,n%(image.getWidth()/8)*8,n/(image.getWidth()/8)*8,darkest),literals);templates.add(glyph);
                    JsonObject entry=new JsonObject();entry.addProperty("sourceId",id);entry.addProperty("profile",profileId);entry.addProperty("tile",glyph.getTile());entry.addProperty("darkInkHex",Long.toUnsignedString(glyph.getInk(),16));entry.add("aliases",new Gson().toJsonTree(literals));bank.add(entry);
                    if(literals.size()>1){JsonObject ambiguity=new JsonObject();ambiguity.addProperty("tile",first+n);ambiguity.add("aliases",new Gson().toJsonTree(literals));aliases.add(ambiguity);}
                }
                JsonObject p=new JsonObject();p.addProperty("id",profileId);p.addProperty("png",file);p.addProperty("pngSha256",VideoTool.sha(checkout.resolve(file)));p.addProperty("decodedRgbSha256",VideoTool.rgbHash(image));p.addProperty("width",image.getWidth());p.addProperty("height",image.getHeight());p.addProperty("imageTiles",imageTiles);p.addProperty("tiles",tileCount);p.addProperty("firstTile",first);p.add("grayscaleRgbValues",new Gson().toJsonTree(colors));p.add("sharedSourceAliases",aliases);p.addProperty("maskMeaning","Darkest source RGB only; retain full source PNG. Only the audited copy extent is loaded. This is not an assumed video threshold or proven shadow-removal algorithm.");profiles.add(p);
            }
            // Every template must survive the image-only ranker; exact-shape ties are allowed.
            PixelGlyphMatcher matcher=new PixelGlyphMatcher(templates);for(GlyphTemplate glyph:templates)FrozenDataset.require(matcher.rank(glyph.getInk(),0).stream().anyMatch(c->c.getGlyph()==glyph),"Exact source glyph candidate was lost");
            JsonArray compositions=new JsonArray();Map<String,Integer> codes=new HashMap<>();for(JsonObject entry:charmap)codes.put(entry.get("literal").getAsString(),entry.get("code").getAsInt());
            for(JsonObject entry:charmap) {
                String literal=entry.get("literal").getAsString(),nfd=Normalizer.normalize(literal,Normalizer.Form.NFD);int code=entry.get("code").getAsInt();int[] points=nfd.codePoints().toArray();
                if(code>=0x60||points.length!=2||(points[1]!=0x3099&&points[1]!=0x309a))continue;
                boolean hand=points[1]==0x309a;int base=hand?code+(code<0x44?0x99-0x40:0xca-0x44):code+(code<0x20?0x85-0x05:0xb6-0x26);
                Integer expected=codes.get(new String(Character.toChars(points[0])));FrozenDataset.require(expected!=null&&base==expected,"Source diacritic arithmetic disagrees with explicit charmap");
                JsonObject row=new JsonObject();row.addProperty("literal",literal);row.addProperty("code",code);row.addProperty("baseTile",base);row.addProperty("markTile",hand?0xe4:0xe5);row.addProperty("markGridRowOffset",-1);row.addProperty("sourceLine",entry.get("sourceLine").getAsInt());compositions.add(row);
            }
            JsonArray licenses=new JsonArray();String tree=new String(git(checkout,"ls-tree","-r","--name-only",commit),StandardCharsets.UTF_8);for(String file:tree.split("\n"))if(file.matches("(?i)(.*/)?(LICENSE|COPYING|NOTICE)(\\..*)?"))licenses.add(file);
            JsonObject bankData=new JsonObject();bankData.add("source",source);bankData.add("templates",bank);bankData.add("diacriticCompositions",compositions);Path bankFile=bankDirectory.resolve(id+"-glyphs.json");VideoTool.save(bankFile,bankData);
            JsonObject report=new JsonObject();report.add("source",source);report.add("fileIdentities",identities);report.add("profiles",profiles);report.addProperty("templates",templates.size());report.add("diacriticCompositions",compositions);report.add("licenseFilesInFullPinnedTree",licenses);report.addProperty("redistribution","Not established; source graphics and derived pixel masks remain in ignored .cache, excluded from product artifacts");report.addProperty("localBankSha256",VideoTool.sha(bankFile));report.addProperty("localBank",bankFile.toString());report.addProperty("rawScoringRepresentation","Unresolved aliases and profile-specific tile reuse are retained; no normalization or first-alias selection performed");sources.add(report);
            System.out.println("Source glyph audit: "+id+" templates="+templates.size()+" composed kana="+compositions.size());
        }
        JsonObject result=new JsonObject();result.addProperty("status","pinned_Japanese_source_glyphs_audited_not_video_OCR_acceptance");result.addProperty("manifestSha256",VideoTool.sha(manifestPath));result.add("sources",sources);result.addProperty("scope","Only source PNGs, source encoding/rendering and glyph candidate primitives; no media/GT or production Chinese mapping loaded");VideoTool.save(output.resolve("report.json"),result);
    }
}
