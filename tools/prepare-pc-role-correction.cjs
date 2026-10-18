'use strict';
// Offline metadata correction from completed original-image review; never reads OCR output.
const fs=require('fs'),crypto=require('crypto'),assert=require('assert/strict');
const read=p=>JSON.parse(fs.readFileSync(p,'utf8').replace(/^\uFEFF/,''));
const hash=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const notes='reports/m3-grid-refinement-v1/pc-role-recheck-notes-v1';
const auditPath='reports/m3-grid-refinement-v1/pc-role-contract-r6-v1.json';
const audit=read(auditPath),first=read(notes+'/pass1.json'),second=read(notes+'/pass2.json'),extract=read(notes+'/extraction-check.json');
assert.equal(first.allSheetsDirectlyViewed,33);assert.equal(second.totalFrames,189);
assert.deepEqual(second.directlyViewedSheets,{'pc-connect':[0,8],'pc-deposit-navigation':[0,20],'pc-exit':[0,2]});
assert.equal(extract.events.reduce((n,x)=>n+x.frames,0),189);assert(extract.events.every(x=>x.allPngBytesEqual));
assert.equal(hash(audit.freeze.path),audit.freeze.sha256);
const out='data/gt/candidates/ocr-observation-corrections-v5';assert(!fs.existsSync(out));
const results=[];
for(const id of ['pc-deposit-navigation','pc-exit']){
 const input=audit.scope.find(x=>x.id===id).annotation;assert.equal(hash(input.path),input.sha256);
 const old=fs.readFileSync(input.path,'utf8').trim().split(/\r?\n/).map(JSON.parse),next=JSON.parse(JSON.stringify(old));
 const changes=[];
 for(const f of audit.findings.filter(x=>x.clip===id)){
  const s=next.find(s=>s.startFrame===f.start&&s.endFrameExclusive===f.end),r=s.regions.find(r=>r.id===f.region);
  assert.equal(r.role,'menu_label');assert.equal(f.priorReferenceRole,'pc_menu_choice');assert.equal(r.readingOrder,f.referenceReadingOrder);
  assert.equal(r.text,f.physical.text);assert.deepEqual(r.gridRect,f.physical.gridRect);assert.deepEqual(r.sourceRect,f.physical.sourceRect);
  r.role='pc_menu_choice';changes.push({start:f.start,end:f.end,region:r.id,before:'menu_label',after:r.role});
 }
 // Only role changes, without rewriting historical observation evidence or any other properties.
 const restored=JSON.parse(JSON.stringify(next));for(const c of changes)restored.find(s=>s.startFrame===c.start).regions.find(r=>r.id===c.region).role=c.before;
 assert.deepEqual(restored,old);
 const dir=out+'/'+id;fs.mkdirSync(dir,{recursive:true});
 const parent=input.path.slice(0,input.path.lastIndexOf('/'));
 for(const n of fs.readdirSync(parent).filter(n=>!n.endsWith('.jsonl')))fs.copyFileSync(parent+'/'+n,dir+'/'+n);
 const target=dir+'/ocr-candidate-role-v1.jsonl';fs.writeFileSync(target,next.map(JSON.stringify).join('\n')+'\n',{flag:'wx'});
 results.push({id,oldGt:input,candidate:{path:target,sha256:hash(target)},changes});
}
assert.equal(results.reduce((n,x)=>n+x.changes.length,0),12);
const evidence={status:'original_review_supported_PC_role_correction_candidate_not_effective_freeze',authority:'如果确认冻结内容有误，可以在置信度较高的情况下调整冻结结论',results,affectedFrames:audit.affectedFrames,reference:audit.reference,unchanged:'All text, Unicode, positions, reading order, marks, cursors, kinds, page opportunities, boundaries and scoring rules. Only 12 region roles across 10 original frames change. Prior GT preserved.',evidence:['pass1.json','pass2.json','pass-differences.json','extraction-check.json'].map(n=>({path:notes+'/'+n,sha256:hash(notes+'/'+n)})),audit:{path:auditPath,sha256:hash(auditPath)},productAcceptance:false};
fs.writeFileSync(notes+'/candidate-evidence.json',JSON.stringify(evidence,null,2)+'\n',{flag:'wx'});
console.log(JSON.stringify(results.map(x=>({id:x.id,candidate:x.candidate,changedRegionStates:x.changes.length}))));
