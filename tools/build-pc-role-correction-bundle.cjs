'use strict';
const fs=require('fs'),path=require('path'),crypto=require('crypto'),assert=require('assert/strict');
const {audit}=require('./check-gt-overlaps.cjs');
const json=p=>JSON.parse(fs.readFileSync(p,'utf8')),rows=p=>fs.readFileSync(p,'utf8').trim().split(/\r?\n/).map(JSON.parse);
const sha=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex'),ident=p=>({path:p,sha256:sha(p),bytes:fs.statSync(p).size});
const save=(p,v)=>fs.writeFileSync(p,JSON.stringify(v,null,2)+'\n',{flag:'wx'}),copy=(a,b)=>fs.copyFileSync(a,b,fs.constants.COPYFILE_EXCL);
const base='data/gt/frozen/ocr-all-scenes-v2-observation-r6',out='data/gt/frozen/ocr-all-scenes-v2-observation-r7',review='reports/gt-review/gt-observation-corrections-v5',notes='reports/m3-grid-refinement-v1/pc-role-recheck-notes-v1';
assert(!fs.existsSync(out)&&!fs.existsSync(review));assert.equal(sha(base+'/freeze.json'),'603450afecb3fb7e45e1c916281cef77f6957c0382f9cd91cda15c109828de80');
const old=json(base+'/freeze.json'),batch=json(base+'/batch.json'),ev=json(notes+'/candidate-evidence.json');
for(const it of old.items)for(const p of it.payloads)assert.equal(sha(p.file?base+'/'+p.file:p.approvedSource),p.sha256);
for(const e of [...ev.evidence,ev.audit])assert.equal(sha(e.path),e.sha256);
const media=rows('data/meta/m1-v1/media.jsonl');for(const m of media)assert.equal(sha(m.path),m.sha256);
const extraction=json(notes+'/extraction-check.json');let retained=0;
fs.mkdirSync(review,{recursive:true});
for(const e of extraction.events){
 const a=path.dirname(e.firstVerification),b=path.dirname(e.secondVerification);assert.equal(sha(e.firstVerification),e.firstVerificationSha256);assert.equal(sha(e.secondVerification),e.secondVerificationSha256);
 assert.deepEqual(json(e.firstVerification).frames,json(e.secondVerification).frames);
 const names=fs.readdirSync(b).filter(n=>/^frame-\d+\.png$/.test(n));assert.equal(names.length,e.frames);retained+=names.length;
 fs.mkdirSync(review+'/'+e.id+'/frames',{recursive:true});for(const n of names){assert.equal(sha(a+'/'+n),sha(b+'/'+n));copy(b+'/'+n,review+'/'+e.id+'/frames/'+n);}
 copy(e.firstVerification,review+'/'+e.id+'/source-pass1.json');copy(e.secondVerification,review+'/'+e.id+'/source-pass2.json');
}
assert.equal(retained,189);
for(const n of ['pass1.json','pass2.json','pass-differences.json','extraction-check.json','candidate-evidence.json'])copy(notes+'/'+n,review+'/'+n);
copy(ev.audit.path,review+'/role-contract-audit.json');
for(const r of ev.results){
 assert.equal(sha(r.oldGt.path),r.oldGt.sha256);assert.equal(sha(r.candidate.path),r.candidate.sha256);
 const before=rows(r.oldGt.path),restored=rows(r.candidate.path);for(const c of r.changes){const s=restored.find(s=>s.startFrame===c.start&&s.endFrameExclusive===c.end),region=s.regions.find(x=>x.id===c.region);assert.equal(region.role,c.after);region.role=c.before;}assert.deepEqual(restored,before);
 copy(r.oldGt.path,review+'/'+r.id+'/old.jsonl');copy(r.candidate.path,review+'/'+r.id+'/corrected.jsonl');
 const suffix=r.id==='pc-exit'?'exit':'navigation',report='reports/m3-grid-refinement-v1/pc-role-'+suffix+'-integrity-v1.json',integrity=json(report);
 assert.equal(integrity.gtSha256,r.candidate.sha256);assert.equal(integrity.sourceFrames,r.id==='pc-exit'?10:120);copy(report,review+'/'+r.id+'/integrity.json');
 const item=batch.items.find(x=>x.id===r.id);item.candidate=r.candidate.path;item.integrity=review+'/'+r.id+'/integrity.json';item.approval='conditional_authorized_original_PC_role_correction';
}
batch.reviewId='gt-observation-corrections-v5';batch.approvalScope='Only main-menu field roles on ten reviewed original frames; all prior qualifications inherited.';
const summary={...old.summary,reviewId:batch.reviewId,status:'qualified_original_PC_role_correction',correctionCharacterDelta:0,allSemanticIntegrityPassed:false};save(review+'/batch.json',batch);save(review+'/summary.json',summary);
const overlap=audit(review+'/batch.json');assert.equal(overlap.uniqueScoredFrames,3361);assert.equal(overlap.overlappingReferences,7);assert.equal(overlap.conflicts.length,0);save(review+'/overlap.json',overlap);
const authority={reviewId:batch.reviewId,message:ev.authority,basis:'Conditional user authorization with full original-event double reading; same coding agent, prior diagnostic exposure disclosed; not independent-session or human per-frame certification.',changes:ev.results,affectedFrames:ev.affectedFrames,limitations:old.limitations};save(review+'/authority.json',authority);
save(review+'/identity.json',{base:ident(base+'/freeze.json'),media:media.map(m=>ident(m.path)),retainedReviewFrames:retained,allSemanticIntegrityPassed:false,productAcceptance:false,uiRendering:'pending'});
fs.writeFileSync(review+'/index.html','<!doctype html><meta charset="utf-8"><title>PC主菜单字段纠错</title><h1>PC主菜单字段纠错</h1><p>完整189原帧已由同一代理双遍核读，仅10帧12区域状态的主菜单角色改为早先冻结定义 pc_menu_choice。文字、位置、页机会和评分规则不变。独立会话、实际UI和真实环境待验证。</p><a href="authority.json">授权与范围</a> <a href="pass2.json">第二遍笔记</a> <a href="pass-differences.json">双遍差异</a><ul>'+extraction.events.map(e=>'<li>'+e.id+'<ul>'+fs.readdirSync(review+'/'+e.id+'/frames').map(n=>'<li><a href="'+e.id+'/frames/'+n+'">'+n+'</a></li>').join('')+'</ul></li>').join('')+'</ul>',{flag:'wx'});
const files=[];function walk(d,p=''){for(const e of fs.readdirSync(d,{withFileTypes:true})){const rel=p+e.name;if(e.isDirectory())walk(d+'/'+e.name,rel+'/');else files.push({...ident(d+'/'+e.name),path:rel});}}walk(review);save(review+'/bundle-manifest.json',{reviewId:batch.reviewId,files});
fs.mkdirSync(out,{recursive:true});for(const n of ['batch.json','summary.json'])copy(review+'/'+n,out+'/'+n);
const items=old.items.map(it=>({id:it.id,priorFreeze:ident(base+'/freeze.json'),payloads:it.payloads.map(p=>{const q={...p};if(q.file){q.approvedSource=base+'/'+q.file;delete q.file;}const r=ev.results.find(r=>r.id===it.id);if(r&&q.kind==='gt'){q.approvedSource=r.candidate.path;q.sha256=r.candidate.sha256;q.bytes=fs.statSync(r.candidate.path).size;}return q;})}));
save(out+'/freeze.json',{...old,freezeVersion:'ocr-all-scenes-v2-observation-r7',authority:'Ten original-frame PC main-menu role corrections under conditional authorization; inherited qualifications remain.',approval:authority,approvedBundle:ident(review+'/bundle-manifest.json'),identityCheck:ident(review+'/identity.json'),items,summary,previousEffectiveFreeze:ident(base+'/freeze.json')});
console.log(JSON.stringify({freeze:ident(out+'/freeze.json'),bundle:ident(review+'/bundle-manifest.json'),retained,affectedFrames:ev.affectedFrames}));
