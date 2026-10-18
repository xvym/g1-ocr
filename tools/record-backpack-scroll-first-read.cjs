'use strict';
// Manual transcription after directly viewing all 28 sheets, not an OCR tool.
const fs=require('fs'),crypto=require('crypto'),assert=require('assert/strict');
const sha=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const input='reports/m3-grid-refinement-v1/blind-recheck-sheets-pass1-v1/backpack-navigation/report.json';
const report=JSON.parse(fs.readFileSync(input,'utf8'));
assert.equal(report.frameCount,166);assert.equal(report.sheets.length,28);assert(report.allDisplayedPixelsMatchOriginal);
for(const s of report.sheets)assert.equal(sha(s.path),s.sha256);
const A='じてんしゃ',B='ポケモンのふえ',C='いいつりざお',D='ボロのつりざお',E='ピーピーエイダー × 1',F='やめる';
const spans=[
[5200,5204,'parent'],[5204,5205,'partial parent redraw'],[5205,5241,[A,B,C,D]],
[5241,5251,[B,C,D,E]],[5251,5260,[C,D,E,F]],[5260,5301,[D,E,F]],
[5301,5302,[D,'ポロのつりさお',E,F]],[5302,5304,[C,D,E,F]],
[5304,5305,[C,'いいつりさお',D,E]],[5305,5306,[B,C,D,E]],
[5306,5307,[B,'ホケモンのふえ',C,D]],[5307,5345,[A,B,C,D]],
[5345,5347,[B,C,D,E]],[5347,5348,[B,'ホロのつりさお',E,F]],
[5348,5349,[C,D,E,F]],[5349,5350,[C,'ビーヒーエイター × 1',F]],
[5350,5359,[D,E,F]],[5359,5360,'mixed closing; uncertain occlusion'],[5360,5366,'parent']];
let end=5200;for(const s of spans){assert.equal(s[0],end);end=s[1];}assert.equal(end,5366);
const note={status:'first_direct_read_complete_not_freeze_decision',range:[5200,5366],input:{path:input,sha256:sha(input)},viewedSheets:[0,27],
exposure:'Same coding agent, historical GT/diagnostic and ledger exposure disclosed. All 166 frames directly viewed in chronological order with GT/actual closed; additionally full PNG 5301,5304,5306,5347,5349 viewed. No independent-session or human verification claim.',
spans,geometry:'List starts column 6; base rows 4,6,8,10. Quantity multiplication at column16, digit at18. List cursor column5. Down arrow column18 row11. Parent labels column14 rows2,4,6,8,10,12,14: ずかん,ポケモン,どうぐ,グリーン,レポート,せってい,とじる. List overlay leaves isolated dakuten column14 row1 plus とじる at row14 and its mark column15 row13.',
markExceptions:[{frame:5301,row:5,attached:[['handakuten',6,'ポ']],detached:[['handakuten',8],['dakuten',12]]},{frame:5304,row:5,detached:[['dakuten',6],['dakuten',11]]},{frame:5306,row:5,detached:[['dakuten',10]]},{frame:5347,row:5,detached:[['dakuten',10]]},{frame:5349,row:5,attached:[['dakuten',6,'ビ']],detached:[['dakuten',11]]}],
cursorSpans:[[5205,5206,[]],[5206,5223,[4]],[5223,5232,[6]],[5232,5241,[8]],[5241,5242,[]],[5242,5251,[8]],[5251,5252,[]],[5252,5260,[8]],[5260,5261,[]],[5261,5270,[8]],[5270,5271,[]],[5271,5282,[8]],[5282,5283,[]],[5283,5291,[8]],[5291,5300,[6]],[5300,5302,[4]],[5302,5303,[]],[5303,5307,[4]],[5307,5308,[]],[5308,5309,[4]],[5309,5310,[]],[5310,5311,[4]],[5311,5312,[]],[5312,5313,[4]],[5313,5314,[]],[5314,5316,[4]],[5316,5317,[]],[5317,5330,[4]],[5330,5331,[4,6]],[5331,5335,[6]],[5335,5345,[8]],[5345,5346,[]],[5346,5347,[8]],[5347,5348,[]],[5348,5349,[8]],[5349,5350,[]],[5350,5351,[8]],[5351,5352,[]],[5352,5359,[8]]],
downArrowVisibleRanges:[[5205,5215],[5226,5237],[5241,5251],[5304,5323],[5324,5326],[5338,5347]],
contextNotes:'5204 parent first five labels and とじる remain; せってい erased. 5205 list fully drawn without cursor. 5359 mixed parent/list closing: old quantity row base ヒーヒーエイター × 1 partially visible; mark area overwritten; やめ then sprite occlusion, do not infer final glyph. This context remains uncertain and outside existing score. 5360 onward complete parent with cursor column13 row6.',
limitations:'First-pass observations require second complete direct read before comparison against frozen GT. Arrow/cursor ranges subject to second-pass verification; no current GT alteration.'};
const out='reports/m3-grid-refinement-v1/backpack-scroll-notes-v1';fs.mkdirSync(out,{recursive:true});fs.writeFileSync(out+'/pass1.json',JSON.stringify(note,null,2)+'\n',{flag:'wx'});console.log(JSON.stringify({path:out+'/pass1.json',sha256:sha(out+'/pass1.json'),frames:166}));
