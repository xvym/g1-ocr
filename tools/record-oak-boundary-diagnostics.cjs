'use strict';
const fs=require('fs'),crypto=require('crypto'),assert=require('assert/strict');
const sha=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const ident=p=>({path:p,sha256:sha(p)}),read=p=>JSON.parse(fs.readFileSync(p,'utf8')),rows=p=>fs.readFileSync(p,'utf8').trim().split(/\r?\n/).map(JSON.parse);
const root='data/gt/frozen/ocr-all-scenes-v2-observation-r9',freeze=read(root+'/freeze.json');
const comparison='reports/m3-grid-refinement-v1/ocr-v7-observation-r9-comparison/report.json',score=read(comparison);
assert.equal(score.freezeSha256,sha(root+'/freeze.json'));
const events=[['lab-oak-reply',13438,'dialogue-lab-oak-reply-reviewed-v1','A visible exclamation mark follows the prolonged sound mark.'],['oak-delivery',24223,'dialogue-oak-delivery-reviewed-v1','The lower line visibly ends at mo; the following no is not visibly present.']].map(([id,n,dir,observation])=>{
 const item=freeze.items.find(x=>x.id===id);for(const p of item.payloads)assert.equal(sha(p.file?root+'/'+p.file:p.approvedSource),p.sha256);
 const p=item.payloads.find(x=>x.kind==='gt'),gp=p.file?root+'/'+p.file:p.approvedSource;
 const cp=item.payloads.find(x=>x.kind==='clip'),clipPath=cp.file?root+'/'+cp.file:cp.approvedSource,clip=read(clipPath);
 const ap='reports/m3-grid-refinement-v1/ocr-regression-all-v7/actual/'+id+'.jsonl';assert.equal(score.clips.find(x=>x.clipId===id).actualSha256,sha(ap));
 return {id,frame:n,retainedRange:[clip.startFrame,clip.endFrameExclusive],gt:ident(gp),clip:ident(clipPath),actual:ident(ap),expected:rows(gp).find(x=>x.startFrame<=n&&x.endFrameExclusive>n),actualFrame:rows(ap).find(x=>x.sourceFrameIndex===n),image:ident('data/frames/video-01/'+dir+'/frame-'+String(n).padStart(6,'0')+'.png'),observation};
});
const out=process.argv[2];assert(out&&!fs.existsSync(out));const report={status:'suspected_annotation_timing_discrepancies_not_confirmed',freeze:ident(root+'/freeze.json'),comparison:ident(comparison),helper:ident(__filename),events,exposure:'Both complete source PNGs directly inspected only after expected and actual exposure; diagnostic, not blind reading or independent verification.',next:'Double-read all retained frames of both events with GT and actual closed, including fresh extraction. Compare all states only after reading; retain prior exposure disclosure.',productionChanged:false,gtChanged:false,productAcceptance:false};fs.writeFileSync(out,JSON.stringify(report,null,2)+'\n',{flag:'wx'});console.log(JSON.stringify(ident(out)));
