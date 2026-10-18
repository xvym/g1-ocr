'use strict';
const fs=require('fs'),path=require('path'),crypto=require('crypto'),cp=require('child_process');
const [input,implementation,out]=process.argv.slice(2);
if(!input||!implementation||!out||fs.existsSync(out))throw Error('Use OCR outputs, their implementation identity and a new evidence directory');
const json=p=>JSON.parse(fs.readFileSync(p,'utf8')),rows=p=>fs.readFileSync(p,'utf8').trim().split(/\r?\n/).map(JSON.parse);
const assert=(yes,message)=>{if(!yes)throw Error(message);};
const hash=p=>new Promise((resolve,reject)=>{const h=crypto.createHash('sha256'),s=fs.createReadStream(p);s.on('data',b=>h.update(b));s.on('error',reject);s.on('end',()=>resolve(h.digest('hex')));});
async function main(){
 const frozen='data/gt/frozen/ocr-all-scenes-v2-effective-r2',env=json(frozen+'/freeze.json'),run=json(input+'/report.json'),score=json(input+'/comparison/report.json');
 assert(run.freezeSha256===await hash(frozen+'/freeze.json')&&run.comparisonSha256===await hash(input+'/comparison/report.json'),'Replay/freeze/comparison identity changed');
 for(const ref of [env.baseFreeze,env.replacement.envelope,env.batchIdentity,env.overlapIntegrity])assert(await hash(ref.path)===ref.sha256,'Approved collection reference changed');
 for(const item of env.items){assert(await hash(item.sourceFreeze.path)===item.sourceFreeze.sha256,'Source freeze changed');for(const p of item.payloads)assert(await hash(p.approvedSource)===p.sha256,'Approved payload changed');}
 const media=rows('data/meta/m1-v1/media.jsonl');for(const m of media)assert(await hash(m.path)===m.sha256&&await hash('data/meta/m1-v1/'+m.id+'/frames.jsonl')===m.framesSha256,'Original media/ledger changed');
 const selected=new Set(),scored=new Set(),times=[];let references=0,scoredReferences=0;
 for(const clip of score.clips){const item=env.items.find(i=>i.id===clip.clipId),clipMeta=json(item.payloads.find(p=>p.kind==='clip').approvedSource),timeline=rows(input+'/'+clip.clipId+'-timeline.jsonl'),prior=rows('reports/m2-effective-r2-replay-v1/'+clip.clipId+'-timeline.jsonl'),actual=rows(input+'/actual/'+clip.clipId+'.jsonl');
  assert(timeline.length===prior.length&&actual.length===timeline.length,'Retained frame count changed');
  assert(await hash(input+'/actual/'+clip.clipId+'.jsonl')===clip.actualSha256,'Actual output changed after scoring');
  for(let i=0;i<timeline.length;i++){const t=timeline[i],a=actual[i];for(const k of ['sourceFrameIndex','sourcePts','durationPts','timeBase','rgbSha256','scored','sourceOffsetNanos'])assert(t[k]===prior[i][k],'Original frame/time differs');
   assert(a.sourceFrameIndex===t.sourceFrameIndex&&a.sourcePts===t.sourcePts&&a.timeBase===t.timeBase,'Output source association differs');
   const k=clipMeta.mediaId+':'+t.sourceFrameIndex;selected.add(k);references++;if(t.scored){scored.add(k);scoredReferences++;}times.push(a.processingNanos);
  }
 }
 assert(score.clips.length===38&&references===3577&&selected.size===3507&&scoredReferences===3368&&scored.size===3361,'Frozen frame denominator changed');
 assert(score.aggregate.raw.characters===86602&&score.aggregate.raw.pageOpportunities===117,'Frozen character/page denominator changed');
 const source=json(implementation+'/source-snapshot.json'),classes=json(implementation+'/class-identity.json');
 assert(classes.sourceSnapshotSha256===await hash(implementation+'/source-snapshot.json'),'Implementation identity differs');
 for(const file of source.files)assert(await hash(file.path)===file.sha256,'Implementation source changed before evidence check: '+file.path);
 for(const file of classes.classes)assert(await hash(file.path)===file.sha256,'Compiled class changed: '+file.path);
 const tests=[];for(const module of ['app','verification'])for(const name of fs.readdirSync(module+'/target/surefire-reports').filter(n=>/^TEST-.*\.xml$/.test(n))){const xml=fs.readFileSync(module+'/target/surefire-reports/'+name,'utf8'),head=xml.match(/<testsuite\b[^>]*>/)[0],attr=k=>Number(head.match(new RegExp('\\b'+k+'="([^"]+)"'))[1]);const row={module,name,tests:attr('tests'),failures:attr('failures'),errors:attr('errors'),skipped:attr('skipped')};assert(row.failures===0&&row.errors===0&&row.skipped===0,'Java test not passed');tests.push(row);}
 const jar='app/target/g1-ocr-app-0.1.0-SNAPSHOT.jar',entries=cp.execFileSync('jar',['tf',jar],{encoding:'utf8'}).trim().split(/\r?\n/);
 assert(entries.some(e=>e.endsWith('/JapaneseOcrEngine.class')),'Current OCR engine absent from package');
 assert(!entries.some(e=>/\.(png|mp4|gb|gbc|rom|1bpp|2bpp|jsonl)$/i.test(e)||e.startsWith('io/github/g1ocr/verification/')||e.startsWith('com/google/gson/')),'Source assets, test data or verifier leaked into product package');
 times.sort((a,b)=>a-b);const q=p=>times[Math.min(times.length-1,Math.floor(p*(times.length-1)))];
 fs.mkdirSync(out,{recursive:true});const result={status:'input_output_build_evidence_verified_OCR_gates_not_passed',command:process.argv.slice(1),freezeSha256:run.freezeSha256,comparison:{path:input+'/comparison/report.json',sha256:await hash(input+'/comparison/report.json')},media,retainedFrameReferences:references,uniqueRetainedFrames:selected.size,scoredFrameReferences:scoredReferences,uniqueScoredFrames:scored.size,raw:score.aggregate.raw,final:score.aggregate.final,sourceSnapshot:{path:implementation+'/source-snapshot.json',sha256:await hash(implementation+'/source-snapshot.json')},classIdentity:{path:implementation+'/class-identity.json',sha256:await hash(implementation+'/class-identity.json')},productJar:{path:jar,sha256:await hash(jar),forbiddenDataAbsent:true},javaTests:tests.reduce((n,t)=>n+t.tests,0),testReports:tests,processingNanos:{p50:q(.5),p95:q(.95),p99:q(.99),maximum:times[times.length-1],meaning:'Algorithm elapsed time only. Not actual UI presentation or latency acceptance.'},independentAcceptance:'pending_no_proven_unused_session',uiAndRealEnvironment:'pending',productionChinese:'not_started',interpretation:'Evidence integrity success does not mean OCR accuracy or independent product acceptance. Old historical candidate/status wording inside frozen payloads remains unchanged; current approval authority is the effective freeze envelope.'};
 fs.writeFileSync(path.join(out,'report.json'),JSON.stringify(result,null,2)+'\n',{flag:'wx'});console.log('Evidence verified; OCR gates remain unmet. '+result.javaTests+' Java tests, '+scored.size+' scored source frames.');
}
main().catch(e=>{console.error(e.stack);process.exitCode=1;});
