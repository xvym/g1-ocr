'use strict';
// Applies only six independently reviewed source-frame corrections under the user's
// explicit conditional authorization. It does not derive annotations from OCR.
const fs=require('fs'),path=require('path'),crypto=require('crypto'),cp=require('child_process');
const {audit}=require('./check-gt-overlaps.cjs');
const read=p=>fs.readFileSync(p,'utf8').replace(/^\uFEFF/,''),json=p=>JSON.parse(read(p));
const rows=p=>read(p).trim().split(/\r?\n/).map(JSON.parse);
const sha=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const identity=p=>({path:p,sha256:sha(p),bytes:fs.statSync(p).size});
const check=(ok,msg)=>{if(!ok)throw Error(msg);};
const save=(p,v)=>fs.writeFileSync(p,JSON.stringify(v,null,2)+'\n',{flag:'wx'});
const base='data/gt/frozen/ocr-all-scenes-v2-effective-r2';
const review='reports/gt-review/gt-observation-corrections-v1';
const out='data/gt/frozen/ocr-all-scenes-v2-observation-r3';
check(!fs.existsSync(review)&&!fs.existsSync(out),'Use a new bundle and frozen version');
check(sha(base+'/freeze.json')==='748087f7049d657ada68e634fda8b21dc37674257769bc559e79fff684dbb246','Base changed');
const old=json(base+'/freeze.json'),batch=json(base+'/batch.json');
const specs=[['intro-creatures',[6258,6291,6292,6293],'7cc5f3cc68e320b98afb3e87f717197073f2b20521a6c053fe3f7e8d6e61c9af'],['status-pages',[526],'7a1d6134b9a0c6c11c4ffe8e863107ac117da99f9c8db27920ae05e62930f7a4'],['status-alt-pages',[3612],'880f326ff0ae317b2d812eca981521915f9f69d84c4d9569c52eaf908f3404cf']];
const scope=Object.fromEntries(specs.map(s=>[s[0],s]));
for(const item of old.items){check(sha(item.sourceFreeze.path)===item.sourceFreeze.sha256,'Prior authority changed');for(const p of item.payloads)check(sha(p.approvedSource)===p.sha256&&fs.statSync(p.approvedSource).size===p.bytes,'Prior payload changed');}
const media=rows('data/meta/m1-v1/media.jsonl');for(const m of media)check(sha(m.path)===m.sha256,'Original media changed');
fs.mkdirSync(review,{recursive:true});
const changed=[],displays=[],checks=[];
const classpath=['verification/target/classes','app/target/classes','.cache/m2/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar'].join(path.delimiter);
for(const [id,frames,expected] of specs){
 const it=batch.items.find(x=>x.id===id),prior=old.items.find(x=>x.id===id),oldGt=prior.payloads.find(p=>p.kind==='gt').approvedSource;
 const candidate='data/gt/candidates/ocr-observation-corrections-v1/'+id+'/ocr-candidate-v2.jsonl';
 const notes='reports/m3-grid-refinement-v1/authorized-correction-'+id+'-notes-v1',evidence=json(notes+'/correction-evidence.json');
 check(sha(candidate)===expected&&sha(oldGt)===evidence.oldGt.sha256,'Correction identity mismatch');
 const before=rows(oldGt),after=rows(candidate);check(before.length===after.length,'State removal or addition');
 const delta=[];before.forEach((a,i)=>{const b=after[i];for(const k of ['stateId','mediaId','mediaSha256','startFrame','endFrameExclusive','startPts','endPtsExclusive','timeBase','kind','completePage','pageOpportunityId','cursor','layoutRef'])check(JSON.stringify(a[k])===JSON.stringify(b[k]),'Unreviewed boundary/state change');if(JSON.stringify(a)!==JSON.stringify(b))for(let n=a.startFrame;n<a.endFrameExclusive;n++)delta.push(n);});
 check(JSON.stringify(delta)===JSON.stringify(frames),'Change exceeds reviewed frames');
 const dir=review+'/'+id;fs.mkdirSync(dir+'/frames',{recursive:true});
 for(const [label,p] of [['old.jsonl',oldGt],['corrected.jsonl',candidate],['clip.json',it.clip],['layout.json',it.layout],['contract.json',it.contract],['correction-evidence.json',notes+'/correction-evidence.json']])fs.copyFileSync(p,dir+'/'+label,fs.constants.COPYFILE_EXCL);
 for(let n=0;n<evidence.observations.length;n++){const p=evidence.observations[n];check(sha(p.path)===p.sha256,'Reading evidence changed');fs.copyFileSync(p.path,dir+'/observation-'+n+'.json',fs.constants.COPYFILE_EXCL);}
 const vs=evidence.inputVerifications.map(ref=>{check(sha(ref.path)===ref.sha256,'Source extraction evidence changed');return json(ref.path);});
 check(JSON.stringify(vs[0].frames)===JSON.stringify(vs[1].frames),'Source extraction timing/RGB mismatch');
 const frameDir=path.dirname(evidence.inputVerifications[1].path).replace(/\\/g,'/');
 for(let n=0;n<2;n++)fs.copyFileSync(evidence.inputVerifications[n].path,dir+'/source-pass'+(n+1)+'.json',fs.constants.COPYFILE_EXCL);
 for(const f of vs[1].frames){const name='frame-'+String(f.frameIndex).padStart(6,'0')+'.png',p=frameDir+'/'+name,pair=evidence.independentPngPairs.find(x=>x.frame===f.frameIndex);check(pair&&sha(p)===pair.sha256,'Original review PNG changed');fs.copyFileSync(p,dir+'/frames/'+name,fs.constants.COPYFILE_EXCL);}
 const report=dir+'/integrity.json',args=['-Dfile.encoding=UTF-8','-cp',classpath,'io.github.g1ocr.verification.VideoTool','gt-check','data/meta/m1-v1',it.clip,candidate,it.layout,report];
 const r=cp.spawnSync('java',args,{encoding:'utf8',maxBuffer:4*1024*1024});fs.writeFileSync(dir+'/integrity.log',r.stdout+r.stderr,{flag:'wx'});check(!r.error&&r.status===0,'Correction structural check failed');
 checks.push({id,command:['java',...args],report:identity(report)});changed.push({id,frames,oldGt:identity(oldGt),correctedGt:identity(candidate),evidence:identity(notes+'/correction-evidence.json'),expectedCharacterDelta:evidence.expectedCharacterDelta});
 displays.push({id,clip:json(it.clip),before,after,frames:vs[1].frames,changedFrames:frames});
 it.candidate=candidate;it.approval='conditional_authorization_six_corrected_frames_only_other_annotations_inherited';it.integrity=report;
}
const limitations=[...old.limitations,{id:'status-alt-3541-mark',status:'pending_semantic_confirmation',mediaId:'video-03',frames:[3541],reason:'Incomplete upper mark remains unresolved after both new original-image readings. Existing historical annotation remains in the denominator. No newly frozen conclusion or pass is granted for this frame.'},{id:'remaining-annotation-audit',status:'pending',reason:'Other previously recorded field-role/order/Unicode and raw-image doubts are unchanged, not resolved by these six corrections. Complete dataset semantic integrity and OCR gates remain unpassed.'}];
batch.reviewId='gt-observation-corrections-v1';batch.status='conditional_authorized_corrections_with_inherited_qualified_reference';batch.gaps=limitations;
batch.outputIndependence='Corrections read from original images in two passes without tested outputs. Existing OCR and prior diagnostic awareness disclosed; no claim of a fresh independent acceptance session.';
batch.approvalScope='Only six specified source frames receive corrected conclusions. All38 original event ranges, all frames and page opportunities retained. Unchanged annotations inherit their old authority and any explicit pending qualifications; they are not newly certified.';
const summary={...old.summary,reviewId:batch.reviewId,status:'historical_scope_preserved_six_frame_corrections_qualified_semantic_integrity',outputIndependence:batch.outputIndependence,correctionCharacterDelta:-9,allSemanticIntegrityPassed:false};
save(review+'/batch.json',batch);save(review+'/summary.json',summary);
const overlap=audit(review+'/batch.json');check(overlap.uniqueScoredFrames===3361&&overlap.overlappingReferences===7&&!overlap.conflicts.length,'Scope or overlap mismatch');save(review+'/overlap.json',overlap);
const authority={reviewId:batch.reviewId,message:'如果确认冻结内容有误，可以在置信度较高的情况下调整冻结结论',date:'2026-10-16',timezone:'Asia/Shanghai',basis:'Direct user conditional authorization; original-image evidence now satisfies the condition for only the six listed frames. Not a claim of subsequent approval of this version or human per-frame reading.',scope:batch.approvalScope,humanPerFrameVerification:'not claimed',changes:changed,limitations};
save(review+'/authority.json',authority);save(review+'/review-data.json',{authority,items:displays});
const data=JSON.stringify({authority,items:displays}).replace(/</g,'\\u003c');
const script=`const data=${data};const scenes=document.getElementById('scenes'),slider=document.getElementById('frame');for(const it of data.items){const o=document.createElement('option');o.textContent=it.id;scenes.append(o);}function show(){const it=data.items[scenes.selectedIndex],f=it.frames[+slider.value];document.getElementById('label').textContent=it.id+' 原帧 '+f.frameIndex+' PTS '+f.pts+' × '+f.timeBase+(it.changedFrames.includes(f.frameIndex)?'（已确认纠错）':'');document.getElementById('image').src=it.id+'/frames/frame-'+String(f.frameIndex).padStart(6,'0')+'.png';for(const side of ['before','after']){const s=it[side].find(s=>s.startFrame<=f.frameIndex&&f.frameIndex<s.endFrameExclusive);document.getElementById(side).textContent=s?JSON.stringify(s,null,2):'事件前后上下文；不计分';}}scenes.onchange=()=>{slider.max=data.items[scenes.selectedIndex].frames.length-1;slider.value=0;show();};slider.oninput=show;scenes.onchange();`;
new Function(script);
fs.writeFileSync(review+'/index.html','<!doctype html><meta charset="utf-8"><title>原帧纠错证据</title><style>body{font:16px sans-serif;margin:24px;background:#fafafa;color:#222}img{max-width:100%;image-rendering:pixelated}pre{white-space:pre-wrap;overflow-wrap:anywhere}main{display:grid;grid-template-columns:1fr 1fr;gap:20px}</style><h1>三个场景的原帧纠错</h1><p>依据用户“如果确认冻结内容有误，可以在置信度较高的情况下调整冻结结论”的条件授权，仅纠正 6 个原帧。全事件 321 帧保留，旧版不覆盖。3541 名字残缺标记及其他未完成复核项仍待验证，不能授予整体验收通过。代理双遍核读，不是用户逐帧核验。</p><p><a href="authority.json">授权范围、全部变更及待验证项</a> · <a href="summary.json">完整评分范围</a></p><select id="scenes"></select> <input id="frame" type="range" min="0" value="0"><p id="label"></p><img id="image"><main><section><h2>旧标注</h2><pre id="before"></pre></section><section><h2>纠正后（未改部分沿用旧版）</h2><pre id="after"></pre></section></main><script>'+script+'</script>',{flag:'wx'});
const verification={status:'identities_bound_structural_checks_passed_semantic_scope_qualified',base:identity(base+'/freeze.json'),media:media.map(m=>identity(m.path)),changed,checks,retainedReviewFrames:displays.reduce((n,it)=>n+it.frames.length,0),allSemanticIntegrityPassed:false,uiRendering:'pending_not_observed'};
save(review+'/identity.json',verification);
const files=[];function walk(dir){for(const d of fs.readdirSync(dir,{withFileTypes:true})){const p=dir+'/'+d.name;if(d.isDirectory())walk(p);else files.push({...identity(p),path:path.relative(review,p).replace(/\\/g,'/')});}}walk(review);save(review+'/bundle-manifest.json',{reviewId:batch.reviewId,files});
// The new authority covers only the enumerated corrections; inherited unresolved
// content stays present for development scoring, never silently treated as passed.
fs.mkdirSync(out,{recursive:true});for(const n of ['batch.json','summary.json'])fs.copyFileSync(review+'/'+n,out+'/'+n,fs.constants.COPYFILE_EXCL);
const items=old.items.map(item=>{const payloads=item.payloads.map(p=>({...p}));if(scope[item.id]){const gt=payloads.find(p=>p.kind==='gt'),candidate=changed.find(c=>c.id===item.id).correctedGt;fs.mkdirSync(out+'/'+item.id);const name=item.id+'/ocr-corrected-v2.jsonl';fs.copyFileSync(candidate.path,out+'/'+name,fs.constants.COPYFILE_EXCL);gt.file=name;gt.approvedSource=candidate.path;gt.sha256=candidate.sha256;gt.bytes=candidate.bytes;}return {id:item.id,priorFreeze:item.sourceFreeze,payloads};});
const envelope={schemaVersion:1,freezeVersion:'ocr-all-scenes-v2-observation-r3',status:'frozen_approved_scope_only',authority:'Conditional authorized six-frame correction overlay plus unchanged historical references. This envelope does not newly certify unresolved inherited frames.',approval:authority,approvedBundle:identity(review+'/bundle-manifest.json'),identityCheck:identity(review+'/identity.json'),items,summary,limitations,sessionPartition:old.sessionPartition,allSemanticIntegrityPassed:false,productAcceptance:false,previousEffectiveFreeze:identity(base+'/freeze.json')};
save(out+'/freeze.json',envelope);for(const f of files)check(sha(review+'/'+f.path)===f.sha256,'Final bundle changed');
console.log(JSON.stringify({freeze:identity(out+'/freeze.json'),bundle:identity(review+'/bundle-manifest.json'),correctedFrames:6,retainedReviewFrames:verification.retainedReviewFrames,semanticIntegrity:'qualified_pending',productAcceptance:false}));
