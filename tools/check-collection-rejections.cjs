'use strict';
const fs=require('fs'),path=require('path'),crypto=require('crypto'),cp=require('child_process');
const [source,fixture,out]=process.argv.slice(2),assert=(ok,msg)=>{if(!ok)throw Error(msg);};
assert(source&&fixture&&out&&!fs.existsSync(fixture)&&!fs.existsSync(out),'Use new fixture/report directories');for(const p of [fixture,out]){const rel=path.relative(process.cwd(),path.resolve(p));assert(rel&&!rel.startsWith('..')&&!path.isAbsolute(rel),'Output outside workspace');fs.mkdirSync(p,{recursive:true});}
const hash=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex'),envelope=JSON.parse(fs.readFileSync(path.join(source,'freeze.json'),'utf8'));
const classes=['verification/target/classes','app/target/classes','.cache/m2/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar'].join(path.delimiter),checks=[];
for(const [name,change] of [
 ['wrong-base-hash',e=>e.baseFreeze.sha256='0'.repeat(64)],
 ['unknown-replacement-id',e=>e.replacement.id='not-an-approved-entry'],
 ['missing-original-item',e=>e.items.pop()],
 ['wrong-payload-provenance',e=>{const a=e.items[0].payloads.find(p=>p.kind==='gt'),b=e.items[1].payloads.find(p=>p.kind==='gt');Object.assign(a,{approvedSource:b.approvedSource,sha256:b.sha256,bytes:b.bytes});}]
]) {
 const dir=path.join(fixture,name);fs.mkdirSync(dir);fs.copyFileSync(path.join(source,'batch.json'),path.join(dir,'batch.json'),fs.constants.COPYFILE_EXCL);
 const e=JSON.parse(JSON.stringify(envelope));e.batchIdentity={path:path.join(dir,'batch.json').replace(/\\/g,'/'),sha256:hash(path.join(dir,'batch.json')),bytes:fs.statSync(path.join(dir,'batch.json')).size};change(e);fs.writeFileSync(path.join(dir,'freeze.json'),JSON.stringify(e,null,2)+'\n',{flag:'wx'});
 const output=path.join(out,name),args=['-Dfile.encoding=UTF-8','-cp',classes,'io.github.g1ocr.verification.VideoTool','replay-audit',dir,'shop-exit',output,'unpaced'];const result=cp.spawnSync('java',args,{encoding:'utf8',maxBuffer:4*1024*1024});fs.writeFileSync(path.join(out,name+'.log'),result.stdout+result.stderr,{flag:'wx'});assert(!result.error&&result.status===3&&!fs.existsSync(path.join(output,'report.json')),'Invalid collection was not rejected before replay');checks.push({name,status:'rejected_before_frame_consumer',command:['java',...args],exitCode:result.status});
}
fs.writeFileSync(path.join(out,'report.json'),JSON.stringify({status:'approved_collection_negative_checks_passed',sourceFreezeSha256:hash(path.join(source,'freeze.json')),checks,scope:'Deliberately corrupted metadata copies only. No approved files, original media or GT changed.'},null,2)+'\n',{flag:'wx'});console.log('All4 corrupt collection fixtures rejected before frame delivery.');
