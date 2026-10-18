'use strict';
const fs=require('fs'),path=require('path'),crypto=require('crypto'),assert=require('assert/strict');
const read=p=>JSON.parse(fs.readFileSync(p,'utf8')),sha=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex'),ident=p=>({path:p,sha256:sha(p)});
const base='reports/m3-grid-refinement-v1/backpack-scroll-notes-v1',audit=read(base+'/frozen-comparison.json');
assert.equal(sha(base+'/frozen-comparison.json'),'d5757eb14566228f98f15cec3a8bed136d6d0858e331eeaae0de608bce122ec9');
for(const p of audit.payloads)assert.equal(sha(p.path),p.sha256);
const input=audit.payloads.find(x=>x.kind==='gt'),old=fs.readFileSync(input.path,'utf8').trim().split(/\r?\n/).map(JSON.parse),next=structuredClone(old);
assert.deepEqual(audit.differences.map(x=>x.frame),[5301,5304,5306,5347,5349]);
const changes=[];
for(const d of audit.differences){
 const index=next.findIndex(x=>x.startFrame===d.frame),s=next[index];assert.equal(s.endFrameExclusive,d.frame+1);
 for(let k=0;k<s.regions.length;k++){const observed=d.observed.regions[k],r=s.regions[k];assert.equal(r.role,observed.role);assert.equal(r.readingOrder,observed.readingOrder);for(const key of ['text','gridRect','sourceRect','markCells'])r[key]=structuredClone(observed[key]);}
 s.detachedMarks=d.observed.detachedMarks.map(m=>({...m,renderNote:m.gridRect[1]===1?old[index].detachedMarks[0].renderNote:'Visible old upper mark during list redraw; current base does not combine into a voiced kana. Retain separately without restoring old word.'}));
 s.evidence={manualNotes:base+'/pass2.json',notesCorrection:base+'/notes-correction.json',originalFrames:'data/frames/video-04/backpack-scroll-pass2-v1',humanPerFrameVerification:'not claimed',outputIndependence:'Full original-frame double reread with tested output closed; prior diagnostic/annotation exposure and same-agent memory disclosed. Not independent-session acceptance.'};
 assert.deepEqual(s.cursor,old[index].cursor);assert.equal(s.kind,old[index].kind);assert.equal(s.completePage,old[index].completePage);
 changes.push({frame:d.frame,before:old[index],after:s,characterDelta:s.regions.reduce((v,r)=>v+[...r.text].length,0)-old[index].regions.reduce((v,r)=>v+[...r.text].length,0)});
}
const restored=structuredClone(next);for(const d of changes){const idx=restored.findIndex(x=>x.startFrame===d.frame);restored[idx]=d.before;}assert.deepEqual(restored,old);
const out='data/gt/candidates/ocr-observation-corrections-v10/backpack-navigation';assert(!fs.existsSync(out));fs.mkdirSync(out,{recursive:true});
for(const p of audit.payloads.filter(x=>x.kind!=='gt'))fs.copyFileSync(p.path,out+'/'+path.basename(p.path),fs.constants.COPYFILE_EXCL);
const candidate=out+'/ocr-candidate-scroll-v1.jsonl';fs.writeFileSync(candidate,next.map(JSON.stringify).join('\n')+'\n',{flag:'wx'});
const evidence={status:'candidate_not_effective_freeze',helper:ident(__filename),audit:ident(base+'/frozen-comparison.json'),input,candidate:ident(candidate),changes,characterDelta:changes.reduce((n,x)=>n+x.characterDelta,0),rationale:'Five single-frame redraws retain old upper marks while base rows change. Full 166-frame double read and 152-frame field/geometry/cursor/state comparison; never chose corrections from tested output.',productAcceptance:false};
fs.writeFileSync(base+'/candidate-evidence.json',JSON.stringify(evidence,null,2)+'\n',{flag:'wx'});console.log(JSON.stringify({candidate:evidence.candidate,changedFrames:changes.map(x=>x.frame),characterDelta:evidence.characterDelta}));
