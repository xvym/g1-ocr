package io.github.g1ocr.ocr;

import io.github.g1ocr.capture.SourceFrame;
import java.math.BigInteger;
import java.util.*;

/** Causal publication gate, separate from the currently visible scene classification.
 * Only current OCR observations and source time enter this tracker. */
public final class OutputReadinessTracker {
    private static final Set<String> MESSAGE_ROLES = set("dialogue", "dialogue_line", "battle_message",
            "pc_prompt", "shop_prompt", "shop_message", "party_prompt");
    private static final Set<String> COMPLETE = set("message_complete", "message_fading", "menu_complete", "status_page_complete");
    // These describe menu/list operations, not scrolling or clearing of the message.
    private static final Set<String> HOLD = set("submenu_partial_redraw", "submenu_cursor_loading",
            "scroll_partial_redraw", "scroll_duplicate_row", "scroll_cursor_transition", "cursor_transition",
            "menu_cursor_loading", "money_temporarily_cleared", "menu_partial_redraw");
    private static Set<String> set(String... values) { return new HashSet<>(Arrays.asList(values)); }

    public static final class Decision {
        public final boolean ready, fire, invalidate, menuReady, messageReady, hasMessage, mainArrow;
        public final boolean parentHollow, activeChild, onlyHollow, suspendedPc, messageConfirmed;
        public final String reason, stableSincePts;
        public final int samples;
        public final double stableAgeMs;
        private Decision(Observation o, boolean ready, boolean fire, boolean invalidate, boolean messageReady,
                         boolean suspended, boolean confirmed, long since, int samples, double age) {
            this.ready=ready;this.fire=fire;this.invalidate=invalidate;this.messageReady=messageReady;
            menuReady="menu_complete".equals(o.kind);hasMessage=o.hasMessage;mainArrow=o.mainArrow;
            parentHollow=o.parentHollow;activeChild=o.activeChild;onlyHollow=o.onlyHollow;suspendedPc=suspended;
            messageConfirmed=confirmed;stableSincePts=Long.toString(since);this.samples=samples;stableAgeMs=age;
            boolean genuine="message_complete".equals(o.kind)||"message_fading".equals(o.kind);
            reason=suspended&&!mainArrow?"suspended_parent_without_active_child":ready?
                    hasMessage?mainArrow||genuine?"message_arrow_or_existing_complete":"message_confirmed_separately":
                    "v18_menu_or_status_complete":"not_ready";
        }
    }

    // Bounded to the current candidate and current message. No previous text is copied into output.
    private String priorMessage, previousWhole, clockMessage;
    private boolean confirmed, emitted, observed;
    private long lastPts, numerator, denominator, since;
    private int samples, width, height;
    public void reset() {
        priorMessage=null;previousWhole=null;clockMessage=null;confirmed=false;emitted=false;
        observed=false;samples=0;width=0;height=0;
    }

    Decision observe(SourceFrame frame, List<JapaneseOcrEngine.Region> regions,
                     JapaneseOcrEngine.VisibleState state, List<BoxTextDetector.Box> boxes) {
        if(width!=0&&(width!=frame.getWidth()||height!=frame.getHeight()))reset();
        Observation o=new Observation(frame.getPts(),frame.getTimeBaseNumerator(),frame.getTimeBaseDenominator(),state.kind);
        List<JapaneseOcrEngine.Region> messages=new ArrayList<>();
        for(JapaneseOcrEngine.Region r:regions) {
            o.hasText|=!r.text.isEmpty();
            if(MESSAGE_ROLES.contains(r.role)&&r.gridRect[1]>=13&&r.gridRect[3]<=17)messages.add(r);
        }
        for(BoxTextDetector.Box b:boxes)if(b.left==0&&b.top==12&&b.right==20&&b.bottom==18) {
            o.hasMessage=messages.stream().anyMatch(r->!r.text.isEmpty());
            for(BoxTextDetector.Symbol symbol:b.symbols)if("down_arrow".equals(symbol.kind))o.mainArrow=true;
        }
        o.mainArrow&=o.hasMessage;
        o.onlyHollow=!state.cursor.menuCursors.isEmpty();
        for(JapaneseOcrEngine.Shape s:state.cursor.menuCursors) {
            o.parentHollow|="outline_right".equals(s.style)&&s.gridRect[0]==1;
            o.activeChild|="filled_right".equals(s.style)&&s.gridRect[0]>1;
            o.onlyHollow&="outline_right".equals(s.style);
        }
        for(JapaneseOcrEngine.Region r:messages) {
            o.pcPrompt|="pc_prompt".equals(r.role);o.unknown|=r.text.indexOf('\ufffd')>=0;
        }
        StringBuilder whole=new StringBuilder(), message=new StringBuilder();
        value(whole,regions.size());
        for(JapaneseOcrEngine.Region r:regions)region(whole,r,r.readingOrder);
        value(whole,"cursors");value(whole,state.cursor.menuCursors.size());
        for(JapaneseOcrEngine.Shape s:state.cursor.menuCursors)shape(whole,s,true);
        value(whole,"marks");value(whole,state.detachedMarks.size());
        for(JapaneseOcrEngine.Shape s:state.detachedMarks)shape(whole,s,false);
        if(o.hasMessage) {
            value(message,messages.size());
            for(int i=0;i<messages.size();i++)region(message,messages.get(i),i);
            value(message,"marks");
            for(JapaneseOcrEngine.Shape s:state.detachedMarks)if(s.gridRect[0]>=1&&s.gridRect[2]<=19&&s.gridRect[1]>=13&&s.gridRect[3]<=17)shape(message,s,false);
        }
        o.wholeKey=whole.toString();o.messageKey=message.toString();Decision result=observe(o);
        width=frame.getWidth();height=frame.getHeight();return result;
    }

