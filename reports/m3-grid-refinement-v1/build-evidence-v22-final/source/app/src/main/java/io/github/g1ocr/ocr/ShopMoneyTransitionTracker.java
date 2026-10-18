package io.github.g1ocr.ocr;

import io.github.g1ocr.capture.SourceFrame;

/** Detects a visible amount being erased inside the still-present merchant panel. */
final class ShopMoneyTransitionTracker {
    private boolean observed,amountSeen;
    private long pts,numerator,denominator;
    private int width,height;
    void reset(){observed=false;amountSeen=false;}
    boolean observe(SourceFrame frame,MenuFieldReader.Layout menu,PixelGlyphReader.Frame pixels){
        if(observed&&(frame.getPts()<=pts||frame.getWidth()!=width||frame.getHeight()!=height
                ||frame.getTimeBaseNumerator()!=numerator||frame.getTimeBaseDenominator()!=denominator))reset();
        if(menu!=MenuFieldReader.Layout.SHOP_PARENT||!panel(pixels)){reset();return false;}
        boolean blank=true;StringBuilder value=new StringBuilder();
        for(int x=12;x<19;x++){
            PixelGlyphReader.Cell cell=pixels.cell(x,1);
            blank&=cell.valid&&cell.blank;
            if(!cell.valid||cell.candidates.size()!=1){reset();return false;}
            value.append(cell.candidates.get(0));
        }
        boolean cleared=amountSeen&&blank;
        if(!blank)amountSeen=value.toString().trim().matches("[0-9]+円");
        observed=true;pts=frame.getPts();numerator=frame.getTimeBaseNumerator();denominator=frame.getTimeBaseDenominator();width=frame.getWidth();height=frame.getHeight();
        return cleared;
    }
    private static boolean has(PixelGlyphReader.Frame f,int x,int y,String s){return f.cell(x,y).valid&&f.cell(x,y).candidates.size()==1&&f.cell(x,y).candidates.contains(s);}
    private static boolean panel(PixelGlyphReader.Frame f){
        if(!has(f,11,0,"┌")||!has(f,19,0,"┐")||!has(f,11,1,"│")||!has(f,19,1,"│")
                ||!has(f,11,2,"└")||!has(f,19,2,"┘")||!has(f,14,0,"お")||!has(f,15,0,"か")||!has(f,16,0,"ね"))return false;
        for(int x=12;x<19;x++)if(!has(f,x,2,"─"))return false;
        for(int x:new int[]{12,13,17,18})if(!has(f,x,0,"─"))return false;
        return true;
    }
}
