package io.github.g1ocr.ocr;

import io.github.g1ocr.capture.SourceFrame;
import java.util.List;

/** Tracks selection availability inside a currently enclosed backpack list. */
final class BackpackTransitionTracker {
    private boolean observed,selected;
    private long pts,numerator,denominator;
    private int width,height;

    void reset(){observed=false;selected=false;}

    String observe(SourceFrame frame,MenuFieldReader.Layout menu,List<BoxTextDetector.Box> boxes,
                   JapaneseOcrEngine.VisibleState state) {
        if(observed&&(frame.getPts()<=pts||frame.getWidth()!=width||frame.getHeight()!=height
                ||frame.getTimeBaseNumerator()!=numerator||frame.getTimeBaseDenominator()!=denominator))reset();
        boolean enclosed=menu==MenuFieldReader.Layout.BACKPACK&&boxes.stream().anyMatch(
                b->b.left==4&&b.top==2&&b.right==20&&b.bottom==13);
        if(!enclosed){reset();return null;}
        int cursors=0;
        for(JapaneseOcrEngine.Shape s:state.cursor.menuCursors)
            if(s.gridRect[0]==5&&s.gridRect[1]>=4&&s.gridRect[1]<=10&&s.gridRect[1]%2==0)cursors++;
        boolean residue=false;
        for(JapaneseOcrEngine.Shape s:state.detachedMarks)
            if(s.gridRect[0]>=6&&s.gridRect[0]<19&&s.gridRect[1]>=3&&s.gridRect[1]<=9&&s.gridRect[1]%2==1)residue=true;
        String kind=cursors==0?(selected?"menu_redraw_or_cursor_transition":"submenu_cursor_loading"):
                cursors>1||residue?"menu_redraw_or_cursor_transition":"menu_complete";
        if(cursors==1)selected=true;
        observed=true;pts=frame.getPts();width=frame.getWidth();height=frame.getHeight();
        numerator=frame.getTimeBaseNumerator();denominator=frame.getTimeBaseDenominator();
        return kind;
    }
}
