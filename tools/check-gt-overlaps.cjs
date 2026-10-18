'use strict';
// Pre-OCR metadata consistency. No image recognition or answer inference.
const fs=require('fs'),crypto=require('crypto');
const sha=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const parse=p=>JSON.parse(fs.readFileSync(p,'utf8'));
function stable(x){if(Array.isArray(x))return x.map(stable);if(x&&typeof x==='object')return Object.fromEntries(Object.keys(x).sort().map(k=>[k,stable(x[k])]));return x;}
function fields(x,names){return Object.fromEntries(names.filter(k=>x&&x[k]!=null).map(k=>[k,x[k]]));}
function symbols(value){return (value==null?[]:Array.isArray(value)?value:[value]).map(x=>{const o=fields(x,['kind','style','gridRect','sourceRect']);if(o.style==='outlined_right')o.style='outline_right';return o;});}
function visible(s){const c=s.cursor||{},arrow=!!c.downArrowVisible;return stable({kind:s.kind,completePage:s.completePage,regions:s.regions.map(r=>fields(r,['role','readingOrder','text','gridRect','sourceRect','markCells','logicalRect','renderUnits'])),cursor:{menuCursors:symbols(c.menuCursors),downArrowVisible:arrow,...(arrow?{downArrow:c.downArrow?fields(c.downArrow,['style','gridRect','sourceRect']):fields(c,['gridRect','sourceRect'])}:{}),additionalDownArrows:symbols(c.additionalDownArrows)},detachedMarks:symbols(s.detachedMarks)});}
function audit(batchPath){const batch=parse(batchPath),seen=new Map(),conflicts=[],overlaps=[],inputs=[];let references=0;
  for(const item of batch.items){inputs.push({id:item.id,path:item.candidate,sha256:sha(item.candidate)});for(const s of fs.readFileSync(item.candidate,'utf8').trim().split(/\r?\n/).map(JSON.parse))for(let n=s.startFrame;n<s.endFrameExclusive;n++){
    references++;const key=s.mediaId+':'+n,v=visible(s),value={item:item.id,stateId:s.stateId,visible:v};
    if(seen.has(key)){const previous=seen.get(key),same=JSON.stringify(previous.visible)===JSON.stringify(v);overlaps.push({key,items:[previous.item,item.id],consistent:same});if(!same)conflicts.push({key,a:previous,b:value});}else seen.set(key,value);
  }}
  return {status:conflicts.length?'conflicting_frozen_scoring_metadata':'cross_clip_scoring_metadata_consistent',batch:batchPath,batchSha256:sha(batchPath),frameReferences:references,uniqueScoredFrames:seen.size,overlappingReferences:overlaps.length,conflicts,overlaps,inputs,scope:'Exact frozen field role, readingOrder, kind, complete-page flag, literal text and visible geometry; only the already-approved triangle descriptor spelling alias is applied. No OCR outputs consumed.'};
}
module.exports={audit,visible};
if(require.main===module){const [batch,out]=process.argv.slice(2);if(!batch||!out||fs.existsSync(out))throw Error('Usage: node tools/check-gt-overlaps.cjs <batch.json> <new-report.json>');const report=audit(batch);fs.writeFileSync(out,JSON.stringify(report,null,2)+'\n',{flag:'wx'});console.log(report.status+': '+report.overlappingReferences+' overlap references, '+report.conflicts.length+' conflicts');process.exitCode=report.conflicts.length?3:0;}
