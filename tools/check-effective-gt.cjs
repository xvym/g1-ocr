'use strict';
const fs=require('fs'),path=require('path'),crypto=require('crypto'),cp=require('child_process');
const {audit}=require('./check-gt-overlaps.cjs');
const assert=(ok,msg)=>{if(!ok)throw Error(msg);},json=p=>JSON.parse(fs.readFileSync(p,'utf8'));
const hash=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const [collection,out]=process.argv.slice(2);assert(collection&&out&&!fs.existsSync(out),'Use a new report directory');const relative=path.relative(process.cwd(),path.resolve(out));assert(relative&&!relative.startsWith('..')&&!path.isAbsolute(relative),'Report outside workspace');fs.mkdirSync(out,{recursive:true});
const envelope=json(path.join(collection,'freeze.json')),batch=json(path.join(collection,'batch.json'));
assert(envelope.status==='approved_frozen_collection','Approved collection required');
const overlap=audit(path.join(collection,'batch.json'));assert(!overlap.conflicts.length&&overlap.overlappingReferences===7,'Effective collection conflict');
const classpath=['verification/target/classes','app/target/classes','.cache/m2/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar'].join(path.delimiter),checks=[];
for(const item of batch.items){const report=path.join(out,item.id+'.json'),args=['-Dfile.encoding=UTF-8','-cp',classpath,'io.github.g1ocr.verification.VideoTool','gt-check','data/meta/m1-v1',item.clip,item.candidate,item.layout,report];const r=cp.spawnSync('java',args,{encoding:'utf8',maxBuffer:4*1024*1024});fs.writeFileSync(path.join(out,item.id+'.log'),r.stdout+r.stderr,{flag:'wx'});assert(!r.error&&r.status===0,'GT integrity failed '+item.id);checks.push({id:item.id,command:['java',...args],reportSha256:hash(report),integrity:json(report)});console.log('Effective GT checked: '+item.id);}
const result={status:'approved_effective_development_GT_integrity_checked_not_OCR_acceptance',collectionSha256:hash(path.join(collection,'freeze.json')),baseEnvelope:envelope.baseFreeze,amendmentEnvelope:envelope.replacement.envelope,sourceScope:envelope.summary,strictOverlap:overlap,checks,independentAcceptance:'pending_no_proven_unused_independent_session',unknownConflictOrUnreviewedScoredStates:0};
fs.writeFileSync(path.join(out,'report.json'),JSON.stringify(result,null,2)+'\n',{flag:'wx'});console.log('All '+checks.length+' effective entries structurally checked; 7 overlaps consistent.');
