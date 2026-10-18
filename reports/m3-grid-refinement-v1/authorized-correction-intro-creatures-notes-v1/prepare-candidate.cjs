'use strict';
// Evidence-only correction. No tested OCR output is an input.
const fs=require('fs'),crypto=require('crypto'),path=require('path');
const json=p=>JSON.parse(fs.readFileSync(p,'utf8').replace(/^\uFEFF/,''));
const hash=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const identity=p=>({path:p,sha256:hash(p)}),assert=(yes,message)=>{if(!yes)throw Error(message);};
const here='reports/m3-grid-refinement-v1/authorized-correction-intro-creatures-notes-v1';
const original='data/gt/frozen/ocr-all-scenes-v2/intro-creatures/ocr-candidate-v1.jsonl';
const output='data/gt/candidates/ocr-observation-corrections-v1/intro-creatures/ocr-candidate-v2.jsonl';
assert(!fs.existsSync(output)&&!fs.existsSync(here+'/correction-evidence.json'),'Use a new candidate/evidence version');
assert(hash(original)==='34c2ba7dd81354736156368f36ed6a4934b951300e8bb97219ac526a8e15729b','Original frozen identity differs');
const notes=json(here+'/pass2.json'),pixels=json(here+'/absent-cell-pixel-checks-v2.json');
const pairs=[];for(let n=6246;n<6324;n++) {
 const files=[1,2].map(p=>'data/frames/video-01/authorized-correction-intro-creatures-pass'+p+'-v1/frame-'+String(n).padStart(6,'0')+'.png');
 assert(hash(files[0])===hash(files[1]),'Independent source extractions differ');pairs.push({frame:n,sha256:hash(files[0])});
}
for(const check of pixels)assert(check.constantLuminance&&check.minimum===140&&check.maximum===140&&hash(check.png)===check.sha256,'Absent-cell check invalid');
const states=fs.readFileSync(original,'utf8').trim().split(/\r?\n/).map(JSON.parse),changes=[];
for(const state of states) {
 const observed=notes.textRanges.find(r=>r[0]<=state.startFrame&&r[1]>=state.endFrameExclusive);
 assert(observed&&observed[2].length===state.regions.length,'Unresolved text boundary or number of fields');
 state.regions.forEach((r,i)=>{
  const text=observed[2][i];if(r.text===text)return;
  assert([[6258,6259],[6291,6293],[6293,6294]].some(x=>x[0]===state.startFrame&&x[1]===state.endFrameExclusive),'Unreviewed correction');
  const before=JSON.parse(JSON.stringify(r));r.text=text;r.gridRect[2]=r.gridRect[0]+Array.from(text).length;
  r.markCells=r.markCells.filter(m=>m.gridColumn<r.gridRect[2]);
  const top=Math.min(r.gridRect[1],...r.markCells.map(m=>m.gridRow));
  r.sourceRect=[240+r.gridRect[0]*16,96+top*16,240+r.gridRect[2]*16,96+r.gridRect[3]*16];
  changes.push({startFrame:state.startFrame,endFrameExclusive:state.endFrameExclusive,before,after:JSON.parse(JSON.stringify(r)),reason:'Both full-scene original-image reads and constant-luminance original cells show that the old suffix has not appeared yet.'});
 });
 if(changes.some(c=>c.startFrame===state.startFrame))state.evidence={...state.evidence,correctionReview:here+'/correction-evidence.json',outputIndependence:'Correction transcribed from two fresh original-image reviews; earlier diagnostic awareness disclosed. No tested output read during reviews or used as correction input.',manualNotes:here+'/pass2.json',originalFrames:'data/frames/video-01/authorized-correction-intro-creatures-pass2-v1'};
}
assert(changes.length===3,'Unexpected changed state count');
fs.mkdirSync(path.dirname(output),{recursive:true});fs.writeFileSync(output,states.map(s=>JSON.stringify(s)).join('\n')+'\n',{flag:'wx'});
const result={status:'high_confidence_old_text_refuted_corrected_candidate_not_yet_effective_freeze',authority:{date:'2026-10-16 Asia/Shanghai',userMessage:'如果确认冻结内容有误，可以在置信度较高的情况下调整冻结结论',meaning:'Conditional authorization for evidence-backed corrections; not a claim that the user reviewed these frames or a new version.'},oldGt:identity(original),candidate:identity(output),observations:[identity(here+'/pass1.json'),identity(here+'/pass2.json'),identity(here+'/absent-cell-pixel-checks-v2.json')],inputVerifications:[1,2].map(p=>identity('data/frames/video-01/authorized-correction-intro-creatures-pass'+p+'-v1/verification.json')),independentPngPairs:pairs,changes,changedOriginalFrames:[6258,6291,6292,6293],expectedCharacterDelta:-5,retainedFrames:78,scoredFrames:74,pageOpportunities:2,unchanged:'All frame/PTS boundaries, scene range, page opportunities, field roles/read order, contract and layout. No scoring outputs used or modified.',next:'Include this complete affected scene in the unified affected-scope correction collection. Existing frozen files and historical scores remain intact; this candidate is not silently substituted into them.'};
fs.writeFileSync(here+'/correction-evidence.json',JSON.stringify(result,null,2)+'\n',{flag:'wx'});
console.log('Three corrected states / four original frames; old frozen files unchanged. Candidate requires integrity and affected-scope integration.');
