'use strict';
const fs=require('fs'),crypto=require('crypto'),assert=require('assert/strict');
const read=p=>JSON.parse(fs.readFileSync(p,'utf8')),sha=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex'),id=p=>({path:p,sha256:sha(p)}),save=(p,v)=>fs.writeFileSync(p,JSON.stringify(v,null,2)+'\n',{flag:'wx'});
const base='reports/m3-grid-refinement-v1/nurse-mark-notes-v1',notes=[1,2].map(n=>read(base+'/pass'+n+'.json'));
for(const k of ['range','spans','downArrow','menu','boxAppears'])assert.deepEqual(notes[0][k],notes[1][k]);
const sheets=notes.map(n=>read(n.sheets+'/report.json')),v=sheets.map(s=>read(s.inputVerification));assert.deepEqual(v[0],v[1]);assert.equal(v[0].frames.length,102);assert(v[0].allRgbAndPngReadbackHashesMatch);
const pngs=[];for(let f=1823;f<1925;f++){const paths=sheets.map(s=>s.inputVerification.replace(/[\\/]verification.json$/,'')+'/frame-'+String(f).padStart(6,'0')+'.png');assert.equal(sha(paths[0]),sha(paths[1]));pngs.push({frame:f,sha256:sha(paths[1])});}
for(const s of sheets){assert(s.allDisplayedPixelsMatchOriginal);for(const p of s.sheets)assert.equal(sha(p.path),p.sha256);}
save(base+'/read-integrity.json',{status:'two_direct_reads_and_original_identity_verified_not_independent_acceptance',notes:[1,2].map(n=>id(base+'/pass'+n+'.json')),verification:sheets.map(s=>id(s.inputVerification)),frames:102,sheets:34,observationsAgree:true,pngs,limitations:'Same agent with prior diagnostic exposure. Not human per-frame verification, fully blind reading, or independent acceptance.'});
const root='data/gt/frozen/ocr-all-scenes-v2-observation-r12',freeze=read(root+'/freeze.json');assert.equal(sha(root+'/freeze.json'),'3949812dd750e62fb8bd562465f0743b928c48310d73919f520db0cce8a7ea8a');
const payloads=freeze.items.find(x=>x.id==='nurse-greeting').payloads.map(p=>{const file=p.file?root+'/'+p.file:p.approvedSource;assert.equal(sha(file),p.sha256);return {...id(file),kind:p.kind};});
const old=fs.readFileSync(payloads.find(p=>p.kind==='gt').path,'utf8').trim().split(/\r?\n/).map(JSON.parse),n=notes[1],project=r=>[180+30*r[0],Math.floor(r[1]*272/9),180+30*r[2],Math.ceil(r[3]*272/9)];
const checks=[],differences=[];
for(let f=1825;f<1923;f++){
 const s=old.find(s=>s.startFrame<=f&&f<s.endFrameExclusive);assert(s);const span=n.spans.find(x=>x[0]<=f&&f<x[1]);assert(span);
 const menu=f>=1886&&f<1921,lines=[...(menu?n.menu.lines:[]),...span[2]],regions=lines.map((text,k)=>{const isMenu=menu&&k<2,col=isMenu?15:1,row=isMenu?8+k*2:14+(k-(menu?2:0))*2,markCells=[];[...text].forEach((ch,j)=>{const d=ch.normalize('NFD');if(d.includes('\u3099')||d.includes('\u309a'))markCells.push({kind:d.includes('\u309a')?'handakuten':'dakuten',gridColumn:col+j,gridRow:row-1});});const gridRect=[col,row,col+[...text].length,row+1];return {text,role:isMenu?'menu_option':'dialogue',readingOrder:k,gridRect,sourceRect:project([col,markCells.length?row-1:row,gridRect[2],row+1]),markCells};});
 const cursor={menuCursors:menu?[{style:'filled_right',gridRect:[14,8,15,9],sourceRect:project([14,8,15,9])}]:[],downArrowVisible:n.downArrow.some(x=>x[0]<=f&&f<x[1])};if(cursor.downArrowVisible)cursor.downArrow={style:'filled_down',gridRect:[18,16,19,17],sourceRect:project([18,16,19,17])};
 const before={regions:s.regions.map(r=>Object.fromEntries(['text','role','readingOrder','gridRect','sourceRect','markCells'].map(k=>[k,r[k]]))),cursor:s.cursor},observed={regions,cursor},fields=[];for(const k of Object.keys(before)){try{assert.deepEqual(before[k],observed[k]);}catch{fields.push(k);}}
 if(fields.length)differences.push({frame:f,fields,before,observed});checks.push({frame:f,stateId:s.stateId,fieldsDiffering:fields,recordedKind:s.kind,recordedCompletePage:s.completePage});
}
save(base+'/frozen-comparison.json',{status:'full_98_frame_content_geometry_cursor_comparison_not_new_freeze',freeze:id(root+'/freeze.json'),integrity:id(base+'/read-integrity.json'),payloads,scoredFrames:98,checks,differences,stateReviewPending:[1920,1921],limitations:'Mark disappearance changes upper text/mark bounds at1920/1921. Re-evaluate transition kind and page-opportunity semantics before versioned correction. No OCR output read. No independent acceptance.'});
console.log(JSON.stringify({integrity:id(base+'/read-integrity.json'),comparison:id(base+'/frozen-comparison.json'),differences:differences.map(x=>x.frame)}));
