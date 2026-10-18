'use strict';
// Diagnosis after seeing OCR output; never a blind GT reading or correction.
const fs=require('fs'),crypto=require('crypto'),assert=require('assert/strict');
const sha=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const ident=p=>({path:p,sha256:sha(p)});
const read=p=>JSON.parse(fs.readFileSync(p,'utf8'));
const rows=p=>fs.readFileSync(p,'utf8').trim().split(/\r?\n/).map(JSON.parse);
const root='data/gt/frozen/ocr-all-scenes-v2-observation-r8';
const f=read(root+'/freeze.json'),item=f.items.find(x=>x.id==='intro-research');
const payload=item.payloads.find(x=>x.kind==='gt'),gtPath=payload.file?root+'/'+payload.file:payload.approvedSource;
assert.equal(sha(gtPath),payload.sha256);
const actualPath='reports/m3-grid-refinement-v1/ocr-regression-all-v7/actual/intro-research.jsonl';
const comparisonPath='reports/m3-grid-refinement-v1/ocr-v7-observation-r8-comparison/report.json';
assert.equal(read(comparisonPath).clips.find(x=>x.clipId==='intro-research').actualSha256,sha(actualPath));
const states=rows(gtPath),actual=rows(actualPath);
const frames=[6408,6409].map(n=>({frame:n,expected:states.find(x=>x.startFrame<=n&&x.endFrameExclusive>n),actual:actual.find(x=>x.sourceFrameIndex===n),image:ident('data/frames/video-01/dialogue-intro-pass2-v1/frame-'+String(n).padStart(6,'0')+'.png')}));
const report={status:'suspected_annotation_timing_discrepancy_not_confirmed',freeze:ident(root+'/freeze.json'),gt:ident(gtPath),actual:ident(actualPath),comparison:ident(comparisonPath),helper:ident(__filename),frames,observation:'Direct full PNG inspection of frame 6408 shows two spatially separated dots. Frame 6409 has only been identity-bound here, not directly inspected. This diagnostic follows exposure to expected and actual text and cannot count as a blind pass.',next:'Review the complete retained intro-research event twice with GT and actual closed, bind fresh original extraction, then compare all affected states before deciding any versioned correction.',productionChanged:false,gtChanged:false,productAcceptance:false};
const out=process.argv[2];assert(out&&!fs.existsSync(out));fs.writeFileSync(out,JSON.stringify(report,null,2)+'\n',{flag:'wx'});console.log(JSON.stringify(ident(out)));
