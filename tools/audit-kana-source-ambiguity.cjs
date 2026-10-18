'use strict';
// Diagnostic only: no GT answers, no product output mutation, no source execution.
const fs=require('fs'),cp=require('child_process'),crypto=require('crypto'),assert=require('assert/strict');
const read=p=>JSON.parse(fs.readFileSync(p)),hash=b=>crypto.createHash('sha256').update(b).digest('hex'),sha=p=>hash(fs.readFileSync(p));
const out=process.argv[2];assert(out&&!fs.existsSync(out),'Use a new report directory');
const root='reports/m3-grid-refinement-v1',auditPath=root+'/ui-source-audit-v5/report.json',audit=read(auditPath),manifest=read('resources/japanese-font-sources-v2.json');
assert.equal(sha('resources/japanese-font-sources-v2.json'),audit.fontManifestSha256);
const sources=[],catalog=[];
for(const source of manifest.sources){
 const git=args=>cp.execFileSync('git',['-C',source.checkout,...args],{env:{...process.env,GIT_NO_LAZY_FETCH:'1'},maxBuffer:8*1024*1024});
 assert.equal(git(['rev-parse','HEAD']).toString().trim(),source.commit);
 const rendererBytes=git(['show',source.commit+':home/text.asm']),renderer=rendererBytes.toString('utf8');
 const charmapBytes=git(['show',source.commit+':constants/charmap.asm']),charmap=charmapBytes.toString('utf8');
 assert(/dict\s+'#',\s+PlacePOKe/.test(renderer));assert(/PlacePOKe::\s+print_name PlacePOKeText/.test(renderer));
 const expansion=renderer.match(/PlacePOKeText::\s+db "([^"@]+)@"/);assert(expansion);
 for(const c of ['へ','ヘ'])assert(new RegExp('charmap "'+c+'", \\$cd').test(charmap));
 const audited=audit.sources.find(s=>s.id===source.id);assert.equal(audited.commit,source.commit);
 const files=[];
 for(const f of audited.files){
  assert.equal(sha(f.localCopy),f.sha256);const bytes=git(['show',source.commit+':'+f.path]);assert.equal(hash(bytes),f.sha256);
  files.push({path:f.path,sha256:f.sha256,gitBlob:f.gitBlob});
  // Whole literal text directives only. No variables, RAM, labels, dynamic concatenation or suffix guessing.
  let label='';const lines=bytes.toString('utf8').split(/\r?\n/);
  for(let i=0;i<lines.length;i++){
   const l=lines[i],lab=l.match(/^([A-Za-z_][\w.]*):/);if(lab)label=lab[1];
   const m=l.match(/^\s*(text|line|cont|para)\s+"([^"\\]*)"\s*(?:;.*)?$/);if(!m)continue;
   const literal=m[2].replace(/#/g,expansion[1]).replace(/　/g,' ');
   if(/[<>@]/.test(literal))continue;
   catalog.push({source:source.id,commit:source.commit,path:f.path,line:i+1,label,command:m[1],literal,sourceLiteral:m[2],sha256:f.sha256});
  }
 }
 sources.push({id:source.id,commit:source.commit,rendererSha256:hash(rendererBytes),charmapSha256:hash(charmapBytes),sharedHeTile:'cd',hashExpansion:expansion[1],files});
}
const paired=a=>a.length===2&&a.every(s=>[...s].length===1)&&Math.abs(a[0].codePointAt(0)-a[1].codePointAt(0))===0x60&&a.some(s=>/^[\u3041-\u3096]$/.test(s))&&a.some(s=>/^[\u30a1-\u30fa]$/.test(s));
const unique=[...new Set(catalog.map(x=>x.literal))];
const matching=region=>unique.filter(t=>{
 const chars=[...t];return chars.length===region.glyphCandidates.length&&chars.every((c,i)=>region.glyphCandidates[i].includes(c));
});
const comparisonPath=root+'/ocr-v9-observation-r12-comparison/report.json',comparison=read(comparisonPath);
const proposed=[],conflicts=[],inputs=[];let frames=0,regions=0,ambiguousRegions=0;
for(const clip of comparison.clips){
 const p=root+'/ocr-regression-all-v9/actual/'+clip.clipId+'.jsonl';assert.equal(sha(p),clip.actualSha256);inputs.push({path:p,sha256:sha(p)});
 for(const row of fs.readFileSync(p,'utf8').trim().split(/\r?\n/).map(JSON.parse)){
  frames++;for(const region of row.rawRegions){
   regions++;if(!region.glyphCandidates.some(paired))continue;ambiguousRegions++;
   const matches=matching(region);if(matches.length>1){conflicts.push({clip:clip.clipId,frame:row.sourceFrameIndex,id:region.id,matches});continue;}
   if(matches.length!==1||matches[0]===region.text)continue;
   const before=[...region.text],after=[...matches[0]],changes=[];assert.equal(before.length,after.length);
   for(let i=0;i<before.length;i++)if(before[i]!==after[i]){
    assert(paired(region.glyphCandidates[i]),'Would modify a non-shared glyph');
    changes.push({index:i,before:before[i],after:after[i],candidates:region.glyphCandidates[i]});
   }
   proposed.push({clip:clip.clipId,frame:row.sourceFrameIndex,sourcePts:row.sourcePts,id:region.id,rawText:region.text,pixelCandidateText:region.pixelCandidateText,proposedText:matches[0],changes,priorResolutions:region.candidateResolutions,sourceReferences:catalog.filter(c=>c.literal===matches[0])});
  }
 }
}
assert.equal(frames,3577);assert.equal(inputs.length,38);
// Independent algorithm-boundary cases: conflicts and unmatched text must not be chosen.
const compatibility=(literal,candidates)=>[...literal].length===candidates.length&&[...literal].every((c,i)=>candidates[i].includes(c));
assert(compatibility('へ',[['へ','ヘ']]));assert(compatibility('ヘ',[['へ','ヘ']]));
assert(!compatibility('へあ',[['へ','ヘ']]));assert(!compatibility('べ',[['へ','ヘ']]));
fs.mkdirSync(out,{recursive:true});
fs.writeFileSync(out+'/report.json',JSON.stringify({status:'source_candidate_feasibility_only_not_runtime_correction_or_acceptance',sourceAuditSha256:sha(auditPath),comparisonSha256:sha(comparisonPath),toolSha256:sha(__filename),sources,catalog,inputs,frames,regions,ambiguousRegions,proposed,conflicts,policy:{rawMustRemainUnchanged:true,sourceTextMustNeverEnterRaw:true,exactVisibleLengthOnly:true,allCurrentGlyphCandidatesMustContainSourceCharacter:true,onlyPairedKanaMayChange:true,allMatchingSourceLiteralsMustAgree:true,noFutureCharacters:true,noClipOrFrameRules:true,scope:'Only existing audited 16 renderer files per version; not a complete Japanese corpus. Unknown or conflicting lines stay unchanged.',caveat:'Uniqueness within this limited catalog is not proof that no unknown source or variable spelling exists. Runtime must disclose this as optional source-assisted interpretation, retain raw and reasons; never certify page completion from this match.'},productAcceptance:false},null,2)+'\n',{flag:'wx'});
console.log(JSON.stringify({out,sha256:sha(out+'/report.json'),frames,regions,ambiguousRegions,catalogEntries:catalog.length,uniqueLiterals:unique.length,proposed:proposed.length,conflicts:conflicts.length,affected:[...new Set(proposed.map(p=>p.clip))]}));
