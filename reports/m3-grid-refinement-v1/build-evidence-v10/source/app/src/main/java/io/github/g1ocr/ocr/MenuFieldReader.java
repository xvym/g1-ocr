package io.github.g1ocr.ocr;
import java.util.*;
import static io.github.g1ocr.ocr.JapaneseOcrEngine.*;

/** Current visible menu layers. Text values are read, including clipped parent prefixes and redraws. */
final class MenuFieldReader {
    enum Layout { NONE,CHOICE,START,BACKPACK,PC_MAIN,PC_MAIN_PARTIAL,PC_ITEMS,SHOP_BUY,SHOP_SELL_LIST,SHOP_PARENT }
    Layout read(PixelGlyphReader.Frame f,List<BoxTextDetector.Box> boxes,List<Region> out) {
        boolean list=box(boxes,4,2,20,13)||layeredList(f),start=box(boxes,12,0,20,16)||box(boxes,12,0,20,14)
                ||list&&has(f,12,15,"└")&&has(f,19,15,"┘");
        for(BoxTextDetector.Box choice:boxes)if((choice.top==6||choice.top==7)&&choice.bottom==12&&choice.right==20&&choice.left>=12) {
            field(out,"menu_option",f,choice.left+2,8,19);field(out,"menu_option",f,choice.left+2,10,19);return Layout.CHOICE;
        }
        boolean shop="かいに きた".equals(text(f,2,1,8));
        boolean pc="どうぐを".equals(text(f,2,2,6))||list&&"どう".equals(text(f,2,2,4));
        if(shop) {
            if(!list) {
                // Complete merchant parent: screen order, including the money panel.
                if("おかね".equals(text(f,14,0,17)))field(out,"shop_money_label",f,14,0,17);
                field(out,"shop_menu_choice",f,2,1,10);
                if(box(boxes,11,0,20,3)||"円".equals(text(f,18,1,19)))field(out,"money_amount",f,12,1,19);
                for(int y=3;y<=5;y+=2)field(out,"shop_menu_choice",f,2,y,10);
                return Layout.SHOP_PARENT;
            }
            for(int y=1;y<=5;y+=2){boolean covered=list&&coveredAt(f,y);field(out,covered?"occluded_menu_visible_prefix":"menu_label",f,2,y,covered?4:10);}
            if("おかね".equals(text(f,14,0,17)))field(out,"money_label",f,14,0,17);
            if(box(boxes,11,0,20,3)||"円".equals(text(f,18,1,19)))field(out,"money",f,12,1,19);
            boolean prices=false;
            if(list) {
                for(int y=4;y<=10;y+=2)if("円".equals(text(f,18,y,19)))prices=true;
                items(f,out,false,prices);
            }
            return list?(prices?Layout.SHOP_BUY:Layout.SHOP_SELL_LIST):Layout.SHOP_PARENT;
        }
        if(pc) {
            for(int y=2;y<=8;y+=2){boolean covered=list&&coveredAt(f,y);field(out,covered?"occluded_menu_visible_prefix":"menu_label",f,2,y,covered?4:11);}
            if(list)items(f,out,false,false);
            return Layout.PC_ITEMS;
        }
        if(box(boxes,0,0,15,12)) {
            for(int y=2;y<=10;y+=2)field(out,"pc_menu_choice",f,2,y,14);
            return Layout.PC_MAIN;
        }
        // The renderer copies the menu from top to bottom. A missing bottom edge does not
        // erase rows already enclosed by the observed top and both side borders.
        if(partialPcOutline(f)) {
            for(int y=2;y<=10;y+=2) {
                if(!has(f,0,y,"│")||!has(f,14,y,"│"))break;
                field(out,"pc_menu_choice",f,2,y,14);
            }
            return Layout.PC_MAIN_PARTIAL;
        }
        if(start) {
            if(list) {
                items(f,out,true,false);field(out,"parent_menu_visible_choice",f,14,14,19);return Layout.BACKPACK;
            }
            for(int y=2;y<=14;y+=2) {
                String first=text(f,14,y,15);
                if(!first.equals(" ")&&!first.matches("[┌┐└┘─│�]"))field(out,"main_menu_label",f,14,y,19);
            }
            return Layout.START;
        }
        return Layout.NONE;
    }
    private static void items(PixelGlyphReader.Frame f,List<Region> out,boolean backpack,boolean prices) {
        for(int y=4;y<=10;y+=2) {
            if(!has(f,4,y,"│")||!has(f,19,y,"│"))continue;
            if(backpack){field(out,"backpack_item_or_cancel",f,6,y,19);continue;}
            int suffix=19;
            if(prices) {
                int first=18;while(first>6&&text(f,first-1,y,first).matches("[0-9]"))first--;suffix=first;
            } else {
                for(int x=6;x<19;x++)if("×".equals(text(f,x,y,x+1))){suffix=x;break;}
            }
            field(out,backpack?"backpack_item_or_cancel":"item_name",f,6,y,suffix);
            if(prices)field(out,"item_price",f,suffix,y,19);
            else if(suffix<19){field(out,"quantity_label",f,suffix,y,suffix+1);field(out,"item_quantity",f,suffix+1,y,19);}
        }
    }
    private static boolean has(PixelGlyphReader.Frame f,int x,int y,String alias){return f.cell(x,y).candidates.contains(alias);}
    private static boolean layeredList(PixelGlyphReader.Frame f){
        if(has(f,4,2,"┌")&&has(f,19,2,"┐"))return true;
        for(int y=4;y<=10;y+=2)if(has(f,4,y,"│")&&has(f,19,y,"│"))return true;return false;
    }
    private static boolean coveredAt(PixelGlyphReader.Frame f,int y){return has(f,4,y,"│")||y==2&&has(f,4,2,"┌");}
    private static boolean partialPcOutline(PixelGlyphReader.Frame f) {
        if(!has(f,0,0,"┌")||!has(f,14,0,"┐")||!has(f,0,2,"│")||!has(f,14,2,"│"))return false;
        int top=0;for(int x=1;x<14;x++)if(has(f,x,0,"─"))top++;
        return top>=11;
    }
    static boolean box(List<BoxTextDetector.Box> boxes,int l,int t,int r,int b){return boxes.stream().anyMatch(v->v.left==l&&v.top==t&&v.right==r&&v.bottom==b);}
    List<BoxTextDetector.Symbol> uncoveredParentSymbols(PixelGlyphReader.Frame f,Layout layout) {
        int[] rect;
        switch(layout) {
            case START:case BACKPACK:rect=new int[]{12,0,20,16};break;
            case PC_MAIN:case PC_MAIN_PARTIAL:rect=new int[]{0,0,15,12};break;
            case PC_ITEMS:rect=new int[]{0,0,12,10};break;
            case SHOP_PARENT:case SHOP_BUY:case SHOP_SELL_LIST:rect=new int[]{0,0,11,7};break;
            default:return Collections.emptyList();
        }
        boolean overlay=layeredList(f);List<BoxTextDetector.Symbol> result=new ArrayList<>();
        for(int y=rect[1]+1;y<rect[3]-1;y++)for(int x=rect[0]+1;x<rect[2]-1;x++) {
            if(layout==Layout.PC_MAIN_PARTIAL&&(!has(f,0,y,"│")||!has(f,14,y,"│")))continue;
            if(overlay&&x>=4&&y>=2&&y<13)continue;
            List<String> c=f.cell(x,y).candidates;if(c.size()!=1)continue;String glyph=c.get(0);
            if("▶".equals(glyph)||"▷".equals(glyph))result.add(new BoxTextDetector.Symbol(x,y,"▶".equals(glyph)?"filled_right":"outline_right"));
            if(("゛".equals(glyph)||"゜".equals(glyph))&&f.literals(x,y+1).equals(f.cell(x,y+1).candidates))
                result.add(new BoxTextDetector.Symbol(x,y,"゛".equals(glyph)?"dakuten":"handakuten"));
        }
        return result;
    }
    static String messageRole(Layout layout) {
        switch(layout){case PC_MAIN:case PC_MAIN_PARTIAL:case SHOP_PARENT:return "dialogue_line";case PC_ITEMS:return "pc_prompt";case SHOP_BUY:return "shop_prompt";case SHOP_SELL_LIST:return "shop_message";default:return "dialogue";}
    }
}
