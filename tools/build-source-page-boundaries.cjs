'use strict';
// Source-only builder. No GT, images, observed messages, frame indexes or OCR scores.
const fs=require('fs'),cp=require('child_process'),crypto=require('crypto'),assert=require('assert/strict');
const out=process.argv[2];assert(out&&!fs.existsSync(out));const json=p=>JSON.parse(fs.readFileSync(p,'utf8')),hash=b=>crypto.createHash('sha256').update(b).digest('hex');
const catalog=json('.cache/japanese-source-aliases-v1/catalog.json'),manifest=json('resources/japanese-font-sources-v2.json'),pages=[],files=[],skipped=[];
function display(s){return s.replaceAll('#','ポケモン').replaceAll('　',' ').replace(/[０-９Ａ-Ｚａ-ｚ]/g,c=>String.fromCharCode(c.charCodeAt(0)-0xfee0)).replaceAll('？','?').replaceAll('／','/').replaceAll('⋯','…').replaceAll('·','・').replaceAll('．','.').trimEnd();}
for(const source of manifest.sources){
 const git=args=>cp.execFileSync('git',['-C',source.checkout,...args],{encoding:'utf8',env:{...process.env,GIT_NO_LAZY_FETCH:'1'},maxBuffer:16*1024*1024});assert.equal(git(['rev-parse','HEAD']).trim(),source.commit);
 const tree=git(['ls-tree','-r',source.commit,'--','engine','scripts','home','data']).trim().split('\n').map(l=>l.match(/^100644 blob ([0-9a-f]+)\t(.+\.asm)$/)).filter(Boolean);
 for(const [,blob,rel]of tree){const p=source.checkout+'/'+rel;if(!fs.existsSync(p)){skipped.push({source:source.id,path:rel,reason:'not locally materialized'});continue;}const bytes=fs.readFileSync(p),actual=crypto.createHash('sha1').update(Buffer.from('blob '+bytes.length+'\0')).update(bytes).digest('hex');assert.equal(actual,blob,p);const sha256=hash(bytes);files.push({source:source.id,commit:source.commit,path:p,relative:rel,sha256,gitBlob:blob});
  const lines=bytes.toString('utf8').split(/\r?\n/),blocks=[];let current=null;
  for(let i=0;i<lines.length;i++){const m=lines[i].match(/^([A-Za-z_][\w.]*):/);if(m){current={label:m[1],lines:[]};blocks.push(current);}if(current)current.lines.push({line:lines[i],number:i+1});}
  for(const block of blocks){if(block.lines.some(l=>/^\s*(IF|ELIF|ELSE|ENDC)\b/.test(l.line)))continue;let upper=null,lower=null,valid=false,lastLine=0;
   const emit=(control,at)=>{if(valid&&upper!==null&&upper.length&&(!/[<>]/.test(upper.replace(/<(PLAYER|RIVAL)>/g,'')))&&(!lower||!/[<>]/.test(lower.replace(/<(PLAYER|RIVAL)>/g,''))))pages.push({upper,lower:lower||'',control,source:source.id,commit:source.commit,path:rel,label:block.label,line:lastLine,boundaryLine:at,sha256});};
   for(const entry of block.lines){const line=entry.line.replace(/\s*;.*$/,'').trim(),m=line.match(/^(text|line|cont|para)\s+"([^"\\]*)"$/);
    if(m){const command=m[1],hasStop=m[2].includes('@'),literal=display(m[2].split('@')[0]);
     if(command==='text'){if(upper!==null){valid=false;continue;}upper=literal;lower=null;valid=true;}
     if(command==='line'){if(upper===null){valid=false;continue;}lower=literal;}
     if(command==='para'){emit('paragraph_wait',entry.number);upper=literal;lower=null;valid=true;}
     if(command==='cont'){emit('scroll_wait',entry.number);if(lower===null){valid=false;continue;}upper=lower;lower=literal;}
     lastLine=entry.number;if(hasStop){emit('string_end',entry.number);valid=false;}continue;
    }
    if(/^(done|prompt)\b/.test(line)){emit(line.split(/\s/)[0],entry.number);upper=null;lower=null;valid=false;}
    else if(/^text_(ram|decimal|bcd|far|asm|move|scroll)|^(next|scrl|_cnt)\b/.test(line))valid=false;
   }
  }
 }
}
assert.equal(skipped.length,0);assert(pages.length>1000);
fs.mkdirSync(out,{recursive:true});const config={...catalog,pageBoundarySchema:'literal-page-boundaries-v1',pageBoundaries:pages,pageBoundarySources:files,pageBoundaryLimitations:'Conservative supported literal text/line/cont/para/done/prompt/string-end paths only; PLAYER/RIVAL bounded names. Unsupported dynamic commands and conditional definitions are not certified. Source evidence never corrects raw text or GT.'};
fs.writeFileSync(out+'/catalog.json',JSON.stringify(config,null,2)+'\n',{flag:'wx'});fs.writeFileSync(out+'/source-audit.json',JSON.stringify({status:'pinned_source_only_page_boundary_catalog_not_video_validation',sources:manifest.sources,files:files.length,pages:pages.length,skipped,configSha256:hash(fs.readFileSync(out+'/catalog.json')),builderSha256:hash(fs.readFileSync(__filename)),allSourceBlobsMatchPinnedCommit:true},null,2)+'\n',{flag:'wx'});console.log(JSON.stringify({files:files.length,pages:pages.length,sha256:hash(fs.readFileSync(out+'/catalog.json'))}));
