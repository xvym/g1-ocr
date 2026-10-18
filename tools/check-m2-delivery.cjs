'use strict';
const fs=require('fs'),path=require('path'),crypto=require('crypto'),cp=require('child_process');
const hash=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex'),json=p=>JSON.parse(fs.readFileSync(p,'utf8'));
const assert=(ok,msg)=>{if(!ok)throw Error(msg);},save=(p,x)=>fs.writeFileSync(p,JSON.stringify(x,null,2)+'\n',{flag:'wx'});
const out=process.argv[2];assert(out&&!fs.existsSync(out),'Use a new report directory');fs.mkdirSync(out,{recursive:true});
const original='reports/gt-review/gt-all-scenes-v2',review='reports/gt-review/gt-all-scenes-v2-shop-metadata-r2',frozen='data/gt/frozen/ocr-all-scenes-v2';
const reports={};
for(const [name,dir,expected] of [['original',original,'e51b7d9ebccae9e784f0b60a4a73a352b164e0d9807bd068afb2aad7c54b2dd3'],['amendment',review,'3f800da7ce58e00a3b0ca5e4d443beeb0629d3d87f40118f43c22100f452241e']]){
 assert(hash(path.join(dir,'bundle-manifest.json'))===expected,'Manifest changed');const m=json(path.join(dir,'bundle-manifest.json'));
 for(const f of m.files)assert(fs.statSync(path.join(dir,f.path)).size===f.bytes&&hash(path.join(dir,f.path))===f.sha256,'Bundle file changed '+f.path);
 reports[name]={manifestSha256:expected,files:m.files.length,bytes:m.files.reduce((a,f)=>a+f.bytes,0)};
}
const media=fs.readFileSync('data/meta/m1-v1/media.jsonl','utf8').trim().split(/\r?\n/).map(JSON.parse);
for(const m of media)assert(hash(m.path)===m.sha256&&hash('data/meta/m1-v1/'+m.id+'/frames.jsonl')===m.framesSha256,'Source/ledger changed');
const e=json(frozen+'/freeze.json');
for(const item of e.items)for(const p of item.payloads)assert(hash(p.file?path.join(frozen,p.file):p.approvedSource)===p.sha256,'Frozen payload changed');
const replay=json('reports/m2-replay-all-scenes-v2-unpaced-v2/report.json'),prior='reports/m2-replay-all-scenes-v2-unpaced-v1',current='reports/m2-replay-all-scenes-v2-unpaced-v2';
let references=0,scoredRefs=0;const all=new Set(),scored=new Set();
for(const clip of replay.clips){const a=fs.readFileSync(prior+'/'+clip.clipId+'-timeline.jsonl','utf8').trim().split(/\r?\n/).map(JSON.parse),b=fs.readFileSync(current+'/'+clip.clipId+'-timeline.jsonl','utf8').trim().split(/\r?\n/).map(JSON.parse);assert(a.length===b.length&&b.length===clip.frames,'Replay count changed');const source=json(frozen+'/batch.json').items.find(x=>x.id===clip.clipId),c=json(source.clip);for(let i=0;i<a.length;i++){for(const field of ['sourceFrameIndex','sourcePts','timeBase','durationPts','rgbSha256','scored','sourceOffsetNanos'])assert(a[i][field]===b[i][field],'Replay source identity changed');assert(b[i].sourceFrameIndex===c.startFrame+i,'Replay frame gap');const key=c.mediaId+':'+b[i].sourceFrameIndex;references++;all.add(key);if(b[i].scored){scoredRefs++;scored.add(key);}}}
assert(replay.clips.length===38&&references===3577&&all.size===3507&&scoredRefs===3368&&scored.size===3361,'Frozen replay scope mismatch');
const timing=['reports/m2-replay-short-page-realtime-v2/shop-exit-report.json','reports/m2-replay-skills-realtime-v1/skills-turn-report.json','reports/m2-replay-exploration-realtime-v1/exploration-npc-report.json'].map(json);
const fixtures=json('reports/m2-foundation-fixtures-v3/check.json');assert(fixtures.knownFrozenMetadataConflicts===1&&fixtures.aggregateStatus==='correctly_rejected_pending_metadata_amendment','Conflict was silently bypassed');
const proposed=json('reports/m2-shop-metadata-amendment-v1/effective-overlap-check.json');assert(proposed.conflicts.length===0&&proposed.overlappingReferences===7,'Amendment does not resolve effective dataset');
const tests=[];for(const module of ['app','verification'])for(const n of fs.readdirSync(module+'/target/surefire-reports'))if(n.startsWith('TEST-')&&n.endsWith('.xml')){const s=fs.readFileSync(module+'/target/surefire-reports/'+n,'utf8'),get=k=>Number(s.match(new RegExp('\\b'+k+'="(\\d+)"'))[1]);tests.push({module,file:n,tests:get('tests'),failures:get('failures'),errors:get('errors'),skipped:get('skipped')});}
assert(tests.reduce((a,t)=>a+t.tests,0)===53&&tests.every(t=>!t.failures&&!t.errors&&!t.skipped),'Final Java tests incomplete');
const sourceFiles=cp.execFileSync('rg',['--files','app/src','verification/src','tools','config','resources'],{encoding:'utf8'}).trim().split(/\r?\n/).concat(['pom.xml','app/pom.xml','verification/pom.xml','.gitattributes']).sort();
save(out+'/source-snapshot.json',{gitHead:cp.execFileSync('git',['rev-parse','HEAD'],{encoding:'utf8'}).trim(),files:sourceFiles.map(p=>({path:p.replace(/\\/g,'/'),sha256:hash(p),bytes:fs.statSync(p).size}))});
const classes=cp.execFileSync('rg',['--files','--no-ignore','app/target/classes','verification/target/classes'],{encoding:'utf8'}).trim().split(/\r?\n/).filter(p=>p.endsWith('.class')).sort();
save(out+'/build-identity.json',{sourceSnapshotSha256:hash(out+'/source-snapshot.json'),javaClasses:classes.map(p=>({path:p.replace(/\\/g,'/'),sha256:hash(p)})),tests,command:"mvn -o -s config/maven-settings.xml '-Dmaven.repo.local=.cache/m2' test",log:'reports/gt-approval-all-scenes-v2/maven-test-m2-final-v1.log'});
save(out+'/report.json',{status:'approved_bytes_preserved_foundation_checked_metadata_amendment_pending_approval',bundles:reports,originalMedia:media.map(m=>({id:m.id,path:m.path,sha256:m.sha256,frames:m.frameCount})),freezeSha256:hash(frozen+'/freeze.json'),replay:{items:38,frameReferences:references,uniqueSelectedFrames:all.size,scoreFrameReferences:scoredRefs,uniqueScoredFrames:scored.size,originalIdentityDifferencesBetweenImplementations:0},timing,uiLatency:'pending; these are input delivery times only',javaTests:53,scorerFixtures:fixtures.status,originalCrossClipMetadataConflicts:1,proposedEffectiveCrossClipMetadataConflicts:0,amendmentFrozen:false,sourceSnapshotSha256:hash(out+'/source-snapshot.json'),buildIdentitySha256:hash(out+'/build-identity.json'),productOCR:'not implemented or run',translation:'not started',independentAcceptance:'pending_no_proven_unused_independent_session'});
console.log('Delivery identities checked; 53 Java tests, 3577 replayed frame references; amendment remains unapproved/unfrozen.');
