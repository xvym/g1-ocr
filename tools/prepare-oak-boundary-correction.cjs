'use strict';
const fs=require('fs'),path=require('path'),crypto=require('crypto'),assert=require('assert/strict');
const read=p=>JSON.parse(fs.readFileSync(p,'utf8')),rows=p=>fs.readFileSync(p,'utf8').trim().split(/\r?\n/).map(JSON.parse);
const sha=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex'),ident=p=>({path:p,sha256:sha(p)}),save=(p,x)=>fs.writeFileSync(p,JSON.stringify(x,null,2)+'\n',{flag:'wx'});
const root='reports/m3-grid-refinement-v1',base=root+'/oak-boundary-notes-v1',freezeRoot='data/gt/frozen/ocr-all-scenes-v2-observation-r9',freeze=read(freezeRoot+'/freeze.json');
const configs=[['lab-oak-reply','oak-reply',13438,0,'オーキド『まー','オーキド『まー！',1],['oak-delivery','oak-delivery',24223,1,'とどけもの','とどけも',-1]];
const checks=[],changes=[];
for(const [id,sheet,frame,region,beforeText,afterText,delta] of configs){
 const notes=[1,2].map(i=>base+'/'+id+'-pass'+i+'.json'),n=notes.map(read);
 const sheets=[1,2].map(i=>root+'/'+sheet+'-boundary-pass'+i+'-sheets-v1/report.json'),r=sheets.map(read);
 const verification=r.map(x=>x.inputVerification),v=verification.map(read);
 assert.deepEqual(v[0].frames,v[1].frames);assert.deepEqual(n[0].range,n[1].range);
 for(let i=0;i<2;i++){assert(v[i].allRgbAndPngReadbackHashesMatch&&r[i].allDisplayedPixelsMatchOriginal);assert.equal(sha(sheets[i]),n[i].input.sha256);assert.equal(sha(verification[i]),r[i].inputVerificationSha256);for(const s of r[i].sheets)assert.equal(sha(s.path),s.sha256);}
 const frames=[];
 for(let j=0;j<v[0].frames.length;j++){
  const f=v[0].frames[j].frameIndex;assert.equal(f,n[0].range[0]+j);
  const f0=r[0].frames[j],f1=r[1].frames[j];assert.equal(f0.frameIndex,f);assert.equal(f1.frameIndex,f);
  assert.equal(sha(f0.png),f0.pngSha256);assert.equal(sha(f1.png),f1.pngSha256);assert(fs.readFileSync(f0.png).equals(fs.readFileSync(f1.png)));
  const s=n.map(x=>x.spans.find(s=>s[0]<=f&&s[1]>f));assert(s.every(Boolean));assert.deepEqual(s[0].slice(2,4),s[1].slice(2,4));
  frames.push({frame:f,paths:[f0.png,f1.png],sha256:f0.pngSha256});
 }
 const item=freeze.items.find(x=>x.id===id),payloads=item.payloads.map(p=>{const file=p.file?freezeRoot+'/'+p.file:p.approvedSource;assert.equal(sha(file),p.sha256);return {kind:p.kind,...ident(file)};});
 const input=payloads.find(x=>x.kind==='gt'),old=rows(input.path),next=structuredClone(old);
 const diffs=[];
 for(const s of old)for(let f=s.startFrame;f<s.endFrameExclusive;f++){
  const obs=n[1].spans.find(x=>x[0]<=f&&x[1]>f);assert.equal(s.cursor.downArrowVisible,obs[3]);
  const wanted=s.regions.map(x=>x.text);if(f===frame){assert.equal(wanted[region],beforeText);wanted[region]=afterText;diffs.push(f);}assert.deepEqual(wanted,obs[2]);
  for(let k=0;k<s.regions.length;k++){
   const q=s.regions[k],text=wanted[k],row=(f>=13467&&f<13470)?13+2*k:14+2*k;
   assert.equal(q.gridRect[0],1);assert.equal(q.gridRect[1],row);assert.equal(q.gridRect[3],row+1);
   const marks=[];[...text].forEach((ch,j)=>{if(ch.normalize('NFD').includes('\u3099'))marks.push({kind:'dakuten',gridColumn:j+1,gridRow:row-1});});
   assert.deepEqual(q.markCells.map(m=>({kind:m.kind,gridColumn:m.gridColumn,gridRow:m.gridRow})),marks);
   const right=1+[...text].length-(f===frame&&k===region?delta:0);assert.equal(q.gridRect[2],right);
   assert.deepEqual(q.sourceRect,[256,96+16*(marks.length?row-1:row),240+16*right,96+16*(row+1)]);
  }
 }
 assert.deepEqual(diffs,[frame]);const idx=old.findIndex(s=>s.startFrame===frame),s=next[idx];assert.equal(s.endFrameExclusive,frame+1);
 s.regions[region].text=afterText;s.regions[region].gridRect[2]+=delta;s.regions[region].sourceRect[2]+=16*delta;
 s.evidence={manualNotes:notes[1],originalFrames:path.dirname(verification[1]),humanPerFrameVerification:'not claimed',outputIndependence:'Full original-frame double reread with GT/actual closed; prior diagnostic exposure disclosed; same agent, not independent acceptance.'};
 const restored=structuredClone(next);restored[idx]=old[idx];assert.deepEqual(restored,old);
 const target='data/gt/candidates/ocr-observation-corrections-v8/'+id;assert(!fs.existsSync(target));fs.mkdirSync(target,{recursive:true});
 for(const p of payloads.filter(p=>p.kind!=='gt'))fs.copyFileSync(p.path,target+'/'+path.basename(p.path),fs.constants.COPYFILE_EXCL);
 const candidate=target+'/ocr-candidate-boundary-v1.jsonl';fs.writeFileSync(candidate,next.map(JSON.stringify).join('\n')+'\n',{flag:'wx'});
 checks.push({id,notes:notes.map(ident),sheets:sheets.map(ident),verification:verification.map(ident),frames,allPngBytesEqual:true,allMetadataEqual:true,allTextAndArrowObservationsAgree:true,scoredFrames:old.at(-1).endFrameExclusive-old[0].startFrame});
 changes.push({id,frame,input,candidate:ident(candidate),before:old[idx],after:s,characterDelta:delta});
}
save(base+'/extraction-and-pass-check.json',{helper:ident(__filename),items:checks,retainedFrames:checks.reduce((a,x)=>a+x.frames.length,0),independentSession:false,humanPerFrameVerification:false});
save(base+'/candidate-evidence.json',{status:'candidate_only_not_effective_freeze',freeze:ident(freezeRoot+'/freeze.json'),doubleRead:ident(base+'/extraction-and-pass-check.json'),changes,rationale:'13438 already contains the exclamation mark; 24223 has only とどけも and the next cell is blank. Full affected events were reread twice. Preserve all other states, roles, timing and page opportunities.',productAcceptance:false});
console.log(JSON.stringify(changes.map(x=>({id:x.id,candidate:x.candidate,characterDelta:x.characterDelta}))));
