'use strict';
const fs=require('fs'),crypto=require('crypto'),assert=require('assert/strict');
const json=p=>JSON.parse(fs.readFileSync(p)),rows=p=>fs.readFileSync(p,'utf8').trim().split(/\r?\n/).map(JSON.parse),sha=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const root='reports/m3-grid-refinement-v1',old=root+'/ocr-regression-all-v8',now=root+'/ocr-regression-all-v9',cmp=json(now+'/comparison/report.json');
const allowed={menu_label:'shop_menu_choice',money_label:'shop_money_label',money:'money_amount',shop_prompt:'dialogue_line'};
const changes=[],inputs=[];let frames=0;
for(const c of cmp.clips){
 const af=old+'/actual/'+c.clipId+'.jsonl',bf=now+'/actual/'+c.clipId+'.jsonl',a=rows(af),b=rows(bf);assert.equal(a.length,b.length);assert.equal(sha(bf),c.actualSha256);inputs.push({clip:c.clipId,oldSha256:sha(af),currentSha256:sha(bf)});
 for(let i=0;i<a.length;i++){
  const x=a[i],y=b[i],xx={...x},yy={...y};delete xx.rawRegions;delete yy.rawRegions;delete xx.processingNanos;delete yy.processingNanos;assert.deepEqual(xx,yy);assert.equal(x.rawRegions.length,y.rawRegions.length);
  for(const p of x.rawRegions){
   const q=y.rawRegions.find(r=>JSON.stringify(r.gridRect)===JSON.stringify(p.gridRect));assert(q,'Missing physical region');const restored={...q};
   if(JSON.stringify(p)!==JSON.stringify(q)){
    assert(['shop-sell-entry','shop-exit','shop-rejection'].includes(c.clipId));
    if(p.role!==q.role)assert.equal(q.role,allowed[p.role]);
    changes.push({clip:c.clipId,frame:y.sourceFrameIndex,before:{role:p.role,order:p.readingOrder},after:{role:q.role,order:q.readingOrder},text:q.text});
    restored.id=p.id;restored.role=p.role;restored.readingOrder=p.readingOrder;
   }
   assert.deepEqual(p,restored);
  }frames++;
 }
}
assert.equal(frames,3577);assert(changes.length>0);const independent=json(root+'/ocr-v9-observation-r11-text-analysis/report.json');assert.equal(independent.errors,cmp.aggregate.raw.editErrors);assert.equal(independent.characters,cmp.aggregate.raw.characters);
const out=process.argv[2];assert(out&&!fs.existsSync(out));fs.writeFileSync(out,JSON.stringify({status:'all_retained_frames_role_order_only_runtime_change_verified',frames,changes,inputs,comparisonSha256:sha(now+'/comparison/report.json'),raw:cmp.aggregate.raw,productAcceptance:false,limitations:'Compiled-class replay; independent sessions and UI remain pending. Raw glyphs, text, positions, marks, state, registration and timeline unchanged.'},null,2)+'\n',{flag:'wx'});console.log(JSON.stringify({path:out,sha256:sha(out),changedRegions:changes.length,changedFrames:new Set(changes.map(x=>x.clip+':'+x.frame)).size,raw:cmp.aggregate.raw}));
