'use strict';
const fs=require('fs'),path=require('path'),crypto=require('crypto'),assert=require('assert/strict'),{audit}=require('./check-gt-overlaps.cjs');
const read=p=>JSON.parse(fs.readFileSync(p,'utf8')),rows=p=>fs.readFileSync(p,'utf8').trim().split(/\r?\n/).map(JSON.parse);
const sha=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex'),ident=p=>({path:p,sha256:sha(p),bytes:fs.statSync(p).size});
const save=(p,v)=>fs.writeFileSync(p,JSON.stringify(v,null,2)+'\n',{flag:'wx'}),copy=(a,b)=>fs.copyFileSync(a,b,fs.constants.COPYFILE_EXCL);
const base='data/gt/frozen/ocr-all-scenes-v2-observation-r9',out='data/gt/frozen/ocr-all-scenes-v2-observation-r10',review='reports/gt-review/gt-observation-corrections-v8',notes='reports/m3-grid-refinement-v1/oak-boundary-notes-v1';
assert(!fs.existsSync(out)&&!fs.existsSync(review));
const old=read(base+'/freeze.json'),batch=read(base+'/batch.json'),ev=read(notes+'/candidate-evidence.json'),check=read(ev.doubleRead.path);
assert.equal(sha(base+'/freeze.json'),ev.freeze.sha256);assert.equal(sha(ev.doubleRead.path),ev.doubleRead.sha256);assert.equal(check.retainedFrames,197);assert.equal(ev.changes.length,2);
for(const it of old.items)for(const p of it.payloads)assert.equal(sha(p.file?base+'/'+p.file:p.approvedSource),p.sha256);
const media=rows('data/meta/m1-v1/media.jsonl');assert.equal(media.length,4);for(const m of media)assert.equal(sha(m.path),m.sha256);
fs.mkdirSync(review,{recursive:true});
for(const it of check.items){
 assert(it.allPngBytesEqual&&it.allMetadataEqual&&it.allTextAndArrowObservationsAgree);
 for(const p of [...it.notes,...it.sheets,...it.verification])assert.equal(sha(p.path),p.sha256);
 const c=ev.changes.find(x=>x.id===it.id);for(const p of [c.input,c.candidate])assert.equal(sha(p.path),p.sha256);
 const a=rows(c.input.path),b=rows(c.candidate.path),idx=a.findIndex(s=>s.startFrame===c.frame),restored=structuredClone(b);
 assert.deepEqual(a[idx],c.before);assert.deepEqual(b[idx],c.after);restored[idx]=a[idx];assert.deepEqual(restored,a);
 const dir=review+'/'+it.id;fs.mkdirSync(dir+'/frames',{recursive:true});
 for(const f of it.frames){for(const p of f.paths)assert.equal(sha(p),f.sha256);copy(f.paths[1],dir+'/frames/'+path.basename(f.paths[1]));}
 it.notes.forEach((p,i)=>copy(p.path,dir+'/pass'+(i+1)+'.json'));it.verification.forEach((p,i)=>copy(p.path,dir+'/source-pass'+(i+1)+'.json'));
 copy(c.input.path,dir+'/old.jsonl');copy(c.candidate.path,dir+'/corrected.jsonl');
 const integrity='reports/m3-grid-refinement-v1/'+(it.id==='lab-oak-reply'?'oak-reply':it.id)+'-boundary-candidate-integrity-v1.json',ir=read(integrity);
 assert.equal(ir.gtSha256,c.candidate.sha256);assert.equal(ir.sourceFrames,it.scoredFrames);copy(integrity,dir+'/integrity.json');
 const bi=batch.items.find(x=>x.id===it.id);bi.candidate=c.candidate.path;bi.integrity=dir+'/integrity.json';bi.approval='conditional_authorized_original_boundary_correction';
}
for(const n of ['candidate-evidence.json','extraction-and-pass-check.json'])copy(notes+'/'+n,review+'/'+n);
batch.reviewId='gt-observation-corrections-v8';batch.approvalScope='Only original visible character boundaries at lab-oak-reply 13438 and oak-delivery 24223 and their necessary rectangles; no timing, roles, state or scoring changes.';
const summary={...old.summary,reviewId:batch.reviewId,status:'qualified_original_boundary_correction',correctionCharacterDelta:0,allSemanticIntegrityPassed:false};save(review+'/batch.json',batch);save(review+'/summary.json',summary);
const overlap=audit(review+'/batch.json');assert.equal(overlap.uniqueScoredFrames,3361);assert.equal(overlap.overlappingReferences,7);assert.equal(overlap.conflicts.length,0);save(review+'/overlap.json',overlap);
const authority={reviewId:batch.reviewId,message:'如果确认冻结内容有误，可以在置信度较高的情况下调整冻结结论',basis:ev.rationale,changes:ev.changes,limitations:old.limitations,sameAgentNotIndependentSession:true};save(review+'/authority.json',authority);
save(review+'/identity.json',{base:ident(base+'/freeze.json'),media:media.map(m=>ident(m.path)),retainedReviewFrames:197,allSemanticIntegrityPassed:false,productAcceptance:false,uiRendering:'pending',builder:ident(__filename)});
fs.writeFileSync(review+'/index.html','<!doctype html><meta charset="utf-8"><title>博士对话字符边界纠错</title><h1>博士对话字符边界纠错</h1><p>两个完整事件197原帧双遍核读。13438补已出现的感叹号；24223移除尚未出现的の及对应占位。字符总分母净变化0，其余范围、时序、状态和评分规则保持。既有诊断接触已披露，代理核读不等于独立会话或人工逐帧验收。UI/真实环境待验证。</p><a href="authority.json">授权和差异</a><ul>'+check.items.map(it=>'<li>'+it.id+' <a href="'+it.id+'/old.jsonl">旧注释</a> <a href="'+it.id+'/corrected.jsonl">新注释</a><ul>'+it.frames.map(f=>'<li><a href="'+it.id+'/frames/'+path.basename(f.paths[1])+'">'+f.frame+'</a></li>').join('')+'</ul></li>').join('')+'</ul>',{flag:'wx'});
const files=[];function walk(d,p=''){for(const e of fs.readdirSync(d,{withFileTypes:true})){const rel=p+e.name;if(e.isDirectory())walk(d+'/'+e.name,rel+'/');else files.push({...ident(d+'/'+e.name),path:rel});}}walk(review);save(review+'/bundle-manifest.json',{reviewId:batch.reviewId,files});
fs.mkdirSync(out,{recursive:true});for(const n of ['batch.json','summary.json'])copy(review+'/'+n,out+'/'+n);
const items=old.items.map(it=>({id:it.id,priorFreeze:ident(base+'/freeze.json'),payloads:it.payloads.map(p=>{const q={...p};if(q.file){q.approvedSource=base+'/'+q.file;delete q.file;}const c=ev.changes.find(c=>c.id===it.id);if(c&&q.kind==='gt'){q.approvedSource=c.candidate.path;q.sha256=c.candidate.sha256;q.bytes=fs.statSync(c.candidate.path).size;}return q;})}));
save(out+'/freeze.json',{...old,freezeVersion:'ocr-all-scenes-v2-observation-r10',authority:'Original visible character boundary correction under conditional user authorization; all qualifications remain.',approval:authority,approvedBundle:ident(review+'/bundle-manifest.json'),identityCheck:ident(review+'/identity.json'),items,summary,previousEffectiveFreeze:ident(base+'/freeze.json')});
console.log(JSON.stringify({freeze:ident(out+'/freeze.json'),bundle:ident(review+'/bundle-manifest.json')}));
