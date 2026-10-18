'use strict';
// Delivery verification: actual image replay must agree with the independently checked saved-v18 experiment.
// No policy module is imported, and no scoring or GT is changed.
const fs=require('fs'),path=require('path'),crypto=require('crypto'),assert=require('assert/strict'),cp=require('child_process');
const [after,build,out]=process.argv.slice(2);assert(after&&build&&out&&!fs.existsSync(out));
const root='reports/m3-grid-refinement-v1',before=root+'/ocr-regression-all-v18',experiment=root+'/completion-message-history-v2';
const json=p=>JSON.parse(fs.readFileSync(p,'utf8'));
const rows=p=>fs.readFileSync(p,'utf8').trim().split(/\r?\n/).filter(Boolean).map(JSON.parse);
function sha(p){const h=crypto.createHash('sha256'),fd=fs.openSync(p,'r'),b=Buffer.alloc(1048576);try{let n;while((n=fs.readSync(fd,b,0,b.length,null)))h.update(b.subarray(0,n));}finally{fs.closeSync(fd);}return h.digest('hex');}
const identities=[];function bind(p,expected){const h=sha(p);if(expected)assert.equal(h,expected,p);identities.push({path:p,sha256:h});return h;}
const offline=json(experiment+'/report.json'),offlineCheck=json(root+'/completion-message-history-check-v1.json');
assert.equal(offlineCheck.experimentCriteriaPassed,true);assert.equal(offlineCheck.report.sha256,bind(experiment+'/report.json'));
bind(root+'/completion-message-history-check-v1.json');bind(root+'/completion-message-history-properties-v1.json');
for(const f of json(experiment+'/manifest.json').files)bind(experiment+'/'+f.file,f.sha256);bind(experiment+'/manifest.json');
// These are the only formerly bound paths allowed to differ after the explicitly authorized build.
const allowed=new Set(['app/src/main/java/io/github/g1ocr/ocr/JapaneseOcrEngine.java',
  'app/src/test/java/io/github/g1ocr/ocr/JapaneseOcrEngineTest.java',
  'app/target/classes/io/github/g1ocr/ocr/JapaneseOcrEngine.class',
  'app/target/classes/io/github/g1ocr/ocr/JapaneseOcrEngine$Result.class']);
