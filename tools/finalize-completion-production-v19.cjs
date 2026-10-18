'use strict';
// Preserve the validated v1 result; distinguish offline-experiment caveats from actual production replay.
const fs=require('fs'),crypto=require('crypto'),assert=require('assert/strict');
const [source,out]=process.argv.slice(2);assert(source&&out&&!fs.existsSync(out));
function sha(p){const h=crypto.createHash('sha256'),fd=fs.openSync(p,'r'),b=Buffer.alloc(1048576);try{let n;while((n=fs.readSync(fd,b,0,b.length,null)))h.update(b.subarray(0,n));}finally{fs.closeSync(fd);}return h.digest('hex');}
const json=p=>JSON.parse(fs.readFileSync(p,'utf8'));
const originalSha=sha(source+'/report.json');assert.equal(originalSha,'7cb05ce47638653f3eec001d11c40db0338c31dc841b23a179f2c67a8eb6d7de');
for(const f of json(source+'/manifest.json').files)assert.equal(sha(source+'/'+f.file),f.sha256);
const r=json(source+'/report.json');for(const f of r.identities)assert.equal(sha(f.path),f.sha256,f.path);
assert.equal(r.productionOfflineDecisionsMatched,3577);assert.equal(r.clipIdentities.length,38);
assert.equal(r.residualFalseTriggers,0);assert.equal(r.otherTransitionFalseTriggers,2);
const priorLimitations=r.limitations;
r.offlineExperimentLimitations=priorLimitations.slice(0,-1);
r.limitations=[
  'Production v19 actually decoded and processed all38 frozen clips. All3577 production gate decisions matched the independently reconstructed saved-v18 experiment; original OCR outputs and strict scores were unchanged.',
  'Coverage remains the selected frozen clips from the same four original videos. Unselected time, missing dimensions and unproved independent sessions remain unverified.',
  'Readiness is separate from visible scene kind and strict text/geometry/symbol/state scores. Two transition false triggers and all original text/geometry failures remain.',
  'Source-PTS readiness delay and unpaced algorithm processing time do not certify capture/queue/translation or actual UI presentation latency.',
  'GT remaining semantic qualification, independent product acceptance, live environment and two-hour stability remain unverified. M3/M4 have not passed; production Chinese has not started.',
  'The production gate is in JapaneseOcrEngine.Result. Existing capture/translation/UI milestones remain unchanged.'
];
r.presentationCorrection={sourceReport:{path:source+'/report.json',sha256:originalSha},
  helper:{path:__filename,sha256:sha(__filename)},
  reason:'The v1 production summary inherited saved-v18 no-pixel-replay wording from the offline experiment. Move those caveats into an explicitly offline-only field; state actual all38 production replay accurately. No metric, policy, score, build or actual observation changed.'};
const comparison={...r};delete comparison.presentationCorrection;delete comparison.offlineExperimentLimitations;comparison.limitations=priorLimitations;
assert.deepEqual(comparison,json(source+'/report.json'));
fs.mkdirSync(out,{recursive:true});fs.writeFileSync(out+'/report.json',JSON.stringify(r,null,2)+'\n',{flag:'wx'});
for(const file of ['pages.jsonl','index.html'])fs.copyFileSync(source+'/'+file,out+'/'+file,fs.constants.COPYFILE_EXCL);
const files=['report.json','pages.jsonl','index.html'].map(file=>({file,sha256:sha(out+'/'+file),bytes:fs.statSync(out+'/'+file).size}));
fs.writeFileSync(out+'/manifest.json',JSON.stringify({files},null,2)+'\n',{flag:'wx'});
console.log(JSON.stringify({out,reportSha256:sha(out+'/report.json'),manifestSha256:sha(out+'/manifest.json'),metricsAndInputsUnchanged:true}));
