package io.github.g1ocr.verification;
import com.google.gson.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class PageOpportunitiesTest {
    private JsonObject page(String id,String text,boolean arrow){JsonObject p=JsonParser.parseString("{\"completePage\":true,\"stateId\":\"state\",\"regions\":[{\"text\":\""+text+"\"}],\"cursor\":{\"menuCursors\":[]}}").getAsJsonObject();p.addProperty("pageOpportunityId",id);p.getAsJsonObject("cursor").addProperty("downArrowVisible",arrow);return p;}
    @Test public void arrowFlashKeepsTwoStatesButOnePage(){PageOpportunities p=new PageOpportunities(true);p.add(page("one","abc",true));p.add(page("one","abc",false));assertEquals(2,p.intervals());assertEquals(1,p.size());}
    @Test(expected=IllegalArgumentException.class) public void differentTextCannotBeHiddenInsideOneOpportunity(){PageOpportunities p=new PageOpportunities(true);p.add(page("one","abc",true));p.add(page("one","abd",false));}
    @Test(expected=IllegalArgumentException.class) public void reappearingSameTextIsAnotherOpportunity(){PageOpportunities p=new PageOpportunities(true);p.add(page("one","abc",true));p.add(page("two","def",true));p.add(page("one","abc",true));}
    @Test public void checkedRasterShakeDoesNotMultiplySameLiteralPage(){
        PageOpportunities p=new PageOpportunities(true);JsonObject a=page("one","abc",false),b=a.deepCopy();
        a.getAsJsonArray("regions").get(0).getAsJsonObject().add("sourceRect",JsonParser.parseString("[240,0,360,61]"));
        b.getAsJsonArray("regions").get(0).getAsJsonObject().add("sourceRect",JsonParser.parseString("[247,0,368,61]"));
        b.add("renderOffsetLogicalPixels",JsonParser.parseString("[2,0]"));p.add(a);p.add(b);assertEquals(1,p.size());
    }
    @Test(expected=IllegalArgumentException.class) public void nominalCellMovementStillCreatesAnotherPage(){
        PageOpportunities p=new PageOpportunities(true);JsonObject a=page("one","abc",false),b=a.deepCopy();
        a.getAsJsonArray("regions").get(0).getAsJsonObject().add("gridRect",JsonParser.parseString("[3,1,6,2]"));
        b.getAsJsonArray("regions").get(0).getAsJsonObject().add("gridRect",JsonParser.parseString("[4,1,7,2]"));p.add(a);p.add(b);
    }
}
