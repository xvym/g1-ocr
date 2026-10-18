'use strict';
const fs=require('fs'),path=require('path'),crypto=require('crypto'),assert=require('assert/strict');
const [out]=process.argv.slice(2);assert(out&&!fs.existsSync(out),'Use a new audit directory');
const protocolPath='tools/coordinate-contract-audit-protocol.json',json=p=>JSON.parse(fs.readFileSync(p,'utf8'));
const rows=p=>fs.readFileSync(p,'utf8').trim().split(/\r?\n/).filter(Boolean).map(JSON.parse);
function sha(p){const h=crypto.createHash('sha256'),fd=fs.openSync(p,'r'),b=Buffer.alloc(1048576);try{let n;while((n=fs.readSync(fd,b,0,b.length,null)))h.update(b.subarray(0,n));}finally{fs.closeSync(fd);}return h.digest('hex');}
const inputs=new Map();function bind(p,h){p=p.replaceAll('\\','/');const actual=sha(p);if(h)assert.equal(actual,h,p);inputs.set(p,actual);return actual;}
const protocol=json(protocolPath),{frozen,run,attribution,productEvidence}=protocol;
bind(protocolPath);bind(__filename);for(const p of ['spec.md','.agent/PLANS.md','verification/src/main/java/io/github/g1ocr/verification/OcrScoring.java','verification/src/main/java/io/github/g1ocr/verification/ComparisonAggregate.java','verification/src/main/java/io/github/g1ocr/verification/GtGeometry.java','verification/src/main/java/io/github/g1ocr/verification/GridProjection.java','app/src/main/java/io/github/g1ocr/ocr/JapaneseOcrEngine.java'])bind(p);
const planAtStart={path:'.agent/EXECPLAN.md',sha256:sha('.agent/EXECPLAN.md')};
const production=json(productEvidence+'/report.json');bind(productEvidence+'/report.json');
for(const f of production.identities)bind(f.path,f.sha256);
for(const f of json(productEvidence+'/manifest.json').files)bind(productEvidence+'/'+f.file,f.sha256);
if(production.presentationCorrection){bind(production.presentationCorrection.helper.path,production.presentationCorrection.helper.sha256);bind(production.presentationCorrection.sourceReport.path,production.presentationCorrection.sourceReport.sha256);}
const freeze=json(frozen+'/freeze.json'),scoreReport=json(run+'/comparison/report.json');bind(frozen+'/freeze.json',scoreReport.freezeSha256);bind(run+'/comparison/report.json');
const originalPages=rows(attribution+'/pages.jsonl'),oldFrames=rows(attribution+'/frames.jsonl'),oldFrameMap=new Map(oldFrames.map(f=>[f.clip+':'+f.frame,f]));
for(const f of json(attribution+'/manifest.json').files)bind(attribution+'/'+f.file,f.sha256);bind(attribution+'/manifest.json');
const eq=(a,b)=>{try{assert.deepEqual(a,b);return true;}catch{return false;}};
const pick=(o,ks)=>Object.fromEntries(ks.filter(k=>o&&o[k]!=null).map(k=>[k,o[k]]));
const strip=v=>Array.isArray(v)?v.map(strip):v&&typeof v==='object'?Object.fromEntries(Object.entries(v).filter(([k])=>k!=='sourceRect').map(([k,v])=>[k,strip(v)])):v;
function decimal(n){if(!Number.isInteger(n)||!n||n%10)return String(n);const digits=String(Math.abs(n)),s=digits.replace(/0+$/,'');return(n<0?'-':'')+s[0]+(s.length>1?'.'+s.slice(1):'')+'E+'+(digits.length-1);}
const key=r=>[r.role,decimal(r.gridRect[1]),decimal(r.gridRect[0]),r.readingOrder].join(':');
function edit(a,b){a=Array.from(a);b=Array.from(b);const d=Array.from({length:a.length+1},()=>new Int32Array(b.length+1));for(let i=0;i<=a.length;i++)d[i][0]=i;for(let j=0;j<=b.length;j++)d[0][j]=j;for(let i=1;i<=a.length;i++)for(let j=1;j<=b.length;j++)d[i][j]=Math.min(d[i-1][j-1]+(a[i-1]!==b[j-1]),d[i-1][j]+1,d[i][j-1]+1);let i=a.length,j=b.length;const e={substitutions:0,deletions:0,insertions:0};while(i||j){if(i&&j&&d[i][j]===d[i-1][j-1]+(a[i-1]!==b[j-1])){if(a[i-1]!==b[j-1])e.substitutions++;i--;j--;}else if(i&&d[i][j]===d[i-1][j]+1){e.deletions++;i--;}else{e.insertions++;j--;}}return e;}
const errors=e=>e.substitutions+e.deletions+e.insertions;
function syms(v){return(v==null?[]:Array.isArray(v)?v:[v]).map(s=>{const x=pick(s,['kind','style','gridRect','sourceRect']);if(x.style==='outlined_right')x.style='outline_right';return x;});}
function symbols(s){const c=s?.cursor||{},x={menuCursors:syms(c.menuCursors),downArrowVisible:c.downArrowVisible===true};if(x.downArrowVisible)x.downArrow=pick(c.downArrow||c,['style','gridRect','sourceRect']);x.additionalDownArrows=syms(c.additionalDownArrows);return{cursor:x,detachedMarks:syms(s?.detachedMarks)};}
function analyse(s,rs,v){
  const score={characters:0,substitutions:0,deletions:0,insertions:0,detachedExpected:0,detachedActual:0,exact:true,regionGeometry:true,symbols:true,state:true,differences:[]};
  const actual=new Map(rs.map(r=>[key(r),r]));assert.equal(actual.size,rs.length);const content=[],sourceChanges=[],unitChanges=[];
  let logicalRegions=true;const fail=x=>{score.exact=false;score.differences.push(x);};
  for(const e of s.regions){const k=key(e),a=actual.get(k);actual.delete(k);score.characters+=Array.from(e.text).length;const ed=edit(e.text,a?.text||'');for(const x of ['substitutions','deletions','insertions'])score[x]+=ed[x];
    if(errors(ed)){fail('text:'+k);content.push({type:!a?'missing_field':e.text.replaceAll('？','?')===a.text.replaceAll('？','?')?'question_encoding':'literal_error',key:k,expected:e.text,actual:a?.text||'',edits:ed});}
    const ks=['gridRect','sourceRect','markCells','logicalRect','renderUnits'];if(!a||!eq(pick(e,ks),pick(a,ks))){score.regionGeometry=false;fail('geometry_or_missing_field:'+k);}
    if(!a||!eq(strip(pick(e,ks)),strip(pick(a,ks))))logicalRegions=false;
    if(a)for(const prop of ks)if(!eq(pick(e,[prop]),pick(a,[prop]))){const z={key:k,property:prop,expected:e[prop]??null,actual:a[prop]??null};if(prop==='sourceRect')sourceChanges.push(z);else {content.push({type:prop==='markCells'?'visible_mark_mismatch':prop==='renderUnits'?'render_unit_mismatch':'logical_occupancy_mismatch',...z});if(prop==='renderUnits')unitChanges.push(z);}}
  }
  for(const[k,a]of actual){score.insertions+=Array.from(a.text).length;score.regionGeometry=false;logicalRegions=false;fail('extra_region:'+k);content.push({type:'extra_field',key:k,actual:a.text});}
  const es=symbols(s),as=symbols(v);score.detachedExpected=es.detachedMarks.length;score.detachedActual=as.detachedMarks.length;score.symbols=eq(es,as);
  const logicalSymbols=eq(strip(es),strip(as));if(!score.symbols){fail('cursor_or_detached_mark');if(logicalSymbols)sourceChanges.push({key:'symbols',property:'symbols_sourceRect',expected:es,actual:as});else content.push({type:'symbol_structure_mismatch',key:'symbols',expected:es,actual:as});}
  score.state=s.kind===v.kind;if(!score.state)fail('visible_state_kind');
  return{score,logicalDiagnostic:{textExact:errors(score)===0,logicalRegions,logicalSymbols,state:score.state,exact:errors(score)===0&&logicalRegions&&logicalSymbols&&score.state},content,sourceChanges,unitChanges};
}
function projection(layout,rect,offset=[0,0]){const sx=(layout.sourceScaleXNumerator??layout.sourceScaleNumerator)/(layout.sourceScaleXDenominator??layout.sourceScaleDenominator),sy=(layout.sourceScaleYNumerator??layout.sourceScaleNumerator)/(layout.sourceScaleYDenominator??layout.sourceScaleDenominator);return rect.map((v,i)=>{const z=layout.sourceOrigin[i%2]+(v+offset[i%2])*(i%2?sy:sx);return i<2?Math.floor(z+1e-7):Math.ceil(z-1e-7);});}
function logicalEnvelope(r){if(r.logicalRect)return r.logicalRect;const b=r.gridRect.map(v=>v*8);for(const m of r.markCells||[])b[1]=Math.min(b[1],m.gridRow*8);return b;}
function currentProjection(grid,r){return logicalEnvelope(r).map((v,i)=>{const z=(i%2?grid.originY:grid.originX)+v*(i%2?grid.scaleY:grid.scaleX);return i<2?Math.floor(z+1e-7):Math.ceil(z-1e-7);});}
const pixelGroups=new Map(),frameRows=[],contracts=new Map();let retained=0,sourceProjectionChecks=0,currentProjectionMismatches=0;
const jobId=(clip,frame,k)=>clip+':'+frame+':'+k;
for(const item of freeze.items){
  const payload=k=>{const p=item.payloads.find(p=>p.kind===k),f=p.file?path.join(frozen,p.file):p.approvedSource;bind(f,p.sha256);return{file:f,value:k==='gt'?rows(f):json(f)};};
  const gt=payload('gt'),clip=payload('clip').value,layout=payload('layout').value,contract=payload('contract');contracts.set(sha(contract.file),{path:contract.file,sha256:sha(contract.file),value:contract.value});
  const actual=rows(run+'/actual/'+item.id+'.jsonl'),timeline=rows(run+'/'+item.id+'-timeline.jsonl'),diff=rows(run+'/comparison/'+item.id+'-frames.jsonl');
  bind(run+'/actual/'+item.id+'.jsonl',scoreReport.clips.find(c=>c.clipId===item.id).actualSha256);bind(run+'/'+item.id+'-timeline.jsonl');bind(run+'/comparison/'+item.id+'-frames.jsonl');retained+=actual.length;assert.equal(actual.length,timeline.length);
  const states=new Map();for(const s of gt.value)for(let f=s.startFrame;f<s.endFrameExclusive;f++)states.set(f,s);const diffs=new Map(diff.map(d=>[d.sourceFrameIndex,d]));
  for(let i=0;i<actual.length;i++){const a=actual[i],t=timeline[i];assert.equal(a.sourceFrameIndex,t.sourceFrameIndex);assert.equal(a.sourcePts,t.sourcePts);if(!t.scored)continue;
    const frame=a.sourceFrameIndex,s=states.get(frame),d=diffs.get(frame),old=oldFrameMap.get(item.id+':'+frame);assert(s&&d&&old);
    const raw=analyse(s,a.rawRegions,a.visibleState),fin=analyse(s,a.correctedRegions||a.rawRegions,a.visibleState);assert.deepEqual(raw.score,d.raw);assert.deepEqual(fin.score,d.final);assert.deepEqual(raw.score,old.raw.score);assert.deepEqual(fin.score,old.final.score);
    const sourceKey=clip.mediaId+':'+frame;let group=pixelGroups.get(sourceKey);
    if(!group){group={sourceKey,media:clip.mediaId,frame,image:old.originalFrame,rgbSha256:t.rgbSha256,pngSha256:bind(old.originalFrame),targets:[]};pixelGroups.set(sourceKey,group);}
    assert.equal(group.rgbSha256,t.rgbSha256);
    const projectionIssues=[],targetIds=[],amap=new Map(a.rawRegions.map(r=>[key(r),r]));
    for(const e of s.regions){const k=key(e),ar=amap.get(k);const expected=projection(layout,logicalEnvelope(e),s.renderOffsetLogicalPixels||[0,0]);assert.deepEqual(expected,e.sourceRect,'Frozen projection mismatch '+sourceKey+' '+k);sourceProjectionChecks++;
      if(!ar)continue;const predicted=currentProjection(a.geometry.grid,ar);if(!eq(predicted,ar.sourceRect)){currentProjectionMismatches++;projectionIssues.push({key:k,predicted,actual:ar.sourceRect});}
      const supportRects=e.logicalRect?[e.sourceRect]:[projection(layout,e.gridRect.map(v=>v*8),s.renderOffsetLogicalPixels||[0,0]),...(e.markCells||[]).map(m=>projection(layout,[m.gridColumn*8,m.gridRow*8,(m.gridColumn+1)*8,(m.gridRow+1)*8],s.renderOffsetLogicalPixels||[0,0]))];
      const target={id:jobId(item.id,frame,k),key:k,kind:'region',expected:e.sourceRect,actual:ar.sourceRect,supportRects,logicalRect:logicalEnvelope(e),text:e.text,actualText:ar.text,marksEqual:eq(e.markCells,ar.markCells),sourceChanged:!eq(e.sourceRect,ar.sourceRect)};
      group.targets.push(target);targetIds.push(target.id);
    }
    const es=symbols(s),as=symbols(a.visibleState);const addSymbols=(ee,aa,prefix)=>{ee.forEach((e,j)=>{if(!e.gridRect||!e.sourceRect)return;assert.deepEqual(projection(layout,e.gridRect.map(v=>v*8),s.renderOffsetLogicalPixels||[0,0]),e.sourceRect);sourceProjectionChecks++;const ar=aa[j];if(!ar||!eq(strip(e),strip(ar)))return;const k=prefix+'/'+j,target={id:jobId(item.id,frame,k),key:k,kind:'symbol',expected:e.sourceRect,actual:ar.sourceRect,supportRects:[e.sourceRect],logicalRect:e.gridRect.map(v=>v*8),text:e.kind||e.style,actualText:ar.kind||ar.style,marksEqual:true,sourceChanged:!eq(e.sourceRect,ar.sourceRect)};group.targets.push(target);targetIds.push(target.id);});};
    addSymbols(es.cursor.menuCursors,as.cursor.menuCursors,'menuCursors');addSymbols(es.cursor.downArrow?[es.cursor.downArrow]:[],as.cursor.downArrow?[as.cursor.downArrow]:[],'downArrow');addSymbols(es.cursor.additionalDownArrows,as.cursor.additionalDownArrows,'additionalDownArrows');addSymbols(es.detachedMarks,as.detachedMarks,'detachedMarks');
    frameRows.push({clip:item.id,media:clip.mediaId,frame,pts:a.sourcePts,timeBase:a.timeBase,durationPts:t.durationPts,pageId:old.pageId,expectedKind:s.kind,actualKind:a.visibleState.kind,originalFrame:old.originalFrame,raw,final:fin,targetIds,projectionIssues,outputReadiness:a.outputReadiness});
  }
}
assert.equal(retained,3577);assert.equal(frameRows.length,3368);assert.equal(pixelGroups.size,3361);assert.equal(originalPages.length,116);
const spec=fs.readFileSync('spec.md','utf8').split(/\r?\n/),scorer=fs.readFileSync('verification/src/main/java/io/github/g1ocr/verification/OcrScoring.java','utf8').split(/\r?\n/);
const contractEvidence={spec:spec.map((text,i)=>({line:i+1,text})).filter(x=>/像素边框差异另报|位置正确以|帧内容正确性与实时呈现分开|所有中间帧仍保留|整页正确率按/.test(x.text)),frozenContracts:[...contracts.values()],scorer:scorer.map((text,i)=>({line:i+1,text})).filter(x=>/fields\(e,"gridRect"|result.symbols=|visible_state_kind/.test(x.text)),interpretation:'Source border offsets are to be reported separately, while logical occupancy, role, marks, unit geometry, symbols, state, and true clipping remain required. Frozen v5 also requires phase projection checks; deleting sourceRect everywhere would be too broad. Audit-only sourceRect removal is a diagnostic, not a replacement acceptance score.'};
for(const[p,h]of inputs)assert.equal(sha(p),h,'Input changed '+p);
fs.mkdirSync(out,{recursive:true});const write=(f,v)=>fs.writeFileSync(out+'/'+f,v,{flag:'wx'});
write('frames.jsonl',frameRows.map(JSON.stringify).join('\n')+'\n');write('pixel-jobs.jsonl',[...pixelGroups.values()].map(JSON.stringify).join('\n')+'\n');write('pages-input.jsonl',originalPages.map(JSON.stringify).join('\n')+'\n');
const preparation={schemaVersion:1,status:'prepared_read_only_coordinate_audit_not_acceptance',protocol,planAtStart,sourceProjectionChecks,currentProjectionMismatches,retainedReferences:retained,scoredReferences:frameRows.length,uniqueScoredFrames:pixelGroups.size,originalAggregate:scoreReport.aggregate,contractEvidence,inputs:[...inputs].map(([path,sha256])=>({path,sha256}))};
write('preparation.json',JSON.stringify(preparation,null,2)+'\n');write('preparation-manifest.json',JSON.stringify({files:['preparation.json','frames.jsonl','pixel-jobs.jsonl','pages-input.jsonl'].map(file=>({file,sha256:sha(out+'/'+file),bytes:fs.statSync(out+'/'+file).size}))},null,2)+'\n');
console.log(JSON.stringify({out,pages:originalPages.length,frames:frameRows.length,unique:pixelGroups.size,sourceProjectionChecks,currentProjectionMismatches,inputs:inputs.size}));
