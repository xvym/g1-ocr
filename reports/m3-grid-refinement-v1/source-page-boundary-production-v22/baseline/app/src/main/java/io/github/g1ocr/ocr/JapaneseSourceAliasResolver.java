package io.github.g1ocr.ocr;

import java.util.*;

/** Optional, stateless source-assisted interpretation; never a pixel observation. */
public final class JapaneseSourceAliasResolver {
    public static final class Entry {
        public final String literal,source,commit,path,sha256;
        public final int line;
        public Entry(String literal,String source,String commit,String path,int line,String sha256) {
            if(literal==null||literal.isEmpty()||literal.matches(".*[<>@#\\\\].*"))throw new IllegalArgumentException("Only expanded literal lines");
            this.literal=literal;this.source=source;this.commit=commit;this.path=path;this.line=line;this.sha256=sha256;
        }
    }
    public static final class Change {
        public final int index;public final String before,after;public final List<String> candidates;
        Change(int i,String a,String b,List<String> c){index=i;before=a;after=b;candidates=Collections.unmodifiableList(new ArrayList<>(c));}
    }
    public static final class Resolution {
        public final String rawText,text,reason="optional_source_assisted_exact_visible_line_not_complete_corpus";
        public final List<Change> changes;public final List<Entry> sourceReferences;
        Resolution(String raw,String text,List<Change> changes,List<Entry> refs){rawText=raw;this.text=text;this.changes=Collections.unmodifiableList(changes);sourceReferences=Collections.unmodifiableList(refs);}
    }
    private final List<Entry> entries;
    public JapaneseSourceAliasResolver(List<Entry> entries){this.entries=Collections.unmodifiableList(new ArrayList<>(entries));}
    private static boolean paired(List<String> c) {
        if(c.size()!=2||c.get(0).length()!=1||c.get(1).length()!=1)return false;
        int a=Math.min(c.get(0).charAt(0),c.get(1).charAt(0)),b=Math.max(c.get(0).charAt(0),c.get(1).charAt(0));
        return a>=0x3041&&a<=0x3096&&b-a==0x60;
    }
    public Resolution resolve(String raw,List<List<String>> candidates) {
        if(raw.length()!=candidates.size())return null;
        String selected=null;List<Entry> refs=new ArrayList<>();
        for(Entry e:entries) {
            if(e.literal.length()!=candidates.size())continue;
            boolean matches=true;
            for(int i=0;i<candidates.size();i++)if(!candidates.get(i).contains(e.literal.substring(i,i+1))){matches=false;break;}
            if(!matches)continue;
            if(selected!=null&&!selected.equals(e.literal))return null;
            selected=e.literal;refs.add(e);
        }
        if(selected==null||selected.equals(raw))return null;
        List<Change> changes=new ArrayList<>();
        for(int i=0;i<raw.length();i++)if(raw.charAt(i)!=selected.charAt(i)) {
            List<String> c=candidates.get(i);String before=raw.substring(i,i+1);
            if(!paired(c)||!c.contains(before))return null;
            changes.add(new Change(i,before,selected.substring(i,i+1),c));
        }
        return new Resolution(raw,selected,changes,refs);
    }
}
