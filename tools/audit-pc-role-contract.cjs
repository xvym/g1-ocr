'use strict';
// Offline annotation audit: deliberately does not read any tested OCR output.
const fs=require('fs'),crypto=require('crypto'),assert=require('assert/strict');
const root='data/gt/frozen/ocr-all-scenes-v2-observation-r6';
const read=p=>JSON.parse(fs.readFileSync(p,'utf8')),rows=p=>fs.readFileSync(p,'utf8').trim().split(/\r?\n/).map(JSON.parse);
const sha=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const env=read(root+'/freeze.json'),batch=read(root+'/batch.json');
const resolve=p=>p.file?root+'/'+p.file:p.approvedSource;
const physical=r=>({text:r.text,gridRect:r.gridRect,sourceRect:r.sourceRect,marks:(r.markCells||[]).map(m=>[m.kind,m.gridColumn,m.gridRow]).sort()});
const items=env.items.filter(x=>['pc-connect','pc-deposit-navigation','pc-exit'].includes(x.id));
const inputs=items.map(it=>{for(const p of it.payloads)assert.equal(sha(resolve(p)),p.sha256);const p=it.payloads.find(p=>p.kind==='gt');return {id:it.id,path:resolve(p),sha256:p.sha256,states:rows(resolve(p))};});
const reference=inputs.find(x=>x.id==='pc-connect').states[0];
const ref=new Map(reference.regions.map(r=>[r.gridRect.slice(0,2).join(':'),r]));
const findings=[],controls=[];
for(const it of inputs)for(const s of it.states){
 const matches=s.regions.filter(r=>{const a=ref.get(r.gridRect.slice(0,2).join(':'));return a&&JSON.stringify(physical(a))===JSON.stringify(physical(r));});
 for(const r of matches){const a=ref.get(r.gridRect.slice(0,2).join(':'));if(r.role!==a.role)findings.push({clip:it.id,start:s.startFrame,end:s.endFrameExclusive,region:r.id,observedRole:r.role,priorReferenceRole:a.role,physical:physical(r),readingOrder:r.readingOrder,referenceReadingOrder:a.readingOrder});}
 if(matches.length===0&&s.regions.length)controls.push({clip:it.id,start:s.startFrame,end:s.endFrameExclusive,roles:[...new Set(s.regions.map(r=>r.role))],reason:'Different visible fields; do not globally rename menu_label or match by row alone.'});
}
assert.equal(findings.length,12); // two partial rows plus two complete five-row states
const frames=new Set(findings.flatMap(x=>Array.from({length:x.end-x.start},(_,i)=>x.clip+':'+(x.start+i))));
assert.equal(frames.size,10);
const scope=inputs.map(x=>{const b=batch.items.find(b=>b.id===x.id);return {id:x.id,annotation:{path:x.path,sha256:x.sha256},batchItem:b,stateCount:x.states.length};});
const report={status:'PC_main_menu_role_inconsistency_confirmed_in_metadata_original_event_review_still_required',freeze:{path:root+'/freeze.json',sha256:sha(root+'/freeze.json')},helperSha256:sha(__filename),inputBoundary:'Only frozen annotations, layout/clip/contract identities. No actual, edit counts or tested recognizer.',reference:{clip:'pc-connect',state:reference.stateId,reason:'Earlier source-time occurrence of the same five visible fields; this is a vocabulary reference, not proof from program output.'},findings,affectedFrames:[...frames],controls,scope,decision:'Do not modify product roles by clip ID, future exit event or source timestamp. A contract-consistent candidate may preserve earlier pc_menu_choice for the same PC main menu, but requires full original-event double review of all three related events first. Do not rename PC item-operation menu labels, other fields or states automatically.',effectiveFreezeChanged:false,productAcceptance:false};
const out=process.argv[2];assert(out&&!fs.existsSync(out),'new report required');fs.writeFileSync(out,JSON.stringify(report,null,2)+'\n',{flag:'wx'});console.log(JSON.stringify({findings:findings.length,affectedFrames:frames.size,scope:scope.map(x=>x.id),reportSha256:sha(out)}));
