'use strict';
const fs=require('fs'),crypto=require('crypto'),assert=require('assert/strict');
const json=p=>JSON.parse(fs.readFileSync(p)),rows=p=>fs.readFileSync(p,'utf8').trim().split(/\r?\n/).map(JSON.parse),sha=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const root='reports/m3-grid-refinement-v1',base='data/gt/frozen/ocr-all-scenes-v2-observation-r10',next='data/gt/frozen/ocr-all-scenes-v2-observation-r11',review='reports/gt-review/gt-observation-corrections-v9';
const old=json(base+'/freeze.json'),now=json(next+'/freeze.json'),a=json(root+'/ocr-regression-all-v8/comparison/report.json'),b=json(root+'/ocr-v8-observation-r11-comparison/report.json');
const resolve=(root,p)=>p.file?root+'/'+p.file:p.approvedSource;
for(const ref of [now.previousEffectiveFreeze,now.approvedBundle,now.identityCheck])assert.equal(sha(ref.path),ref.sha256);
for(const f of json(now.approvedBundle.path).files)assert.equal(sha(review+'/'+f.path),f.sha256);
assert.equal(b.freezeSha256,sha(next+'/freeze.json'));assert.equal(now.allSemanticIntegrityPassed,false);assert.equal(now.productAcceptance,false);
const changed=[],scoreChanges=[],actuals=[];let frames=0;
for(const it of now.items){
 const prior=old.items.find(x=>x.id===it.id);assert(prior);
 for(const p of it.payloads){assert.equal(sha(resolve(next,p)),p.sha256);if(p.kind!=='gt'||it.id!=='shop-sell-entry')assert.equal(p.sha256,prior.payloads.find(x=>x.kind===p.kind).sha256);}
 const x=rows(resolve(base,prior.payloads.find(p=>p.kind==='gt'))),y=rows(resolve(next,it.payloads.find(p=>p.kind==='gt')));assert.equal(x.length,y.length);
 x.forEach((s,i)=>{const t=y[i];if(JSON.stringify(s)===JSON.stringify(t))return;assert.equal(it.id,'shop-sell-entry');
  const restored=structuredClone(t);restored.regions=s.regions.map(p=>{const r=restored.regions.find(q=>q.id===p.id);assert(r);r.role=p.role;r.readingOrder=p.readingOrder;return r;});assert.deepEqual(restored,s);
  for(let f=s.startFrame;f<s.endFrameExclusive;f++)changed.push(f);
 });
 const xs=rows(root+'/ocr-regression-all-v8/comparison/'+it.id+'-frames.jsonl'),ys=rows(root+'/ocr-v8-observation-r11-comparison/'+it.id+'-frames.jsonl');assert.equal(xs.length,ys.length);
 xs.forEach((r,i)=>{const t=ys[i];assert.equal(r.sourceFrameIndex,t.sourceFrameIndex);assert.equal(r.sourcePts,t.sourcePts);frames++;if(JSON.stringify(r)!==JSON.stringify(t)){assert.equal(it.id,'shop-sell-entry');assert(t.sourceFrameIndex>=1044&&t.sourceFrameIndex<1102);scoreChanges.push(t.sourceFrameIndex);}});
 const actual=root+'/ocr-regression-all-v8/actual/'+it.id+'.jsonl',h=sha(actual);assert.equal(a.clips.find(c=>c.clipId===it.id).actualSha256,h);assert.equal(b.clips.find(c=>c.clipId===it.id).actualSha256,h);actuals.push({path:actual,sha256:h});
}
assert.deepEqual(changed,Array.from({length:58},(_,i)=>1044+i));assert.equal(frames,3368);assert.equal(now.items.length,38);
for(const k of ['characters','emptyFrames','pageOpportunities','uniqueContinuousStates','equalStateCharacters'])assert.equal(a.aggregate.raw[k],b.aggregate.raw[k]);
const independent=json(root+'/ocr-v8-observation-r11-text-analysis/report.json');assert.equal(independent.errors,b.aggregate.raw.editErrors);assert.equal(independent.characters,b.aggregate.raw.characters);
const out=process.argv[2];assert(out&&!fs.existsSync(out));fs.writeFileSync(out,JSON.stringify({status:'role_order_only_correction_verified',freezeSha256:sha(next+'/freeze.json'),changedFrames:changed,scoreChangedFrames:scoreChanges,unchangedActuals:actuals,oldRaw:a.aggregate.raw,newRaw:b.aggregate.raw,productAcceptance:false},null,2)+'\n',{flag:'wx'});console.log(JSON.stringify({path:out,sha256:sha(out),changedFrames:changed.length,scoreChangedFrames:scoreChanges.length}));
