package io.github.g1ocr.verification;

import com.google.gson.*;
import java.util.*;

/** Frozen-contract content scoring. UI presentation cannot be inferred from these results. */
final class OcrScoring {
    static final class Edit {
        int substitutions,deletions,insertions;
        int errors(){return substitutions+deletions+insertions;}
    }
    static Edit edit(String expected,String actual) {
        int[] a=scalars(expected),b=scalars(actual);int[][] d=new int[a.length+1][b.length+1];
        for(int i=0;i<=a.length;i++)d[i][0]=i;for(int j=0;j<=b.length;j++)d[0][j]=j;
        for(int i=1;i<=a.length;i++)for(int j=1;j<=b.length;j++)d[i][j]=Math.min(d[i-1][j-1]+(a[i-1]==b[j-1]?0:1),Math.min(d[i-1][j]+1,d[i][j-1]+1));
        Edit e=new Edit();int i=a.length,j=b.length;
        while(i>0||j>0) {
            if(i>0&&j>0&&d[i][j]==d[i-1][j-1]+(a[i-1]==b[j-1]?0:1)){if(a[i-1]!=b[j-1])e.substitutions++;i--;j--;}
            else if(i>0&&d[i][j]==d[i-1][j]+1){e.deletions++;i--;}else{e.insertions++;j--;}
        }
        return e;
    }
    static int[] scalars(String s) {
        for(int i=0;i<s.length();i++) if(Character.isSurrogate(s.charAt(i))) {
            FrozenDataset.require(Character.isHighSurrogate(s.charAt(i)) && i+1<s.length() && Character.isLowSurrogate(s.charAt(++i)),"Unpaired Unicode surrogate");
        }
        return s.codePoints().toArray();
    }
    static double accuracy(double errors,double characters) {return characters==0?Double.NaN:Math.max(0,1-errors/characters);}
    static final class FrameScore {
        int characters,substitutions,deletions,insertions,detachedExpected,detachedActual;
        boolean exact=true,regionGeometry=true,symbols=true,state=true;
        final JsonArray differences=new JsonArray();
        int errors(){return substitutions+deletions+insertions;}
        void fail(String reason){exact=false;differences.add(reason);}
    }
    static Map<String,JsonObject> regions(JsonArray array) {
        Map<String,JsonObject> result=new LinkedHashMap<>();
        for(JsonElement e:array) {
            JsonObject r=e.getAsJsonObject();JsonArray rect=r.getAsJsonArray("gridRect");
            FrozenDataset.require(rect.size()==4 && r.has("sourceRect") && r.has("markCells"),"Incomplete actual region geometry");
            String key=r.get("role").getAsString()+":"+rect.get(1).getAsBigDecimal().stripTrailingZeros()+":"+rect.get(0).getAsBigDecimal().stripTrailingZeros()+":"+r.get("readingOrder").getAsInt();
            FrozenDataset.require(result.put(key,r)==null,"Duplicate region match key: "+key);
            scalars(r.get("text").getAsString());
        }
        return result;
    }
    static JsonArray array(JsonObject object,String key) {return object!=null && object.has(key) && !object.get(key).isJsonNull()?object.getAsJsonArray(key):new JsonArray();}
    static JsonElement fields(JsonObject value,String... fields) {
        JsonObject out=new JsonObject();if(value!=null)for(String field:fields) if(value.has(field) && !value.get(field).isJsonNull())out.add(field,value.get(field));return out;
    }
    static JsonArray symbolList(JsonElement value) {
        JsonArray result=new JsonArray();if(value==null || value.isJsonNull())return result;
        JsonArray a=new JsonArray();if(value.isJsonArray())a=value.getAsJsonArray();else a.add(value);
        for(JsonElement element:a) {
            JsonObject symbol=fields(element.getAsJsonObject(),"kind","style","gridRect","sourceRect").getAsJsonObject();
            // Approved annotation-descriptor-equivalence-v1: same original outlined triangle.
            if(symbol.has("style") && "outlined_right".equals(symbol.get("style").getAsString()))symbol.addProperty("style","outline_right");
            result.add(symbol);
        }
        return result;
    }
    static JsonObject cursor(JsonObject state) {
        JsonObject in=state!=null && state.has("cursor")?state.getAsJsonObject("cursor"):new JsonObject(),out=new JsonObject();
        out.add("menuCursors",symbolList(in.get("menuCursors")));
        boolean arrow=in.has("downArrowVisible") && in.get("downArrowVisible").getAsBoolean();out.addProperty("downArrowVisible",arrow);
        if(arrow) {
            if(in.has("downArrow"))out.add("downArrow",fields(in.getAsJsonObject("downArrow"),"style","gridRect","sourceRect"));
            else out.add("downArrow",fields(in,"gridRect","sourceRect")); // unchanged approved trial representation
        }
        out.add("additionalDownArrows",symbolList(in.get("additionalDownArrows")));return out;
    }
    static FrameScore score(JsonObject expected,JsonArray actualRegions,JsonObject visible,boolean missing) {
        FrameScore result=new FrameScore();Map<String,JsonObject> actual=regions(actualRegions),gt=regions(expected.getAsJsonArray("regions"));
        for(Map.Entry<String,JsonObject> entry:gt.entrySet()) {
            JsonObject e=entry.getValue(),a=actual.remove(entry.getKey());String text=e.get("text").getAsString();result.characters+=scalars(text).length;
            Edit d=edit(text,a==null?"":a.get("text").getAsString());result.substitutions+=d.substitutions;result.deletions+=d.deletions;result.insertions+=d.insertions;
            if(d.errors()>0)result.fail("text:"+entry.getKey());
            if(a==null || !fields(e,"gridRect","sourceRect","markCells","logicalRect","renderUnits").equals(fields(a,"gridRect","sourceRect","markCells","logicalRect","renderUnits"))) {result.regionGeometry=false;result.fail("geometry_or_missing_field:"+entry.getKey());}
        }
        for(Map.Entry<String,JsonObject> entry:actual.entrySet()){result.insertions+=scalars(entry.getValue().get("text").getAsString()).length;result.regionGeometry=false;result.fail("extra_region:"+entry.getKey());}
        JsonArray detached=symbolList(expected.get("detachedMarks")),actualDetached=symbolList(visible==null?null:visible.get("detachedMarks"));result.detachedExpected=detached.size();result.detachedActual=actualDetached.size();
        result.symbols=detached.equals(actualDetached)&&cursor(expected).equals(cursor(visible));if(!result.symbols)result.fail("cursor_or_detached_mark");
        result.state=visible!=null && expected.get("kind").equals(visible.get("kind"));if(!result.state)result.fail("visible_state_kind");
        if(missing)result.fail("missing_output");return result;
    }
}
