'use strict';
// Transcription supplied by the coding agent after directly viewing every second-pass sheet.
// This records observations; it does not infer text from images or read tested OCR.
const fs=require('fs'),crypto=require('crypto'),assert=require('assert/strict');
const sha=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const read=p=>JSON.parse(fs.readFileSync(p,'utf8')),ident=p=>({path:p,sha256:sha(p)});
const root='reports/m3-grid-refinement-v1',base=root+'/oak-boundary-notes-v1';
const u='オーキド『まー！',a='あわてるな グリーン！',b='レッドは オーキド はかせに',c='とどけものを わたした！';
const items=[{id:'lab-oak-reply',sheet:'oak-reply',range:[13429,13512],spans:[
 [13429,13433,[],false,'no box'],[13433,13435,[],false,'empty box'],
 [13435,13436,['オー'],false],[13436,13438,['オーキド『'],false],
 [13438,13439,[u],false],[13439,13441,[u,'あわて'],false],
 [13441,13442,[u,'あわてるな'],false],[13442,13444,[u,'あわてるな グリー'],false],
 [13444,13467,[u,a],true],[13467,13470,['オーキト『まー！',a],false,'scrolling; top dakuten clipped; rows 13 and 15'],
 [13470,13471,[a],false],[13471,13473,[a,'お'],false],
 [13473,13474,[a,'おまえも'],false],[13474,13476,[a,'おまえも すき'],false],
 [13476,13477,[a,'おまえも すきなもの'],false],[13477,13479,[a,'おまえも すきなものを と'],false],
 [13479,13506,[a,'おまえも すきなものを とれ！'],false],[13506,13512,[],false,'no box']
 ],geometry:'Normal lines start column 1 at rows 14/16. Scroll at 13467-13469 moves both lines up one row; upper dakuten is absent. No detached marks observed.'},
 {id:'oak-delivery',sheet:'oak-delivery',range:[24211,24325],spans:[
 [24211,24215,[],false,'empty box'],[24215,24217,['レッド'],false],
 [24217,24218,['レッドは オ'],false],[24218,24220,['レッドは オーキド'],false],
 [24220,24221,['レッドは オーキド はか'],false],[24221,24223,[b,'と'],false],
 [24223,24224,[b,'とどけも'],false],[24224,24226,[b,'とどけものを'],false],
 [24226,24227,[b,'とどけものを わたし'],false],[24227,24289,[b,c],false],
 [24289,24316,[b,c],true],[24316,24325,[],false,'empty box']
 ],geometry:'Box present throughout. Lines start column 1 at rows 14/16; upper dakuten columns 3/9, lower column 2. No scrolling or detached marks observed.'}];
for(const it of items){
 let n=it.range[0];for(const s of it.spans){assert.equal(s[0],n);assert(s[1]>n);n=s[1];}assert.equal(n,it.range[1]);
 const sheet=root+'/'+it.sheet+'-boundary-pass2-sheets-v1/report.json',r=read(sheet);assert.equal(r.frameCount,n-it.range[0]);assert(r.allDisplayedPixelsMatchOriginal);
 const note={status:'second_direct_read_complete',range:it.range,input:ident(sheet),viewedSheets:[0,r.sheets.length-1],exposure:'Same coding agent; previous diagnostic, ledger and first-pass exposure disclosed. Every second-pass sheet directly viewed in order with GT and actual closed. No independent session or human verification claimed.',spans:it.spans,geometry:it.geometry};
 fs.writeFileSync(base+'/'+it.id+'-pass2.json',JSON.stringify(note,null,2)+'\n',{flag:'wx'});
}
console.log('Recorded 197 directly viewed second-pass frames; no GT mutation.');
