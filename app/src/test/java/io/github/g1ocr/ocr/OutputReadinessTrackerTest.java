package io.github.g1ocr.ocr;

import org.junit.Test;
import static org.junit.Assert.*;

public class OutputReadinessTrackerTest {
    private OutputReadinessTracker.Observation observation(long pts,String text,String kind) {
        OutputReadinessTracker.Observation o=new OutputReadinessTracker.Observation(pts,1,1000,kind);
        o.wholeKey="menu1:"+text;o.messageKey=text+"@14";o.hasText=!text.isEmpty();o.hasMessage=o.hasText;
        o.unknown=text.indexOf('\ufffd')>=0;return o;
    }
    private OutputReadinessTracker.Decision feed(OutputReadinessTracker p,long pts,String text,String kind) {
        return p.observe(observation(pts,text,kind));
    }
    @Test public void filledMenuNeverCertifiesGrowingMessage() {
        OutputReadinessTracker p=new OutputReadinessTracker();
        assertFalse(feed(p,0,"あ","menu_complete").ready);
        assertFalse(feed(p,100,"あい","menu_complete").ready);
        assertFalse(feed(p,199,"あい","menu_complete").ready);
        assertTrue(feed(p,200,"あい","menu_complete").ready);
    }
    @Test public void confirmedMessageSurvivesMenuOperationsWithoutPublishingDuringThem() {
        for(String kind:new String[]{"submenu_partial_redraw","submenu_cursor_loading","scroll_partial_redraw",
                "scroll_duplicate_row","scroll_cursor_transition","cursor_transition","menu_cursor_loading",
                "money_temporarily_cleared","menu_partial_redraw"}) {
            OutputReadinessTracker p=new OutputReadinessTracker();feed(p,0,"あい","menu_complete");
            assertTrue(feed(p,100,"あい","menu_complete").ready);
            OutputReadinessTracker.Decision held=feed(p,101,"あい",kind);
            assertFalse(held.ready);assertFalse(held.fire);assertTrue(held.messageConfirmed);
            assertTrue(feed(p,102,"あい","menu_complete").fire);
        }
    }
    @Test public void unsafeMenuFramesDoNotConfirmANewMessage() {
        OutputReadinessTracker p=new OutputReadinessTracker();feed(p,0,"あい","submenu_cursor_loading");
        feed(p,1000,"あい","submenu_cursor_loading");
        assertFalse(feed(p,1001,"あい","menu_complete").ready);
    }
    @Test public void messageScrollClearAndUnknownStatesRevokeCompletion() {
        for(String kind:new String[]{"scrolling","message_clearing","textbox_empty","unexpected_state"}) {
            OutputReadinessTracker p=new OutputReadinessTracker();feed(p,0,"あい","menu_complete");feed(p,100,"あい","menu_complete");
            assertFalse(feed(p,101,"あい",kind).ready);assertFalse(feed(p,102,"あい","menu_complete").ready);
        }
    }
    @Test public void changedMessageIdentityRevokesButChangedMenuDoesNot() {
        OutputReadinessTracker p=new OutputReadinessTracker();feed(p,0,"あい","menu_complete");feed(p,100,"あい","menu_complete");
        OutputReadinessTracker.Observation menu=observation(101,"あい","menu_complete");menu.wholeKey="menu2";
        assertTrue(p.observe(menu).fire);
        OutputReadinessTracker.Observation mark=observation(102,"あい","menu_complete");mark.messageKey+="new mark";
        assertFalse(p.observe(mark).ready);
    }
    @Test public void pcSuspensionBlocksStableTextUntilCurrentChildOrMainArrow() {
        OutputReadinessTracker p=new OutputReadinessTracker();
        for(int t:new int[]{0,100}){OutputReadinessTracker.Observation o=observation(t,"あい","menu_complete");
            o.pcPrompt=true;o.parentHollow=true;assertFalse(p.observe(o).ready);}
        OutputReadinessTracker.Observation child=observation(101,"あい","menu_complete");child.pcPrompt=true;child.parentHollow=true;child.activeChild=true;
        assertTrue(p.observe(child).ready);
        p.reset();OutputReadinessTracker.Observation arrow=observation(0,"あい","message_loading");arrow.pcPrompt=true;arrow.parentHollow=true;arrow.mainArrow=true;
        assertTrue(p.observe(arrow).ready);
    }
    @Test public void unknownGlyphAndSingleFrameAreNotConfirmedBySilence() {
        OutputReadinessTracker p=new OutputReadinessTracker();feed(p,0,"\ufffd","menu_complete");assertFalse(feed(p,1000,"\ufffd","menu_complete").ready);
        p.reset();assertFalse(feed(p,0,"あい","menu_complete").ready);assertFalse(feed(p,1000,"","textbox_empty").ready);
    }
    @Test public void resetReversedOrChangedTimeBaseCannotInheritCompletion() {
        for(int mode=0;mode<3;mode++) {
            OutputReadinessTracker p=new OutputReadinessTracker();feed(p,0,"あい","menu_complete");feed(p,100,"あい","menu_complete");
            if(mode==0)p.reset();
            OutputReadinessTracker.Observation next=mode==2?new OutputReadinessTracker.Observation(101,1,30,"menu_complete"):observation(mode==1?99:101,"あい","menu_complete");
            next.wholeKey="menu1:あい";next.messageKey="あい@14";next.hasMessage=true;next.hasText=true;
            assertFalse(p.observe(next).ready);
        }
    }
    @Test public void ptsThresholdUsesExactRationalTimeAndDoesNotOverflow() {
        for(int denominator:new int[]{19,24,30}) {
            OutputReadinessTracker p=new OutputReadinessTracker();
            for(int i=0;i<=3;i++) {
                OutputReadinessTracker.Observation o=new OutputReadinessTracker.Observation(i,1,denominator,"message_loading");
                o.hasText=true;o.hasMessage=true;o.wholeKey="x";o.messageKey="x";
                assertEquals(i*1000>=100*denominator,p.observe(o).ready);
            }
        }
        OutputReadinessTracker p=new OutputReadinessTracker();feed(p,Long.MIN_VALUE,"あい","menu_complete");
        assertTrue(feed(p,Long.MAX_VALUE,"あい","menu_complete").ready);
    }
    @Test public void menuOnlyPagesRemainImmediateAndRepeatedCandidateEmitsOnce() {
        OutputReadinessTracker p=new OutputReadinessTracker();OutputReadinessTracker.Observation o=observation(0,"あい","menu_complete");o.hasMessage=false;
        assertTrue(p.observe(o).fire);o=observation(1,"あい","menu_complete");o.hasMessage=false;assertFalse(p.observe(o).fire);
    }
}