const changedHistoricalInputs=[];
for(const f of offline.inputs){const now=sha(f.path);if(now!==f.sha256){assert(allowed.has(f.path),'Unexpected change to experiment input: '+f.path);
  const previousCopy=root+'/build-evidence-v18/source/'+f.path;
  if(f.path.endsWith('.java'))bind(previousCopy,f.sha256);
  changedHistoricalInputs.push({path:f.path,before:f.sha256,after:now,previousSourceCopy:fs.existsSync(previousCopy)?previousCopy:null});
}else identities.push(f);}
assert(changedHistoricalInputs.some(x=>x.path.endsWith('JapaneseOcrEngine.java')));
const snapshot=json(build+'/source-snapshot.json'),classes=json(build+'/class-identity.json');
bind(build+'/source-snapshot.json',classes.sourceSnapshotSha256);bind(build+'/class-identity.json');
for(const f of snapshot.files)bind(f.path,f.sha256);for(const f of classes.classes)bind(f.path,f.sha256);
const frozen=offline.frozen,freeze=json(frozen+'/freeze.json');bind(frozen+'/freeze.json',offline.freezeSha256);
for(const item of freeze.items)for(const f of item.payloads)bind(f.file?frozen+'/'+f.file:f.approvedSource,f.sha256);
const oldScore=json(before+'/comparison/report.json'),score=json(after+'/comparison/report.json'),run=json(after+'/report.json');
assert.equal(run.freezeSha256,offline.freezeSha256);bind(after+'/comparison/report.json',run.comparisonSha256);bind(after+'/report.json');
assert.equal(score.clips.length,38);assert.deepEqual(score.aggregate,oldScore.aggregate);
const ds=rows(experiment+'/decisions.jsonl'),dm=new Map(ds.map(d=>[d.clip+':'+d.frame,d]));
const actualMap=new Map(),processingBefore=[],processingAfter=[],clipIdentities=[];
let references=0,scoredReferences=0;
for(const c of score.clips){
  const a=rows(before+'/actual/'+c.clipId+'.jsonl'),b=rows(after+'/actual/'+c.clipId+'.jsonl');assert.equal(a.length,b.length);
  bind(after+'/actual/'+c.clipId+'.jsonl',c.actualSha256);
  const at=rows(before+'/'+c.clipId+'-timeline.jsonl'),bt=rows(after+'/'+c.clipId+'-timeline.jsonl');assert.equal(bt.length,b.length);assert.equal(bt.length,at.length);
  bind(after+'/'+c.clipId+'-timeline.jsonl');bind(after+'/'+c.clipId+'-transport.json');
  for(let i=0;i<b.length;i++){
    const ref=c.clipId+':'+b[i].sourceFrameIndex,expected={...dm.get(ref).decisions.message_history_100};
    delete expected.signature;delete expected.messageSignature;
    const observed=b[i].outputReadiness;assert(observed,'Production readiness absent: '+ref);
    assert(Math.abs(observed.stableAgeMs-expected.stableAgeMs)<1e-8,ref+' rational elapsed time');
    const strictObserved={...observed};delete strictObserved.stableAgeMs;delete expected.stableAgeMs;
    assert.deepEqual(strictObserved,expected,'Production/offline decision mismatch: '+ref);
    actualMap.set(ref,b[i]);processingBefore.push(a[i].processingNanos/1e6);processingAfter.push(b[i].processingNanos/1e6);
    const prior={...a[i]},current={...b[i]};delete prior.processingNanos;delete current.processingNanos;delete current.outputReadiness;
    assert.deepEqual(current,prior,'Existing OCR output changed: '+ref);
    for(const k of ['sourceFrameIndex','sourcePts','durationPts','timeBase','rgbSha256','scored','sourceOffsetNanos'])assert.deepEqual(bt[i][k],at[i][k],ref+' '+k);
    assert.equal(b[i].sourcePts,bt[i].sourcePts);assert.equal(b[i].timeBase,bt[i].timeBase);
    references++;if(bt[i].scored)scoredReferences++;
  }
  const sf=after+'/comparison/'+c.clipId+'-frames.jsonl';bind(sf);
  assert.deepEqual(rows(sf),rows(before+'/comparison/'+c.clipId+'-frames.jsonl'));
  clipIdentities.push({clip:c.clipId,frames:b.length,actualSha256:c.actualSha256});
}
assert.equal(references,3577);assert.equal(scoredReferences,3368);assert.equal(actualMap.size,references);
const frameRefs=rows(experiment+'/frames.jsonl'),groups=new Map();
for(const f of frameRefs){const k=f.media+':'+f.frame;if(!groups.has(k))groups.set(k,[]);groups.get(k).push(f);}
assert.equal(groups.size,3361);
const ready=g=>g.every(f=>actualMap.get(f.clip+':'+f.frame).outputReadiness.ready);
const fired=g=>g.some(f=>actualMap.get(f.clip+':'+f.frame).outputReadiness.fire);
const ms=(v,tb)=>{const [n,d]=tb.split('/').map(Number);return v*n*1000/d;};
const pages=rows(experiment+'/pages.jsonl').map(p=>{
  const gs=[...groups.values()].filter(g=>g[0].pageId===p.id).sort((a,b)=>a[0].frame-b[0].frame),first=gs[0][0];
  const hit=gs.find(ready),correct=gs.find(g=>ready(g)&&g.every(f=>f.finalTextCorrect));
  const detail=g=>g?{frame:g[0].frame,delayMs:ms(g[0].pts-first.pts,p.timeBase)}:null;
  const actual={...p,production:{ready:detail(hit),finalTextCorrect:detail(correct)}};
  for(const prop of ['ready','finalTextCorrect']){const old=p.variants.message_history_100[prop],now=actual.production[prop];assert.equal(!!now,!!old,p.id);if(now){assert.equal(now.frame,old.frame);assert.equal(now.delayMs,old.delayMs);}}
  return actual;
});
const noncomplete=[...groups.values()].filter(g=>!g[0].completePage);
const residual=noncomplete.filter(g=>fired(g)&&['loading','message_loading'].includes(g[0].expectedKind));
const other=noncomplete.filter(g=>fired(g)&&!['loading','message_loading'].includes(g[0].expectedKind));
assert.equal(residual.length,0);assert.equal(other.length,2);assert.equal(noncomplete.length,1316);
for(const g of noncomplete.filter(g=>/scroll|clear|empty|no_textbox|non_target|black_frame/.test(g[0].expectedKind)))assert(!ready(g)&&!fired(g));
const retained=offline.comparison.previousRescuedPages.filter(id=>pages.find(p=>p.id===id).production.finalTextCorrect?.delayMs<=200);
assert.equal(retained.length,20);assert.equal(pages.filter(p=>p.short&&!p.production.ready).length,2);
assert.equal(pages.filter(p=>p.production.ready).length,114);assert.equal(pages.filter(p=>p.production.finalTextCorrect).length,98);
const tests=[];
for(const module of ['app','verification'])for(const name of fs.readdirSync(module+'/target/surefire-reports').filter(n=>/^TEST-.*\.xml$/.test(n))){
  const p=module+'/target/surefire-reports/'+name,head=fs.readFileSync(p,'utf8').match(/<testsuite\b[^>]*>/)[0],attr=k=>Number(head.match(new RegExp('\\b'+k+'="([^"]+)"'))[1]);
  const row={module,name,tests:attr('tests'),failures:attr('failures'),errors:attr('errors'),skipped:attr('skipped')};
  assert.equal(row.failures+row.errors+row.skipped,0);tests.push(row);bind(p);
}
assert.equal(tests.reduce((n,t)=>n+t.tests,0),176);
const jar='app/target/g1-ocr-app-0.1.0-SNAPSHOT.jar',entries=cp.execFileSync('jar',['tf',jar],{encoding:'utf8'}).trim().split(/\r?\n/);
assert(entries.includes('io/github/g1ocr/ocr/OutputReadinessTracker.class'));
assert(!entries.some(e=>/\.(png|mp4|gb|gbc|rom|1bpp|2bpp|jsonl)$/i.test(e)||e.startsWith('io/github/g1ocr/verification/')||e.startsWith('com/google/gson/')));
const jarSha=bind(jar);bind(root+'/build-v19-final-test.log');bind(root+'/build-v19-package.log');
const q=xs=>{xs=xs.slice().sort((a,b)=>a-b);const f=p=>xs[Math.ceil(xs.length*p)-1];return{n:xs.length,p50:f(.5),p95:f(.95),p99:f(.99),max:xs.at(-1)};};
const result={status:'production_gate_and_all38_image_replay_verified_not_independent_acceptance',
  command:process.argv.slice(1),checkerSha256:sha(__filename),freezeSha256:offline.freezeSha256,
  experiment:{path:experiment+'/report.json',sha256:sha(experiment+'/report.json')},build,jar:{path:jar,sha256:jarSha},
  javaTests:176,compiledClasses:classes.classes.length,sourceFiles:snapshot.files.length,clipIdentities,
  retainedReferences:references,scoredReferences,uniqueScoredFrames:groups.size,pages:pages.length,noncompleteFrames:noncomplete.length,
  productionOfflineDecisionsMatched:references,residualFalseTriggers:residual.length,otherTransitionFalseTriggers:other.length,
  remainingFalseTriggers:other.map(g=>({media:g[0].media,frame:g[0].frame,kind:g[0].expectedKind})),
  readyPages:114,finalTextCorrectPages:98,retainedPreviousRescues:retained,shortPages:11,shortReadyMisses:2,shortFinalTextMisses:4,
  improvedFromPrevious14:offline.comparison.previous14.filter(p=>p.afterDelayMs<p.beforeDelayMs),
  allDelayChanges:offline.comparison.readinessDelayChanges,unchangedDelayEvidence:offline.comparison.unimprovedDelayEvidence,
  originalOutputUnchangedExceptProcessingAndNewGate:true,strictAggregate:score.aggregate,changedHistoricalInputs,
  processingMs:{before:q(processingBefore),after:q(processingAfter),meaning:'Unpaced algorithm elapsed time on this machine. Not a controlled speed comparison or actual UI presentation latency.'},
  productAcceptance:false,uiLatencyAcceptance:false,allSemanticIntegrityPassed:false,
  limitations:offline.limitations.concat(['The gate is integrated in the production JapaneseOcrEngine Result; capture/translation/UI stages remain subject to their existing milestones. No production Chinese work was started.']),
  identities};
