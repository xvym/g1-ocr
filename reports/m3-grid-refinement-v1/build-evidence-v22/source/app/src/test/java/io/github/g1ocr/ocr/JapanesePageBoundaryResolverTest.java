package io.github.g1ocr.ocr;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class JapanesePageBoundaryResolverTest {
    private JapanesePageBoundaryResolver.Entry entry(String upper,String lower,String control){return new JapanesePageBoundaryResolver.Entry(upper,lower,control,"unit-source","commit","script.asm",3,4,"sha");}
    private List<JapanesePageBoundaryResolver.Line> lines(String upper,String lower){return Arrays.asList(new JapanesePageBoundaryResolver.Line("dialogue",upper,1,14,0),new JapanesePageBoundaryResolver.Line("dialogue",lower,1,16,1));}
    @Test public void exactTwoLineLiteralReportsSourceBoundaryWithoutAlteringObservation(){
        for(String control:Arrays.asList("paragraph_wait","scroll_wait","string_end","done","prompt")){
            JapanesePageBoundaryResolver.Entry e=entry("AB","CD",control);JapanesePageBoundaryResolver resolver=new JapanesePageBoundaryResolver(Collections.singletonList(e));
            List<JapanesePageBoundaryResolver.Line> observed=lines("AB","CD");JapanesePageBoundaryResolver.Resolution r=resolver.resolve("message_loading",observed,false);
            assertNotNull(r);assertEquals("message_complete",r.kind);assertEquals("message_loading",r.rawKind);assertSame(e,r.matchedSources.get(0));assertEquals("AB",observed.get(0).text);assertEquals("CD",observed.get(1).text);
        }
    }
    @Test public void competingLongerPageAndTruncatedOrChangedTextAbstain(){
        JapanesePageBoundaryResolver r=new JapanesePageBoundaryResolver(Arrays.asList(entry("AB","CD","done"),entry("AB","CDE","prompt")));
        assertNull(r.resolve("message_loading",lines("AB","CD"),false));assertNull(r.resolve("message_loading",lines("AB","C"),false));assertNull(r.resolve("message_loading",lines("AC","CDE"),false));
        assertNotNull(r.resolve("message_loading",lines("AB","CDE"),false));
    }
    @Test public void boundedNamesCannotConsumePunctuationSpacesOrUnseenSuffixes(){
        JapanesePageBoundaryResolver r=new JapanesePageBoundaryResolver(Collections.singletonList(entry("<PLAYER>！","おわり","done")));
        assertNotNull(r.resolve("message_loading",lines("あ！","おわり"),false));assertNotNull(r.resolve("message_loading",lines("アイウエオ！","おわり"),false));
        for(String upper:Arrays.asList("！","アイウエオカ！","あ い！","あ?！"))assertNull(r.resolve("message_loading",lines(upper,"おわり"),false));
        JapanesePageBoundaryResolver repeated=new JapanesePageBoundaryResolver(Collections.singletonList(entry("<PLAYER>！","<PLAYER>！","done")));
        assertNull(repeated.resolve("message_loading",lines("あ！","い！"),false));
    }
    @Test public void imageKindsRolesRowsSelectionsAndUnknownsRemainSeparate(){
        JapanesePageBoundaryResolver r=new JapanesePageBoundaryResolver(Collections.singletonList(entry("AB","CD","done")));
        for(String kind:Arrays.asList("message_complete","scrolling","message_clearing","menu_complete","cursor_transition"))assertNull(r.resolve(kind,lines("AB","CD"),false));
        assertNull(r.resolve("message_loading",lines("AB","CD"),true));assertNull(r.resolve("message_loading",lines("AB","C\ufffd"),false));
        assertNull(r.resolve("message_loading",Collections.singletonList(lines("AB","CD").get(0)),false));
        for(int change=0;change<4;change++){List<JapanesePageBoundaryResolver.Line> wrong=new ArrayList<>(lines("AB","CD"));wrong.set(0,new JapanesePageBoundaryResolver.Line(change==0?"pc_prompt":"dialogue","AB",change==1?2:1,change==2?13:14,change==3?1:0));assertNull(r.resolve("message_loading",wrong,false));}
    }
}
