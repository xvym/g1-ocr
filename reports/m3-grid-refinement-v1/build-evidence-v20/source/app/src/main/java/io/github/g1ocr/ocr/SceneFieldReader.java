package io.github.g1ocr.ocr;

import io.github.g1ocr.capture.SourceFrame;
import java.util.*;
import static io.github.g1ocr.ocr.JapaneseOcrEngine.*;

/** Field coordinates are renderer rules, never clip identities, expected names or saved answers. */
final class SceneFieldReader {
    enum Scene { NONE,STATUS,STATUS_REDRAW,PARTY,BATTLE }
    private final PackedGlyphReader packed;
    SceneFieldReader(List<GlyphTemplate> bank){packed=new PackedGlyphReader(bank);}
    Scene read(SourceFrame pixels,PixelGlyphReader.Frame f,List<Region> out) {
        if(":L".equals(text(f,16,1,17))&&"No.".equals(text(f,1,7,3)))return status(pixels,f,out);
        if(":L".equals(text(f,8,1,9))&&packed.hp(pixels,f.grid,88,4)) {
            for(int y=1;y<12;y+=2)if(":L".equals(text(f,8,y,9))) {
                field(out,"party_pokemon_name",f,3,y,8);level(out,"compact_party_label","level",f,8,y,11);
                hp(out,"compact_party_label",pixels,f,11,y-1,true);field(out,"hp_current_max",f,12,y,19);
            }
            return Scene.PARTY;
        }
        int ownLevel=findLevel(f,8,10,18);
        if(ownLevel>=0&&packed.hp(pixels,f.grid,80,76)) {
            int enemyLevel=findLevel(f,1,2,11);
            if(enemyLevel>=0&&packed.hp(pixels,f.grid,16,20)) {
                field(out,"enemy_pokemon_name",f,2,1,enemyLevel);level(out,"compact_hud_label","enemy_level",f,enemyLevel,1,11);hp(out,"compact_hud_label",pixels,f,2,2,true);
            }
            field(out,"own_pokemon_name",f,10,8,ownLevel);level(out,"compact_hud_label","own_level",f,ownLevel,8,18);
            hp(out,"compact_hud_label",pixels,f,10,9,true);field(out,"hp_current_max",f,11,10,18);
            if(text(f,10,15,15).equals("わざタイプ")) {
                for(int y=10;y<=16;y+=2)field(out,"move_name",f,2,y,9);
                field(out,"pp_current_max",f,14,13,19);field(out,"move_type_label",f,10,15,15);field(out,"move_type",f,14,16,19);
            } else if(text(f,11,14,15).equals("たたかう")) {
                field(out,"battle_command",f,11,14,15);field(out,"battle_command",f,16,14,19);field(out,"battle_command",f,11,16,15);field(out,"battle_command",f,16,16,19);
            }
            return Scene.BATTLE;
        }
        return Scene.NONE;
    }
    private Scene status(SourceFrame pixels,PixelGlyphReader.Frame f,List<Region> out) {
        field(out,"pokemon_name",f,11,1,16);level(out,"compact_status_label","pokemon_level",f,16,1,19);
        packed(out,"compact_status_label","No.",f,new double[]{8,56,24,64},false);field(out,"pokedex_number",f,3,7,6);
        boolean experience=text(f,9,3,15).equals("けいけんち/");
        if(experience) {
            field(out,"experience_label",f,9,3,15);field(out,"experience",f,12,4,19);field(out,"next_level_label",f,9,5,11);
            int firstDigit=7;while(firstDigit<14&&!text(f,firstDigit,6,firstDigit+1).matches("[0-9]"))firstDigit++;
            field(out,"experience_remaining",f,firstDigit,6,15);level(out,"compact_status_label","next_level",f,16,6,19);
        } else {
            hp(out,"compact_status_label",pixels,f,11,3,false);field(out,"hp_current_max",f,12,4,19);field(out,"pokemon_condition",f,10,6,19);
        }
        boolean[] moves=new boolean[4];int moveRows=0;
        for(int i=0;i<4;i++)if("PP".equals(text(f,11,10+2*i,13))) {
            int y=10+2*i;moves[i]=true;moveRows++;
            field(out,"move_name",f,2,y,10);field(out,"pp_label",f,11,y,13);field(out,"move_pp",f,14,y,19);
        }
        String[] roles={"attack","defense","speed","special"};
        for(int i=0;i<4;i++)if(!moves[i]) {
            int y=10+2*i;field(out,"stat_label",f,1,y,5);field(out,roles[i],f,6,y,9);
        }
        for(int i=0;i<2;i++)if(!moves[i])field(out,"pokemon_type",f,10,10+2*i,19);
        if(!moves[2]&&text(f,10,14,13).equals("IDNo/")) {
            packed(out,"compact_status_label","IDNo/",f,new double[]{80,112,104,120},false);field(out,"trainer_id",f,14,14,19);
        }
        if(!moves[3])field(out,"trainer_name",f,10,16,19);
        return moveRows>0&&moveRows<4?Scene.STATUS_REDRAW:Scene.STATUS;
    }
    private void hp(List<Region> out,String role,SourceFrame pixels,PixelGlyphReader.Frame f,int x,int y,boolean split) {
        int px=x*8,py=y*8+4;
        if(packed.hp(pixels,f.grid,px,py))packed(out,role,"HP:",f,new double[]{px,py,px+12,py+4},split);
    }
    private static int findLevel(PixelGlyphReader.Frame f,int y,int l,int r){for(int x=l;x<r;x++)if(text(f,x,y,x+1).equals(":L"))return x;return -1;}
    private static void level(List<Region> out,String labelRole,String valueRole,PixelGlyphReader.Frame f,int x,int y,int end) {
        if(!":L".equals(text(f,x,y,x+1)))return;
        packed(out,labelRole,":L",f,new double[]{x*8,y*8,(x+1)*8,(y+1)*8},false);field(out,valueRole,f,x+1,y,end);
    }
}
