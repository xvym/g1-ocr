'use strict';
// Frozen annotation audit, deliberately independent of tested OCR and its scores.
const fs=require('fs'),crypto=require('crypto'),assert=require('assert/strict');
const read=p=>JSON.parse(fs.readFileSync(p,'utf8')),rows=p=>fs.readFileSync(p,'utf8').trim().split(/\r?\n/).map(JSON.parse);
const sha=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex'),ident=p=>({path:p,sha256:sha(p)});
const root='data/gt/frozen/ocr-all-scenes-v2-observation-r8',freeze=read(root+'/freeze.json'),out=process.argv[2];assert(out&&!fs.existsSync(out));
const scope=[];
for(const it of freeze.items){for(const p of it.payloads)assert.equal(sha(p.file?root+'/'+p.file:p.approvedSource),p.sha256);if(!it.id.startsWith('shop-'))continue;
 const payloads=it.payloads.map(p=>({kind:p.kind,...ident(p.file?root+'/'+p.file:p.approvedSource)}));
 scope.push({id:it.id,payloads,states:rows(payloads.find(p=>p.kind==='gt').path)});
}
const state=(id,n)=>scope.find(x=>x.id===id).states.find(s=>s.startFrame<=n&&s.endFrameExclusive>n);
const physical=r=>({text:r.text,gridRect:r.gridRect,sourceRect:r.sourceRect,markCells:r.markCells});
const comparisons=[];
for(const [leftId,leftFrame,rightId,rightFrame] of [['shop-sell-entry',1044,'shop-exit',1450],['shop-rejection',1439,'shop-exit',1439]]){
 const a=state(leftId,leftFrame),b=state(rightId,rightFrame),matches=[];
 assert.equal(a.regions.length,b.regions.length);
 for(const r of a.regions){const other=b.regions.find(t=>require('util').isDeepStrictEqual(physical(t),physical(r)));assert(other);matches.push({physical:physical(r),left:{role:r.role,readingOrder:r.readingOrder},right:{role:other.role,readingOrder:other.readingOrder}});}
 comparisons.push({left:{clip:leftId,frame:leftFrame,kind:a.kind},right:{clip:rightId,frame:rightFrame,kind:b.kind},matches});
}
const notes='reports/m3-grid-refinement-v1/shop-dialogue-role-notes-v1',check=read(notes+'/extraction-and-pass-check.json');
assert(check.allPngBytesEqual&&check.allMetadataEqual);assert.equal(check.frames,49);assert.deepEqual(check.differences,[]);for(const p of check.notes)assert.equal(sha(p.path),p.sha256);
const report={status:'shop_same_physical_fields_have_inconsistent_role_and_order',freeze:ident(root+'/freeze.json'),helper:ident(__filename),scope,comparisons,doubleRead:ident(notes+'/extraction-and-pass-check.json'),decision:'A parent shop screen cannot use different field roles/order merely because it occurs in an exit clip versus a sell-entry clip. The 1439 overlap was deliberately amended to the legacy exit convention, so resolving the broader parent vocabulary must include both events and preserve overlap consistency. Occluded menu prefixes and item-list layers remain distinct. Message roles shop_prompt/shop_message require a separate examination of list-entry continuity; do not globally rename either. No candidate or score change is made by this audit.',pending:['Choose one evidence-supported canonical parent role/order convention with original complete sell-entry and rejection context checks.','Inspect whether the same sell question continues when the item list overlays the parent; distinguish annotation inconsistency from runtime role classification.','Question-mark encoding remains unresolved and outside this role audit.'],productAcceptance:false};
fs.writeFileSync(out,JSON.stringify(report,null,2)+'\n',{flag:'wx'});console.log(JSON.stringify({report:ident(out),comparisons:comparisons.map(c=>({left:c.left,right:c.right,fields:c.matches.length,differences:c.matches.filter(m=>JSON.stringify(m.left)!==JSON.stringify(m.right)).length}))}));
