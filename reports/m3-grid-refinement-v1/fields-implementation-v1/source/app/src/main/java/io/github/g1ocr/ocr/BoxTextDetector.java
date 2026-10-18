package io.github.g1ocr.ocr;

import java.util.*;

/** Visible bordered text areas from glyph geometry, without script/message lookup. */
public final class BoxTextDetector {
    public static final class Line {
        public final int left,top,right;
        public final String rawText;
        public final String pixelCandidateText;
        public final List<List<String>> candidates;
        public final List<KanaScriptResolver.Resolution> candidateResolutions;
        private Line(int left,int row,int right,String text,String original,List<List<String>> candidates,List<KanaScriptResolver.Resolution> reasons) {
            this.left=left;top=row;this.right=right;rawText=text;pixelCandidateText=original;this.candidates=Collections.unmodifiableList(candidates);candidateResolutions=Collections.unmodifiableList(reasons);
        }
    }
    public static final class Symbol {
        public final int column,row;
        public final String kind;
        private Symbol(int x,int y,String kind){column=x;row=y;this.kind=kind;}
    }
    public static final class Box {
        public final int left,top,right,bottom;
        public final List<Line> lines;
        public final List<Symbol> symbols;
        private Box(int l,int t,int r,int b,List<Line> lines,List<Symbol> symbols){left=l;top=t;right=r;bottom=b;this.lines=Collections.unmodifiableList(lines);this.symbols=Collections.unmodifiableList(symbols);}
    }
    public List<Box> detect(PixelGlyphReader.Frame frame) {
        List<Box> boxes=new ArrayList<>();
        for(int t=0;t<16;t++)for(int l=0;l<18;l++)if(has(frame,l,t,"┌")) {
            for(int r=l+2;r<20;r++)if(has(frame,r,t,"┐"))for(int b=t+2;b<18;b++) {
                if(!has(frame,l,b,"└")||!has(frame,r,b,"┘"))continue;
                int horizontal=0,vertical=0;
                for(int x=l+1;x<r;x++){if(has(frame,x,t,"─"))horizontal++;if(has(frame,x,b,"─"))horizontal++;}
                for(int y=t+1;y<b;y++){if(has(frame,l,y,"│"))vertical++;if(has(frame,r,y,"│"))vertical++;}
                if(horizontal<2*(r-l-1)*.65||vertical<2*(b-t-1)*.65)continue;
                boxes.add(readBox(frame,l,t,r,b));
            }
        }
        boxes.sort(Comparator.comparingInt((Box b)->b.top).thenComparingInt(b->b.left));
        return Collections.unmodifiableList(boxes);
    }
    private static Box readBox(PixelGlyphReader.Frame frame,int l,int t,int r,int b) {
        List<Line> lines=new ArrayList<>();List<Symbol> symbols=new ArrayList<>();
        for(int y=t+1;y<b;y++) {
            List<List<String>> literals=new ArrayList<>();
            KanaScriptResolver.Result script=frame.contextualRow(y);
            for(int x=l+1;x<r;x++) {
                PixelGlyphReader.Cell cell=frame.cell(x,y);List<String> base=cell.candidates;
                String control=base.size()==1?base.get(0):"";
                if("▶".equals(control)||"▷".equals(control)||"▼".equals(control)) {
                    symbols.add(new Symbol(x,y,"▼".equals(control)?"down_arrow":"▶".equals(control)?"filled_right":"outline_right"));
                    literals.add(Collections.singletonList(" "));continue;
                }
                if("゛".equals(control)||"゜".equals(control)) {
                    boolean consumed=y+1<18&&!frame.literals(x,y+1).equals(frame.cell(x,y+1).candidates);
                    if(!consumed)symbols.add(new Symbol(x,y,"゛".equals(control)?"dakuten":"handakuten"));
                    literals.add(Collections.singletonList(" "));continue;
                }
                List<String> chars=script.literals.get(x);
                // Mark-only rows and border fragments are controls, never silently interpreted as words.
                if(!chars.isEmpty()&&chars.stream().allMatch(BoxTextDetector::structural))chars=Collections.singletonList(" ");
                literals.add(chars);
            }
            int first=0,last=literals.size();while(first<last&&space(literals.get(first)))first++;while(last>first&&space(literals.get(last-1)))last--;
            if(first==last)continue;
            StringBuilder raw=new StringBuilder(),original=new StringBuilder();List<List<String>> kept=new ArrayList<>();List<KanaScriptResolver.Resolution> reasons=new ArrayList<>();
            for(int i=first;i<last;i++) {List<String> chars=literals.get(i),before=frame.literals(l+1+i,y);raw.append(chars.size()==1?chars.get(0):"\ufffd");original.append(before.size()==1?before.get(0):"\ufffd");kept.add(before);}
            for(KanaScriptResolver.Resolution reason:script.resolutions)if(reason.cell>=l+1+first&&reason.cell<l+1+last)reasons.add(reason);
            lines.add(new Line(l+1+first,y,l+1+last,raw.toString(),original.toString(),kept,reasons));
        }
        return new Box(l,t,r+1,b+1,lines,symbols);
    }
    private static boolean has(PixelGlyphReader.Frame frame,int x,int y,String glyph){return frame.cell(x,y).valid&&frame.cell(x,y).candidates.contains(glyph);}
    private static boolean space(List<String> chars){return chars.size()==1&&" ".equals(chars.get(0));}
    private static boolean structural(String value){return value.codePoints().allMatch(c->c>=0x2500&&c<=0x257f||c==0x25b6||c==0x25b7||c==0x25bc||c==0x309b||c==0x309c||c==32);}
}
