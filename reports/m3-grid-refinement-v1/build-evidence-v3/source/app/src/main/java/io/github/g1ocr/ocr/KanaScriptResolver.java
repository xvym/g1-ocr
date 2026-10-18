package io.github.g1ocr.ocr;

import java.util.*;

/** Selects only between pixel-identical hira/kata aliases using nearby observed kana script. No lexicon. */
public final class KanaScriptResolver {
    public static final class Resolution {
        public final int cell,anchor;
        public final String selected,reason;
        private Resolution(int cell,int anchor,String selected){this.cell=cell;this.anchor=anchor;this.selected=selected;reason="nearest_unambiguous_current_frame_kana_script";}
    }
    public static final class Result {
        public final List<List<String>> literals;
        public final List<Resolution> resolutions;
        private Result(List<List<String>> l,List<Resolution> r){literals=Collections.unmodifiableList(l);resolutions=Collections.unmodifiableList(r);}
    }
    public Result resolve(List<List<String>> observed,int[] distances) {
        if(observed==null||distances==null||observed.size()!=distances.length)throw new IllegalArgumentException("One confidence per cell required");
        List<List<String>> output=new ArrayList<>();for(List<String> c:observed)output.add(Collections.unmodifiableList(new ArrayList<>(c)));
        List<Resolution> evidence=new ArrayList<>();
        for(int i=0;i<observed.size();i++) {
            List<String> aliases=observed.get(i);if(!paired(aliases))continue;
            int left=anchor(observed,distances,i,-1),right=anchor(observed,distances,i,1),chosen=-1;
            if(left>=0&&right>=0) {
                int dl=i-left,dr=right-i;
                if(dl==dr&&script(observed.get(left).get(0))!=script(observed.get(right).get(0)))continue;
                chosen=dl<=dr?left:right;
            } else chosen=left>=0?left:right;
            if(chosen<0)continue;int desired=script(observed.get(chosen).get(0));
            for(String c:aliases)if(script(c)==desired){output.set(i,Collections.singletonList(c));evidence.add(new Resolution(i,chosen,c));break;}
        }
        return new Result(output,evidence);
    }
    private static int anchor(List<List<String>> cells,int[] distances,int at,int direction) {
        for(int i=at+direction;i>=0&&i<cells.size();i+=direction) {
            List<String> c=cells.get(i);
            if(paired(c))continue;
            if(c.size()!=1)return -1;String value=c.get(0);
            if("ー".equals(value))continue;
            int script=script(value);if(script==0)return -1;
            return distances[i]>=0&&distances[i]<=2?i:-1;
        }
        return -1;
    }
    private static boolean paired(List<String> c) {
        if(c.size()!=2||c.get(0).codePointCount(0,c.get(0).length())!=1||c.get(1).codePointCount(0,c.get(1).length())!=1)return false;
        int a=c.get(0).codePointAt(0),b=c.get(1).codePointAt(0);return script(c.get(0))*script(c.get(1))==-1&&Math.abs(a-b)==0x60;
    }
    private static int script(String s){if(s.codePointCount(0,s.length())!=1)return 0;int c=s.codePointAt(0);return c>=0x3041&&c<=0x3096?-1:c>=0x30a1&&c<=0x30fa&&c!=0x30fb?1:0;}
}
