'use strict';
// Independent reconstruction. Does not import either policy or comparison implementation.
const fs = require('fs'), path = require('path'), crypto = require('crypto'), assert = require('assert/strict'), vm = require('vm');
const [directory, output] = process.argv.slice(2); assert(directory && output && !fs.existsSync(output));
const json = p => JSON.parse(fs.readFileSync(p, 'utf8'));
const rows = p => fs.readFileSync(p,'utf8').trim().split(/\r?\n/).filter(Boolean).map(JSON.parse);
function sha(p) { const h=crypto.createHash('sha256'),fd=fs.openSync(p,'r'),b=Buffer.alloc(1048576);try{let n;while((n=fs.readSync(fd,b,0,b.length,null)))h.update(b.subarray(0,n));}finally{fs.closeSync(fd);}return h.digest('hex'); }
const report = json(directory+'/report.json'), pages=rows(directory+'/pages.jsonl'), frames=rows(directory+'/frames.jsonl'), decisions=rows(directory+'/decisions.jsonl'), events=rows(directory+'/events.jsonl');
for(const f of json(directory+'/manifest.json').files){assert.equal(sha(directory+'/'+f.file),f.sha256);assert.equal(fs.statSync(directory+'/'+f.file).size,f.bytes);}
for(const f of report.inputs)assert.equal(sha(f.path),f.sha256,f.path);
assert.equal(report.productAcceptance,false);assert.equal(report.uiLatencyAcceptance,false);assert.equal(report.allSemanticIntegrityPassed,false);
assert.equal(pages.length,116);assert.equal(frames.length,3368);assert.equal(decisions.length,3577);
const protocol=JSON.parse(fs.readFileSync('tools/completion-trigger-protocol.json','utf8'));assert.deepEqual(protocol,report.protocol);
const variants=report.summary.map(s=>({id:s.id,ms:s.stableMs})), allByRef=new Map(decisions.map(d=>[d.clip+':'+d.frame,d]));
const scoredByRef=new Map(frames.map(f=>[f.clip+':'+f.frame,f]));assert.equal(scoredByRef.size,3368);
const freeze=json(report.frozen+'/freeze.json'), actualByRef=new Map(), gtByRef=new Map(), timelineByRef=new Map(), independentEvents=[];
const pick=(o,keys)=>Object.fromEntries(keys.filter(k=>o[k]!=null).map(k=>[k,o[k]]));
const readyKinds=['message_complete','message_fading','menu_complete','status_page_complete'];
const milliseconds=(v,tb)=>{const [n,d]=tb.split('/').map(Number);return v*n*1000/d;};
let verifiedPolicyDecisions=0;
for(const item of freeze.items){
  const resolve=p=>p.file?path.join(report.frozen,p.file):p.approvedSource;
  const clip=json(resolve(item.payloads.find(p=>p.kind==='clip'))), gt=rows(resolve(item.payloads.find(p=>p.kind==='gt')));
  const stateMap=new Map();for(const s of gt)for(let n=s.startFrame;n<s.endFrameExclusive;n++)stateMap.set(n,s);
  const aa=rows(report.run+'/actual/'+item.id+'.jsonl'),tt=rows(report.run+'/'+item.id+'-timeline.jsonl');assert.equal(aa.length,tt.length);
  const history=[], latched=Object.fromEntries(variants.map(v=>[v.id,false]));
  for(let i=0;i<aa.length;i++){
    const a=aa[i],t=tt[i], ref=item.id+':'+a.sourceFrameIndex, saved=allByRef.get(ref);assert(saved);
    actualByRef.set(ref,a);timelineByRef.set(ref,t);
    const descriptor={regions:a.rawRegions.map(r=>pick(r,['role','readingOrder','text','gridRect','markCells','logicalRect','renderUnits'])),
      cursors:(a.visibleState.cursor.menuCursors||[]).map(s=>pick(s,['style','gridRect'])),detachedMarks:(a.visibleState.detachedMarks||[]).map(s=>pick(s,['kind','gridRect']))};
    const key=JSON.stringify(descriptor),hasText=a.rawRegions.some(r=>r.text.length>0),kind=a.visibleState.kind;
    const eligible=hasText&&(readyKinds.includes(kind)||kind==='message_loading');
    const resetClock=i===0||aa[i-1].timeBase!==a.timeBase||a.sourcePts<=aa[i-1].sourcePts;
    let start=i;
    if(eligible&&!resetClock)while(start>0&&history[start-1].eligible&&history[start-1].key===key&&history[start-1].tb===a.timeBase&&history[start-1].pts<a.sourcePts)start--;
    const changed=resetClock||history.at(-1)?.key!==key,invalid=changed||!eligible;
    const count=eligible?i-start+1:0, since=aa[start].sourcePts, age=milliseconds(a.sourcePts-since,a.timeBase);
    const [n,d]=a.timeBase.split('/').map(BigInt);
    for(const v of variants){
      if(invalid)latched[v.id]=false;
      const stable=v.ms!==null&&kind==='message_loading'&&count>=2&&!a.rawRegions.some(r=>r.text.includes('\ufffd'))&&
        BigInt(a.sourcePts-since)*n*1000n>=BigInt(v.ms)*d;
      const ready=hasText&&(readyKinds.includes(kind)||stable),fire=ready&&!latched[v.id];if(fire)latched[v.id]=true;
      const expected={ready,fire,reason:ready?readyKinds.includes(kind)?'v18_complete_state':'text_stable':'not_ready',
        stableSincePts:String(since),samples:count,stableAgeMs:age,invalidate:invalid,signature:key};
      assert.deepEqual(saved.decisions[v.id],expected,ref+' '+v.id);verifiedPolicyDecisions++;
      if(t.scored&&fire){const s=stateMap.get(a.sourceFrameIndex);assert(s);independentEvents.push({variant:v.id,clip:item.id,media:clip.mediaId,frame:a.sourceFrameIndex,
        classification:s.completePage?'complete_page':['loading','message_loading'].includes(s.kind)?'residual_false_trigger':'other_noncomplete_trigger',signature:key});}
    }
    history.push({key,eligible,tb:a.timeBase,pts:a.sourcePts});
    assert.equal(saved.scored,t.scored);assert.equal(saved.pts,t.sourcePts);assert.equal(saved.timeBase,t.timeBase);
    if(t.scored){const s=stateMap.get(a.sourceFrameIndex),f=scoredByRef.get(ref);assert(s&&f);gtByRef.set(ref,s);assert.equal(f.expectedKind,s.kind);assert.equal(f.completePage,s.completePage===true);
      assert.deepEqual(f.decisions,saved.decisions);assert.deepEqual(f.actualText,a.rawRegions.map(r=>({role:r.role,gridRect:r.gridRect,text:r.text})));}
  }
}
const eventKeys=es=>es.map(e=>JSON.stringify([e.variant,e.clip,e.media,e.frame,e.classification,e.signature??e.decision.signature])).sort();assert.deepEqual(eventKeys(independentEvents),eventKeys(events));
const sourceGroups=new Map();for(const f of frames){const k=f.media+':'+f.frame;if(!sourceGroups.has(k))sourceGroups.set(k,[]);sourceGroups.get(k).push(f);}assert.equal(sourceGroups.size,3361);
const oldPages=rows(report.attribution+'/pages.jsonl'),oldFrames=rows(report.attribution+'/frames.jsonl'),oldByRef=new Map(oldFrames.map(f=>[f.clip+':'+f.frame,f]));
for(const f of frames){const o=oldByRef.get(f.clip+':'+f.frame);assert.equal(f.pageId,o.pageId);for(const st of ['raw','final'])assert.equal(f[st+'TextCorrect'],o[st].score.substitutions+o[st].score.deletions+o[st].score.insertions===0);}
const q=xs=>{xs=xs.slice().sort((a,b)=>a-b);return xs.length?{n:xs.length,p50:xs[Math.ceil(xs.length*.5)-1],p95:xs[Math.ceil(xs.length*.95)-1],p99:xs[Math.ceil(xs.length*.99)-1],max:xs.at(-1)}:{n:0,p50:null,p95:null,p99:null,max:null};};
for(const p of pages){
  const original=oldPages.find(o=>o.id===p.id);assert(original);const sources=[...sourceGroups.values()].filter(g=>g[0].pageId===p.id).sort((a,b)=>a[0].frame-b[0].frame);
  assert.equal(sources.length,original.frames);assert.deepEqual(p.completeFrameRanges,original.completeFrameRanges);
  const first=sources[0][0];let duration=0;for(const g of sources)duration+=milliseconds(g[0].durationPts,g[0].timeBase);
  assert.equal(p.durationMs,duration);assert.equal(p.short,duration<200-1e-7);
  for(const v of variants){
    const fkey=type=>sources.find(g=>g.every(f=>f.decisions[v.id].ready&&(type==='ready'||f[type])));
    for(const [type,prop]of [['ready','ready'],['rawTextCorrect','rawTextCorrect'],['finalTextCorrect','finalTextCorrect']]){
      const g=fkey(type),hit=p.variants[v.id][prop];assert.equal(!!hit,!!g);if(g){assert.equal(hit.frame,g[0].frame);assert.equal(hit.pts,g[0].pts);assert.equal(hit.delayMs,milliseconds(g[0].pts-first.pts,first.timeBase));}
    }
    for(const [type,flag]of [['ready','readyWithin200'],['rawTextCorrect','rawWithin200'],['finalTextCorrect','finalWithin200']])assert.equal(p.variants[v.id][flag],!!p.variants[v.id][type]&&p.variants[v.id][type].delayMs<=200);
  }
}
for(const v of variants){
  const s=report.summary.find(s=>s.id===v.id),uniqueEventMap=new Map();for(const e of events.filter(e=>e.variant===v.id))uniqueEventMap.set(e.media+':'+e.frame+':'+e.decision.signature,e);const es=[...uniqueEventMap.values()];
  assert.equal(s.newTriggers,es.length);assert.equal(s.residualFalseTriggers,es.filter(e=>e.classification==='residual_false_trigger').length);assert.equal(s.otherNoncompleteTriggers,es.filter(e=>e.classification==='other_noncomplete_trigger').length);
  assert.equal(s.residualTextCorrect,es.filter(e=>e.classification==='residual_false_trigger'&&e.rawTextCorrect).length);
  assert.equal(s.readyMisses,116-s.readyPages);
  assert.equal(s.readyWithin200,pages.filter(p=>p.variants[v.id].readyWithin200).length);
  assert.equal(s.rawWithin200,pages.filter(p=>p.variants[v.id].rawWithin200).length);
  assert.equal(s.finalWithin200,pages.filter(p=>p.variants[v.id].finalWithin200).length);
  assert.equal(s.readyOver200,pages.filter(p=>p.variants[v.id].ready&&p.variants[v.id].ready.delayMs>200+1e-7).length);
  for(const [prop,name,delay]of [['ready','readyPages','readinessDelayMs'],['rawTextCorrect','rawTextCorrectPages','rawTextDelayMs'],['finalTextCorrect','finalTextCorrectPages','finalTextDelayMs']]){
    const hits=pages.filter(p=>p.variants[v.id][prop]);assert.equal(s[name],hits.length);assert.deepEqual(s[delay],q(hits.map(p=>p.variants[v.id][prop].delayMs)));
  }
  const short=pages.filter(p=>p.short);assert.equal(s.shortPages,short.length);assert.equal(s.shortReadyMisses,short.filter(p=>!p.variants[v.id].ready).length);assert.equal(s.shortRawTextMisses,short.filter(p=>!p.variants[v.id].rawTextCorrect).length);assert.equal(s.shortFinalTextMisses,short.filter(p=>!p.variants[v.id].finalTextCorrect).length);
}
let transitionFrames=0;const coveredKinds=new Set();
for(const t of Object.values(report.transitions)){
  const kinds=Object.keys(t.kinds);for(const kind of kinds){assert(!coveredKinds.has(kind));coveredKinds.add(kind);}
  const gs=[...sourceGroups.values()].filter(g=>!g[0].completePage&&kinds.includes(g[0].expectedKind));
  assert.equal(t.frames,gs.length);transitionFrames+=gs.length;
  for(const [kind,count]of Object.entries(t.kinds))assert.equal(count,gs.filter(g=>g[0].expectedKind===kind).length);
  for(const v of variants){assert.equal(t.variants[v.id].readyFrames,gs.filter(g=>g.every(f=>f.decisions[v.id].ready)).length);assert.equal(t.variants[v.id].newTriggers,gs.filter(g=>g.some(f=>f.decisions[v.id].fire)).length);}
}
assert.equal(transitionFrames,1316);
const primary=report.comparison.primary;assert.equal(primary,'arrow_stable_'+protocol.primaryStableMs);
assert.deepEqual(report.comparison.rescuedReadyPages,pages.filter(p=>!p.variants.arrow.ready&&p.variants[primary].ready).map(p=>p.id));
assert.deepEqual(report.comparison.rescuedFinalTextPages,pages.filter(p=>!p.variants.arrow.finalTextCorrect&&p.variants[primary].finalTextCorrect).map(p=>p.id));
assert.deepEqual(report.comparison.regressedReadyPages,pages.filter(p=>p.variants.arrow.ready&&!p.variants[primary].ready).map(p=>p.id));
assert.deepEqual(report.comparison.shortReadyMisses,pages.filter(p=>p.short&&!p.variants[primary].ready).map(p=>({id:p.id,clips:p.clips,durationMs:p.durationMs})));
const baseKeys=new Set(events.filter(e=>e.variant==='arrow').map(e=>e.media+':'+e.frame+':'+e.classification));
for(const [classification,field]of [['residual_false_trigger','additionalResidualTriggers'],['other_noncomplete_trigger','additionalOtherNoncompleteTriggers']]){
  const m=new Map();for(const e of events.filter(e=>e.variant===primary&&e.classification===classification&&!baseKeys.has(e.media+':'+e.frame+':'+e.classification)))m.set(e.media+':'+e.frame,{media:e.media,frame:e.frame,clip:e.clip,kind:e.expectedKind});
  assert.deepEqual(report.comparison[field],[...m.values()]);
}
assert.deepEqual(report.existingAggregateUnchanged,json(report.run+'/comparison/report.json').aggregate);
const html=fs.readFileSync(directory+'/index.html','utf8');assert.equal((html.match(/<tr class="page"/g)||[]).length,116);
let links=0;for(const [,href]of html.matchAll(/href="([^"]+)"/g)){assert(!/^(https?:|javascript:)/.test(href));assert(fs.existsSync(path.resolve(directory,href.replaceAll('&amp;','&'))),href);links++;}
const uiRows=[...html.matchAll(/<tr class="page" data-search="([^"]*)" data-miss="(true|false)" data-short="(true|false)"/g)].map(m=>({dataset:{search:m[1],miss:m[2],short:m[3]},hidden:false}));
const controls={search:{value:'',addEventListener(){}},filter:{value:'',addEventListener(){}},count:{}};const context=vm.createContext({document:{getElementById:id=>controls[id],querySelectorAll:()=>uiRows}});
new vm.Script([...html.matchAll(/<script>([\s\S]*?)<\/script>/g)][0][1]).runInContext(context);
for(const [filter,n]of [['',116],['short',pages.filter(p=>p.short).length],['miss',pages.filter(p=>!p.variants[report.comparison.primary].finalTextCorrect).length]]){controls.filter.value=filter;vm.runInContext('update()',context);assert.equal(uiRows.filter(r=>!r.hidden).length,n);}
controls.filter.value='';controls.search.value='no-such-page-test';vm.runInContext('update()',context);assert.equal(uiRows.filter(r=>!r.hidden).length,0);
const result={status:'independent_reconstruction_passed_not_product_acceptance',report:{path:directory+'/report.json',sha256:sha(directory+'/report.json')},checker:{path:__filename,sha256:sha(__filename)},
  policyDecisions:verifiedPolicyDecisions,retainedReferences:decisions.length,scoredReferences:frames.length,uniqueScoredFrames:sourceGroups.size,pages:pages.length,shortPages:pages.filter(p=>p.short).length,
  events:events.length,immutableHashes:report.inputs.length,htmlRows:uiRows.length,localLinks:links,filters:'all/short/missed/no-match verified in DOM stub',actualBrowser:'not verified',actualUiLatency:'not measured',productAcceptance:false};
fs.writeFileSync(output,JSON.stringify(result,null,2)+'\n',{flag:'wx'});console.log(JSON.stringify(result));
