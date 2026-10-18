'use strict';
const fs=require('fs'),path=require('path'),crypto=require('crypto');
const [input,out,frozenArgument,comparisonArgument]=process.argv.slice(2);if(!input||!out||fs.existsSync(out))throw Error('Use an OCR report and a new diagnostic directory; optional frozen directory and comparison report');
const json=p=>JSON.parse(fs.readFileSync(p,'utf8')),rows=p=>fs.readFileSync(p,'utf8').trim().split(/\r?\n/).map(JSON.parse);
const hash=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const key=r=>[r.role,r.gridRect[1],r.gridRect[0],r.readingOrder].join(':');
function distance(a,b){a=Array.from(a);b=Array.from(b);let prev=Array.from({length:b.length+1},(_,i)=>i);for(let i=0;i<a.length;i++){let next=[i+1];for(let j=0;j<b.length;j++)next.push(Math.min(prev[j]+(a[i]!==b[j]),prev[j+1]+1,next[j]+1));prev=next;}return prev[b.length];}
const frozen=frozenArgument||'data/gt/frozen/ocr-all-scenes-v2-effective-r2',comparisonPath=comparisonArgument||input+'/comparison/report.json',env=json(frozen+'/freeze.json'),scored=json(comparisonPath);
if(scored.freezeSha256!==hash(frozen+'/freeze.json'))throw Error('Frozen input differs');
fs.mkdirSync(out,{recursive:true});const clips=[],unique=new Map(),frequency=new Map();let count=0;
for(const clip of scored.clips){const item=env.items.find(i=>i.id===clip.clipId),payload=item.payloads.find(p=>p.kind==='gt'),gtPath=payload.file?path.join(frozen,payload.file):payload.approvedSource;
 if(hash(gtPath)!==payload.sha256||hash(input+'/actual/'+clip.clipId+'.jsonl')!==clip.actualSha256)throw Error('GT or actual identity differs');
 const gt=rows(gtPath),actual=rows(input+'/actual/'+clip.clipId+'.jsonl'),timeline=rows(input+'/'+clip.clipId+'-timeline.jsonl');const failures=[];let characters=0,errors=0;
 for(let i=0;i<timeline.length;i++){let t=timeline[i],a=actual[i];if(a.sourceFrameIndex!==t.sourceFrameIndex||a.sourcePts!==t.sourcePts)throw Error('Frame association differs');if(!t.scored)continue;const s=gt.find(s=>t.sourceFrameIndex>=s.startFrame&&t.sourceFrameIndex<s.endFrameExclusive),map=new Map(a.rawRegions.map(r=>[key(r),r]));let n=0,e=0,d=[];
  for(const expected of s.regions){const k=key(expected),observed=map.get(k),error=distance(expected.text,observed?observed.text:'');map.delete(k);n+=Array.from(expected.text).length;e+=error;if(error)d.push({kind:observed?'text':'missing_region',key:k,expected:expected.text,actual:observed?.text??'',errors:error});}
  for(const [k,r]of map){const error=Array.from(r.text).length;e+=error;if(error)d.push({kind:'extra_region',key:k,expected:'',actual:r.text,errors:error});}
  characters+=n;errors+=e;for(const d0 of d){let fk=[d0.kind,d0.key,d0.expected,d0.actual].join('|');frequency.set(fk,(frequency.get(fk)||0)+1);}
  if(e)failures.push({sourceFrameIndex:t.sourceFrameIndex,sourcePts:t.sourcePts,stateId:s.stateId,expectedCharacters:n,errors:e,differences:d});
  const k=s.mediaId+':'+t.sourceFrameIndex,old=unique.get(k);if(old&&old.characters!==n)throw Error('Overlapping expected character count differs');unique.set(k,{characters:n,errors:Math.max(e,old?.errors||0)});count++;
 }
 if(errors!==clip.raw.editErrors||characters!==clip.raw.characters)throw Error('Independent edit count differs: '+clip.clipId);
 fs.writeFileSync(path.join(out,clip.clipId+'.json'),JSON.stringify(failures,null,2)+'\n');clips.push({clipId:clip.clipId,characters,errors,framesWithTextErrors:failures.length});
}
const values=[...unique.values()],characters=values.reduce((s,r)=>s+r.characters,0),errors=values.reduce((s,r)=>s+r.errors,0);
if(characters!==scored.aggregate.raw.characters||errors!==scored.aggregate.raw.editErrors)throw Error('Independent aggregate differs');
fs.writeFileSync(path.join(out,'report.json'),JSON.stringify({status:'independent_raw_edit_counts_agree_not_OCR_acceptance',inputReport:{path:comparisonPath,sha256:hash(comparisonPath)},freezeSha256:hash(frozen+'/freeze.json'),semanticIntegrity:env.allSemanticIntegrityPassed===false?'qualified_pending':'not_asserted_by_this_tool',frameReferences:count,uniqueSourceFrames:unique.size,characters,errors,frameCharacterAccuracy:characters?Math.max(0,1-errors/characters):null,clips,frequentDifferences:[...frequency].sort((a,b)=>b[1]-a[1]).map(([difference,frames])=>({difference,frames})),scope:'Independent source-frame association and Unicode scalar edit counts; strict region keys unchanged. Geometry, state, page/UI and independent-session gates remain separate.'},null,2)+'\n');
console.log('Independent raw edit counts agree: '+errors+' errors / '+characters+' characters.');
