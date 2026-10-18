// Encode independently read original-image evidence; never reads OCR actuals.
const fs=require('fs'),crypto=require('crypto'),path=require('path');
const sha=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const base='data/gt/frozen/ocr-all-scenes-v2/league-centre-npc';
const out='data/gt/candidates/ocr-observation-corrections-v2/league-centre-npc';
const notes='reports/m3-grid-refinement-v1/authorized-correction-league-centre-npc-notes-v1';
if(fs.existsSync(out))throw Error('Use a fresh candidate output');
const first='data/frames/video-03/annotation-recheck-league-centre-npc-pass1-v1';
const second='data/frames/video-03/authorized-correction-league-centre-npc-pass2-v1';
const frameIdentity=[];
for(let n=1590;n<1736;n++){
 const name=`frame-${String(n).padStart(6,'0')}.png`,a=sha(path.join(first,name)),b=sha(path.join(second,name));
 if(a!==b)throw Error('Original image pair mismatch '+n);
 frameIdentity.push({frame:n,sha256:a});
}
const oldPath=base+'/ocr-candidate-v1.jsonl';
const rows=fs.readFileSync(oldPath,'utf8').trim().split(/\r?\n/).map(JSON.parse);
const copy=x=>JSON.parse(JSON.stringify(x));
const result=[];
for(const old of rows){
 if(![1630,1692].includes(old.startFrame)){result.push(copy(old));continue;}
 const split=old.startFrame===1630?1632:1693;
 const prior=rows.find(x=>x.endFrameExclusive===old.startFrame);
 if(old.kind!=='scrolling'||!prior.completePage)throw Error('Unexpected source annotation');
 const normal=copy(prior);
 normal.startFrame=old.startFrame;normal.endFrameExclusive=split;
 normal.startPts=old.startPts;normal.endPtsExclusive=split*512;
 normal.stateId=`9c8aee036f72-f${normal.startFrame}-f${split}`;
 normal.cursor={menuCursors:[],downArrowVisible:false};
 normal.renderNote='Original image retains complete text at normal rows14/16 after arrow clears; same visible page opportunity, before upward scrolling.';
 normal.evidence={outputIndependence:'Original-image double recheck; previous diagnostic awareness disclosed; actuals not used as answers',humanPerFrameVerification:'not claimed',manualNotes:notes+'/pass2.json',originalFrames:second};
 const scroll=copy(old);scroll.startFrame=split;scroll.startPts=split*512;
 scroll.stateId=`9c8aee036f72-f${split}-f${scroll.endFrameExclusive}`;
 scroll.evidence=copy(normal.evidence);
 result.push(normal,scroll);
}
if(result.length!==rows.length+2)throw Error('Unexpected state count');
for(let n=1592;n<1734;n++)if(result.filter(x=>x.startFrame<=n&&n<x.endFrameExclusive).length!==1)throw Error('Frame coverage '+n);
fs.mkdirSync(out,{recursive:true});
for(const name of fs.readdirSync(base))if(name!=='ocr-candidate-v1.jsonl')fs.copyFileSync(path.join(base,name),path.join(out,name));
const target=out+'/ocr-candidate-v2.jsonl';
fs.writeFileSync(target,result.map(x=>JSON.stringify(x)).join('\n')+'\n');
const report={status:'candidate_only_not_frozen',sourceGt:{path:oldPath,sha256:sha(oldPath)},candidate:{path:target,sha256:sha(target)},changedVisibleFrames:[1630,1631,1692],preservedRange:[1592,1734],retainedRange:[1590,1736],oldStates:rows.length,newStates:result.length,pageOpportunityIds:[...new Set(result.filter(x=>x.completePage).map(x=>x.pageOpportunityId))],basis:'Completed original-image double review. Normal complete content persists after arrow clearing; existing explicit page identity retained. Scroll starts at1632 and1693. No role, Unicode, layout, threshold, original frame or OCR output modified.',notes:{first:'reports/m3-grid-refinement-v1/blind-recheck-notes-pass1-v1/league-centre-npc.json',second:notes+'/pass2.json'},frameIdentity};
fs.writeFileSync(notes+'/candidate-evidence.json',JSON.stringify(report,null,2)+'\n',{flag:'wx'});
console.log(JSON.stringify({...report,frameIdentity:frameIdentity.length}));
