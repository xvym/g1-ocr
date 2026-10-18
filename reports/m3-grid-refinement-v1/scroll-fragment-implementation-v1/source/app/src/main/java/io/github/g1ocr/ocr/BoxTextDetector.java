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
        public final List<Fragment> unclassifiedFragments;
        private Box(int l,int t,int r,int b,List<Line> lines,List<Symbol> symbols,List<Fragment> fragments){left=l;top=t;right=r;bottom=b;this.lines=Collections.unmodifiableList(lines);this.symbols=Collections.unmodifiableList(symbols);unclassifiedFragments=Collections.unmodifiableList(fragments);}
    }
    public static final class Fragment {
        public final int column,row;public final int[] sourceRect;public final String inkHex,knownPixelMaskHex,reason="unresolved_sparse_ink_between_two_current_text_baselines";
        public final List<String> candidates;
        private Fragment(PixelGlyphReader.Cell cell){column=cell.column;row=cell.row;sourceRect=cell.copySourceRect();inkHex=Long.toUnsignedString(cell.ink,16);knownPixelMaskHex=Long.toUnsignedString(cell.knownPixelMask,16);candidates=cell.candidates;}
    }
    public List<Box> detect(PixelGlyphReader.Frame frame) {
        return detect(frame,0);
    }
    public List<Box> detect(PixelGlyphReader.Frame frame,int registeredHorizontalOffset) {
        if(Math.abs(registeredHorizontalOffset)>3)throw new IllegalArgumentException("Unsupported registered raster offset");
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
        // A list window can cover the message box's upper-right corner while its current text remains visible.
        if(boxes.stream().noneMatch(v->v.left==0&&v.top==12&&v.right==20&&v.bottom==18)
                &&has(frame,0,12,"┌")&&has(frame,0,17,"└")&&has(frame,19,17,"┘")) {
            int edges=0;for(int y=13;y<17;y++){if(has(frame,0,y,"│"))edges++;if(has(frame,19,y,"│"))edges++;}
            if(edges>=7)boxes.add(readBox(frame,0,12,19,17));
        }
        // Registration observes the complete raster shifting into its fixed display aperture.
        // Only a registered shift permits a missing outer corner; text still comes from this frame.
        if(registeredHorizontalOffset!=0&&boxes.stream().noneMatch(v->v.left==0&&v.top==12&&v.right==20&&v.bottom==18)) {
            boolean fixedSide=registeredHorizontalOffset>0?has(frame,0,12,"┌")&&has(frame,0,17,"└"):has(frame,19,12,"┐")&&has(frame,19,17,"┘");
            int horizontal=0,vertical=0;for(int x=1;x<19;x++){if(has(frame,x,12,"─"))horizontal++;if(has(frame,x,17,"─"))horizontal++;}
            for(int y=13;y<17;y++)if(has(frame,registeredHorizontalOffset>0?0:19,y,"│"))vertical++;
            if(fixedSide&&horizontal>=30&&vertical>=3)boxes.add(readBox(frame,0,12,19,17));
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
        List<Fragment> fragments=new ArrayList<>();Set<Line> markRows=Collections.newSetFromMap(new IdentityHashMap<Line,Boolean>());
        for(int i=1;i+1<lines.size();i++) {
            Line line=lines.get(i),previous=lines.get(i-1),next=lines.get(i+1);
            if(previous.top+1!=line.top||line.top+1!=next.top||line.right-line.left>2)continue;
            boolean sparse=true;
            for(int x=line.left;x<line.right;x++){PixelGlyphReader.Cell c=frame.cell(x,line.top);if(c.candidates.size()==1||Long.bitCount(c.ink&c.knownPixelMask)>8)sparse=false;}
            if(sparse){markRows.add(line);for(int x=line.left;x<line.right;x++)fragments.add(new Fragment(frame.cell(x,line.top)));}
        }
        lines.removeAll(markRows);
        return new Box(l,t,r+1,b+1,lines,symbols,fragments);
    }
    private static boolean has(PixelGlyphReader.Frame frame,int x,int y,String glyph){return frame.cell(x,y).valid&&frame.cell(x,y).candidates.contains(glyph);}
    private static boolean space(List<String> chars){return chars.size()==1&&" ".equals(chars.get(0));}
    private static boolean structural(String value){return value.codePoints().allMatch(c->c>=0x2500&&c<=0x257f||c==0x25b6||c==0x25b7||c==0x25bc||c==0x309b||c==0x309c||c==32);}
}
