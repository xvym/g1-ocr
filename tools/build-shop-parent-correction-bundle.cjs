'use strict';
const fs=require('fs'),path=require('path'),crypto=require('crypto'),assert=require('assert/strict'),{audit}=require('./check-gt-overlaps.cjs');
const json=p=>JSON.parse(fs.readFileSync(p,'utf8')),rows=p=>fs.readFileSync(p,'utf8').trim().split(/\r?\n/).map(JSON.parse),sha=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const ident=p=>({path:p,sha256:sha(p),bytes:fs.statSync(p).size}),save=(p,v)=>fs.writeFileSync(p,JSON.stringify(v,null,2)+'\n',{flag:'wx'}),copy=(a,b)=>fs.copyFileSync(a,b,fs.constants.COPYFILE_EXCL);
const base='data/gt/frozen/ocr-all-scenes-v2-observation-r10',out='data/gt/frozen/ocr-all-scenes-v2-observation-r11',review='reports/gt-review/gt-observation-corrections-v9';
assert(!fs.existsSync(out)&&!fs.existsSync(review));
const evPath='data/gt/candidates/ocr-observation-corrections-v9/shop-sell-entry/candidate-evidence.json',ev=json(evPath),old=json(base+'/freeze.json'),batch=json(base+'/batch.json');
for(const p of [ev.base,ev.oldGt,ev.candidate,ev.priorExplicitApproval,...ev.evidence])assert.equal(sha(p.path),p.sha256);
for(const it of old.items)for(const p of it.payloads)assert.equal(sha(p.file?base+'/'+p.file:p.approvedSource),p.sha256);
const media=rows('data/meta/m1-v1/media.jsonl');assert.equal(media.length,4);for(const m of media)assert.equal(sha(m.path),m.sha256);
const before=rows(ev.oldGt.path),after=rows(ev.candidate.path),restored=structuredClone(after);
for(const s of restored){for(const c of ev.changes.filter(c=>c.start===s.startFrame)){const r=s.regions.find(r=>r.id===c.id);assert.equal(r.role,c.after.role);assert.equal(r.readingOrder,c.after.readingOrder);Object.assign(r,c.before);}s.regions.sort((a,b)=>a.readingOrder-b.readingOrder);}
assert.deepEqual(restored,before);
fs.mkdirSync(review,{recursive:true});let retained=0;
for(const [id,notes] of [['shop-sell-entry','reports/m3-grid-refinement-v1/shop-sell-contract-notes-v1'],['shop-exit','reports/m3-grid-refinement-v1/shop-dialogue-role-notes-v1']]){
 const dir=review+'/'+id;fs.mkdirSync(dir+'/frames',{recursive:true});
 const passes=[1,2].map(n=>json(notes+'/pass'+n+'.json'));
 const vp=passes.map(p=>p.sourceVerification?p.sourceVerification.path:p.source+'/verification.json'),v=vp.map(json);assert.deepEqual(v[0].frames,v[1].frames);
 for(const f of v[0].frames){const n=`frame-${String(f.frameIndex).padStart(6,'0')}.png`,a=path.dirname(vp[0])+'/'+n,b=path.dirname(vp[1])+'/'+n;assert.equal(sha(a),sha(b));copy(b,dir+'/frames/'+n);retained++;}
 for(let i=0;i<2;i++){copy(notes+'/pass'+(i+1)+'.json',dir+'/pass'+(i+1)+'.json');copy(vp[i],dir+'/source-pass'+(i+1)+'.json');}
}
assert.equal(retained,111);
for(const it of old.items.filter(x=>x.id.startsWith('shop-'))){const dir=review+'/'+it.id;fs.mkdirSync(dir,{recursive:true});for(const p of it.payloads){const f=p.file?base+'/'+p.file:p.approvedSource;copy(f,dir+'/reference-'+p.kind+path.extname(f));}}
copy(ev.candidate.path,review+'/shop-sell-entry/corrected.jsonl');copy(evPath,review+'/candidate-evidence.json');
const integrity='reports/m3-grid-refinement-v1/shop-parent-role-candidate-integrity-v1.json',ir=json(integrity);assert.equal(ir.gtSha256,ev.candidate.sha256);assert.equal(ir.sourceFrames,58);copy(integrity,review+'/shop-sell-entry/integrity.json');
const bi=batch.items.find(x=>x.id==='shop-sell-entry');bi.candidate=ev.candidate.path;bi.integrity=review+'/shop-sell-entry/integrity.json';bi.approval='conditional_authorized_parent_field_consistency_correction';
batch.reviewId='gt-observation-corrections-v9';batch.approvalScope='Shop sell-entry role/order metadata only; full parent convention anchored in explicit shop-metadata-r2 approval, unchanged question retains its role across the overlay. Other37 entries unchanged.';
const summary={...old.summary,reviewId:batch.reviewId,status:'qualified_shop_parent_contract_correction',correctionCharacterDelta:0,allSemanticIntegrityPassed:false};save(review+'/batch.json',batch);save(review+'/summary.json',summary);
const overlap=audit(review+'/batch.json');assert.equal(overlap.uniqueScoredFrames,3361);assert.equal(overlap.overlappingReferences,7);assert.equal(overlap.conflicts.length,0);save(review+'/overlap.json',overlap);
const authority={reviewId:batch.reviewId,message:ev.authority,basis:ev.rationale,changes:ev.changes,limitations:old.limitations,sameAgentNotIndependentSession:true};save(review+'/authority.json',authority);
save(review+'/identity.json',{base:ident(base+'/freeze.json'),media:media.map(m=>ident(m.path)),retainedReviewFrames:retained,allSemanticIntegrityPassed:false,productAcceptance:false,uiRendering:'pending',builder:ident(__filename)});
fs.writeFileSync(review+'/index.html','<!doctype html><meta charset="utf-8"><title>商店父菜单字段一致性纠正</title><h1>商店父菜单字段一致性纠正</h1><p>111原帧双遍证据；仅卖出入口58帧角色/顺序修订。完整父菜单24帧采用早先明确批准约定，连续问句跨列表保持归属。其他列表字段、全部文字、变量元数据、位置、状态、时间、页面和评分规则不改；问号规范仍待定。代理复核不等于独立验收，UI待验证。</p><a href="candidate-evidence.json">完整差异与依据</a> <a href="authority.json">授权边界</a>'+['shop-sell-entry','shop-exit','shop-buy-navigation','shop-rejection'].map(id=>'<h2>'+id+'</h2><a href="'+id+'/reference-gt.jsonl">原注释</a>'+(id==='shop-sell-entry'?' <a href="'+id+'/corrected.jsonl">新注释</a>':'')+(fs.existsSync(review+'/'+id+'/frames')?'<ul>'+fs.readdirSync(review+'/'+id+'/frames').map(n=>'<li><a href="'+id+'/frames/'+n+'">'+n+'</a></li>').join('')+'</ul>':'')).join(''),{flag:'wx'});
const files=[];function walk(d,p=''){for(const e of fs.readdirSync(d,{withFileTypes:true})){const rel=p+e.name;if(e.isDirectory())walk(d+'/'+e.name,rel+'/');else files.push({...ident(d+'/'+e.name),path:rel});}}walk(review);save(review+'/bundle-manifest.json',{reviewId:batch.reviewId,files});
fs.mkdirSync(out,{recursive:true});for(const n of ['batch.json','summary.json'])copy(review+'/'+n,out+'/'+n);
const items=old.items.map(it=>({id:it.id,priorFreeze:ident(base+'/freeze.json'),payloads:it.payloads.map(p=>{const q={...p};if(q.file){q.approvedSource=base+'/'+q.file;delete q.file;}if(it.id==='shop-sell-entry'&&q.kind==='gt'){q.approvedSource=ev.candidate.path;q.sha256=ev.candidate.sha256;q.bytes=fs.statSync(ev.candidate.path).size;}return q;})}));
save(out+'/freeze.json',{...old,freezeVersion:'ocr-all-scenes-v2-observation-r11',authority:'Original-context parent field consistency under conditional user authorization; all qualifications retained.',approval:authority,approvedBundle:ident(review+'/bundle-manifest.json'),identityCheck:ident(review+'/identity.json'),items,summary,previousEffectiveFreeze:ident(base+'/freeze.json')});
console.log(JSON.stringify({freeze:ident(out+'/freeze.json'),bundle:ident(review+'/bundle-manifest.json'),retained}));
