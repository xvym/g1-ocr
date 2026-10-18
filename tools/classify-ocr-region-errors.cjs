'use strict';
const fs=require('fs'),path=require('path'),crypto=require('crypto');const [input,out]=process.argv.slice(2);
if(!input||!out||fs.existsSync(out))throw Error('Use OCR outputs and a new diagnostic directory');
const json=p=>JSON.parse(fs.readFileSync(p,'utf8')),rows=p=>fs.readFileSync(p,'utf8').trim().split(/\r?\n/).map(JSON.parse),hash=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
function distance(a,b){a=Array.from(a);b=Array.from(b);let row=Array.from({length:b.length+1},(_,i)=>i);for(let i=0;i<a.length;i++){let next=[i+1];for(let j=0;j<b.length;j++)next.push(Math.min(row[j]+(a[i]!==b[j]),row[j+1]+1,next[j]+1));row=next;}return row[b.length];}
const env=json('data/gt/frozen/ocr-all-scenes-v2-effective-r2/freeze.json'),score=json(input+'/comparison/report.json'),unique=new Map(),clips=[];
for(const clip of score.clips){const item=env.items.find(i=>i.id===clip.clipId),states=rows(item.payloads.find(p=>p.kind==='gt').approvedSource),actual=rows(input+'/actual/'+clip.clipId+'.jsonl'),time=rows(input+'/'+clip.clipId+'-timeline.jsonl');let metadata=0,content=0,ambiguous=0;
 for(let n=0;n<time.length;n++){const t=time[n];if(!t.scored)continue;const state=states.find(s=>t.sourceFrameIndex>=s.startFrame&&t.sourceFrameIndex<s.endFrameExclusive),a=actual[n],expected=state.regions,strict=new Map(a.rawRegions.map(r=>[[r.role,r.gridRect[1],r.gridRect[0],r.readingOrder].join(':'),r]));let formal=0,positionOnly=0;
  for(const e of expected){const k=[e.role,e.gridRect[1],e.gridRect[0],e.readingOrder].join(':'),r=strict.get(k);formal+=distance(e.text,r?.text??'');strict.delete(k);}for(const r of strict.values())formal+=Array.from(r.text).length;
  const fixed=new Map();for(const r of a.rawRegions){const k=JSON.stringify(r.gridRect);if(fixed.has(k))throw Error('Ambiguous diagnostic fixed-occupancy key');fixed.set(k,r);}
  for(const e of expected){const k=JSON.stringify(e.gridRect),r=fixed.get(k);positionOnly+=distance(e.text,r?.text??'');fixed.delete(k);}for(const r of fixed.values())positionOnly+=Array.from(r.text).length;
  const key=state.mediaId+':'+t.sourceFrameIndex,old=unique.get(key);unique.set(key,{formal:Math.max(old?.formal??0,formal),fixedOccupancy:Math.max(old?.fixedOccupancy??0,positionOnly)});
  metadata+=Math.max(0,formal-positionOnly);content+=positionOnly;if(positionOnly>formal)ambiguous++;
 }
 clips.push({clipId:clip.clipId,formalRawEditErrors:clip.raw.editErrors,diagnosticErrorReductionWhenRoleAndOrderAreIgnored:metadata,remainingFixedOccupancyDifferences:content,framesWhereDiagnosticIsWorse:ambiguous});
}
const all=[...unique.values()],formal=all.reduce((n,v)=>n+v.formal,0),fixed=all.reduce((n,v)=>n+v.fixedOccupancy,0);if(formal!==score.aggregate.raw.editErrors)throw Error('Formal edit count differs');
fs.mkdirSync(out,{recursive:true});fs.writeFileSync(path.join(out,'report.json'),JSON.stringify({status:'error_cause_diagnostic_not_a_score_or_acceptance',inputReport:{path:input+'/comparison/report.json',sha256:hash(input+'/comparison/report.json')},formalRawEditErrors:formal,diagnosticFixedOccupancyDifferences:fixed,diagnosticDifference:formal-fixed,clips,method:'Original formal score is unchanged. Diagnostic pairing uses exact gridRect only, never text similarity, and deliberately ignores role/read-order solely to locate causes. It does not establish correct fields, source coordinates, marks, states or recognition acceptance. No GT or actual is changed.'},null,2)+'\n',{flag:'wx'});console.log('Formal errors unchanged: '+formal+'. Fixed-occupancy diagnostic differences: '+fixed+'.');
