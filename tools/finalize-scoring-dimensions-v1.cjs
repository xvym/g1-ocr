'use strict';
// Keep inherited audit qualifications explicitly historical. No scoring/data changes.
const fs=require('fs'),crypto=require('crypto'),assert=require('assert/strict');
const[src,dest]=process.argv.slice(2);assert(src&&dest&&!fs.existsSync(dest));
const json=p=>JSON.parse(fs.readFileSync(p,'utf8')),sha=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const original=json(src+'/report.json'),manifest=json(src+'/manifest.json');for(const f of manifest.files)assert.equal(sha(src+'/'+f.file),f.sha256);for(const f of original.inputs)assert.equal(sha(f.path),f.sha256);
const report=structuredClone(original);report.priorAuditLimitations=original.limitations.filter(s=>!s.startsWith('This new comparison')&&!s.startsWith('Actual UI presentation'));
report.limitations=[
 'This is a new isolated dimension-comparison report. The existing Java scorer, GT, production OCR, v19 actuals and every original score are unchanged; no new OCR replay or production build was performed.',
 'LegacyStrict remains7/116, raw188/final177 errors. Logical agreement60/61 (raw/final) excludes numeric pixel borders and includes explicitly guarded legacy descriptor compatibility; it is diagnostic, not accepted page correctness.',
 'Field roles/order, occupancy, visible marks, render units, all symbols and remaining state mismatches remain checked. Missing characters or fields are never exempted by pixel-border differences or compatibility.',
 'Source rectangles, unmatched targets and earlier original-pixel warnings are retained separately. Numeric equality and no detected warning do not certify physical envelope safety; warnings do not prove glyph clipping.',
 'Prior original-image review and pixel probes are reused with unchanged identities; this task is not new blind GT or human per-frame annotation.',
 'Actual UI latency, GT semantic qualification, independent-session acceptance, unselected video ranges and real-window/stability requirements remain unverified. M3/M4 are not passed and production Chinese is not started.'
];
report.presentationClarification={sourceReport:{path:src+'/report.json',sha256:sha(src+'/report.json')},helper:{path:__filename,sha256:sha(__filename)},scope:'Only qualification wording and provenance organization; all scores/dimensions/frame/page/state data unchanged.'};
const extra=[...manifest.files.map(f=>({path:src+'/'+f.file,sha256:f.sha256})),{path:src+'/manifest.json',sha256:sha(src+'/manifest.json')},report.presentationClarification.helper];const inputs=new Map(report.inputs.map(f=>[f.path,f.sha256]));for(const f of extra){if(inputs.has(f.path))assert.equal(inputs.get(f.path),f.sha256);inputs.set(f.path,f.sha256);}report.inputs=[...inputs].map(([path,sha256])=>({path,sha256}));
assert.deepEqual(report.counts,original.counts);assert.deepEqual(report.originalAggregateUnchanged,original.originalAggregateUnchanged);assert.deepEqual(report.compatibility,original.compatibility);
fs.mkdirSync(dest,{recursive:true});for(const f of manifest.files)if(f.file!=='report.json')fs.copyFileSync(src+'/'+f.file,dest+'/'+f.file,fs.constants.COPYFILE_EXCL);fs.writeFileSync(dest+'/report.json',JSON.stringify(report,null,2)+'\n',{flag:'wx'});
fs.writeFileSync(dest+'/manifest.json',JSON.stringify({files:manifest.files.map(f=>({file:f.file,sha256:sha(dest+'/'+f.file),bytes:fs.statSync(dest+'/'+f.file).size}))},null,2)+'\n',{flag:'wx'});console.log(JSON.stringify({dest,reportSha256:sha(dest+'/report.json'),inputs:report.inputs.length,qualificationOnly:true}));
