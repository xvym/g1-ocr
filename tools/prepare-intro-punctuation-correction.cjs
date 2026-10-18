'use strict';
// Human-supplied original-frame observations only; no tested OCR input.
const fs=require('fs'),crypto=require('crypto'),assert=require('assert/strict');
const read=p=>JSON.parse(fs.readFileSync(p,'utf8')),sha=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex'),ident=p=>({path:p,sha256:sha(p)});
const base='reports/m3-grid-refinement-v1/intro-punctuation-notes-v1',check=read(base+'/extraction-and-pass-check.json');
assert.equal(check.frames.length,80);assert(check.allPngBytesEqual&&check.allMetadataEqual);
for(const p of check.notes)assert.equal(sha(p.path),p.sha256);
const root='data/gt/frozen/ocr-all-scenes-v2-observation-r8',freeze=read(root+'/freeze.json'),it=freeze.items.find(x=>x.id==='intro-research');
const payloads=it.payloads.map(p=>{const path=p.file?root+'/'+p.file:p.approvedSource;assert.equal(sha(path),p.sha256);return {kind:p.kind,...ident(path)};});
const input=payloads.find(x=>x.kind==='gt'),old=fs.readFileSync(input.path,'utf8').trim().split(/\r?\n/).map(JSON.parse),next=JSON.parse(JSON.stringify(old)),notes=read(base+'/pass2.json');
for(let n=6404;n<6480;n++){
 const s=old.find(s=>s.startFrame<=n&&s.endFrameExclusive>n),observed=notes.textSpans.find(s=>s[0]<=n&&s[1]>n).slice(2).filter(Boolean),expected=s.regions.map(r=>r.text);
 if(n===6408||n===6409){assert.deepEqual(expected,['そして・']);assert.deepEqual(observed,['そして・・']);}else assert.deepEqual(expected,observed);
 // Full original PNG recheck corrected the draft notes' 6419 arrow transcription.
 const arrow=(n>=6410&&n<6429)||(n>=6453&&n<6469);assert.equal(s.cursor.downArrowVisible,arrow);
}
const s=next.find(s=>s.startFrame===6408);assert.equal(s.endFrameExclusive,6410);assert.equal(s.regions.length,1);
const r=s.regions[0];assert.deepEqual(r.gridRect,[1,14,5,15]);assert.deepEqual(r.sourceRect,[256,320,320,336]);
r.text='そして・・';r.gridRect[2]=6;r.sourceRect[2]=336;
s.evidence={manualNotes:base+'/pass2.json',humanPerFrameVerification:'not claimed',outputIndependence:'Original-frame rereads with GT/actual closed; prior diagnostic exposure disclosed. Not an unseen independent acceptance sample.',originalFrames:'data/frames/video-01/intro-punctuation-pass2-v1'};
const restored=JSON.parse(JSON.stringify(next));restored[old.findIndex(s=>s.startFrame===6408)]=old.find(s=>s.startFrame===6408);assert.deepEqual(restored,old);
const target='data/gt/candidates/ocr-observation-corrections-v7/intro-research';assert(!fs.existsSync(target));fs.mkdirSync(target,{recursive:true});
for(const p of payloads.filter(p=>p.kind!=='gt'))fs.copyFileSync(p.path,target+'/'+p.path.split('/').pop());
const gt=target+'/ocr-candidate-punctuation-v1.jsonl';fs.writeFileSync(gt,next.map(JSON.stringify).join('\n')+'\n',{flag:'wx'});
const report={status:'candidate_only_not_effective_freeze',freeze:ident(root+'/freeze.json'),input,candidate:ident(gt),helper:ident(__filename),doubleRead:ident(base+'/extraction-and-pass-check.json'),notesCorrection:ident(base+'/notes-correction.json'),changes:[{start:6408,end:6410,before:old.find(s=>s.startFrame===6408),after:s}],rationale:'Both original-frame passes show a second dot already visible in column 5 at frames 6408/6409; the third dot and arrow appear at 6410. Correct only the observed second dot and its necessary rectangle, never anticipating the third.',unchanged:'All retained/scored frames, timestamps, state boundaries, roles, reading order, page opportunities, cursor and scoring rules.',productAcceptance:false};
fs.writeFileSync(base+'/candidate-evidence.json',JSON.stringify(report,null,2)+'\n',{flag:'wx'});console.log(JSON.stringify(report.candidate));
