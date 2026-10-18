'use strict';
// Source-only configuration builder: verifies local Git objects; no GT or OCR input.
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
fs.mkdirSync(out,{recursive:true});
fs.writeFileSync(out+'/catalog.json',JSON.stringify({schema:'japanese-source-aliases-v1',sources,catalog},null,2)+'\n',{flag:'wx'});
console.log(JSON.stringify({path:out+'/catalog.json',sha256:sha(out+'/catalog.json'),entries:catalog.length}));
