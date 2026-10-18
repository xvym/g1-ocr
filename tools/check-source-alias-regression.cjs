'use strict';
const fs=require('fs'),assert=require('assert/strict'),crypto=require('crypto');
const root='reports/m3-grid-refinement-v1',out=process.argv[2];assert(out&&!fs.existsSync(out));
const read=p=>JSON.parse(fs.readFileSync(p)),sha=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const baseline=read(root+'/ocr-v9-observation-r12-comparison/report.json'),current=read(root+'/ocr-regression-all-v10/comparison/report.json');
assert.equal(current.freezeSha256,baseline.freezeSha256);assert.equal(current.clips.length,38);
let frames=0,changed=0;const inputs=[],changes=[];
for(const clip of current.clips){
 const old=baseline.clips.find(c=>c.clipId===clip.clipId);assert.deepEqual(clip.raw,old.raw);
 const oldPath=root+'/ocr-regression-all-v9/actual/'+clip.clipId+'.jsonl',newPath=root+'/ocr-regression-all-v10/actual/'+clip.clipId+'.jsonl';
 assert.equal(sha(oldPath),old.actualSha256);assert.equal(sha(newPath),clip.actualSha256);
 const rows=p=>fs.readFileSync(p,'utf8').trim().split(/\r?\n/).map(JSON.parse),a=rows(oldPath),b=rows(newPath);assert.equal(a.length,b.length);
 const oldTimeline=rows(root+'/ocr-regression-all-v9/'+clip.clipId+'-timeline.jsonl'),newTimeline=rows(root+'/ocr-regression-all-v10/'+clip.clipId+'-timeline.jsonl');assert.equal(oldTimeline.length,newTimeline.length);assert.equal(b.length,newTimeline.length);
 for(let i=0;i<newTimeline.length;i++)for(const key of ['sourceFrameIndex','sourcePts','durationPts','timeBase','rgbSha256','scored','sourceOffsetNanos'])assert.deepEqual(newTimeline[i][key],oldTimeline[i][key]);
 inputs.push({clip:clip.clipId,beforeSha256:sha(oldPath),afterSha256:sha(newPath)});
 for(let i=0;i<a.length;i++){
  frames++;const before={...a[i]},after={...b[i]};delete before.processingNanos;delete after.processingNanos;delete after.correctedRegions;delete after.correctionEvidence;assert.deepEqual(after,before);
  if(!b[i].correctedRegions)continue;
  changed++;const corrected=b[i].correctedRegions;assert.equal(corrected.length,b[i].rawRegions.length);
  for(let j=0;j<corrected.length;j++){
   const raw=b[i].rawRegions[j],final=corrected[j];assert.deepEqual({...final,text:raw.text},raw);
   if(final.text===raw.text)continue;
   const proofs=b[i].correctionEvidence.filter(p=>p.regionId===raw.id);assert.equal(proofs.length,1);const p=proofs[0];assert.equal(p.rawText,raw.text);assert.equal(p.text,final.text);assert(p.sourceReferences.length>0);
   assert.equal(raw.text.length,final.text.length);
   for(let k=0;k<final.text.length;k++)assert(raw.glyphCandidates[k].includes(final.text[k]));
   for(const ref of p.sourceReferences)assert.equal(ref.literal,final.text);
   changes.push({clip:clip.clipId,frame:b[i].sourceFrameIndex,id:raw.id,before:raw.text,after:final.text});
  }
 }
}
assert.equal(frames,3577);assert.equal(changed,11);assert.equal(changes.length,11);
fs.mkdirSync(out,{recursive:true});fs.writeFileSync(out+'/report.json',JSON.stringify({status:'development_regression_verified_not_independent_acceptance',freezeSha256:current.freezeSha256,baselineComparisonSha256:sha(root+'/ocr-v9-observation-r12-comparison/report.json'),comparisonSha256:sha(root+'/ocr-regression-all-v10/comparison/report.json'),frames,changed,rawAndStateUnchanged:true,inputs,changes,aggregate:current.aggregate,productAcceptance:false},null,2)+'\n',{flag:'wx'});
console.log(JSON.stringify({frames,changed,reportSha256:sha(out+'/report.json'),aggregate:current.aggregate}));