    static final class Observation {
        final long pts, numerator, denominator;
        final String kind;
        String wholeKey, messageKey;
        boolean hasText, hasMessage, unknown, mainArrow, parentHollow, activeChild, onlyHollow, pcPrompt;
        Observation(long pts,long numerator,long denominator,String kind) {
            if(numerator<=0||denominator<=0)throw new IllegalArgumentException("Invalid source time base");
            this.pts=pts;this.numerator=numerator;this.denominator=denominator;this.kind=kind;
        }
    }

    Decision observe(Observation o) {
        if(observed&&(o.pts<=lastPts||o.numerator!=numerator||o.denominator!=denominator))reset();
        boolean changed=!Objects.equals(previousWhole,o.wholeKey);
        boolean eligible=o.hasText&&(COMPLETE.contains(o.kind)||"message_loading".equals(o.kind));
        boolean messageEligible=o.hasMessage&&eligible;
        if(!Objects.equals(clockMessage,o.messageKey)||!messageEligible||samples==0){since=o.pts;samples=0;}
        clockMessage=o.messageKey;
        if(messageEligible&&samples<Integer.MAX_VALUE)samples++;
        BigInteger elapsed=BigInteger.valueOf(o.pts).subtract(BigInteger.valueOf(since))
                .multiply(BigInteger.valueOf(o.numerator)).multiply(BigInteger.valueOf(1000));
        boolean stable=samples>=2&&!o.unknown&&elapsed.compareTo(BigInteger.valueOf(o.denominator).multiply(BigInteger.valueOf(100)))>=0;
        if(!Objects.equals(priorMessage,o.messageKey)||(!eligible&&!HOLD.contains(o.kind))||!o.hasMessage)confirmed=false;
        priorMessage=o.messageKey;
        boolean genuine="message_complete".equals(o.kind)||"message_fading".equals(o.kind);
        if(messageEligible&&(o.mainArrow||genuine||stable))confirmed=true;
        boolean suspended=o.hasMessage&&o.pcPrompt&&o.parentHollow&&!o.activeChild;
        boolean blocked=suspended&&!o.mainArrow;
        boolean messageReady=messageEligible&&confirmed&&!blocked;
        boolean ready=eligible&&(o.hasMessage?messageReady:COMPLETE.contains(o.kind));
        boolean invalidate=changed||!eligible||blocked;
        if(invalidate)emitted=false;
        boolean fire=ready&&!emitted;if(fire)emitted=true;
        previousWhole=o.wholeKey;lastPts=o.pts;numerator=o.numerator;denominator=o.denominator;observed=true;
        return new Decision(o,ready,fire,invalidate,messageReady,suspended,confirmed,since,samples,
                elapsed.doubleValue()/o.denominator);
    }

    // Length framing avoids collisions without depending on a JSON library or source rectangle jitter.
    private static void value(StringBuilder b,Object v) {String s=String.valueOf(v);b.append(s.length()).append(':').append(s);}
    private static void shape(StringBuilder b,JapaneseOcrEngine.Shape s,boolean cursor) {
        value(b,cursor?s.style:s.kind);value(b,Arrays.toString(s.gridRect));
    }
    private static void region(StringBuilder b,JapaneseOcrEngine.Region r,int order) {
        value(b,"region");value(b,r.role);value(b,order);value(b,r.text);value(b,Arrays.toString(r.gridRect));
        value(b,r.markCells.size());for(JapaneseOcrEngine.Mark m:r.markCells){value(b,m.gridColumn);value(b,m.gridRow);value(b,m.kind);}
        value(b,Arrays.toString(r.logicalRect));
        value(b,r.renderUnits==null?"null":r.renderUnits.size());
        if(r.renderUnits!=null)for(JapaneseOcrEngine.RenderUnit u:r.renderUnits){value(b,u.text);value(b,u.kind);value(b,Arrays.toString(u.logicalRect));}
    }
}
