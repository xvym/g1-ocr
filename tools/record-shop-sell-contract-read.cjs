'use strict';
// Observations transcribed from all 62 original frames; no tested output input.
const fs=require('fs'),crypto=require('crypto'),assert=require('assert/strict');
const pass=Number(process.argv[2]);assert([1,2].includes(pass));
const dir='reports/m3-grid-refinement-v1/shop-sell-contract-notes-v1';
fs.mkdirSync(dir,{recursive:true});
const sheet=`reports/m3-grid-refinement-v1/shop-sell-contract-pass${pass}-sheets-v1/report.json`;
const sha=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const r=JSON.parse(fs.readFileSync(sheet));assert.equal(r.frameCount,62);assert(r.allDisplayedPixelsMatchOriginal);
const spans=[
 [1042,1048,'そのほかに わたくしどもで|おちからに なれることは？','parent','22526円','filled:1',null,false],
 [1048,1056,'そのほかに わたくしどもで|おちからに なれることは？','parent','22526円','filled:3',null,false],
 [1056,1057,'そのほかに わたくしどもで|おちからに なれることは？','parent','22526円','outline:3',null,false],
 [1057,1058,'','parent','22526円','outline:3',null,false],
 [1058,1059,'どれを','parent','22526円','outline:3',null,false],
 [1059,1060,'どれを うっ','parent','22526円','outline:3',null,false],
 [1060,1062,'どれを うっていた','parent','22526円','outline:3',null,false],
 [1062,1063,'どれを うっていただけま','parent','22526円','outline:3',null,false],
 [1063,1068,'どれを うっていただけますか？','parent','','outline:3',null,false],
 [1068,1069,'どれを うっていただけますか？','partial_list_lower_three_items','','outline:3',null,true],
 [1069,1070,'どれを うっていただけますか？','list','22526円','outline:3',null,true],
 [1070,1082,'どれを うっていただけますか？','list','22526円','outline:3',4,true],
 [1082,1092,'どれを うっていただけますか？','list','22526円','outline:3',4,false],
 [1092,1096,'どれを うっていただけますか？','list','22526円','outline:3',6,false],
 [1096,1104,'どれを うっていただけますか？','list','22526円','outline:3',6,true]
];
const observations=spans.map(([start,end,text,layer,money,parentCursor,itemCursorRow,listDownArrow])=>({start,end,text,layer,money,parentCursor,itemCursorRow,listDownArrow}));
for(let f=1042;f<1104;f++)assert.equal(observations.filter(x=>x.start<=f&&x.end>f).length,1);
const result={pass,range:[1042,1104],status:'complete_original_context_read_not_candidate',sourceVerification:{path:r.inputVerification,sha256:r.inputVerificationSha256},sheets:{path:sheet,sha256:sha(sheet)},observations,
 fields:{parent:['かいに きた','うりに きた','べつに いいです'],moneyLabel:'おかね',items:['じてんしゃ','ポケモンのふえ','いいつりざお','ボロのつりざお'],occludedParent:['かいに きた','うり','べつ']},
 continuity:'The sell question stays visible without clearing or changing when the item list is drawn. At1068 the lower three item rows and bottom list border are drawn while the full parent labels remain; at1069 the top list layer obscures sell/leave suffixes. The list down arrow is distinct from a message wait arrow.',
 limitations:'Same coding agent. Historical approved annotations and role audit already exposed before this recheck; no tested output open during this read. Question glyph transcribed as fullwidth for readability, not a new encoding decision. Role strings/order are contracts, not visible pixels. No human per-frame or independent-session claim.'};
fs.writeFileSync(`${dir}/pass${pass}.json`,JSON.stringify(result,null,2)+'\n',{flag:'wx'});
console.log(JSON.stringify({path:`${dir}/pass${pass}.json`,sha256:sha(`${dir}/pass${pass}.json`)}));
