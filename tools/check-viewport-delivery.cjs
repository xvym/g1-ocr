'use strict';
const fs=require('fs'),path=require('path'),crypto=require('crypto'),cp=require('child_process');
const json=p=>JSON.parse(fs.readFileSync(p,'utf8')),hash=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const rows=p=>fs.readFileSync(p,'utf8').trim().split(/\r?\n/).map(JSON.parse),assert=(ok,why)=>{if(!ok)throw Error(why);};
const [input,out]=process.argv.slice(2);assert(input&&out&&!fs.existsSync(out),'Use input report and a new output directory');fs.mkdirSync(out,{recursive:true});
const save=(name,v)=>fs.writeFileSync(path.join(out,name),JSON.stringify(v,null,2)+'\n',{flag:'wx'});
const frozen='data/gt/frozen/ocr-all-scenes-v2-effective-r2',env=json(frozen+'/freeze.json'),batch=json(frozen+'/batch.json'),report=json(input+'/report.json');
assert(hash(frozen+'/freeze.json')===report.freezeSha256,'Freeze identity changed');
for(const ref of [env.baseFreeze,env.replacement.envelope,env.batchIdentity,env.overlapIntegrity])assert(hash(ref.path)===ref.sha256,'Approved collection changed');
for(const item of env.items)for(const p of item.payloads)assert(hash(p.approvedSource)===p.sha256,'Approved GT payload changed');
const media=rows('data/meta/m1-v1/media.jsonl');for(const m of media)assert(hash(m.path)===m.sha256&&hash('data/meta/m1-v1/'+m.id+'/frames.jsonl')===m.framesSha256,'Original media/ledger changed');
const unique=new Map(),selected=new Set(),groups=new Map(),samples=[],times=[];let refs=0,scored=0,geometry=0,exact=0,unlocated=0,black=0,rejectedBlack=0,maxError=0;
for(const item of batch.items){
 const clip=json(item.clip),layout=json(item.layout),gt=rows(item.candidate),actual=rows(input+'/'+item.id+'-actual.jsonl'),cmp=rows(input+'/'+item.id+'-comparison.jsonl'),timeline=rows(input+'/'+item.id+'-timeline.jsonl'),original=rows('reports/m2-effective-r2-replay-v1/'+item.id+'-timeline.jsonl');
 assert(actual.length===original.length&&actual.length===timeline.length&&cmp.length===original.length,'Retained frame count mismatch');
 const blackHash=crypto.createHash('sha256').update(Buffer.alloc(layout.sourceFrameSize[0]*layout.sourceFrameSize[1]*3)).digest('hex');
 const key=layout.sourceGameRect?JSON.stringify({origin:layout.sourceOrigin,rect:layout.sourceGameRect}):'non_target_no_grid_assertion';
 if(!groups.has(key))groups.set(key,{layout:key,scoredReferences:0,geometryReferences:0,exactReferences:0,unlocatedReferences:0,maxPixelBoundaryError:0});const g=groups.get(key);
 for(let i=0;i<actual.length;i++){
  const a=actual[i],t=timeline[i],c=cmp[i],old=original[i];refs++;times.push(a.processingNanos);
  for(const field of ['sourceFrameIndex','sourcePts','durationPts','timeBase','rgbSha256','scored','sourceOffsetNanos'])assert(t[field]===old[field],'Source timeline changed: '+item.id+':'+i+':'+field);
  assert(a.pts===t.sourcePts&&c.sourceFrameIndex===t.sourceFrameIndex&&c.rgbSha256===t.rgbSha256,'Actual/comparison association differs');
  selected.add(clip.mediaId+':'+t.sourceFrameIndex);if(!t.scored)continue;scored++;g.scoredReferences++;
  const state=gt.find(s=>t.sourceFrameIndex>=s.startFrame&&t.sourceFrameIndex<s.endFrameExclusive);assert(state,'Missing frozen state');
  let error=null;const located=a.status==='LOCATED',offset=state.renderOffsetLogicalPixels||[0,0];
  if(layout.sourceGameRect){
   geometry++;g.geometryReferences++;error=located?0:-1;
   if(located)for(let d=0;d<2;d++){
    const num=layout['sourceScale'+(layout.sourceScaleXNumerator?(d?'Y':'X'):'')+'Numerator'],den=layout['sourceScale'+(layout.sourceScaleXNumerator?(d?'Y':'X'):'')+'Denominator'];
    for(let p=0;p<=(d?144:160);p++)if(p+offset[d]>=0&&p+offset[d]<=(d?144:160)){
     const truth=layout.sourceOrigin[d]+(p+offset[d])*num/den,v=a[d?'originY':'originX']+p*a[d?'scaleY':'scaleX'];
     error=Math.max(error,Math.abs(Math.floor(v+1e-7)-Math.floor(truth+1e-7)),Math.abs(Math.ceil(v-1e-7)-Math.ceil(truth-1e-7)));
    }
   }
   assert(c.maxPixelBoundaryError===error&&c.exactGeometry===(error===0),'Independent geometry score differs');
   if(error===0){exact++;g.exactReferences++;}else if(error<0){unlocated++;g.unlocatedReferences++;}else{maxError=Math.max(maxError,error);g.maxPixelBoundaryError=Math.max(g.maxPixelBoundaryError,error);}
   const category=error<0?'no_observable_or_accepted_grid':offset.some(x=>x!==0)?'render_offset':error>0?'pixel_scale_or_origin':'exact';
   if(error!==0&&!samples.some(s=>s.category===category&&s.layout===key))samples.push({category,layout:key,clipId:item.id,frame:t.sourceFrameIndex,pts:t.sourcePts,rgbSha256:t.rgbSha256,actual:a,expected:{sourceOrigin:layout.sourceOrigin,sourceGameRect:layout.sourceGameRect,renderOffsetLogicalPixels:offset},maxPixelBoundaryError:error,framePath:c.framePath,previousFramePath:c.previousFramePath,nextFramePath:c.nextFramePath});
  }
  if(t.rgbSha256===blackHash){black++;if(a.status==='NO_SIGNAL')rejectedBlack++;}
  const sourceKey=clip.mediaId+':'+t.sourceFrameIndex,entry={hasGeometry:!!layout.sourceGameRect,error,status:a.status};
  if(unique.has(sourceKey))assert(JSON.stringify(unique.get(sourceKey))===JSON.stringify(entry),'Overlapping actual output differs');unique.set(sourceKey,entry);
 }
 const declared=report.clips.find(c=>c.clipId===item.id);assert(declared&&hash(input+'/'+item.id+'-actual.jsonl')===declared.actualSha256&&hash(item.candidate)===declared.gtSha256&&hash(item.layout)===declared.layoutSha256,'Report input/output hashes differ');
}
assert(refs===3577&&selected.size===3507&&scored===3368&&unique.size===3361,'Scope changed');
for(const [key,value] of Object.entries({retainedFrameReferences:refs,scoredFrameReferences:scored,uniqueScoredFrames:unique.size,frozenGeometryFrameReferences:geometry,exactGeometryFrameReferences:exact,unlocatedGeometryFrameReferences:unlocated,exactBlackFrames:black,exactBlackRejected:rejectedBlack}))assert(report[key]===value,'Summary differs: '+key);
times.sort((a,b)=>a-b);const percentile=p=>times[Math.min(times.length-1,Math.floor(times.length*p))];
const tests=[];for(const module of ['app','verification'])for(const name of fs.readdirSync(module+'/target/surefire-reports'))if(/^TEST-.*\.xml$/.test(name)){const xml=fs.readFileSync(module+'/target/surefire-reports/'+name,'utf8'),n=k=>Number(xml.match(new RegExp('\\b'+k+'="(\\d+)"'))[1]);tests.push({module,file:name,tests:n('tests'),failures:n('failures'),errors:n('errors'),skipped:n('skipped')});}
assert(tests.reduce((a,t)=>a+t.tests,0)===80&&tests.every(t=>!t.failures&&!t.errors&&!t.skipped),'Final test evidence is incomplete');
const jar='app/target/g1-ocr-app-0.1.0-SNAPSHOT.jar',entries=cp.execFileSync('jar',['tf',jar],{encoding:'utf8'}).trim().split(/\r?\n/);assert(entries.every(n=>!/(\.(png|1bpp|2bpp|gb|mp4|jsonl)$|data\/gt|verification\/|com\/google\/gson)/.test(n)),'Product contains excluded data');
const files=cp.execFileSync('rg',['--files','app/src','verification/src','tools','config','resources'],{encoding:'utf8'}).trim().split(/\r?\n/).concat(['pom.xml','app/pom.xml','verification/pom.xml','.gitattributes']).sort();
save('source-snapshot.json',{gitHead:cp.execFileSync('git',['rev-parse','HEAD'],{encoding:'utf8'}).trim(),files:files.map(p=>({path:p.replace(/\\/g,'/'),sha256:hash(p),bytes:fs.statSync(p).size}))});
const classes=cp.execFileSync('rg',['--files','--no-ignore','app/target/classes','verification/target/classes'],{encoding:'utf8'}).trim().split(/\r?\n/).filter(p=>p.endsWith('.class')).sort();
save('build-identity.json',{sourceSnapshotSha256:hash(out+'/source-snapshot.json'),classes:classes.map(p=>({path:p.replace(/\\/g,'/'),sha256:hash(p)})),productJar:{path:jar,sha256:hash(jar),excludedDataAbsent:true},tests});save('failure-examples.json',samples);
const values=[...unique.values()];save('report.json',{status:'outputs_and_evidence_audited_geometry_gate_'+(exact===geometry?'passed':'failed'),inputReport:{path:input+'/report.json',sha256:hash(input+'/report.json')},freezeSha256:hash(frozen+'/freeze.json'),locatorConfiguration:{colorTolerance:22,minimumScale:1,maximumScale:16,pixelLimit:4096*2160,paletteSeedsPerFrame:6,edgeDifferenceThreshold:12,minimumFourierConcentration:.12,minimumPaletteCoverage:.92,scaleSpanSearchStepPixels:.125},media,selectedFrameReferences:refs,uniqueRetainedFrames:selected.size,scoredFrameReferences:scored,uniqueScoredFrames:unique.size,geometryFrameReferences:geometry,exactGeometryFrameReferences:exact,uniqueGeometryFrames:values.filter(v=>v.hasGeometry).length,uniqueExactGeometryFrames:values.filter(v=>v.hasGeometry&&v.error===0).length,unlocatedGeometryFrameReferences:unlocated,maxPixelBoundaryError:maxError,exactBlackFrames:black,exactBlackRejected:rejectedBlack,groups:[...groups.values()],processingNanos:{p50:percentile(.5),p95:percentile(.95),p99:percentile(.99),maximum:times[times.length-1],meaning:'algorithm CPU/wall elapsed only, not source delivery or actual UI latency'},javaTests:tests.reduce((a,t)=>a+t.tests,0),sourceSnapshotSha256:hash(out+'/source-snapshot.json'),buildIdentitySha256:hash(out+'/build-identity.json'),independentAcceptance:'pending',textOcr:'not_implemented',productionChinese:'not_started'});
console.log(JSON.stringify({retained:refs,uniqueScored:unique.size,exact,geometry,unlocated,maxError,black,rejectedBlack,tests:tests.reduce((a,t)=>a+t.tests,0)}));
