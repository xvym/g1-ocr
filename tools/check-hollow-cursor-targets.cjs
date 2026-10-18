const fs=require('fs'),assert=require('assert/strict'),c=require('crypto');
const root='reports/m3-grid-refinement-v1',rows=p=>fs.readFileSync(p,'utf8').trim().split(/\r?\n/).map(JSON.parse),sha=p=>c.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const changes=[],bindings=[];
for(const clip of ['pc-deposit-navigation','shop-sell-entry','shop-rejection','backpack-navigation']){
 const a=root+'/ocr-regression-all-v16-r14/actual/'+clip+'.jsonl',b=root+'/ocr-hollow-cursor-v2-'+clip+'/actual/'+clip+'.jsonl',x=rows(a),y=rows(b);assert.equal(x.length,y.length);
 for(let i=0;i<x.length;i++){delete x[i].processingNanos;delete y[i].processingNanos;if(x[i].visibleState.kind!==y[i].visibleState.kind){assert(x[i].visibleState.cursor.menuCursors.every(s=>s.style==='outline_right'));changes.push({clip,frame:x[i].sourceFrameIndex,before:x[i].visibleState.kind,after:y[i].visibleState.kind});}if(clip==='shop-sell-entry'&&x[i].sourceFrameIndex===1056)assert.equal(y[i].visibleState.kind,'menu_complete');y[i].visibleState.kind=x[i].visibleState.kind;assert.deepEqual(x[i],y[i]);}
 bindings.push({clip,before:sha(a),after:sha(b),frames:x.length});
}
const out=root+'/hollow-cursor-target-evidence-v2.json';fs.writeFileSync(out,JSON.stringify({status:'only_kind_changes_with_hollow_cursors_other_outputs_identical_1056_regression_fixed',bindings,changes},null,2)+'\n',{flag:'wx'});console.log(JSON.stringify({changedFrames:changes.length,bindings,sha256:sha(out)}));
