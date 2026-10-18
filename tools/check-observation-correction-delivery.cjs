'use strict';
const fs=require('fs'),path=require('path'),crypto=require('crypto');
const json=p=>JSON.parse(fs.readFileSync(p,'utf8')),rows=p=>fs.readFileSync(p,'utf8').trim().split(/\r?\n/).map(JSON.parse);
const sha=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const identity=p=>({path:p,sha256:sha(p)}),assert=(ok,msg)=>{if(!ok)throw Error(msg);};
const out=process.argv[2];assert(out&&!fs.existsSync(out),'Use a new report directory');
const frozen='data/gt/frozen/ocr-all-scenes-v2-observation-r3',review='reports/gt-review/gt-observation-corrections-v1',run='reports/m3-grid-refinement-v1/ocr-regression-all-v6',comparison='reports/m3-grid-refinement-v1/ocr-v6-observation-r3-comparison';
const e=json(frozen+'/freeze.json'),prior=json(e.previousEffectiveFreeze.path),manifest=json(e.approvedBundle.path);
assert(sha(e.previousEffectiveFreeze.path)===e.previousEffectiveFreeze.sha256&&sha(e.approvedBundle.path)===e.approvedBundle.sha256&&sha(e.identityCheck.path)===e.identityCheck.sha256,'Authority/evidence identity changed');
for(const f of manifest.files)assert(sha(review+'/'+f.path)===f.sha256&&fs.statSync(review+'/'+f.path).size===f.bytes,'Review file changed');
for(const n of ['batch.json','summary.json'])assert(sha(frozen+'/'+n)===sha(review+'/'+n),'Frozen metadata changed');
const permitted=new Set(e.approval.changes.flatMap(c=>c.frames.map(n=>c.id+':'+n))),changedGt=[],changedScores=[];
assert(permitted.size===6&&e.allSemanticIntegrityPassed===false&&!e.productAcceptance,'Qualification removed');
const oldScore=json(run+'/comparison/report.json'),newScore=json(comparison+'/report.json');assert(sha(frozen+'/freeze.json')===newScore.freezeSha256,'Comparison freeze mismatch');
const stateMap=p=>new Map(rows(p).flatMap(s=>Array.from({length:s.endFrameExclusive-s.startFrame},(_,i)=>[s.startFrame+i,s])));
const actuals=[];let frameRefs=0,characterDelta=0;
for(const item of e.items){
 const old=prior.items.find(x=>x.id===item.id);assert(old,'New unapproved event');
 for(const p of item.payloads){const file=p.file?frozen+'/'+p.file:p.approvedSource;assert(sha(file)===p.sha256&&fs.statSync(file).size===p.bytes,'Frozen payload mismatch');const original=old.payloads.find(x=>x.kind===p.kind);if(p.kind!=='gt')assert(p.sha256===original.sha256,'Non-GT scope changed');}
 const p=item.payloads.find(x=>x.kind==='gt'),before=stateMap(old.payloads.find(x=>x.kind==='gt').approvedSource),after=stateMap(p.file?frozen+'/'+p.file:p.approvedSource);assert(before.size===after.size,'Removed source frame');
 for(const [n,a] of before){const b=after.get(n);assert(b,'Missing source frame');if(JSON.stringify(a)!==JSON.stringify(b)){assert(permitted.has(item.id+':'+n),'Unreviewed annotation changed');changedGt.push(item.id+':'+n);}characterDelta+=b.regions.reduce((x,r)=>x+Array.from(r.text).length,0)-a.regions.reduce((x,r)=>x+Array.from(r.text).length,0);}
 const a=rows(run+'/comparison/'+item.id+'-frames.jsonl'),b=rows(comparison+'/'+item.id+'-frames.jsonl');assert(a.length===b.length,'Scored frame count changed');frameRefs+=a.length;
 a.forEach((row,i)=>{assert(row.sourceFrameIndex===b[i].sourceFrameIndex&&row.sourcePts===b[i].sourcePts,'Score frame association changed');if(JSON.stringify(row)!==JSON.stringify(b[i])){const key=item.id+':'+row.sourceFrameIndex;assert(permitted.has(key),'Score changed outside correction scope');changedScores.push(key);}});
 const oldClip=oldScore.clips.find(x=>x.clipId===item.id),newClip=newScore.clips.find(x=>x.clipId===item.id),actual=identity(run+'/actual/'+item.id+'.jsonl');assert(actual.sha256===oldClip.actualSha256&&actual.sha256===newClip.actualSha256,'Actual output changed');actuals.push(actual);
}
assert(e.items.length===38&&frameRefs===3368&&changedGt.length===6&&characterDelta===-9,'Unexpected correction size');
for(const k of ['uniqueSourceFrames','frameReferences','duplicateReferences'])assert(oldScore.aggregate[k]===newScore.aggregate[k],'Source denominator changed');
for(const k of ['emptyFrames','pageOpportunities','uniqueContinuousStates'])assert(oldScore.aggregate.raw[k]===newScore.aggregate.raw[k],'Opportunity denominator changed');
assert(newScore.aggregate.raw.characters===oldScore.aggregate.raw.characters+characterDelta,'Character accounting mismatch');
const independent=json('reports/m3-grid-refinement-v1/ocr-v6-observation-r3-text-analysis/report.json');assert(independent.characters===newScore.aggregate.raw.characters&&independent.errors===newScore.aggregate.raw.editErrors,'Independent recount mismatch');
const checked=JSON.parse(JSON.stringify({status:'six_frame_correction_identity_and_rescoring_verified_not_product_acceptance',freeze:identity(frozen+'/freeze.json'),bundle:identity(review+'/bundle-manifest.json'),comparison:identity(comparison+'/report.json'),priorComparison:identity(run+'/comparison/report.json'),changedGt,changedScores,characterDelta,oldRaw:oldScore.aggregate.raw,newRaw:newScore.aggregate.raw,actualsUnchanged:actuals,retainedReviewFrames:321,qualifiedSemanticIntegrity:e.allSemanticIntegrityPassed,limitations:e.limitations,javaImplementation:'Unchanged v6 engine/build,128 tests previously passed; no new Java test run claimed.',uiRendering:'pending',independentAcceptance:'pending',productionChinese:'not_started'}));
fs.mkdirSync(out,{recursive:true});fs.writeFileSync(out+'/report.json',JSON.stringify(checked,null,2)+'\n',{flag:'wx'});console.log(JSON.stringify({changedGt,changedScores,characterDelta,newCharacters:newScore.aggregate.raw.characters,editErrors:newScore.aggregate.raw.editErrors,status:checked.status}));
