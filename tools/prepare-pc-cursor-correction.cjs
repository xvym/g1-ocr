'use strict';
const fs=require('fs'),crypto=require('crypto'),assert=require('assert/strict'),path=require('path');
const read=p=>JSON.parse(fs.readFileSync(p,'utf8')),sha=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex'),id=p=>({path:p,sha256:sha(p)}),save=(p,x)=>fs.writeFileSync(p,JSON.stringify(x,null,2)+'\n',{flag:'wx'});
const base='reports/m3-grid-refinement-v1/pc-cursor-notes-v1',root='data/gt/frozen/ocr-all-scenes-v2-observation-r13';
const pass1=read(base+'/pass1.json'),pass2=read(base+'/pass2.json');
assert.equal(sha(base+'/pass1.json'),'6334cceeeae46a50a480005580185becd0f75b745195a1fbb7d3ff0cc72d6f19');
assert.equal(sha(base+'/pass2.json'),'109baf47588636cc5f130799e735cd60d97997eb1372e18fd154559800251591');
assert.equal(sha(root+'/freeze.json'),'83f59aad5b1af0d5b295bf70740309599ec5af065eb45367c6254af9e78f1ab2');
for(const p of [pass1,pass2]){assert.equal(sha(p.sourceReport),p.sourceReportSha256);for(const s of p.sheets)assert.equal(sha(s.path),s.sha256);}
const extraction=read(base+'/extraction-check.json');assert.equal(extraction.frames.length,124);
const item=read(root+'/freeze.json').items.find(x=>x.id==='pc-deposit-navigation');
const payloads=item.payloads.map(p=>({kind:p.kind,...id(p.file?root+'/'+p.file:p.approvedSource),expectedSha256:p.sha256}));for(const p of payloads)assert.equal(p.sha256,p.expectedSha256);
const input=payloads.find(p=>p.kind==='gt'),old=fs.readFileSync(input.path,'utf8').trim().split(/\r?\n/).map(JSON.parse);
const observed=(p,f)=>{const s=p.spans.find(s=>s[0]<=f&&f<s[1]);assert(s);return s.slice(2,5)};
const frozen=s=>{const m=s.cursor.menuCursors;const cell=x=>x?`${x.style==='filled_right'?'filled':'outline'}:${x.gridRect[0]},${x.gridRect[1]}`:null;return [cell(m.find(x=>x.gridRect[0]===1)),m.filter(x=>x.gridRect[0]===5).map(cell),s.cursor.downArrowVisible]};
const differences=[];let scored=0;
for(let f=1632;f<1756;f++){
 const a=observed(pass1,f),b=observed(pass2,f);assert.deepEqual(a,b);
 const states=old.filter(s=>s.startFrame<=f&&f<s.endFrameExclusive);if(f<1634||f>=1754){assert.equal(states.length,0);continue;}assert.equal(states.length,1);scored++;
 const actual=frozen(states[0]),expected=[b[0],b[1]?[b[1]]:[],b[2]];
 if(JSON.stringify(actual)!==JSON.stringify(expected))differences.push({frame:f,frozen:actual,observed:expected,stateId:states[0].stateId});
}
assert.equal(scored,120);assert.deepEqual(differences.map(x=>x.frame),[1673,1688]);
save(base+'/frozen-comparison.json',{status:'all120_scoring_frames_compared_after_all124_frame_double_read',helper:id(__filename),freeze:id(root+'/freeze.json'),payloads,notes:[id(base+'/pass1.json'),id(base+'/pass2.json')],integrity:id(base+'/extraction-check.json'),differences,unchangedScoringFrames:118,scope:'All parent/child cursors and down arrows; transition observations checked against all32 GT intervals. No OCR output used.',productAcceptance:false});
const next=structuredClone(old),changes=[];
for(const frame of [1673,1688]){
 const s=next.find(x=>x.startFrame===frame);assert.equal(s.endFrameExclusive,frame+1);
 if(frame===1673)s.cursor.menuCursors.push(structuredClone(old.find(x=>x.startFrame===1661).cursor.menuCursors[1]));
 else s.cursor.menuCursors=s.cursor.menuCursors.filter(x=>x.gridRect[0]!==5||x.gridRect[1]!==6);
 s.kind='menu_complete';s.completePage=true;s.pageOpportunityId=frame===1673?'pc-list-A-first':'pc-list-A-third';
 s.evidence={manualNotes:base+'/pass2.json',originalFrames:'data/frames/video-02/pc-cursor-pass2-v1',humanPerFrameVerification:'not claimed',outputIndependence:'Full124-frame double read with GT/actual closed; prior diagnostic and same-agent memory exposure disclosed. Not independent acceptance.'};
 s.renderNote=frame===1673?'First child selection remains; only the down arrow becomes invisible. Continue the existing first-selection page.':'Only third child selection is visible; previous second cursor is absent. Begin the existing third-selection page one frame earlier; down arrow returns next frame.';
 changes.push({frame,before:old.find(x=>x.startFrame===frame),after:s});
}
for(let i=0;i<old.length;i++)if(![1673,1688].includes(old[i].startFrame))assert.deepEqual(old[i],next[i]);
for(const change of changes)assert.deepEqual(change.before.regions,change.after.regions);
const pages=rows=>[...new Set(rows.filter(s=>s.completePage).map(s=>s.pageOpportunityId))];assert.deepEqual(pages(old),pages(next));
const out='data/gt/candidates/ocr-observation-corrections-v12/pc-deposit-navigation';assert(!fs.existsSync(out));fs.mkdirSync(out,{recursive:true});for(const p of payloads.filter(p=>p.kind!=='gt'))fs.copyFileSync(p.path,out+'/'+path.basename(p.path),fs.constants.COPYFILE_EXCL);
const target=out+'/ocr-candidate-cursor-v1.jsonl';fs.writeFileSync(target,next.map(JSON.stringify).join('\n')+'\n',{flag:'wx'});
save(base+'/candidate-evidence.json',{status:'candidate_not_effective_freeze',helper:id(__filename),audit:id(base+'/frozen-comparison.json'),input,candidate:id(target),changes,characterDelta:0,oldStates:old.length,newStates:next.length,oldPageIds:pages(old),newPageIds:pages(next),pageBoundaryChanges:[{id:'pc-list-A-first',oldEnd:1673,newEnd:1674},{id:'pc-list-A-third',oldStart:1689,newStart:1688}],rationale:'Full original-event double read confirms two cursor transcription errors. Preserve all120 scoring frames, text, geometry and existing page opportunities; neither add a new opportunity nor remove a failing one. Contract/review bundle/integrity checks still required before freezing.',productAcceptance:false});
console.log(JSON.stringify({candidate:id(target),scored,retained:124,differences,states:next.length,pages:pages(next)}));