for(const f of identities)assert.equal(sha(f.path),f.sha256,'Evidence changed during verification: '+f.path);
fs.mkdirSync(out,{recursive:true});
fs.writeFileSync(out+'/report.json',JSON.stringify(result,null,2)+'\n',{flag:'wx'});
fs.writeFileSync(out+'/pages.jsonl',pages.map(JSON.stringify).join('\n')+'\n',{flag:'wx'});
const href=p=>path.relative(out,p).replaceAll('\\','/'),esc=x=>String(x).replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;').replaceAll('"','&quot;');
const table=result.improvedFromPrevious14.map(p=>`<tr><td>${esc(p.page)}</td><td>${p.beforeDelayMs.toFixed(1)}</td><td>${p.afterDelayMs.toFixed(1)}</td></tr>`).join('');
fs.writeFileSync(out+'/index.html',`<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>v19 消息完成门控 · 生产回放</title><style>body{font:16px/1.65 system-ui;margin:32px;background:#f5f7fa;color:#17212b}main{max-width:1100px;margin:auto}table{border-collapse:collapse;background:white}td,th{border:1px solid #ccd7e5;padding:8px 18px;text-align:left}.note{padding:16px;background:#fff1cd}a{color:#075ca9}</style><main><h1>消息完成门控已接入生产OCR</h1><p>v19 · 全部38片重跑 · 3577保留引用与离线预期逐帧一致 · 116页及1316转换原帧复核</p><p><b>残句误触发0，其他过渡误触发2；原新增20页全部保留，短页未触发仍2/11。</b>可触发114/116页，最终文字正确98/116页，无页面变慢。原14个额外等待页中5页消除125ms；另museum3542提前41.7ms。</p><p>关键短页 video-03:f860：源时间等待125→0ms，显示有效期剩余41.7→166.7ms。其余9页仍等待：片首历史不足、清屏后新消息或当前文字刚变化；不能继承旧确认。</p><table><tr><th>原14页中改善的页面</th><th>上一门控等待 ms</th><th>当前等待 ms</th></tr>${table}</table><p>176项Java测试通过，生产包包含门控类；原文字/坐标/符号/可见状态及严格评分逐帧保持。</p><p class="note">严格整页仍7/116，原始188错/86569字、最终177错；M3/M4尚未通过。以上触发时间按原帧PTS计算，未测实际UI延迟。GT剩余语义资格、独立会话和真实环境仍待验证；中文阶段未启动。</p><p><a href="${href(experiment+'/index.html')}">全部116页、转换帧及原图证据</a> · <a href="report.json">生产回放、构建与哈希证据</a> · <a href="pages.jsonl">116页生产判定</a> · <a href="${href(after+'/comparison/report.json')}">全38片严格评分</a></p></main></html>\n`,{flag:'wx'});
fs.writeFileSync(out+'/manifest.json',JSON.stringify({files:['report.json','pages.jsonl','index.html'].map(file=>({file,sha256:sha(out+'/'+file),bytes:fs.statSync(out+'/'+file).size}))},null,2)+'\n',{flag:'wx'});
console.log(JSON.stringify({out,reportSha256:sha(out+'/report.json'),clips:38,decisions:references,residual:0,other:2,ready:114,correct:98,retained20:retained.length,tests:176,classes:classes.classes.length,changedHistoricalInputs,processingMs:result.processingMs}));
