'use strict';
const fs=require('fs'),assert=require('assert/strict');
const root=process.argv[2],file=root+'/similar-comparison-scope-v1.json';assert(root&&!fs.existsSync(file));
const rows=p=>fs.readFileSync(p,'utf8').trim().split(/\r?\n/).map(JSON.parse),cases=[];
for(const id of ['status-pages','status-alt-pages','battle-result']){
 const a=rows('reports/m3-grid-refinement-v1/ocr-regression-all-v19/actual/'+id+'.jsonl'),b=rows(root+'/similar-'+id+'/actual/'+id+'.jsonl'),t=rows(root+'/similar-'+id+'/'+id+'-timeline.jsonl'),changes=[];
 for(let i=0;i<b.length;i++){
  const x=structuredClone(a[i]),y=structuredClone(b[i]);delete x.processingNanos;delete y.processingNanos;let labels=0;
  for(const k of ['rawRegions','correctedRegions'])for(const r of x[k]||[])if(r.role==='compact_party_label'&&r.text==='HP:'){
   const p=r.logicalRect;r.renderUnits=[{text:'HP',kind:'compound_label',logicalRect:[p[0],p[1],p[0]+8,p[3]]},{text:':',kind:'compound_label',logicalRect:[p[0]+8,p[1],p[2],p[3]]}];if(k==='rawRegions')labels++;
  }
  assert.deepEqual(x,y,id+':'+b[i].sourceFrameIndex);
  if(labels)changes.push({frame:b[i].sourceFrameIndex,scored:t[i].scored,labels});
 }
 cases.push({clip:id,retained:b.length,changes});
}
const report={status:'validation_scope_correction_no_algorithm_change',firstFailedAssumption:{clip:'status-pages',frame:460,reason:'Exact unchanged-output comparison rejected a real party-menu frame retained as warmup of status-pages. The approved party structure fix also applies to this current image.'},correction:'Keep all warmup/context; permit only the already-approved party HP unit change there. Scored failure target remains20 originals/115 labels. Every other output including readiness must remain equal.',cases};fs.writeFileSync(file,JSON.stringify(report,null,2)+'\n',{flag:'wx'});console.log(JSON.stringify(report));
