'use strict';
// Compare manually read pixels to frozen annotations. Never reads tested OCR.
const fs=require('fs'),crypto=require('crypto'),assert=require('assert/strict');
const read=p=>JSON.parse(fs.readFileSync(p,'utf8')),rows=p=>fs.readFileSync(p,'utf8').trim().split(/\r?\n/).map(JSON.parse);
const sha=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex'),id=p=>({path:p,sha256:sha(p)}),save=(p,v)=>fs.writeFileSync(p,JSON.stringify(v,null,2)+'\n',{flag:'wx'});
const base='reports/m3-grid-refinement-v1/backpack-scroll-notes-v1',root='data/gt/frozen/ocr-all-scenes-v2-observation-r11',freeze=read(root+'/freeze.json');
assert.equal(sha(root+'/freeze.json'),'715b7297c1bd4327c6a8a50a93ddee7fef2410b8b25c4f2d4c2f4ac8a54199dd');
const notes=[1,2].map(n=>read(base+'/pass'+n+'.json'));
assert.equal(sha(base+'/pass1.json'),'8768763dde0a439de709cf237ba599bcabb6ebdf9737b4051359642ddeb8cf89');
assert.equal(sha(base+'/pass2.json'),'f7aa1df8f051264243f2f58e35fa3878fcf36fcb5e8d1f10586b9caa343993ea');
const correctedArrows=[[5205,5216],[5226,5236],[5241,5251],[5304,5326],[5338,5347]];
const originals=[5214,5215,5216,5235,5236,5237,5323,5326,5301,5349].map(n=>id('data/frames/video-04/backpack-scroll-pass2-v1/frame-'+String(n).padStart(6,'0')+'.png'));
save(base+'/notes-correction.json',{status:'manual_notes_corrected_after_full_original_png_recheck',notes:[1,2].map(n=>id(base+'/pass'+n+'.json')),originals,corrections:[{field:'downArrowVisibleRanges',before:notes[1].downArrowVisibleRanges,after:correctedArrows,reason:'Direct full PNG reread confirms 5215 and 5323 arrow present, 5236 absent. Original notes retained.'},{field:'quantity columns',before:[16,18],after:[15,17],reason:'Counting from base column6 across 8 item characters, blank column14, multiplication column15, blank16, digit17; full PNG directly reread. Text and original frozen width already correct.'}],exposure:'Frozen annotations already inspected during discrepancy resolution; no actual OCR read. Same-agent evidence, not independent acceptance.'});
function payloads(name){return freeze.items.find(x=>x.id===name).payloads.map(p=>{const file=p.file?root+'/'+p.file:p.approvedSource;assert.equal(sha(file),p.sha256);return {...id(file),kind:p.kind};});}
const p=payloads('backpack-navigation'),old=rows(p.find(x=>x.kind==='gt').path);
const project=r=>[150+r[0]*30,Math.floor(r[1]*272/9),150+r[2]*30,Math.ceil(r[3]*272/9)];
const differences=[],checks=[];
for(let f=5206;f<5358;f++){
 const s=old.find(s=>s.startFrame<=f&&s.endFrameExclusive>f),n=notes[1],obs=n.spans.find(x=>x[0]<=f&&x[1]>f);
 const wanted=[...obs[2],'とじる'],ex=n.markExceptions.find(x=>x.frame===f),wantedRegions=[];
 for(let k=0;k<wanted.length;k++){
  const text=wanted[k],isParent=k===wanted.length-1,col=isParent?14:6,row=isParent?14:4+2*k,marks=[];
  [...text].forEach((ch,j)=>{const decomp=ch.normalize('NFD');if(decomp.includes('\u3099')||decomp.includes('\u309a'))marks.push({gridRow:row-1,gridColumn:col+j,kind:decomp.includes('\u309a')?'handakuten':'dakuten'});});
  const gridRect=[col,row,col+[...text].length,row+1],sourceRect=project([col,marks.length?row-1:row,gridRect[2],row+1]);
  wantedRegions.push({text,gridRect,sourceRect,markCells:marks,role:isParent?'parent_menu_visible_choice':'backpack_item_or_cancel',readingOrder:k});
 }
 const detached=[{kind:'dakuten',gridRect:[14,1,15,2],sourceRect:project([14,1,15,2])}];
 if(ex)for(const [kind,col] of ex.detached)detached.push({kind,gridRect:[col,5,col+1,6],sourceRect:project([col,5,col+1,6])});
 const cursorRows=n.cursorSpans.find(x=>x[0]<=f&&x[1]>f)[2];
 const cursors=cursorRows.map(row=>({style:'filled_right',gridRect:[5,row,6,row+1],sourceRect:project([5,row,6,row+1])}));
 const arrow=correctedArrows.some(x=>x[0]<=f&&x[1]>f),cursor={menuCursors:cursors,downArrowVisible:arrow};
 if(arrow)cursor.downArrow={style:'filled_down',gridRect:[18,11,19,12],sourceRect:project([18,11,19,12])};
 const kind=ex||cursorRows.length!==1?'menu_redraw_or_cursor_transition':'menu_complete';
 const actual={regions:s.regions.map(r=>({text:r.text,gridRect:r.gridRect,sourceRect:r.sourceRect,markCells:r.markCells,role:r.role,readingOrder:r.readingOrder})),cursor:s.cursor,detachedMarks:s.detachedMarks.map(m=>({kind:m.kind,gridRect:m.gridRect,sourceRect:m.sourceRect})),kind:s.kind,completePage:s.completePage};
 const observed={regions:wantedRegions,cursor,detachedMarks:detached,kind,completePage:kind==='menu_complete'};
 const fields=[];for(const key of Object.keys(actual)){try{assert.deepEqual(actual[key],observed[key]);}catch(e){fields.push(key);}}
 if(fields.length)differences.push({frame:f,fields,before:actual,observed});
 checks.push({frame:f,stateId:s.stateId,fieldsDiffering:fields});
}
const op=payloads('backpack-opening'),opening=rows(op.find(x=>x.kind==='gt').path);
for(const f of [5206,5207]){const a=old.find(s=>s.startFrame<=f&&s.endFrameExclusive>f),b=opening.find(s=>s.startFrame<=f&&s.endFrameExclusive>f);for(const k of ['regions','cursor','detachedMarks','kind','completePage'])assert.deepEqual(a[k],b[k]);}
save(base+'/frozen-comparison.json',{status:'full_scored_scope_observation_comparison_not_new_freeze',freeze:id(root+'/freeze.json'),notes:[1,2].map(n=>id(base+'/pass'+n+'.json')),correction:id(base+'/notes-correction.json'),payloads:p,openingPayloads:op,scoredFrames:152,states:old.length,overlapChecked:[5206,5207],checks,differences,limitations:'Comparing manually observed geometry and content, not granting runtime or independent acceptance. No tested OCR input.'});
console.log(JSON.stringify({frames:152,states:old.length,differences:differences.map(x=>({frame:x.frame,fields:x.fields})),report:id(base+'/frozen-comparison.json')}));
