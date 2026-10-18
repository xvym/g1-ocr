package io.github.g1ocr.ocr;

import java.util.*;

/** Optional pinned-script interpretation. Never rewrites image observations or copies source text into output. */
public final class JapanesePageBoundaryResolver {
    public static final class Entry {
        public final String upper,lower,control,source,commit,path,sha256;
        public final int line,boundaryLine;
        private final transient int[] upperTokens,lowerTokens;
        private final transient int variables;
        public Entry(String upper,String lower,String control,String source,String commit,String path,int line,int boundaryLine,String sha256){
            if(!Arrays.asList("paragraph_wait","scroll_wait","string_end","done","prompt").contains(control))throw new IllegalArgumentException("Unsupported boundary control");
            this.upper=upper;this.lower=lower;this.control=control;this.source=source;this.commit=commit;this.path=path;this.line=line;this.boundaryLine=boundaryLine;this.sha256=sha256;
            upperTokens=tokens(upper);lowerTokens=tokens(lower);int count=0;for(int c:upperTokens)if(c<0)count++;for(int c:lowerTokens)if(c<0)count++;variables=count;
        }
    }
    public static final class Line {
        public final String role,text;public final double column,row;public final int order;
        public Line(String role,String text,double column,double row,int order){this.role=role;this.text=text;this.column=column;this.row=row;this.order=order;}
    }
    public static final class Resolution {
        public final String kind="message_complete",rawKind="message_loading";
        public final String reason="optional_pinned_source_exact_current_two_line_page_boundary";
        public final List<Entry> matchedSources;
        private Resolution(List<Entry> entries){matchedSources=Collections.unmodifiableList(new ArrayList<>(entries));}
    }
    private final List<Entry> entries;
    public JapanesePageBoundaryResolver(List<Entry> entries){this.entries=Collections.unmodifiableList(new ArrayList<>(entries));}
    public Resolution resolve(String kind,List<Line> lines,boolean hasMenuSelection){
        if(!"message_loading".equals(kind)||hasMenuSelection||lines.size()!=2)return null;
        Line u=lines.get(0),l=lines.get(1);
        if(!"dialogue".equals(u.role)||!"dialogue".equals(l.role)||u.column!=1||l.column!=1||u.row!=14||l.row!=16||u.order!=0||l.order!=1
                ||u.text.isEmpty()||l.text.isEmpty()||u.text.indexOf('\ufffd')>=0||l.text.indexOf('\ufffd')>=0)return null;
        int[] upper=u.text.codePoints().toArray(),lower=l.text.codePoints().toArray();List<Entry> exact=new ArrayList<>();
        for(Entry e:entries){if(!(e.upper.indexOf('<')<0?e.upper.equals(u.text):matches(upper,e.upperTokens,false)))continue;
            boolean whole=e.lower.indexOf('<')<0?e.lower.equals(l.text):matches(lower,e.lowerTokens,false);
            // Overapproximate competing names; an ambiguous longer page always wins abstention.
            if(!whole&&(e.lower.indexOf('<')<0?e.lower.startsWith(l.text):matches(lower,e.lowerTokens,true)))return null;
            // Repeated variable identity is deliberately unsupported, never guessed independently.
            if(whole&&e.variables<=1)exact.add(e);
        }
        return exact.isEmpty()?null:new Resolution(exact);
    }
    private static int[] tokens(String text){
        List<Integer> out=new ArrayList<>();for(int i=0;i<text.length();){
            if(text.startsWith("<PLAYER>",i)){out.add(-1);i+=8;}
            else if(text.startsWith("<RIVAL>",i)){out.add(-2);i+=7;}
            else {int c=text.codePointAt(i);if(c=='<'||c=='>')throw new IllegalArgumentException("Unsupported source substitution");out.add(c);i+=Character.charCount(c);}
        }int[] result=new int[out.size()];for(int i=0;i<result.length;i++)result[i]=out.get(i);return result;
    }
    private static boolean matches(int[] text,int[] pattern,boolean prefix){
        int maximum=pattern.length;for(int c:pattern)if(c<0)maximum+=4;
        if(text.length>maximum||!prefix&&text.length<pattern.length)return false;
        boolean[][] reachable=new boolean[pattern.length+1][text.length+1];reachable[0][0]=true;
        for(int p=0;p<=pattern.length;p++)for(int n=0;n<=text.length;n++)if(reachable[p][n]){
            if(n==text.length&&(prefix||p==pattern.length))return true;if(p==pattern.length)continue;
            if(pattern[p]>=0){if(n<text.length&&pattern[p]==text[n])reachable[p+1][n+1]=true;}
            else for(int k=1;k<=5&&n+k<=text.length;k++){if(!Character.isLetterOrDigit(text[n+k-1]))break;reachable[p+1][n+k]=true;}
        }return false;
    }
}
