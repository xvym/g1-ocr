'use strict';
const fs=require('fs'),crypto=require('crypto');
const {audit}=require('./check-gt-overlaps.cjs');
const json=p=>JSON.parse(fs.readFileSync(p,'utf8')),rows=p=>fs.readFileSync(p,'utf8').trim().split(/\r?\n/).map(JSON.parse);
const sha=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const ident=p=>({path:p,sha256:sha(p),bytes:fs.statSync(p).size});
const check=(v,m)=>{if(!v)throw Error(m);},save=(p,v)=>fs.writeFileSync(p,JSON.stringify(v,null,2)+'\n',{flag:'wx'});
const base='data/gt/frozen/ocr-all-scenes-v2-observation-r3',out='data/gt/frozen/ocr-all-scenes-v2-observation-r4',review='reports/gt-review/gt-observation-corrections-v2';
const id='league-centre-npc',notes='reports/m3-grid-refinement-v1/authorized-correction-league-centre-npc-notes-v1';
check(!fs.existsSync(out)&&!fs.existsSync(review),'New outputs required');
check(sha(base+'/freeze.json')==='dd6aa72aca1ddecfe2c6c0143a1def2458f7de5a8767cbf3ca04e9407b7f5141','Prior freeze changed');
const old=json(base+'/freeze.json'),batch=json(base+'/batch.json'),ev=json(notes+'/candidate-evidence.json');
const candidate=ev.candidate.path;check(sha(candidate)===ev.candidate.sha256&&ev.candidate.sha256==='05bb1f63e45c4efeb2ecea8fe7b03ac039aac593db613bb33036d7e78495c542','Candidate changed');
for(const it of old.items)for(const p of it.payloads){const f=p.file?base+'/'+p.file:p.approvedSource;check(sha(f)===p.sha256,'Prior payload changed');}
const media=rows('data/meta/m1-v1/media.jsonl');for(const m of media)check(sha(m.path)===m.sha256,'Source changed');
const p1='data/frames/video-03/annotation-recheck-league-centre-npc-pass1-v1',p2='data/frames/video-03/authorized-correction-league-centre-npc-pass2-v1';
const v1=json(p1+'/verification.json'),v2=json(p2+'/verification.json');check(JSON.stringify(v1.frames)===JSON.stringify(v2.frames)&&v2.frames.length===146,'Original frame identities differ');
const before=rows(ev.sourceGt.path),after=rows(candidate),visible=s=>JSON.stringify([s.regions,s.cursor,s.kind,s.completePage,s.pageOpportunityId]);
const changes=[];for(let n=1592;n<1734;n++){const a=before.find(s=>s.startFrame<=n&&n<s.endFrameExclusive),b=after.find(s=>s.startFrame<=n&&n<s.endFrameExclusive);check(a&&b,'Missing frame');if(visible(a)!==visible(b))changes.push(n);}
check(JSON.stringify(changes)==='[1630,1631,1692]','Unexpected visible changes');
fs.mkdirSync(review+'/frames',{recursive:true});
for(const f of ev.frameIdentity){const name=`frame-${String(f.frame).padStart(6,'0')}.png`;check(sha(p1+'/'+name)===f.sha256&&sha(p2+'/'+name)===f.sha256,'PNG changed');fs.copyFileSync(p2+'/'+name,review+'/frames/'+name,fs.constants.COPYFILE_EXCL);}
for(const [name,file] of Object.entries({'old.jsonl':ev.sourceGt.path,'corrected.jsonl':candidate,'pass1.json':ev.notes.first,'pass2.json':ev.notes.second,'candidate-evidence.json':notes+'/candidate-evidence.json','source-pass1.json':p1+'/verification.json','source-pass2.json':p2+'/verification.json','integrity.json':'reports/m3-grid-refinement-v1/authorized-correction-league-centre-npc-integrity-v1.json'}))fs.copyFileSync(file,review+'/'+name,fs.constants.COPYFILE_EXCL);
const integrity=json(review+'/integrity.json');check(integrity.gtSha256===sha(candidate)&&integrity.sourceFrames===142&&integrity.states===33&&integrity.completePageOpportunities===4,'Integrity report mismatch');
const item=batch.items.find(x=>x.id===id);item.candidate=candidate;item.integrity=review+'/integrity.json';item.approval='conditional_authorized_three_original_frame_boundary_correction';
batch.reviewId='gt-observation-corrections-v2';batch.approvalScope='Three source-frame corrections only; previous r3 references and qualifications inherited. Full ranges preserved.';
const summary={...old.summary,reviewId:batch.reviewId,continuousStateIntervals:old.summary.continuousStateIntervals+2,completeStateIntervals:old.summary.completeStateIntervals+2,status:'qualified_three_frame_boundary_correction',correctionCharacterDelta:0,allSemanticIntegrityPassed:false};
save(review+'/batch.json',batch);save(review+'/summary.json',summary);
const overlap=audit(review+'/batch.json');check(overlap.uniqueScoredFrames===3361&&overlap.overlappingReferences===7&&!overlap.conflicts.length,'Overlap failure');save(review+'/overlap.json',overlap);
const authority={reviewId:batch.reviewId,message:'如果确认冻结内容有误，可以在置信度较高的情况下调整冻结结论',basis:'Direct user conditional authorization, satisfied by full event original-image double review. Not subsequent user review of this version; not human per-frame certification.',changes:[{id,frames:changes,oldGt:ev.sourceGt,correctedGt:ev.candidate}],limitations:old.limitations};save(review+'/authority.json',authority);
save(review+'/identity.json',{status:'original_identity_and_candidate_integrity_verified_not_product_acceptance',base:ident(base+'/freeze.json'),media:media.map(m=>ident(m.path)),changes,retainedReviewFrames:146,allSemanticIntegrityPassed:false,uiRendering:'pending'});
const script=`const before=${JSON.stringify(before)},after=${JSON.stringify(after)};const slider=document.querySelector('input');function show(){const n=+slider.value;document.querySelector('img').src='frames/frame-'+String(n).padStart(6,'0')+'.png';document.querySelector('#label').textContent='原帧 '+n+' / PTS '+n*512+' × 1/12288';for(const [key,rows] of [['old',before],['new',after]])document.getElementById(key).textContent=JSON.stringify(rows.find(s=>s.startFrame<=n&&n<s.endFrameExclusive)||'上下文，不计分',null,2);}slider.oninput=show;show();`;
new Function(script);fs.writeFileSync(review+'/index.html','<!doctype html><meta charset="utf-8"><title>联盟中心滚动边界纠错</title><style>body{font:16px sans-serif;margin:24px}img{max-width:100%;image-rendering:pixelated}main{display:grid;grid-template-columns:1fr 1fr;gap:20px}pre{white-space:pre-wrap}</style><h1>联盟中心原图边界纠错</h1><p>条件授权下仅修正1630、1631、1692帧。保留146原帧、142评分帧和4页面机会；增加2个真实状态区间。旧版保留。代理双遍核读，整体语义完整性、独立会话及真实UI仍待验证。</p><a href="authority.json">授权与缺口</a> · <a href="pass2.json">双遍差异</a><p id="label"></p><input type="range" min="1590" max="1735" value="1590"><img><main><section><h2>旧标注</h2><pre id="old"></pre></section><section><h2>修正后</h2><pre id="new"></pre></section></main><script>'+script.replace(/<\//g,'<\\/')+'</script>',{flag:'wx'});
const files=[];function walk(d,p=''){for(const e of fs.readdirSync(d,{withFileTypes:true})){const rel=p+e.name;if(e.isDirectory())walk(d+'/'+e.name,rel+'/');else files.push({...ident(d+'/'+e.name),path:rel});}}walk(review);save(review+'/bundle-manifest.json',{reviewId:batch.reviewId,files});
fs.mkdirSync(out,{recursive:true});for(const n of ['batch.json','summary.json'])fs.copyFileSync(review+'/'+n,out+'/'+n);
const items=old.items.map(it=>({id:it.id,priorFreeze:ident(base+'/freeze.json'),payloads:it.payloads.map(p=>{const q={...p};if(q.file){q.approvedSource=base+'/'+q.file;delete q.file;}if(it.id===id&&q.kind==='gt'){q.approvedSource=candidate;q.sha256=sha(candidate);q.bytes=fs.statSync(candidate).size;}return q;})}));
save(out+'/freeze.json',{...old,freezeVersion:'ocr-all-scenes-v2-observation-r4',authority:'Three reviewed frame corrections under conditional user authorization; inherited reference remains qualified.',approval:authority,approvedBundle:ident(review+'/bundle-manifest.json'),identityCheck:ident(review+'/identity.json'),items,summary,previousEffectiveFreeze:ident(base+'/freeze.json')});
console.log(JSON.stringify({freeze:ident(out+'/freeze.json'),bundle:ident(review+'/bundle-manifest.json'),changes,states:33,scoredFrames:142,pages:4}));
