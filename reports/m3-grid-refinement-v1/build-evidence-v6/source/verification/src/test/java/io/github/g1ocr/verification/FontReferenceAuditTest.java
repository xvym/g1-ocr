package io.github.g1ocr.verification;
import com.google.gson.*;
import org.junit.Test;
import java.util.*;
import java.awt.image.BufferedImage;
import static org.junit.Assert.*;
public class FontReferenceAuditTest {
    @Test public void repeatedTileCodeRetainsDifferentProfilesAndSharedKana(){String s="; Actual characters (from gfx/font/font_extra.png)\n charmap \"ぅ\", $6f\n; Actual characters (from gfx/font/font_battle_extra.png)\n charmap \"<LV>\", $6f\n; Actual characters (from gfx/font/font.png)\n charmap \"へ\", $cd\n charmap \"ヘ\", $cd ; shared\n";List<JsonObject> rows=FontReferenceAudit.parseCharmap(s);assertEquals(4,rows.size());assertNotEquals(rows.get(0).get("section"),rows.get(1).get("section"));assertEquals(rows.get(2).get("code"),rows.get(3).get("code"));assertNotEquals(rows.get(2).get("literal"),rows.get(3).get("literal"));}
    @Test(expected=IllegalArgumentException.class) public void unsupportedSourceExpressionRejected(){FontReferenceAudit.parseCharmap("charmap \"a\", $100");}
    @Test public void tileMaskPreservesCornersAndOnlyDarkestInk(){BufferedImage image=new BufferedImage(8,8,BufferedImage.TYPE_INT_RGB);for(int y=0;y<8;y++)for(int x=0;x<8;x++)image.setRGB(x,y,0xffffff);image.setRGB(0,0,0);image.setRGB(7,7,0);image.setRGB(1,0,0x555555);assertEquals(Long.MIN_VALUE|1,FontReferenceAudit.ink(image,0,0,0));}
    @Test(expected=IllegalArgumentException.class) public void incompleteTileRejected(){FontReferenceAudit.ink(new BufferedImage(8,7,BufferedImage.TYPE_INT_RGB),0,0,0);}
    @Test public void partialSourceCopyDoesNotLoadTrailingImageTiles(){JsonObject profile=JsonParser.parseString("{\"loadedTiles\":1}").getAsJsonObject();assertEquals(1,FontReferenceAudit.loadedTiles(profile,3));}
    @Test(expected=IllegalArgumentException.class) public void copyExtentCannotExceedImage(){FontReferenceAudit.loadedTiles(JsonParser.parseString("{\"loadedTiles\":4}").getAsJsonObject(),3);}
}
