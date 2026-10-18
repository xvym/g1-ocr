const fs=require('fs'),crypto=require('crypto'),assert=require('assert/strict');
const hash=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const notes='reports/m3-grid-refinement-v1/authorized-correction-safari-warden-npc-notes-v1',base='data/gt/frozen/ocr-all-scenes-v2/safari-warden-npc',out='data/gt/candidates/ocr-observation-corrections-v4/safari-warden-npc';
assert(!fs.existsSync(out),'New candidate directory only');
const proof=JSON.parse(fs.readFileSync(notes+'/space-cell-evidence.json'));
assert.equal(proof.pairedOriginalFrames.length,83);assert.equal(proof.measurements.length,26);
for(const [i,m] of proof.measurements.entries()){
 assert.equal(m.frame,4496+i);assert(m.cells[4].darkPixelCount>=27);assert(m.cells[5].darkPixelCount>=99);assert.equal(m.cells[8].darkPixelCount,0);
 assert.equal(m.cells[7].darkPixelCount===0,m.frame<4498);
}
for(const pass of ['pass1','pass2']){const p=JSON.parse(fs.readFileSync(notes+'/'+pass+'.json'));assert.deepEqual(p.range,[4445,4528]);assert.equal(p.readSheets.length,14);}
const oldPath=base+'/ocr-candidate-v1.jsonl',old=fs.readFileSync(oldPath,'utf8').trim().split(/\r?\n/).map(JSON.parse),next=JSON.parse(JSON.stringify(old)),changes=[];
for(const s of next){if(s.startFrame<4496||s.startFrame>=4522)continue;
 const r=s.regions[0];assert.equal(r.id,'message-top');assert(r.text.startsWith('でも…… 「は」'));assert(s.endFrameExclusive<=4522);
 const before=JSON.parse(JSON.stringify(r));r.text=r.text.replace('でも…… 「','でも……「');r.gridRect[2]--;r.sourceRect[2]-=18;
 changes.push({start:s.startFrame,end:s.endFrameExclusive,before,after:r});
 s.evidence={...s.evidence,priorAnnotationEvidence:s.evidence,outputIndependence:'Two complete original-image readings, after disclosed earlier diagnostic exposure. Actual and GT closed during readings. Original fixed-cell measurements confirm the quote in column5, not a blank.',originalFrames:'data/frames/video-02/authorized-correction-safari-warden-npc-pass2-v1',correctionNotes:notes,humanPerFrameVerification:'not claimed'};
}
assert.equal(changes.length,6);assert.equal(changes.reduce((n,c)=>n+c.end-c.start,0),26);
fs.mkdirSync(out,{recursive:true});for(const n of fs.readdirSync(base).filter(n=>n!=='ocr-candidate-v1.jsonl'))fs.copyFileSync(base+'/'+n,out+'/'+n);
const target=out+'/ocr-candidate-v2.jsonl';fs.writeFileSync(target,next.map(JSON.stringify).join('\n')+'\n');
fs.writeFileSync(notes+'/candidate-evidence.json',JSON.stringify({status:'high_confidence_original_spacing_correction_candidate_not_effective_freeze',authority:'如果确认冻结内容有误，可以在置信度较高的情况下调整冻结结论',oldGt:{path:oldPath,sha256:hash(oldPath)},candidate:{path:target,sha256:hash(target)},changes,weightedCharacterDelta:-26,unchanged:'All original frames, time intervals, page opportunities, states, other text, roles, marks and cursors. Only remove an erroneously transcribed blank before quote; corresponding upper-region right edge moves left one observed cell. No scoring threshold changes. Prior versions retained.',evidence:['pass1.json','pass2.json','space-cell-evidence.json','4496-row-original-nearest6.png'].map(n=>({path:notes+'/'+n,sha256:hash(notes+'/'+n)})),productAcceptance:false},null,2)+'\n');
console.log(JSON.stringify({candidate:target,changedIntervals:changes.length,changedFrames:26,sha256:hash(target)}));
