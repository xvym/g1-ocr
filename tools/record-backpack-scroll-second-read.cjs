'use strict';
// Manually recorded after directly viewing sheets 000-027 of the fresh export.
const fs=require('fs'),crypto=require('crypto'),assert=require('assert/strict');
const sha=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const input='reports/m3-grid-refinement-v1/backpack-scroll-pass2-sheets-v1/report.json';
const report=JSON.parse(fs.readFileSync(input,'utf8'));
assert.equal(report.frameCount,166);assert.equal(report.sheets.length,28);assert(report.allDisplayedPixelsMatchOriginal);
for(const s of report.sheets)assert.equal(sha(s.path),s.sha256);
const A='じてんしゃ',B='ポケモンのふえ',C='いいつりざお',D='ボロのつりざお',E='ピーピーエイダー × 1',F='やめる';
const note={status:'second_direct_read_complete',range:[5200,5366],input:{path:input,sha256:sha(input)},viewedSheets:[0,27],
exposure:'Same coding agent with previous diagnostic/ledger and first-read memory. All second-pass sheets directly viewed in chronological order. GT and tested output closed throughout. Not independent-session or human verification.',
spans:[[5200,5204,'parent'],[5204,5205,'partial parent redraw'],[5205,5241,[A,B,C,D]],[5241,5251,[B,C,D,E]],[5251,5260,[C,D,E,F]],[5260,5301,[D,E,F]],[5301,5302,[D,'ポロのつりさお',E,F]],[5302,5304,[C,D,E,F]],[5304,5305,[C,'いいつりさお',D,E]],[5305,5306,[B,C,D,E]],[5306,5307,[B,'ホケモンのふえ',C,D]],[5307,5345,[A,B,C,D]],[5345,5347,[B,C,D,E]],[5347,5348,[B,'ホロのつりさお',E,F]],[5348,5349,[C,D,E,F]],[5349,5350,[C,'ビーヒーエイター × 1',F]],[5350,5359,[D,E,F]],[5359,5360,'mixed closing; uncertain occlusion'],[5360,5366,'parent']],
markExceptions:[{frame:5301,row:5,attached:[['handakuten',6,'ポ']],detached:[['handakuten',8],['dakuten',12]]},{frame:5304,row:5,detached:[['dakuten',6],['dakuten',11]]},{frame:5306,row:5,detached:[['dakuten',10]]},{frame:5347,row:5,detached:[['dakuten',10]]},{frame:5349,row:5,attached:[['dakuten',6,'ビ']],detached:[['dakuten',11]]}],
cursorSpans:[[5205,5206,[]],[5206,5223,[4]],[5223,5232,[6]],[5232,5241,[8]],[5241,5242,[]],[5242,5251,[8]],[5251,5252,[]],[5252,5260,[8]],[5260,5261,[]],[5261,5270,[8]],[5270,5271,[]],[5271,5282,[8]],[5282,5283,[]],[5283,5291,[8]],[5291,5300,[6]],[5300,5302,[4]],[5302,5303,[]],[5303,5307,[4]],[5307,5308,[]],[5308,5309,[4]],[5309,5310,[]],[5310,5311,[4]],[5311,5312,[]],[5312,5313,[4]],[5313,5314,[]],[5314,5316,[4]],[5316,5317,[]],[5317,5330,[4]],[5330,5331,[4,6]],[5331,5335,[6]],[5335,5345,[8]],[5345,5346,[]],[5346,5347,[8]],[5347,5348,[]],[5348,5349,[8]],[5349,5350,[]],[5350,5351,[8]],[5351,5352,[]],[5352,5359,[8]]],
downArrowVisibleRanges:[[5205,5215],[5226,5237],[5241,5251],[5304,5323],[5324,5326],[5338,5347]],
geometry:'List column6 base rows4,6,8,10; cursor column5; arrow column18 row11. Quantity at columns16 and18. Parent visible labels column14 rows2 through14 at two-row intervals. Overlay retains only top detached mark column14 row1, lower とじる row14 and its dakuten column15 row13.',
contextNotes:'5200-5203 complete parent cursor row6 column13. 5204 bottom redraw removes せってい but keeps とじる. 5359 partial parent restoration overwrites marks of old list quantity row, retains やめ with last character occluded; no inference. 5360-5365 parent restored cursor row6 column13. Closing context remains uncertain.'};
for(const [key,start,stop] of [['spans',5200,5366],['cursorSpans',5205,5359]]){let n=start;for(const s of note[key]){assert.equal(s[0],n);assert(s[1]>n);n=s[1];}assert.equal(n,stop);}
const out='reports/m3-grid-refinement-v1/backpack-scroll-notes-v1/pass2.json';fs.writeFileSync(out,JSON.stringify(note,null,2)+'\n',{flag:'wx'});console.log(JSON.stringify({path:out,sha256:sha(out)}));
