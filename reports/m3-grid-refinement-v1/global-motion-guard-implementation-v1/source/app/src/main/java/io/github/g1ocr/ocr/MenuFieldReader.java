package io.github.g1ocr.ocr;
import java.util.*;
import static io.github.g1ocr.ocr.JapaneseOcrEngine.*;

/** Current visible menu layers. Text values are read, including clipped parent prefixes and redraws. */
final class MenuFieldReader {
    enum Layout { NONE,CHOICE,START,BACKPACK,PC_MAIN,PC_ITEMS,SHOP_BUY,SHOP_SELL_LIST,SHOP_PARENT }
    Layout read(PixelGlyphReader.Frame f,List<BoxTextDetector.Box> boxes,List<Region> out) {
        boolean list=box(boxes,4,2,20,13)||layeredList(f),start=box(boxes,12,0,20,16)||box(boxes,12,0,20,14)
                ||list&&has(f,12,15,"└")&&has(f,19,15,"┘");
        for(BoxTextDetector.Box choice:boxes)if((choice.top==6||choice.top==7)&&choice.bottom==12&&choice.right==20&&choice.left>=12) {
            field(out,"menu_option",f,choice.left+2,8,19);field(out,"menu_option",f,choice.left+2,10,19);return Layout.CHOICE;
        }
        boolean shop="かいに きた".equals(text(f,2,1,8));
        boolean pc="どうぐを".equals(text(f,2,2,6))||list&&"どう".equals(text(f,2,2,4));
        if(shop) {
            for(int y=1;y<=5;y+=2)field(out,list&&y>1?"occluded_menu_visible_prefix":"menu_label",f,2,y,list&&y>1?4:10);
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
            for(int y=2;y<=8;y+=2)field(out,list?"occluded_menu_visible_prefix":"menu_label",f,2,y,list?4:11);
            if(list)items(f,out,false,false);
            return Layout.PC_ITEMS;
        }
        if(box(boxes,0,0,15,12)) {
            for(int y=2;y<=10;y+=2)field(out,"pc_menu_choice",f,2,y,14);
            return Layout.PC_MAIN;
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
            if(backpack){field(out,"backpack_item_or_cancel",f,6,y,19);continue;}
            int suffix=19;
            if(prices) {
                int first=18;while(first>6&&text(f,first-1,y,first).matches("[0-9]"))first--;suffix=first;
            } else {
                for(int x=6;x<19;x++)if("×".equals(text(f,x,y,x+1))){suffix=x;break;}
            }
            field(out,backpack?"backpack_item_or_cancel":"item_name",f,6,y,suffix);
            if(prices)field(out,"item_price",f,suffix,y,19);
            else if(suffix<19)field(out,"item_quantity",f,suffix,y,19);
        }
    }
    private static boolean has(PixelGlyphReader.Frame f,int x,int y,String alias){return f.cell(x,y).candidates.contains(alias);}
    private static boolean layeredList(PixelGlyphReader.Frame f){
        if(!has(f,4,2,"┌")||!has(f,19,2,"┐"))return false;int edges=0;
        for(int y=3;y<=11;y++){if(has(f,4,y,"│"))edges++;if(has(f,19,y,"│"))edges++;}return edges>=16;
    }
    static boolean box(List<BoxTextDetector.Box> boxes,int l,int t,int r,int b){return boxes.stream().anyMatch(v->v.left==l&&v.top==t&&v.right==r&&v.bottom==b);}
    static String messageRole(Layout layout) {
        switch(layout){case PC_MAIN:return "dialogue_line";case PC_ITEMS:return "pc_prompt";case SHOP_PARENT:case SHOP_BUY:return "shop_prompt";case SHOP_SELL_LIST:return "shop_message";default:return "dialogue";}
    }
}
