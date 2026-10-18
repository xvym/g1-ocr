'use strict';
// Diagnostic only: preserve strict scoring and expose, never normalize, state labels.
const fs=require('fs'),crypto=require('crypto'),assert=require('assert/strict');
const json=p=>JSON.parse(fs.readFileSync(p,'utf8')),rows=p=>fs.readFileSync(p,'utf8').trim().split(/\r?\n/).map(JSON.parse);
const sha=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex'),id=p=>({path:p,sha256:sha(p)});
const [frozen,run,comparison,output,signalPath]=process.argv.slice(2);assert(output&&!fs.existsSync(output));
const freeze=json(frozen+'/freeze.json'),score=json(comparison+'/report.json');assert.equal(score.freezeSha256,sha(frozen+'/freeze.json'));
const pairs={},clips=[],bindings=[],all=[];let scoredRefs=0;
for(const item of freeze.items){
 const payload=item.payloads.find(p=>p.kind==='gt'),gtPath=payload.file?frozen+'/'+payload.file:payload.approvedSource;
 assert.equal(sha(gtPath),payload.sha256);const states=rows(gtPath),actualPath=run+'/actual/'+item.id+'.jsonl',actual=rows(actualPath),timeline=rows(run+'/'+item.id+'-timeline.jsonl');
 assert.equal(actual.length,timeline.length);assert.equal(sha(actualPath),score.clips.find(c=>c.clipId===item.id).actualSha256);
 const mismatches=[];
 for(let i=0;i<actual.length;i++){
  const a=actual[i],t=timeline[i];assert.equal(a.sourceFrameIndex,t.sourceFrameIndex);assert.equal(a.sourcePts,t.sourcePts);if(!t.scored)continue;scoredRefs++;
  const matches=states.filter(s=>s.startFrame<=a.sourceFrameIndex&&a.sourceFrameIndex<s.endFrameExclusive);assert.equal(matches.length,1);const s=matches[0];
  if(a.visibleState.kind===s.kind)continue;
  const key=s.kind+' <- '+a.visibleState.kind,p=pairs[key]||(pairs[key]={count:0,examples:[]});p.count++;
  const row={clip:item.id,frame:a.sourceFrameIndex,pts:a.sourcePts,expected:s.kind,actual:a.visibleState.kind,geometryMethod:a.geometry.method,expectedCharacters:s.regions.reduce((n,r)=>n+Array.from(r.text).length,0),actualCharacters:a.rawRegions.reduce((n,r)=>n+Array.from(r.text).length,0)};
  if(p.examples.length<3)p.examples.push(row);mismatches.push(row);all.push(row);
 }
 clips.push({clip:item.id,stateMismatchReferences:mismatches.length});bindings.push({clip:item.id,gt:id(gtPath),actual:id(actualPath)});
}
const signal=json(signalPath);assert.equal(signal.length,300);const lowButNonBlack=signal.filter(r=>r.locatorLowSignalPredicate&&!r.exactRgbBlack);
const sourceVerification='data/frames/video-01/all-scenes-startup-pass2-v1/verification.json';
fs.mkdirSync(output,{recursive:true});
for(const row of signal){const filename=row.file.replace(/\\/g,'/');row.pngSha256=sha(filename);}
const report={status:'state_diagnosis_only_no_gt_or_production_changes',freeze:id(frozen+'/freeze.json'),comparison:id(comparison+'/report.json'),bindings,scoredReferences:scoredRefs,mismatchReferences:all.length,pairs,clips,
 startup:{retainedFrames:signal.length,exactBlackFrames:signal.filter(r=>r.exactRgbBlack).length,lowSignalButNonBlackFrames:lowButNonBlack,signalReport:id(signalPath),sourceVerification:id(sourceVerification),originalPngs:signal.map(r=>({path:r.file,sha256:r.pngSha256}))},
 findings:['Missing a grid is not evidence of a non-target scene. Startup non_target includes a blank game raster, so absence of glyphs alone cannot disambiguate it from empty_target.',
 'Five non-black startup frames satisfy the locator low-signal heuristic. NO_SIGNAL is a geometry confidence result, not proof of exact RGB black.',
 'Frozen no_textbox and empty_target both describe zero-region game scenes. Do not remap by clip identity or normalize scorer states; their semantic distinction needs an explicit evidence audit.',
 'No state label, GT payload, threshold or output has been changed. Existing failures remain counted.'],productAcceptance:false};
fs.writeFileSync(output+'/report.json',JSON.stringify(report,null,2)+'\n',{flag:'wx'});fs.writeFileSync(output+'/mismatches.jsonl',all.map(JSON.stringify).join('\n')+'\n',{flag:'wx'});
console.log(JSON.stringify({scoredRefs,mismatches:all.length,exactBlack:report.startup.exactBlackFrames,lowButNonBlack:lowButNonBlack.map(r=>r.file),report:id(output+'/report.json')}));
