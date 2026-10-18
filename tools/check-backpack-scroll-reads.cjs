'use strict';
const fs=require('fs'),crypto=require('crypto'),assert=require('assert/strict');
const sha=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const read=p=>JSON.parse(fs.readFileSync(p,'utf8')),identity=p=>({path:p,sha256:sha(p)});
const base='reports/m3-grid-refinement-v1/backpack-scroll-notes-v1';
const paths=[base+'/pass1.json',base+'/pass2.json'],notes=paths.map(read);
const sheetReports=notes.map(n=>{assert.equal(sha(n.input.path),n.input.sha256);return read(n.input.path);});
const inputs=sheetReports.map(r=>r.inputVerification),v=inputs.map(read);
assert.deepEqual(v[0],v[1]);assert.equal(v[0].frames.length,166);assert(v[0].allRgbAndPngReadbackHashesMatch);
const pngs=[];for(let n=5200;n<5366;n++){
 const name='/frame-'+String(n).padStart(6,'0')+'.png';
 const pair=inputs.map(p=>p.replace(/[\\/]verification.json$/,'')+name);assert.equal(sha(pair[0]),sha(pair[1]));pngs.push({frame:n,sha256:sha(pair[1])});
}
for(const r of sheetReports)for(const s of r.sheets)assert.equal(sha(s.path),s.sha256);
for(const k of ['range','spans','markExceptions','cursorSpans','downArrowVisibleRanges'])assert.deepEqual(notes[0][k],notes[1][k],k);
const result={status:'two_direct_reads_and_original_frame_identity_checked_not_gt_acceptance',notes:paths.map(identity),verification:inputs.map(identity),frameCount:166,sheetsChecked:56,observationsAgree:true,pngs,limitations:'Same-agent readings with disclosed historical exposure. No tested OCR or frozen annotations read by this checker. Frozen comparison and semantic completeness are still pending; hashes alone do not prove semantic correctness.'};
const out=base+'/read-integrity.json';fs.writeFileSync(out,JSON.stringify(result,null,2)+'\n',{flag:'wx'});console.log(JSON.stringify({path:out,sha256:sha(out),frames:166,sheets:56}));
