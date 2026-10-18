package io.github.g1ocr.ocr;

import io.github.g1ocr.capture.SourceFrame;
import java.util.List;

/** Tracks selection availability and copying inside currently enclosed item lists. */
final class BackpackTransitionTracker {
    private boolean observed,selected;
    private long pts,numerator,denominator;
    private int width,height;
    private MenuFieldReader.Layout previousMenu;
    private String[] previousRows;

    void reset(){observed=false;selected=false;previousMenu=null;previousRows=null;}

    String observe(SourceFrame frame,MenuFieldReader.Layout menu,List<BoxTextDetector.Box> boxes,
                   JapaneseOcrEngine.VisibleState state,PixelGlyphReader.Frame pixels) {
        if(observed&&(frame.getPts()<=pts||frame.getWidth()!=width||frame.getHeight()!=height
                ||frame.getTimeBaseNumerator()!=numerator||frame.getTimeBaseDenominator()!=denominator||menu!=previousMenu))reset();
        boolean itemMenu=menu==MenuFieldReader.Layout.BACKPACK||menu==MenuFieldReader.Layout.PC_ITEMS
                ||menu==MenuFieldReader.Layout.SHOP_BUY||menu==MenuFieldReader.Layout.SHOP_SELL_LIST;
        boolean enclosed=itemMenu&&boxes.stream().anyMatch(
                b->b.left==4&&b.top==2&&b.right==20&&b.bottom==13);
        if(!enclosed){reset();return null;}
        int cursors=0;
        for(JapaneseOcrEngine.Shape s:state.cursor.menuCursors)
            if(s.gridRect[0]==5&&s.gridRect[1]>=4&&s.gridRect[1]<=10&&s.gridRect[1]%2==0)cursors++;
        boolean residue=false;
        for(JapaneseOcrEngine.Shape s:state.detachedMarks)
            if(s.gridRect[0]>=6&&s.gridRect[0]<19&&s.gridRect[1]>=3&&s.gridRect[1]<=9&&s.gridRect[1]%2==1)residue=true;
        String[] rows=new String[4];
        for(int i=0;i<4;i++){StringBuilder row=new StringBuilder();for(int x=6;x<19;x++)row.append(pixels.cell(x,4+i*2).candidates).append(';');rows[i]=row.toString();}
        boolean duplicateCopy=false;
        if(previousRows!=null)for(int i=0;i<3;i++)
            if(!previousRows[i].equals(previousRows[i+1])&&rows[i].equals(previousRows[i+1])&&rows[i+1].equals(previousRows[i+1]))duplicateCopy=true;
        String kind;
        if(menu==MenuFieldReader.Layout.BACKPACK)kind=cursors==0?(selected?"menu_redraw_or_cursor_transition":"submenu_cursor_loading"):
                cursors>1||residue?"menu_redraw_or_cursor_transition":"menu_complete";
        else if(!selected&&cursors==0)kind="submenu_cursor_loading";
        else if(cursors>1)kind="dual_cursor_transition";
        else if(duplicateCopy)kind="scroll_duplicate_row";
        else if(residue)kind="scroll_partial_redraw";
        else if(cursors==0)kind=menu==MenuFieldReader.Layout.SHOP_BUY?"scroll_cursor_transition":"cursor_transition";
        else kind=null; // An intact selection does not override a concurrent message state.
        if(cursors==1)selected=true;
        observed=true;pts=frame.getPts();width=frame.getWidth();height=frame.getHeight();
        numerator=frame.getTimeBaseNumerator();denominator=frame.getTimeBaseDenominator();
        previousMenu=menu;previousRows=rows;
        return kind;
    }
}
