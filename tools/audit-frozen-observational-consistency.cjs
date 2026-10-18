'use strict';
const fs=require('fs'),crypto=require('crypto'),path=require('path');
const out=process.argv[2];if(!out||fs.existsSync(out))throw Error('Use a new observation audit directory');
const json=p=>JSON.parse(fs.readFileSync(p,'utf8')),rows=p=>fs.readFileSync(p,'utf8').trim().split(/\r?\n/).map(JSON.parse);
const hash=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const canonical=v=>Array.isArray(v)?v.map(canonical):v&&typeof v==='object'?Object.fromEntries(Object.keys(v).sort().map(k=>[k,canonical(v[k])])):v;
const signature=v=>JSON.stringify(canonical(v));
const root='data/gt/frozen/ocr-all-scenes-v2-effective-r2',env=json(root+'/freeze.json'),batch=json(root+'/batch.json');
const ledger=new Map(rows('data/meta/m1-v1/frames.jsonl').map(f=>[f.mediaId+':'+f.frameIndex,f])),groups=new Map();
for(const item of env.items){const p=item.payloads.find(p=>p.kind==='gt');if(hash(p.approvedSource)!==p.sha256)throw Error('Frozen payload changed');const b=batch.items.find(b=>b.id===item.id);
 for(const state of rows(p.approvedSource))for(let n=state.startFrame;n<state.endFrameExclusive;n++){
  const f=ledger.get(state.mediaId+':'+n);if(!f)throw Error('Missing original frame ledger');
  const physical=state.regions.map(r=>{const o={};for(const k of ['text','gridRect','sourceRect','markCells','logicalRect','renderUnits'])if(r[k]!==undefined)o[k]=r[k];return o;}).sort((a,b)=>a.gridRect[1]-b.gridRect[1]||a.gridRect[0]-b.gridRect[0]);
  const role=state.regions.map(r=>({text:r.text,role:r.role,readingOrder:r.readingOrder,gridRect:r.gridRect}));
  const record={clipId:item.id,mediaId:state.mediaId,frameIndex:n,pts:f.pts,timeBase:f.timeBase,rgbSha256:f.rgbSha256,originalPng:b.frames+'/frame-'+String(n).padStart(6,'0')+'.png',stateId:state.stateId,kind:state.kind,regions:state.regions,cursor:state.cursor,detachedMarks:state.detachedMarks,physicalSignature:signature(physical),roleSignature:signature(role)};
  const k=[f.width,f.height,f.rgbSha256].join(':');if(!groups.has(k))groups.set(k,[]);groups.get(k).push(record);
 }
}
const cases=[];for(const group of groups.values()){
 if(group.length<2)continue;let best=null;
 for(let i=0;i<group.length;i++)for(let j=i+1;j<group.length;j++){
  const a=group[i],b=group[j];if(a.mediaId===b.mediaId&&a.frameIndex===b.frameIndex)continue;
  const physical=a.physicalSignature!==b.physicalSignature,role=a.roleSignature!==b.roleSignature;if(!physical&&!role)continue;
  const distance=a.mediaId===b.mediaId?Math.abs(a.frameIndex-b.frameIndex):Infinity,withinClip=a.clipId===b.clipId;
  const rank=(withinClip?0:1000000)+(Number.isFinite(distance)?distance:100000);
  if(best&&best.rank<=rank)continue;best={rank,physical,role,withinClip,frameDistance:Number.isFinite(distance)?distance:null,a,b};
 }
 if(best){for(const rec of [best.a,best.b]){delete rec.physicalSignature;delete rec.roleSignature;}delete best.rank;cases.push(best);}
}
cases.sort((a,b)=>Number(b.withinClip)-Number(a.withinClip)||(a.frameDistance??1e8)-(b.frameDistance??1e8));
fs.mkdirSync(out,{recursive:true});fs.writeFileSync(path.join(out,'report.json'),JSON.stringify({status:'identical_RGB_annotation_differences_require_original_review_not_automatic_GT_repair',freezeSha256:hash(root+'/freeze.json'),helperSha256:hash(__filename),inputBoundary:'Only frozen payloads, original frame hashes and source time ledger. No tested OCR output, glyph ranker or message index is read.',pixelIdenticalGroups:groups.size,cases:cases.length,nearbySameClipPhysicalDifferences:cases.filter(c=>c.physical&&c.withinClip&&c.frameDistance<=3).length,findings:cases,interpretation:'Identical RGB can legitimately have different temporal state kinds or contextual roles. Physical text/geometry differences, especially nearby within one clip, require direct original-image review. No case is automatically marked wrong or used to alter GT; same-shape kana and segmentation also need review.'},null,2)+'\n',{flag:'wx'});
console.log('Identical-RGB differences needing review: '+cases.length+'; nearby same-clip physical differences: '+cases.filter(c=>c.physical&&c.withinClip&&c.frameDistance<=3).length+'.');
