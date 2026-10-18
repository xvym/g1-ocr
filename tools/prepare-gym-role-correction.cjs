'use strict';
// Metadata-only candidate from original double reads; no tested OCR input.
const fs=require('fs'),crypto=require('crypto'),assert=require('assert/strict');
const read=p=>JSON.parse(fs.readFileSync(p,'utf8'));
const sha=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const auditPath='reports/m3-grid-refinement-v1/gym-dialogue-contract-r7-v2.json';
const audit=read(auditPath),base='reports/m3-grid-refinement-v1/gym-dialogue-role-notes-v1';
const check=read(base+'/extraction-and-pass-check.json');
assert.equal(check.frames,68);assert(check.allPngBytesEqual&&check.allVerificationMetadataEqual);
assert.deepEqual(check.textAndArrowDifferences,[]);
for(const n of check.notes)assert.equal(sha(n.path),n.sha256);
assert.equal(sha(audit.freeze.path),audit.freeze.sha256);
const trial=audit.scope.find(x=>x.id==='approved-trial');
const input=trial.payloads.find(x=>x.kind==='gt');assert.equal(sha(input.path),input.sha256);
const old=readRows(input.path),next=JSON.parse(JSON.stringify(old)),changes=[];
for(const s of next)for(const r of s.regions){
 assert.equal(r.role,'dialogue_line');
 changes.push({start:s.startFrame,end:s.endFrameExclusive,region:r.id,before:r.role,after:'dialogue'});
 r.role='dialogue';
}
assert(changes.length>0);
const restore=JSON.parse(JSON.stringify(next));
for(const s of restore)for(const r of s.regions)r.role='dialogue_line';
assert.deepEqual(restore,old);
const target='data/gt/candidates/ocr-observation-corrections-v6/approved-trial';
assert(!fs.existsSync(target));fs.mkdirSync(target,{recursive:true});
for(const p of trial.payloads.filter(p=>p.kind!=='gt')){
 assert.equal(sha(p.path),p.sha256);
 fs.copyFileSync(p.path,target+'/'+p.path.split('/').pop());
}
const gt=target+'/ocr-candidate-role-v1.jsonl';
fs.writeFileSync(gt,next.map(JSON.stringify).join('\n')+'\n',{flag:'wx'});
const evidence={status:'candidate_only_not_effective_freeze',authority:'如果确认冻结内容有误，可以在置信度较高的情况下调整冻结结论',input,candidate:{path:gt,sha256:sha(gt)},changes,audit:{path:auditPath,sha256:sha(auditPath)},doubleRead:{path:base+'/extraction-and-pass-check.json',sha256:sha(base+'/extraction-and-pass-check.json')},rationale:'The ordinary sign dialogue continues across 5298/5299 with the same physical field. Adopt the existing ordinary-dialogue role used by the continuation; no observable scene or field transition explains a role change at this annotation boundary.',scopeDecision:'Only ordinary gym sign dialogue. PC main-menu connection and shop parent-menu messages have different scene contexts; their dialogue_line labels are not globally rewritten. Shop parent metadata needs a separate consistent scene review, including prior approved overlap amendment.',unchanged:'All text, Unicode, source/grid rectangles, reading order, state kinds, symbols, boundaries, page IDs and scoring rules.',productAcceptance:false};
fs.writeFileSync(base+'/candidate-evidence.json',JSON.stringify(evidence,null,2)+'\n',{flag:'wx'});
console.log(JSON.stringify({candidate:evidence.candidate,changedRegionStates:changes.length}));
function readRows(p){return fs.readFileSync(p,'utf8').trim().split(/\r?\n/).map(JSON.parse);}
