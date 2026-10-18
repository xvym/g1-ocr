'use strict';
const fs=require('fs'),path=require('path'),crypto=require('crypto'),assert=require('assert/strict');
const sha=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex'),ident=p=>({path:p,sha256:sha(p)});
const json=p=>JSON.parse(fs.readFileSync(p)),rows=p=>fs.readFileSync(p,'utf8').trim().split(/\r?\n/).map(JSON.parse);
const notes='reports/m3-grid-refinement-v1/shop-sell-contract-notes-v1',out=process.argv[2];assert(out&&!fs.existsSync(out));
const passes=[1,2].map(n=>json(`${notes}/pass${n}.json`));
assert.deepEqual(passes[0].observations,passes[1].observations);assert.deepEqual(passes[0].fields,passes[1].fields);
const ver=passes.map(p=>{assert.equal(sha(p.sourceVerification.path),p.sourceVerification.sha256);assert.equal(sha(p.sheets.path),p.sheets.sha256);return json(p.sourceVerification.path);});
assert.deepEqual(ver[0].frames,ver[1].frames);assert.equal(ver[0].frames.length,62);
const pngs=ver[0].frames.map(f=>{const name=`frame-${String(f.frameIndex).padStart(6,'0')}.png`,a=path.dirname(passes[0].sourceVerification.path)+'/'+name,b=path.dirname(passes[1].sourceVerification.path)+'/'+name;assert.equal(sha(a),sha(b));return ident(b);});
for(const p of passes)for(const s of json(p.sheets.path).sheets)assert.equal(sha(s.path),s.sha256);
const root='reports/m3-grid-refinement-v1',old=root+'/ocr-regression-all-v7',now=root+'/ocr-regression-all-v8',run=json(now+'/report.json'),cmp=json(now+'/comparison/report.json');
assert.equal(run.comparisonSha256,sha(now+'/comparison/report.json'));
const changes=[],inputs=[];let frames=0;
for(const c of cmp.clips){
 const af=old+'/actual/'+c.clipId+'.jsonl',bf=now+'/actual/'+c.clipId+'.jsonl',a=rows(af),b=rows(bf);assert.equal(a.length,b.length);assert.equal(sha(bf),c.actualSha256);inputs.push({clip:c.clipId,old:ident(af),current:ident(bf)});
 for(let i=0;i<a.length;i++){
  const x=a[i],y=b[i];for(const key of ['sourceFrameIndex','sourcePts','timeBase','geometry','visibleState','diagnosticBoxes','imageRegistration'])assert.deepEqual(x[key],y[key]);
  assert.equal(x.rawRegions.length,y.rawRegions.length);
  for(let j=0;j<x.rawRegions.length;j++){
   const p=x.rawRegions[j],q=y.rawRegions[j],restored={...q};
   if(p.role!==q.role){assert(c.clipId.includes('shop-sell-entry'));assert.equal(p.role,'shop_message');assert.equal(q.role,'shop_prompt');assert(y.sourceFrameIndex>=1068&&y.sourceFrameIndex<1104);changes.push({clip:c.clipId,frame:y.sourceFrameIndex,before:p.role,after:q.role,text:q.text});restored.role=p.role;restored.id=p.id;}
   assert.deepEqual(p,restored);
  }frames++;
 }
}
assert.equal(frames,3577);assert.equal(changes.length,36);
const result={status:'original_context_double_read_and_role_only_regression_verified',helper:ident(__filename),notes:[1,2].map(n=>ident(`${notes}/pass${n}.json`)),pngs,allPngBytesAndMetadataEqual:true,observationsEqual:true,retainedOriginalFrames:62,replayFrameReferences:frames,changes,inputs,comparison:ident(now+'/comparison/report.json'),acceptance:false,limitations:'Same-agent double reading after historical annotation exposure; not independent-session acceptance. No GT or scoring changes. Parent role/order contract correction remains separate.'};
fs.writeFileSync(out,JSON.stringify(result,null,2)+'\n',{flag:'wx'});console.log(JSON.stringify({report:ident(out),frames,changedRegions:changes.length}));
