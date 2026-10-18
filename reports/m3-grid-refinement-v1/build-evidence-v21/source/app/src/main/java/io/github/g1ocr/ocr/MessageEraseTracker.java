package io.github.g1ocr.ocr;

import io.github.g1ocr.capture.SourceFrame;
import java.util.*;

/** Tracks an observed removal of marks, never restores the previous text. */
final class MessageEraseTracker {
    private String bases;
    private List<String> marks;
    private boolean complete,clearing;
    private long pts,numerator,denominator;
    private int width,height;

    void reset(){bases=null;marks=null;complete=false;clearing=false;}

    boolean observe(SourceFrame source,PixelGlyphReader.Frame pixels,List<BoxTextDetector.Line> lines,
                    boolean currentComplete,boolean scrolling) {
        if(bases!=null&&(source.getPts()<=pts||source.getWidth()!=width||source.getHeight()!=height
                ||source.getTimeBaseNumerator()!=numerator||source.getTimeBaseDenominator()!=denominator))reset();
        if(scrolling||lines.isEmpty()||lines.size()>2){reset();return false;}
        StringBuilder signature=new StringBuilder();List<String> currentMarks=new ArrayList<>();
        for(BoxTextDetector.Line line:lines){
            if((line.top!=14&&line.top!=16)||line.left<1||line.right>19){reset();return false;}
            signature.append(line.left).append(':').append(line.top).append(':').append(line.right).append(';');
            for(int x=line.left;x<line.right;x++){
                PixelGlyphReader.Cell base=pixels.cell(x,line.top),above=pixels.cell(x,line.top-1);
                if(!base.valid||base.candidates.isEmpty()||!above.valid){reset();return false;}
                signature.append(base.candidates).append(';');
                String mark=above.blank?"":above.candidates.size()==1?above.candidates.get(0):"unknown";
                if(!mark.isEmpty()&&!"゛".equals(mark)&&!"゜".equals(mark)){reset();return false;}
                currentMarks.add(mark);
            }
        }
        String currentBases=signature.toString();boolean removed=false,onlyRemoved=true;
        if(currentBases.equals(bases)&&marks.size()==currentMarks.size()){
            for(int i=0;i<marks.size();i++)if(!marks.get(i).equals(currentMarks.get(i))){
                if(!marks.get(i).isEmpty()&&currentMarks.get(i).isEmpty())removed=true;
                else onlyRemoved=false;
            }
            clearing=onlyRemoved&&((complete&&removed)||clearing);
        }else clearing=false;
        bases=currentBases;marks=currentMarks;complete=currentComplete&&!clearing;
        pts=source.getPts();width=source.getWidth();height=source.getHeight();
        numerator=source.getTimeBaseNumerator();denominator=source.getTimeBaseDenominator();
        return clearing;
    }
}
