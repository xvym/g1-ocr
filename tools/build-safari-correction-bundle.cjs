'use strict';
const fs=require('fs'),crypto=require('crypto');
const {audit}=require('./check-gt-overlaps.cjs');
const json=p=>JSON.parse(fs.readFileSync(p,'utf8')),rows=p=>fs.readFileSync(p,'utf8').trim().split(/\r?\n/).map(JSON.parse);
const sha=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const ident=p=>({path:p,sha256:sha(p),bytes:fs.statSync(p).size});
const check=(v,m)=>{if(!v)throw Error(m);},save=(p,v)=>fs.writeFileSync(p,JSON.stringify(v,null,2)+'\n',{flag:'wx'});
const base='data/gt/frozen/ocr-all-scenes-v2-observation-r5',out='data/gt/frozen/ocr-all-scenes-v2-observation-r6',review='reports/gt-review/gt-observation-corrections-v4';
const id='safari-warden-npc',notes='reports/m3-grid-refinement-v1/authorized-correction-safari-warden-npc-notes-v1';
check(!fs.existsSync(out)&&!fs.existsSync(review),'New outputs required');
check(sha(base+'/freeze.json')==='7e122cc67f89da660936daa413d6f7d822d6a747998169724c92b5aa2f8702fc','Prior freeze changed');
const old=json(base+'/freeze.json'),batch=json(base+'/batch.json'),ev=json(notes+'/candidate-evidence.json');
const candidate=ev.candidate.path;check(sha(candidate)===ev.candidate.sha256&&ev.candidate.sha256==='28e541e118f35e9467037d4c5f662ef0f33a4478019f53f8c6350cd4a6cf6aae','Candidate changed');
for(const it of old.items)for(const p of it.payloads){const f=p.file?base+'/'+p.file:p.approvedSource;check(sha(f)===p.sha256,'Prior payload changed');}
const media=rows('data/meta/m1-v1/media.jsonl');for(const m of media)check(sha(m.path)===m.sha256,'Source changed');
const p1='data/frames/video-02/ocr-observation-recheck-safari-warden-npc-v1',p2='data/frames/video-02/authorized-correction-safari-warden-npc-pass2-v1';
const v1=json(p1+'/verification.json'),v2=json(p2+'/verification.json');check(JSON.stringify(v1.frames)===JSON.stringify(v2.frames)&&v2.frames.length===83,'Original frame identities differ');
const before=rows(ev.oldGt.path),after=rows(candidate),visible=s=>JSON.stringify([s.regions,s.cursor,s.kind,s.completePage,s.pageOpportunityId]);
const changes=[];for(let n=4447;n<4526;n++){const a=before.find(s=>s.startFrame<=n&&n<s.endFrameExclusive),b=after.find(s=>s.startFrame<=n&&n<s.endFrameExclusive);check(a&&b,'Missing frame');if(visible(a)!==visible(b))changes.push(n);}
check(JSON.stringify(changes)===JSON.stringify(Array.from({length:26},(_,i)=>4496+i)),'Unexpected visible changes');
fs.mkdirSync(review+'/frames',{recursive:true});
for(const f of json(notes+'/space-cell-evidence.json').pairedOriginalFrames){const name=`frame-${String(f.frame).padStart(6,'0')}.png`;check(sha(p1+'/'+name)===f.pngSha256&&sha(p2+'/'+name)===f.pngSha256,'PNG changed');fs.copyFileSync(p2+'/'+name,review+'/frames/'+name,fs.constants.COPYFILE_EXCL);}
for(const [name,file] of Object.entries({'old.jsonl':ev.oldGt.path,'corrected.jsonl':candidate,'pass1.json':notes+'/pass1.json','pass2.json':notes+'/pass2.json','candidate-evidence.json':notes+'/candidate-evidence.json','source-pass1.json':p1+'/verification.json','source-pass2.json':p2+'/verification.json','integrity.json':'reports/m3-grid-refinement-v1/authorized-correction-safari-warden-npc-integrity-v1'}))fs.copyFileSync(file,review+'/'+name,fs.constants.COPYFILE_EXCL);
for(const name of ['space-cell-evidence.json','4496-row-original-nearest6.png'])fs.copyFileSync(notes+'/'+name,review+'/'+name,fs.constants.COPYFILE_EXCL);
const integrity=json(review+'/integrity.json');check(integrity.gtSha256===sha(candidate)&&integrity.sourceFrames===79&&integrity.states===23&&integrity.completePageOpportunities===2,'Integrity report mismatch');
const item=batch.items.find(x=>x.id===id);item.candidate=candidate;item.integrity=review+'/integrity.json';item.approval='conditional_authorized_original_spacing_correction';
batch.reviewId='gt-observation-corrections-v4';batch.approvalScope='Twenty-six source-frame spacing corrections only; previous r5 references and qualifications inherited. Full ranges preserved.';
const summary={...old.summary,reviewId:batch.reviewId,continuousStateIntervals:old.summary.continuousStateIntervals,completeStateIntervals:old.summary.completeStateIntervals,status:'qualified_original_spacing_correction',correctionCharacterDelta:-26,allSemanticIntegrityPassed:false};
save(review+'/batch.json',batch);save(review+'/summary.json',summary);
const overlap=audit(review+'/batch.json');check(overlap.uniqueScoredFrames===3361&&overlap.overlappingReferences===7&&!overlap.conflicts.length,'Overlap failure');save(review+'/overlap.json',overlap);
const authority={reviewId:batch.reviewId,message:'如果确认冻结内容有误，可以在置信度较高的情况下调整冻结结论',basis:'Direct user conditional authorization, satisfied by full event original-image double review. Not subsequent user review of this version; not human per-frame certification.',changes:[{id,frames:changes,oldGt:ev.oldGt,correctedGt:ev.candidate}],limitations:old.limitations};save(review+'/authority.json',authority);
save(review+'/identity.json',{status:'original_identity_and_candidate_integrity_verified_not_product_acceptance',base:ident(base+'/freeze.json'),media:media.map(m=>ident(m.path)),changes,retainedReviewFrames:83,allSemanticIntegrityPassed:false,uiRendering:'pending'});
const script=`const before=${JSON.stringify(before)},after=${JSON.stringify(after)};const slider=document.querySelector('input');function show(){const n=+slider.value;document.querySelector('img').src='frames/frame-'+String(n).padStart(6,'0')+'.png';document.querySelector('#label').textContent='原帧 '+n+' / PTS '+(n*512)+' × 1/12288';for(const [key,rows] of [['old',before],['new',after]])document.getElementById(key).textContent=JSON.stringify(rows.find(s=>s.startFrame<=n&&n<s.endFrameExclusive)||'上下文，不计分',null,2);}slider.oninput=show;show();`;
new Function(script);fs.writeFileSync(review+'/index.html','<!doctype html><meta charset="utf-8"><title>园长对白空格纠错</title><style>body{font:16px sans-serif;margin:24px}img{max-width:100%;image-rendering:pixelated}main{display:grid;grid-template-columns:1fr 1fr;gap:20px}pre{white-space:pre-wrap}</style><h1>园长对白原图空格纠错</h1><p>条件授权下仅删除4496–4521帧中引号前误标的一个空格，并将对应上行右边界左移一格。保留83原帧、79评分帧、23状态和2页面机会。旧版保留。代理双遍核读，整体语义完整性、独立会话及真实UI仍待验证。</p><a href="authority.json">授权与缺口</a> · <a href="pass2.json">双遍差异</a><p id="label"></p><input type="range" min="4445" max="4527" value="4445"><img><main><section><h2>旧标注</h2><pre id="old"></pre></section><section><h2>修正后</h2><pre id="new"></pre></section></main><script>'+script.replace(/<\//g,'<\\/')+'</script>',{flag:'wx'});
const files=[];function walk(d,p=''){for(const e of fs.readdirSync(d,{withFileTypes:true})){const rel=p+e.name;if(e.isDirectory())walk(d+'/'+e.name,rel+'/');else files.push({...ident(d+'/'+e.name),path:rel});}}walk(review);save(review+'/bundle-manifest.json',{reviewId:batch.reviewId,files});
fs.mkdirSync(out,{recursive:true});for(const n of ['batch.json','summary.json'])fs.copyFileSync(review+'/'+n,out+'/'+n);
const items=old.items.map(it=>({id:it.id,priorFreeze:ident(base+'/freeze.json'),payloads:it.payloads.map(p=>{const q={...p};if(q.file){q.approvedSource=base+'/'+q.file;delete q.file;}if(it.id===id&&q.kind==='gt'){q.approvedSource=candidate;q.sha256=sha(candidate);q.bytes=fs.statSync(candidate).size;}return q;})}));
save(out+'/freeze.json',{...old,freezeVersion:'ocr-all-scenes-v2-observation-r6',authority:'Twenty-six reviewed spacing corrections under conditional user authorization; inherited reference remains qualified.',approval:authority,approvedBundle:ident(review+'/bundle-manifest.json'),identityCheck:ident(review+'/identity.json'),items,summary,previousEffectiveFreeze:ident(base+'/freeze.json')});
console.log(JSON.stringify({freeze:ident(out+'/freeze.json'),bundle:ident(review+'/bundle-manifest.json'),changes,states:23,scoredFrames:79,pages:2}));
