const fs=require('fs'),crypto=require('crypto'),{visible}=require('./check-gt-overlaps.cjs');
const json=p=>JSON.parse(fs.readFileSync(p,'utf8')),rows=p=>fs.readFileSync(p,'utf8').trim().split(/\r?\n/).map(JSON.parse),sha=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const check=(v,m)=>{if(!v)throw Error(m);};
const base='data/gt/frozen/ocr-all-scenes-v2-observation-r12',next='data/gt/frozen/ocr-all-scenes-v2-observation-r13',review='reports/gt-review/gt-observation-corrections-v11';
const prevScore='reports/m3-grid-refinement-v1/ocr-regression-all-v10/comparison',nextScore='reports/m3-grid-refinement-v1/ocr-v10-observation-r13-comparison',run='reports/m3-grid-refinement-v1/ocr-regression-all-v10';
const old=json(base+'/freeze.json'),now=json(next+'/freeze.json'),a=json(prevScore+'/report.json'),b=json(nextScore+'/report.json');
for(const ref of [now.previousEffectiveFreeze,now.approvedBundle,now.identityCheck])check(sha(ref.path)===ref.sha256,'Envelope binding changed');
for(const f of json(now.approvedBundle.path).files)check(sha(review+'/'+f.path)===f.sha256,'Review file changed');
check(b.freezeSha256===sha(next+'/freeze.json')&&now.allSemanticIntegrityPassed===false&&!now.productAcceptance,'Score or qualification mismatch');
const map=p=>new Map(rows(p).flatMap(s=>Array.from({length:s.endFrameExclusive-s.startFrame},(_,i)=>[s.startFrame+i,s])));
const resolve=(root,p)=>p.file?root+'/'+p.file:p.approvedSource;
const changes=[],scoreChanges=[],identities=[];let refs=0;
for(const it of now.items){const prior=old.items.find(x=>x.id===it.id);check(prior,'Added event');
 for(const p of it.payloads){check(sha(resolve(next,p))===p.sha256,'Payload changed');if(p.kind!=='gt')check(p.sha256===prior.payloads.find(x=>x.kind===p.kind).sha256,'Range/layout/contract changed');}
 const x=map(resolve(base,prior.payloads.find(p=>p.kind==='gt'))),y=map(resolve(next,it.payloads.find(p=>p.kind==='gt')));check(x.size===y.size,'Removed frame');
 for(const [n,s] of x){check(y.has(n),'Missing frame');if(JSON.stringify(visible(s))!==JSON.stringify(visible(y.get(n))))changes.push(it.id+':'+n);}
 const xs=rows(prevScore+'/'+it.id+'-frames.jsonl'),ys=rows(nextScore+'/'+it.id+'-frames.jsonl');check(xs.length===ys.length,'Score length changed');refs+=xs.length;
 xs.forEach((r,i)=>{const t=ys[i];check(r.sourceFrameIndex===t.sourceFrameIndex&&r.sourcePts===t.sourcePts,'Timeline changed');const u={...r},v={...t};delete u.stateId;delete v.stateId;if(JSON.stringify(u)!==JSON.stringify(v))scoreChanges.push(it.id+':'+r.sourceFrameIndex);});
 const actual=run+'/actual/'+it.id+'.jsonl',h=sha(actual);check(a.clips.find(c=>c.clipId===it.id).actualSha256===h&&b.clips.find(c=>c.clipId===it.id).actualSha256===h,'Actual changed');identities.push({path:actual,sha256:h});
}
const ev=json(review+'/candidate-evidence.json');const expected=ev.changes.map(c=>'nurse-greeting:'+c.frame);check(JSON.stringify(changes)===JSON.stringify(expected)&&JSON.stringify(scoreChanges)===JSON.stringify(expected),'Unexpected change scope');
check(b.aggregate.raw.characters===a.aggregate.raw.characters,'Character denominator changed');check(refs===3368&&now.items.length===38,'Scope changed');for(const k of ['emptyFrames'])check(a.aggregate.raw[k]===b.aggregate.raw[k],'Denominator changed '+k);
check(b.aggregate.raw.uniqueContinuousStates===a.aggregate.raw.uniqueContinuousStates+1,'State accounting mismatch');
check(b.aggregate.raw.pageOpportunities===a.aggregate.raw.pageOpportunities-1,'Withdrawn opportunity accounting mismatch');
const independent=json('reports/m3-grid-refinement-v1/ocr-v10-observation-r13-text-analysis/report.json');check(independent.characters===b.aggregate.raw.characters&&independent.errors===b.aggregate.raw.editErrors,'Recount mismatch');
const report={status:'nurse_partial_erase_correction_and_same_actual_rescoring_verified',freeze:{path:next+'/freeze.json',sha256:sha(next+'/freeze.json')},comparison:{path:nextScore+'/report.json',sha256:sha(nextScore+'/report.json')},changes,scoreChanges,unchangedActuals:identities,oldRaw:a.aggregate.raw,newRaw:b.aggregate.raw,oldFinal:a.aggregate.final,newFinal:b.aggregate.final,withdrawnPage:ev.withdrawnPage,allSemanticIntegrityPassed:false,productAcceptance:false,uiRendering:'pending',independentSessions:'pending'};
const out=process.argv[2];check(out&&!fs.existsSync(out),'New output required');fs.mkdirSync(out,{recursive:true});fs.writeFileSync(out+'/report.json',JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify({changes,scoreChanges,newRaw:b.aggregate.raw,reportSha256:sha(out+'/report.json')}));
