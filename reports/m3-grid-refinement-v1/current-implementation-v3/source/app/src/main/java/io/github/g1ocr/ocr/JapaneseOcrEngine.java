package io.github.g1ocr.ocr;

import io.github.g1ocr.capture.SourceFrame;
import java.util.*;

/** Image-only OCR pipeline. Renderer layout classifies fields; their values always come from current pixels. */
public final class JapaneseOcrEngine {
    public static final class Mark {
        public final int gridColumn,gridRow;public final String kind;
        Mark(int x,int y,String kind){gridColumn=x;gridRow=y;this.kind=kind;}
    }
    public static final class Shape {
        public final String style,kind;public final int[] gridRect,sourceRect;
        private Shape(String style,String kind,int x,int y,PixelGlyphReader.Grid grid){this.style=style;this.kind=kind;gridRect=new int[]{x,y,x+1,y+1};sourceRect=rectangle(grid,x,y,x+1,y+1);}
    }
    public static final class Cursor {
        public final List<Shape> menuCursors=new ArrayList<>(),additionalDownArrows=new ArrayList<>();
        public boolean downArrowVisible;public Shape downArrow;
    }
    public static final class VisibleState {
        public String kind;
        public final Cursor cursor=new Cursor();
        public final List<Shape> detachedMarks=new ArrayList<>();
    }
    public static final class Region {
        public final String id,role,text,pixelCandidateText;
        public final int readingOrder;
        public final double[] gridRect;
        public final int[] sourceRect;
        public final List<Mark> markCells;
        public final List<List<String>> glyphCandidates;
        public final List<KanaScriptResolver.Resolution> candidateResolutions;
        public final double[] logicalRect;
        public final List<RenderUnit> renderUnits;
        private Region(int order,BoxTextDetector.Line line,PixelGlyphReader.Frame read) {
            this(order,"dialogue",line,read);
        }
        private Region(int order,String role,BoxTextDetector.Line line,PixelGlyphReader.Frame read) {
            id=role+"-"+line.left+"-"+line.top;this.role=role;readingOrder=order;text=line.rawText;pixelCandidateText=line.pixelCandidateText;
            gridRect=new double[]{line.left,line.top,line.right,line.top+1};markCells=new ArrayList<>();int envelopeTop=line.top;
            for(int x=line.left;x<line.right;x++)if(line.top>0) {
                List<String> above=read.cell(x,line.top-1).candidates;
                if(above.size()==1&&("゛".equals(above.get(0))||"゜".equals(above.get(0)))&&!read.literals(x,line.top).equals(read.cell(x,line.top).candidates)) {
                    markCells.add(new Mark(x,line.top-1,"゛".equals(above.get(0))?"dakuten":"handakuten"));envelopeTop=line.top-1;
                }
            }
            sourceRect=rectangle(read.grid,line.left,envelopeTop,line.right,line.top+1);glyphCandidates=line.candidates;candidateResolutions=line.candidateResolutions;logicalRect=null;renderUnits=null;
        }
        private Region(int order,String role,PixelGlyphReader.Frame read,int l,int y,int r) {
            id=role+"-"+l+"-"+y;this.role=role;readingOrder=order;gridRect=new double[]{l,y,r,y+1};logicalRect=null;renderUnits=null;
            markCells=new ArrayList<>();glyphCandidates=new ArrayList<>();candidateResolutions=new ArrayList<>();
            KanaScriptResolver.Result script=read.contextualRow(y);StringBuilder raw=new StringBuilder(),original=new StringBuilder();int top=y;
            for(int x=l;x<r;x++) {
                List<String> before=read.literals(x,y),after=script.literals.get(x);glyphCandidates.add(before);
                raw.append(literal(after));original.append(literal(before));
                if(y>0) {List<String> above=read.cell(x,y-1).candidates;if(above.size()==1&&("゛".equals(above.get(0))||"゜".equals(above.get(0)))&&!before.equals(read.cell(x,y).candidates)) {markCells.add(new Mark(x,y-1,"゛".equals(above.get(0))?"dakuten":"handakuten"));top=y-1;}}
            }
            for(KanaScriptResolver.Resolution reason:script.resolutions)if(reason.cell>=l&&reason.cell<r)candidateResolutions.add(reason);
            text=raw.toString();pixelCandidateText=original.toString();sourceRect=rectangle(read.grid,l,top,r,y+1);
        }
        private Region(int order,String role,String text,PixelGlyphReader.Grid grid,double[] logical,List<RenderUnit> units) {
            id=role+"-"+logical[0]+"-"+logical[1];this.role=role;this.text=text;pixelCandidateText=text;readingOrder=order;
            logicalRect=logical.clone();gridRect=new double[]{logical[0]/8,logical[1]/8,logical[2]/8,logical[3]/8};
            sourceRect=rectangle(grid,gridRect[0],gridRect[1],gridRect[2],gridRect[3]);renderUnits=units;
            markCells=Collections.emptyList();glyphCandidates=Collections.emptyList();candidateResolutions=Collections.emptyList();
        }
    }
    public static final class RenderUnit {
        public final String text,kind="compound_label";public final double[] logicalRect;
        RenderUnit(String text,double[] rect){this.text=text;logicalRect=rect.clone();}
    }
    static String literal(List<String> chars) {return chars.size()==1?chars.get(0):"\ufffd";}
    static String text(PixelGlyphReader.Frame read,int l,int y,int r) {StringBuilder s=new StringBuilder();List<List<String>> chars=read.contextualRow(y).literals;for(int x=l;x<r;x++)s.append(literal(chars.get(x)));return s.toString();}
    static void field(List<Region> out,String role,PixelGlyphReader.Frame read,int l,int y,int r) {
        List<List<String>> chars=read.contextualRow(y).literals;
        while(l<r&&chars.get(l).equals(Collections.singletonList(" ")))l++;
        while(r>l&&chars.get(r-1).equals(Collections.singletonList(" ")))r--;
        if(l<r)out.add(new Region(out.size(),role,read,l,y,r));
    }
    static void packed(List<Region> out,String role,String text,PixelGlyphReader.Frame read,double[] rect,boolean separateHp) {
        List<RenderUnit> units=new ArrayList<>();
        if(separateHp){units.add(new RenderUnit("HP",new double[]{rect[0],rect[1],rect[0]+8,rect[3]}));units.add(new RenderUnit(":",new double[]{rect[0]+8,rect[1],rect[2],rect[3]}));}
        else units.add(new RenderUnit(text,rect));
        out.add(new Region(out.size(),role,text,read.grid,rect,units));
    }
    public static final class Result {
        public final long sourcePts,processingNanos;
        public final String timeBase;
        public final List<Region> rawRegions;
        public final VisibleState visibleState;
        public final FrameGeometryEstimator.Estimate geometry;
        public final List<BoxTextDetector.Box> diagnosticBoxes;
        public final RasterMotionTracker.Alignment imageRegistration;
        private Result(SourceFrame frame,long begin,List<Region> regions,VisibleState state,FrameGeometryEstimator.Estimate geometry,List<BoxTextDetector.Box> boxes,RasterMotionTracker.Alignment registration) {
            sourcePts=frame.getPts();timeBase=frame.getTimeBaseNumerator()+"/"+frame.getTimeBaseDenominator();processingNanos=System.nanoTime()-begin;
            rawRegions=Collections.unmodifiableList(regions);visibleState=state;this.geometry=geometry;diagnosticBoxes=boxes;imageRegistration=registration;
        }
    }
    private final PixelGlyphReader reader;
    private final SceneFieldReader fields;
    private final RasterMotionTracker motion=new RasterMotionTracker();
    private String previousSignature="";private boolean complete;
    public JapaneseOcrEngine(List<GlyphTemplate> bank){reader=new PixelGlyphReader(bank);fields=new SceneFieldReader(bank);}
    public void reset(){resetTextState();motion.reset();}
    private void resetTextState(){previousSignature="";complete=false;}
    public Result process(SourceFrame frame) {
        long begin=System.nanoTime();FrameGeometryEstimator.Estimate geometry=new FrameGeometryEstimator().estimate(frame);
        List<Region> regions=new ArrayList<>();VisibleState state=new VisibleState();
        if(!geometry.located) {
            reset();state.kind="no_signal".equals(geometry.method)?"black_frame":"empty_target";
            return new Result(frame,begin,regions,state,geometry,Collections.emptyList(),null);
        }
        RasterMotionTracker.Alignment registration=motion.align(frame,geometry.grid);
        geometry=geometry.registered(registration.grid);
        PixelGlyphReader.Frame glyphs=reader.read(frame,geometry.grid);List<BoxTextDetector.Box> boxes=new BoxTextDetector().detect(glyphs,registration.offsetLogicalX);BoxTextDetector.Box main=null;
        SceneFieldReader.Scene scene=fields.read(frame,glyphs,regions);
        MenuFieldReader.Layout menu=scene==SceneFieldReader.Scene.NONE?new MenuFieldReader().read(glyphs,boxes,regions):MenuFieldReader.Layout.NONE;
        List<Shape> arrows=new ArrayList<>();Shape mainArrow=null;
        Set<String> seenSymbols=new HashSet<>();
        for(BoxTextDetector.Box box:boxes) {
            boolean standard=box.left==0&&box.right==20&&box.top==12&&box.bottom==18;
            if(standard)main=box;
            for(BoxTextDetector.Symbol symbol:box.symbols) {
                if(!seenSymbols.add(symbol.kind+":"+symbol.column+":"+symbol.row))continue;
                if("down_arrow".equals(symbol.kind)) {
                    Shape arrow=new Shape("filled_down",null,symbol.column,symbol.row,geometry.grid);arrows.add(arrow);if(standard)mainArrow=arrow;
                } else if("filled_right".equals(symbol.kind)||"outline_right".equals(symbol.kind))state.cursor.menuCursors.add(new Shape(symbol.kind,null,symbol.column,symbol.row,geometry.grid));
                else state.detachedMarks.add(new Shape(null,symbol.kind,symbol.column,symbol.row,geometry.grid));
            }
        }
        if(scene==SceneFieldReader.Scene.PARTY)for(int y=1;y<12;y+=2) {
            List<String> c=glyphs.cell(0,y).candidates;if(c.contains("▶")||c.contains("▷"))state.cursor.menuCursors.add(new Shape(c.contains("▶")?"filled_right":"outline_right",null,0,y,geometry.grid));
        }
        if(!arrows.isEmpty()) {
            state.cursor.downArrowVisible=true;state.cursor.downArrow=mainArrow==null?arrows.get(0):mainArrow;
            for(Shape arrow:arrows)if(arrow!=state.cursor.downArrow)state.cursor.additionalDownArrows.add(arrow);
        }
        if(scene==SceneFieldReader.Scene.BATTLE&&regions.stream().anyMatch(r->r.role.equals("battle_command")||r.role.equals("move_name")))main=null;
        if(scene==SceneFieldReader.Scene.STATUS||scene==SceneFieldReader.Scene.STATUS_REDRAW) {
            resetTextState();state.kind=scene==SceneFieldReader.Scene.STATUS?"status_page_complete":"status_page_redraw";
        } else if(scene==SceneFieldReader.Scene.PARTY) {
            if(main!=null)for(BoxTextDetector.Line line:main.lines)regions.add(new Region(regions.size(),"party_prompt",line,glyphs));
            resetTextState();state.kind=regions.size()<=5?"menu_partial_clear":state.cursor.menuCursors.isEmpty()?"menu_cursor_loading":"menu_complete";
        } else if(main==null) {resetTextState();state.kind=regions.isEmpty()?"no_textbox":"menu_complete";}
        else {
            StringBuilder signature=new StringBuilder();boolean scrolling=false;
            String role=scene==SceneFieldReader.Scene.BATTLE?"battle_message":MenuFieldReader.messageRole(menu);int before=regions.size();
            for(BoxTextDetector.Line line:main.lines){regions.add(new Region(regions.size(),role,line,glyphs));signature.append(line.top).append(':').append(line.rawText).append('\n');if(line.top==13||line.top==15)scrolling=true;}
            String current=signature.toString();
            if(regions.size()==before){state.kind=scene==SceneFieldReader.Scene.BATTLE?"message_clear_hud_remains":menu!=MenuFieldReader.Layout.NONE?"message_clear_menu_remains":"textbox_empty";complete=false;}
            else if(scrolling){state.kind="scrolling";complete=false;}
            else {complete=mainArrow!=null||complete&&current.equals(previousSignature);state.kind=complete?"message_complete":"message_loading";if(menu!=MenuFieldReader.Layout.NONE&&menu!=MenuFieldReader.Layout.CHOICE&&!state.cursor.menuCursors.isEmpty())state.kind="menu_complete";}
            previousSignature=current;
        }
        return new Result(frame,begin,regions,state,geometry,boxes,registration);
    }
    private static int[] rectangle(PixelGlyphReader.Grid grid,double l,double t,double r,double b) {
        return new int[]{(int)Math.floor(grid.originX+l*8*grid.scaleX+1e-7),(int)Math.floor(grid.originY+t*8*grid.scaleY+1e-7),
                (int)Math.ceil(grid.originX+r*8*grid.scaleX-1e-7),(int)Math.ceil(grid.originY+b*8*grid.scaleY-1e-7)};
    }
}
