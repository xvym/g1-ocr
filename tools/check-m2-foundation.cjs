'use strict';
// Comparator fixtures only. These files copy GT to exercise a scorer, never an OCR result.
const fs=require('fs'), path=require('path'), cp=require('child_process'), crypto=require('crypto');
const assert=(ok,msg)=>{if(!ok)throw Error(msg);};
const json=p=>JSON.parse(fs.readFileSync(p,'utf8'));
const sha=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const [frozen,fixture,report]=process.argv.slice(2);
assert(frozen&&fixture&&report,'Usage: node tools/check-m2-foundation.cjs <frozen-dir> <new-fixture-dir> <new-report-dir>');
for(const dir of [fixture,report]){const rel=path.relative(process.cwd(),path.resolve(dir));assert(rel&&!rel.startsWith('..')&&!path.isAbsolute(rel)&&!fs.existsSync(dir),'Output must be new and inside workspace');fs.mkdirSync(dir,{recursive:true});}
const classpath=['verification/target/classes','app/target/classes','.cache/m2/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar'].join(path.delimiter);
const envelope=json(path.join(frozen,'freeze.json')),batch=json(path.join(frozen,'batch.json'));
function payload(item,kind){const p=item.payloads.find(x=>x.kind===kind);return p.file?path.join(frozen,p.file):p.approvedSource;}
const commands=[];
function run(selection,actual,out,expectedExit=0){
  const args=['-Dfile.encoding=UTF-8','-cp',classpath,'io.github.g1ocr.verification.VideoTool','compare-ocr',frozen,selection,actual,out];
  const result=cp.spawnSync('java',args,{encoding:'utf8',maxBuffer:8*1024*1024});
  const log=path.join(report,'command-'+commands.length+'.log');fs.writeFileSync(log,result.stdout+result.stderr,{flag:'wx'});commands.push({command:['java',...args],exitCode:result.status,expectedExit,log});
  assert(!result.error&&result.status===expectedExit,'Unexpected comparator exit; see '+log);
  if(expectedExit!==0)assert(!fs.existsSync(path.join(out,'report.json')),'Rejected input produced success report');
}
const exact=path.join(fixture,'exact-GT-copy-not-OCR');fs.mkdirSync(exact);
let totalFrames=0;
for(const item of envelope.items){
  const states=fs.readFileSync(payload(item,'gt'),'utf8').trim().split(/\r?\n/).map(JSON.parse);
  const clip=json(payload(item,'clip')),ledger=fs.readFileSync('data/meta/m1-v1/'+clip.mediaId+'/frames.jsonl','utf8').trim().split(/\r?\n/).map(JSON.parse);
  const rows=[];
  for(const s of states)for(let n=s.startFrame;n<s.endFrameExclusive;n++)rows.push({sourceFrameIndex:n,sourcePts:ledger[n].pts,timeBase:ledger[n].timeBase,rawRegions:s.regions,visibleState:{kind:s.kind,cursor:s.cursor,...(s.detachedMarks?{detachedMarks:s.detachedMarks}:{})},fixture:'GT_COPY_SCORER_TEST_NOT_OCR'});
  fs.writeFileSync(path.join(exact,item.id+'.jsonl'),rows.map(JSON.stringify).join('\n')+'\n',{flag:'wx'});totalFrames+=rows.length;
}
const knownConflicts=require('./check-gt-overlaps.cjs').audit(path.join(frozen,'batch.json'));
assert(knownConflicts.conflicts.length===0 || knownConflicts.conflicts.length===1&&knownConflicts.conflicts[0].key==='video-03:1439','Unexpected frozen metadata conflicts');
run('all',exact,path.join(report,'exact'),knownConflicts.conflicts.length?3:0);
for(const item of batch.items){const r=json(path.join(report,'exact',item.id+'-report.json'));assert(r.raw.editErrors===0&&r.final.failedFrames===0&&r.missingOutputs===0,'Exact fixture failed: '+item.id);}
if(!knownConflicts.conflicts.length){const aggregate=json(path.join(report,'exact','report.json')).aggregate;
assert(aggregate.uniqueSourceFrames===envelope.summary.uniqueScoredOriginalFrames&&aggregate.duplicateReferences===7&&aggregate.raw.pageOpportunities===117&&aggregate.raw.editErrors===0&&aggregate.final.failedFrames===0,'Deduplicated scope differs from approved frozen summary');}
function altered(name,id,change){const dir=path.join(fixture,name);fs.mkdirSync(dir);const rows=fs.readFileSync(path.join(exact,id+'.jsonl'),'utf8').trim().split(/\r?\n/).map(JSON.parse);change(rows);fs.writeFileSync(path.join(dir,id+'.jsonl'),rows.map(JSON.stringify).join('\n')+'\n',{flag:'wx'});return dir;}
const shortId='shop-exit';
const missing=altered('missing-one-frame',shortId,rows=>{const i=rows.findIndex(r=>r.sourceFrameIndex===1450);assert(i>=0,'Short page fixture missing');rows.splice(i,1);});
run(shortId,missing,path.join(report,'missing'));
const missed=json(path.join(report,'missing',shortId+'-report.json'));assert(missed.missingOutputs===1&&missed.raw.failedFrames===1&&missed.raw.contentExactPages===missed.raw.pageOpportunities-1,'Single-frame page omission not retained');
const empty=altered('empty-false-positive','startup-nontarget',rows=>{rows[0].rawRegions=[{role:'unexpected',readingOrder:0,text:'あ',gridRect:[1,1,2,2],sourceRect:[8,8,16,16],markCells:[]}];});
run('startup-nontarget',empty,path.join(report,'false-positive'));const falsePositive=json(path.join(report,'false-positive','startup-nontarget-report.json'));assert(falsePositive.raw.emptyFalsePositiveFrames===1&&falsePositive.raw.insertions===1&&falsePositive.raw.frameCharacterAccuracy===null,'Empty false positive disappeared into denominator');
const duplicates=altered('duplicate-region',shortId,rows=>{const row=rows.find(r=>r.rawRegions.length);row.rawRegions.push({...row.rawRegions[0]});});run(shortId,duplicates,path.join(report,'duplicate-rejected'),3);
const wrongTime=altered('wrong-source-time',shortId,rows=>rows[0].sourcePts++);run(shortId,wrongTime,path.join(report,'time-rejected'),3);
const duplicateFrame=altered('duplicate-frame',shortId,rows=>rows.push({...rows[0]}));run(shortId,duplicateFrame,path.join(report,'frame-rejected'),3);
const unprovedCorrection=altered('uncited-correction',shortId,rows=>{rows[0].correctedRegions=rows[0].rawRegions;});run(shortId,unprovedCorrection,path.join(report,'correction-rejected'),3);
fs.writeFileSync(path.join(report,'check.json'),JSON.stringify({status:'scorer_fixture_checks_passed_not_OCR_acceptance',freezeSha256:sha(path.join(frozen,'freeze.json')),knownFrozenMetadataConflicts:knownConflicts.conflicts.length,aggregateStatus:knownConflicts.conflicts.length?'correctly_rejected_pending_metadata_amendment':'fixture_consistent',exactItems:batch.items.length,exactFrameReferences:totalFrames,checks:['38-item per-clip exact fixture','cross-clip conflict rejection when present','single-frame short-page omission','empty false positive','duplicate region rejection','wrong source time rejection','duplicate frame rejection','uncited correction rejection'],commands},null,2)+'\n',{flag:'wx'});
console.log('Scorer fixtures passed: '+batch.items.length+' items / '+totalFrames+' frame references. Not OCR evidence.');
