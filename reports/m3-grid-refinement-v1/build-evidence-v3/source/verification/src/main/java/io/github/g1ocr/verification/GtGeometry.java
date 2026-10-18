package io.github.g1ocr.verification;

import com.google.gson.*;
import java.text.Normalizer;
import java.util.*;

/** Geometry and literal-unit integrity. Never reads pixels or invents a character. */
final class GtGeometry {
    static int region(JsonObject region, JsonObject layout) {
        String text = region.get("text").getAsString();
        require(!text.isEmpty() && Normalizer.isNormalized(text, Normalizer.Form.NFC), "Empty/non-NFC region");
        int count = text.codePointCount(0, text.length());
        JsonArray grid = region.getAsJsonArray("gridRect"), marks = region.getAsJsonArray("markCells");
        require(grid.size() == 4, "Invalid grid rectangle");
        if (region.has("renderUnits")) {
            int[] logical = integers(region.getAsJsonArray("logicalRect"));
            for (int i=0;i<4;i++) require(grid.get(i).getAsDouble() == logical[i]/8.0, "Packed label grid/pixel mismatch");
            require(marks.size() == 0 && region.has("renderEvidence") && !region.get("renderEvidence").getAsString().trim().isEmpty(), "Packed label needs explicit raw evidence and unit geometry");
            StringBuilder literal = new StringBuilder(); int next = logical[0];
            JsonArray units = region.getAsJsonArray("renderUnits"); require(units.size()>0, "Empty render units");
            for (JsonElement element : units) {
                JsonObject unit=element.getAsJsonObject(); String part=unit.get("text").getAsString();
                require(!part.isEmpty() && Normalizer.isNormalized(part, Normalizer.Form.NFC), "Empty/non-NFC packed unit");
                int[] rect=integers(unit.getAsJsonArray("logicalRect"));
                require(rect[0]==next && rect[1]==logical[1] && rect[3]==logical[3] && rect[2]>rect[0] && rect[2]<=logical[2], "Packed unit gap/overlap/envelope mismatch");
                require(Arrays.asList("compound_label","base_glyph").contains(unit.get("kind").getAsString()), "Unknown render-unit kind");
                literal.append(part); next=rect[2];
            }
            require(next==logical[2] && literal.toString().equals(text), "Packed units do not exactly reproduce literal text");
            same(region.getAsJsonArray("sourceRect"), GridProjection.pixels(layout,logical[0],logical[1],logical[2],logical[3]));
        } else {
            int[] rect=integers(grid);
            require(rect[2]-rect[0]==count && rect[3]-rect[1]==1, "Base cells/text count mismatch");
            int top=rect[1]; Set<Integer> marked=new HashSet<>();
            for (JsonElement element : marks) {
                JsonObject mark=element.getAsJsonObject(); int column=mark.get("gridColumn").getAsInt(), row=mark.get("gridRow").getAsInt();
                require(column>=rect[0] && column<rect[2] && row==top-1 && marked.add(column), "Mark outside glyph envelope or duplicate");
                require(Arrays.asList("dakuten","handakuten").contains(mark.get("kind").getAsString()), "Unknown mark kind");
            }
            if(marks.size()>0)top--;
            same(region.getAsJsonArray("sourceRect"),GridProjection.rectangle(layout,rect[0],top,rect[2],rect[3]));
        }
        return count;
    }
    static int symbols(JsonObject state, JsonObject layout) {
        int count=0;
        if(state.has("detachedMarks")) for(JsonElement element:state.getAsJsonArray("detachedMarks")) {
            JsonObject mark=element.getAsJsonObject();
            require(Arrays.asList("dakuten","handakuten").contains(mark.get("kind").getAsString()), "Unknown detached mark");
            require(mark.has("renderNote") && !mark.get("renderNote").getAsString().trim().isEmpty() && !mark.has("restoredBaseText"), "Detached mark cannot restore an invisible base");
            symbol(mark,layout);count++;
        }
        if(state.has("cursor")) {
            JsonObject cursor=state.getAsJsonObject("cursor");
            if(cursor.has("menuCursors")) {
                JsonElement menu=cursor.get("menuCursors");
                if(menu.isJsonObject()) symbol(menu.getAsJsonObject(),layout);
                else {require(menu.isJsonArray(),"Invalid menu cursor");for(JsonElement element:menu.getAsJsonArray())symbol(element.getAsJsonObject(),layout);}
            }
            if(cursor.has("downArrow")) {require(cursor.get("downArrowVisible").getAsBoolean(),"Hidden arrow has visible geometry");symbol(cursor.getAsJsonObject("downArrow"),layout);}
            if(cursor.has("additionalDownArrows")) {
                require(cursor.get("downArrowVisible").getAsBoolean() && cursor.has("downArrow"),"Additional arrow needs a visible primary arrow");
                Set<String> arrows=new HashSet<String>();arrows.add(cursor.getAsJsonObject("downArrow").get("gridRect").toString());
                for(JsonElement element:cursor.getAsJsonArray("additionalDownArrows")) {JsonObject arrow=element.getAsJsonObject();require(arrow.has("renderNote") && !arrow.get("renderNote").getAsString().trim().isEmpty(),"Additional arrow needs source evidence");require(arrows.add(arrow.get("gridRect").toString()),"Duplicate arrow geometry");symbol(arrow,layout);}
            }
        }
        return count;
    }
    static boolean nonTarget(JsonObject clip) {
        if(!clip.has("scoreKind"))return false;
        String kind=clip.get("scoreKind").getAsString();
        require("text_and_state".equals(kind)||"non_target_only".equals(kind),"Unknown score kind");
        return "non_target_only".equals(kind);
    }
    static void nonTargetState(JsonObject state,int characters,int detachedMarks) {
        require(characters==0 && detachedMarks==0 && !state.get("completePage").getAsBoolean(),"Non-target scope has target text/marks/page");
        require(Arrays.asList("non_target","black_frame","invalid_target_image").contains(state.get("kind").getAsString()),"Unknown non-target state");
        JsonObject cursor=state.getAsJsonObject("cursor");
        require(!cursor.get("downArrowVisible").getAsBoolean() && cursor.get("menuCursors").isJsonArray() && cursor.getAsJsonArray("menuCursors").size()==0,"Non-target scope has visible cursor");
    }
    private static void symbol(JsonObject symbol,JsonObject layout) {
        int[] r=integers(symbol.getAsJsonArray("gridRect"));
        require(r[2]-r[0]==1 && r[3]-r[1]==1,"Symbol must occupy one canonical cell");
        same(symbol.getAsJsonArray("sourceRect"),GridProjection.rectangle(layout,r[0],r[1],r[2],r[3]));
    }
    private static int[] integers(JsonArray array) {
        require(array.size()==4,"Invalid rectangle size");int[] r=new int[4];
        for(int i=0;i<4;i++){double number=array.get(i).getAsDouble();require(Double.isFinite(number)&&number==Math.rint(number),"Fractional coordinates require explicit renderUnits/logicalRect");r[i]=Math.toIntExact((long)number);}return r;
    }
    private static void same(JsonArray array,int[] expected) {int[] actual=integers(array);require(Arrays.equals(actual,expected),"Source/grid geometry mismatch");}
    private static void require(boolean ok,String message){if(!ok)throw new IllegalArgumentException(message);}
}
