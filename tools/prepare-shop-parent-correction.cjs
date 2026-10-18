'use strict';
// Contract consistency correction. No actual OCR, score, or recognizer is read.
const fs=require('fs'),path=require('path'),crypto=require('crypto'),assert=require('assert/strict');
const json=p=>JSON.parse(fs.readFileSync(p,'utf8')),rows=p=>fs.readFileSync(p,'utf8').trim().split(/\r?\n/).map(JSON.parse);
const sha=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex'),ident=p=>({path:p,sha256:sha(p)});
const root='data/gt/frozen/ocr-all-scenes-v2-observation-r10',freeze=json(root+'/freeze.json');
assert.equal(sha(root+'/freeze.json'),'fd098d60c720dfad568475d6ee16ba71e5338e9faa22982c80cf5b6590b6ace2');
for(const it of freeze.items)for(const p of it.payloads)assert.equal(sha(p.file?root+'/'+p.file:p.approvedSource),p.sha256);
const payload=(id,kind)=>{const p=freeze.items.find(x=>x.id===id).payloads.find(x=>x.kind===kind);return p.file?root+'/'+p.file:p.approvedSource;};
const notes='reports/m3-grid-refinement-v1/shop-sell-contract-notes-v1',passes=[1,2].map(n=>json(`${notes}/pass${n}.json`));
assert.deepEqual(passes[0].observations,passes[1].observations);assert.deepEqual(passes[0].fields,passes[1].fields);
const vers=passes.map(p=>{assert.equal(sha(p.sourceVerification.path),p.sourceVerification.sha256);assert.equal(sha(p.sheets.path),p.sheets.sha256);for(const s of json(p.sheets.path).sheets)assert.equal(sha(s.path),s.sha256);return json(p.sourceVerification.path);});
assert.deepEqual(vers[0].frames,vers[1].frames);assert.equal(vers[0].frames.length,62);
for(const f of vers[0].frames){const n=`frame-${String(f.frameIndex).padStart(6,'0')}.png`;assert.equal(sha(path.dirname(passes[0].sourceVerification.path)+'/'+n),sha(path.dirname(passes[1].sourceVerification.path)+'/'+n));}
const exitNotes='reports/m3-grid-refinement-v1/shop-dialogue-role-notes-v1/extraction-and-pass-check.json',exitCheck=json(exitNotes);
assert(exitCheck.allPngBytesEqual&&exitCheck.allMetadataEqual);assert.equal(exitCheck.frames,49);for(const n of exitCheck.notes)assert.equal(sha(n.path),n.sha256);
const exit=rows(payload('shop-exit','gt')),reference=exit.find(s=>s.startFrame===1450),old=rows(payload('shop-sell-entry','gt')),next=JSON.parse(JSON.stringify(old));
const physical=r=>({text:r.text,gridRect:r.gridRect,sourceRect:r.sourceRect,markCells:r.markCells});
for(const r of old[0].regions)assert(reference.regions.some(t=>require('util').isDeepStrictEqual(physical(r),physical(t))));
const approved='data/gt/frozen/ocr-shop-metadata-r2/freeze.json';assert.equal(sha(approved),'141777e5b8c357239135450bdedd2adf43d9dd5ca35f1914f956a9f23422f016');
const roleMap={menu_label:'shop_menu_choice',money_label:'shop_money_label',money:'money_amount',shop_prompt:'dialogue_line'},changes=[];
for(const s of next){
 const before=JSON.parse(JSON.stringify(s));
 // Only the complete parent layer uses its established screen-order contract.
 // Partial item-list redraw and list fields retain their existing layer contract.
 const parent=s.endFrameExclusive<=1068;
 for(const r of s.regions)if(parent||r.role==='shop_prompt'){
  assert(roleMap[r.role]);r.role=roleMap[r.role];
 }
 if(parent){s.regions.sort((a,b)=>a.gridRect[1]-b.gridRect[1]||a.gridRect[0]-b.gridRect[0]);s.regions.forEach((r,i)=>r.readingOrder=i);}
 for(const r of s.regions){const p=before.regions.find(x=>x.id===r.id);if(p.role!==r.role||p.readingOrder!==r.readingOrder)changes.push({start:s.startFrame,end:s.endFrameExclusive,id:r.id,before:{role:p.role,readingOrder:p.readingOrder},after:{role:r.role,readingOrder:r.readingOrder}});}
 // Undo only role/order and permutation: every other byte-level JSON value must match.
 const restored=JSON.parse(JSON.stringify(s));restored.regions=before.regions.map(p=>{const r=restored.regions.find(x=>x.id===p.id);r.role=p.role;r.readingOrder=p.readingOrder;return r;});assert.deepEqual(restored,before);
}
assert.equal(next.length,15);assert.equal(next[0].startFrame,1044);assert.equal(next.at(-1).endFrameExclusive,1102);
const dir='data/gt/candidates/ocr-observation-corrections-v9/shop-sell-entry';assert(!fs.existsSync(dir));fs.mkdirSync(dir,{recursive:true});
for(const kind of ['clip','layout'])fs.copyFileSync(payload('shop-sell-entry',kind),dir+'/'+kind+'-reference.json',fs.constants.COPYFILE_EXCL);
const target=dir+'/ocr-candidate-parent-role-v1.jsonl';fs.writeFileSync(target,next.map(JSON.stringify).join('\n')+'\n',{flag:'wx'});
const evidence={status:'candidate_only_not_effective',authority:'如果确认冻结内容有误，可以在置信度较高的情况下调整冻结结论',base:ident(root+'/freeze.json'),priorExplicitApproval:ident(approved),reference:{gt:ident(payload('shop-exit','gt')),frame:1450},oldGt:ident(payload('shop-sell-entry','gt')),candidate:ident(target),changes,affectedFrames:58,parentFrames:24,continuousMessageOnlyAfter:1068,
 evidence:[...passes.flatMap((p,i)=>[ident(`${notes}/pass${i+1}.json`),p.sourceVerification,p.sheets]),ident(exitNotes)],
 rationale:'Same physical full parent fields use the explicitly approved legacy shop convention. The unchanged sell question retains that role across an overlay, as established by complete original-frame double reading. English role names are a canonical contract choice anchored in prior approval, not a glyph fact. Partial and complete list fields are separate layout contracts. No actual output or score selected this convention.',
 unchanged:'All text/Unicode, variable metadata, rectangles, marks, cursors, kinds, page opportunities, frame/PTS boundaries and scoring keys/rules. Other37 entries unchanged. No question-encoding change.',productAcceptance:false};
fs.writeFileSync(dir+'/candidate-evidence.json',JSON.stringify(evidence,null,2)+'\n',{flag:'wx'});
console.log(JSON.stringify({candidate:ident(target),changes:changes.length,affectedFrames:58,parentFrames:24}));
