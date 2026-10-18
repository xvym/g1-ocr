'use strict';
// Offline evidence encoding only. No OCR outputs or scores are inputs.
const fs=require('fs'),path=require('path'),crypto=require('crypto');
const json=p=>JSON.parse(fs.readFileSync(p,'utf8').replace(/^\uFEFF/,''));
const hash=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const identity=p=>({path:p,sha256:hash(p)}),copy=x=>JSON.parse(JSON.stringify(x));
const check=(ok,msg)=>{if(!ok)throw Error(msg);};
const id=process.argv[2];check(['status-pages','status-alt-pages'].includes(id),'Specify a reviewed status event');
const alt=id==='status-alt-pages',source=alt?'video-03':'video-02',range=alt?[3532,3667]:[460,568],frame=alt?3612:526;
const here='reports/m3-grid-refinement-v1/authorized-correction-'+id+'-notes-v1';
const original='data/gt/frozen/ocr-all-scenes-v2/'+id+'/ocr-candidate-v1.jsonl';
check(hash(original)===(alt?'021fd43307990be1c54539071b9dd4f93761fb35929d14009bc194170e798f4e':'cdfc263ad361fb0db955c3f9275f9bbfdac232b18340fd0e71a075a7799d61c9'),'Old frozen bytes changed');
const dest='data/gt/candidates/ocr-observation-corrections-v1/'+id+'/ocr-candidate-v2.jsonl';
check(!fs.existsSync(dest)&&!fs.existsSync(here+'/correction-evidence.json'),'Use a new version');
const first='reports/m3-grid-refinement-v1/blind-recheck-notes-pass1-v1/'+id+'.json',second=here+'/pass2.json';
const notes=json(second);check(notes.viewedSheets.length===(alt?23:18),'Incomplete original-image review');
const inputs=['data/frames/'+source+'/ocr-observation-recheck-'+id+'-v1','data/frames/'+source+'/authorized-correction-'+id+'-pass2-v1'];
const verifications=inputs.map(p=>json(p+'/verification.json'));
for(const v of verifications)check(v.step===1&&v.allRgbAndPngReadbackHashesMatch&&v.startFrame===range[0]&&v.endFrameExclusive===range[1],'Input verification mismatch');
const pairs=[];
for(let n=range[0];n<range[1];n++){
 const files=inputs.map(p=>p+'/frame-'+String(n).padStart(6,'0')+'.png');
 check(hash(files[0])===hash(files[1]),'Independent PNG mismatch');pairs.push({frame:n,sha256:hash(files[0])});
}
const states=fs.readFileSync(original,'utf8').trim().split(/\r?\n/).map(JSON.parse),before=copy(states.find(s=>s.startFrame===frame));
const state=states.find(s=>s.startFrame===frame);check(state&&state.endFrameExclusive===frame+1,'Expected exact one-frame redraw');
if(!alt){
 for(const [rid,old,text] of [['defense-label','ぼうぎょ','ほうきょ'],['type2','タイプ2/こおり','タイフ2/こおり']]){
  const r=state.regions.find(r=>r.id===rid);check(r&&r.text===old,'Unexpected old field');r.text=text;r.markCells=[];
  r.sourceRect[1]=108+r.gridRect[1]*18;
 }
 state.renderNote='Original526: experience panel and first move row redrawn; old lower bases remain with defence/type2 marks erased. All visible fields retained.';
}else{
 const oldPage=states.find(s=>s.startFrame===3541);
 const oldHp=['hp-label','hp-value','status'].map(id=>copy(oldPage.regions.find(r=>r.id===id)));
 const removed=new Set(['exp-label','exp','next-label','next-exp','next-level-label','next-level']);
 // Keep the original retained-field ordering; replace the absent upper panel at its slot.
 state.regions=[...state.regions.slice(0,5),...oldHp,...state.regions.slice(5).filter(r=>!removed.has(r.id))];
 state.regions.forEach((r,i)=>r.readingOrder=i);
 const move=state.regions.find(r=>r.id==='move-1');check(move.text==='ねむりごな','Unexpected old move');
 move.text='ねむりこな';move.variable.displayValue=move.text;move.markCells=[];move.sourceRect[1]=Math.floor(12*8*34/9);
 state.detachedMarks=notes.observations.find(o=>o.range[0]===3612).detachedMarks.map(m=>({kind:m.kind,gridRect:[m.gridColumn,m.gridRow,m.gridColumn+1,m.gridRow+1],sourceRect:[180+m.gridColumn*30,Math.floor(m.gridRow*8*34/9),180+(m.gridColumn+1)*30,Math.ceil((m.gridRow+1)*8*34/9)],renderNote:'Original3612 old upper marks remain after their former bases were overwritten; not attached to newly drawn text.'}));
 state.renderNote='Original3612 retains HP/condition upper panel and attack/type1 first lower row; three new lower moves visible, plainこ inねむりこな, four detached old marks. Experience panel first appears3613.';
}
state.evidence={manualNotes:second,originalFrames:inputs[1],humanPerFrameVerification:'not claimed',outputIndependence:'Two full-event original-image reviews; no tested output used as annotation input. Prior diagnostic awareness disclosed.',correctionReview:here+'/correction-evidence.json'};
const chars=s=>s.regions.reduce((n,r)=>n+Array.from(r.text).length,0);
fs.mkdirSync(path.dirname(dest),{recursive:true});fs.writeFileSync(dest,states.map(s=>JSON.stringify(s)).join('\n')+'\n',{flag:'wx'});
const report={status:'high_confidence_single_frame_correction_candidate_not_effective_freeze',authority:{userMessage:'如果确认冻结内容有误，可以在置信度较高的情况下调整冻结结论',scope:'Evidence-backed corrections only; not user per-frame verification or approval of unobserved claims.'},oldGt:identity(original),candidate:identity(dest),observations:[identity(first),identity(second)],inputVerifications:inputs.map(p=>identity(p+'/verification.json')),independentPngPairs:pairs,changes:[{startFrame:frame,endFrameExclusive:frame+1,before,after:state}],expectedCharacterDelta:chars(state)-chars(before),unchanged:'All retained/scored frames, PTS boundaries, page opportunities, layout, scoring contract and all other state objects. Existing field roles/order retained except the removed absent panel and replacement visible HP fields.',limitations:alt?['3541 name-mark fragment remains unresolved; old annotation is retained as historical scoring input, not newly certified. This candidate cannot establish complete semantic integrity for that frame or the entire dataset.']:[],noProductAcceptance:true};
fs.writeFileSync(here+'/correction-evidence.json',JSON.stringify(report,null,2)+'\n',{flag:'wx'});
console.log(JSON.stringify({event:id,changedFrame:frame,expectedCharacterDelta:report.expectedCharacterDelta,candidate:report.candidate}));
