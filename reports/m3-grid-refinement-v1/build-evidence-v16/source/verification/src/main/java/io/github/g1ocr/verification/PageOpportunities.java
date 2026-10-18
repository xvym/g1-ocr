package io.github.g1ocr.verification;

import com.google.gson.*;
import java.util.*;

/** Explicit pre-OCR opportunity identity keeps cosmetic flashes from multiplying page counts. */
final class PageOpportunities {
    private final boolean explicit;
    private final Map<String,JsonObject> first=new LinkedHashMap<>();
    private String lastId;
    private int intervals;
    PageOpportunities(boolean explicit){this.explicit=explicit;}
    void add(JsonObject state){
        if(!state.get("completePage").getAsBoolean())return;
        intervals++;
        require(!explicit || state.has("pageOpportunityId"),"Explicit page identity missing");
        String id=explicit?state.get("pageOpportunityId").getAsString():state.get("stateId").getAsString();
        require(!id.trim().isEmpty(),"Empty opportunity identity");
        if(first.containsKey(id)){
            require(id.equals(lastId),"An ended page cannot silently reuse an earlier opportunity");
            JsonObject previous=first.get(id);
            require(nominal(previous.getAsJsonArray("regions")).equals(nominal(state.getAsJsonArray("regions"))),"Opportunity cannot merge changed text/fields/nominal positions");
            require(nominal(previous.getAsJsonObject("cursor").get("menuCursors")).equals(nominal(state.getAsJsonObject("cursor").get("menuCursors"))),"Opportunity cannot merge changed menu selection");
        }else first.put(id,state);
        lastId=id;
    }
    int size(){return first.size();}
    int intervals(){return intervals;}
    // Source rectangles are checked against each state's raster offset by GtGeometry.
    // Page identity follows literal fields and their nominal cells through a shake.
    private static JsonElement nominal(JsonElement value){
        JsonElement copy=value.deepCopy();
        if(copy.isJsonObject())copy.getAsJsonObject().remove("sourceRect");
        else if(copy.isJsonArray())for(JsonElement item:copy.getAsJsonArray())if(item.isJsonObject())item.getAsJsonObject().remove("sourceRect");
        return copy;
    }
    private static void require(boolean ok,String message){if(!ok)throw new IllegalArgumentException(message);}
}
