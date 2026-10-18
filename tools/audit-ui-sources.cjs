'use strict';
const fs=require('fs'),path=require('path'),crypto=require('crypto'),cp=require('child_process');
const hash=b=>crypto.createHash('sha256').update(b).digest('hex'),read=p=>JSON.parse(fs.readFileSync(p,'utf8')),assert=(b,m)=>{if(!b)throw Error(m);};
const [manifestPath,out,cache]=process.argv.slice(2);assert(manifestPath&&out&&cache&&!fs.existsSync(out)&&!fs.existsSync(cache),'Use manifest and new report/cache directories');
const manifest=read(manifestPath),font=read(manifest.fontSourceManifest),root=path.resolve('.');
assert(path.resolve(cache).startsWith(path.join(root,'.cache')+path.sep),'Source copies belong only in ignored .cache');
fs.mkdirSync(out,{recursive:true});fs.mkdirSync(cache,{recursive:true});const sources=[];
for(const source of font.sources){const git=args=>cp.execFileSync('git',['-c','http.sslBackend=openssl','-C',source.checkout,...args],{maxBuffer:20*1024*1024});assert(git(['rev-parse','HEAD']).toString().trim()===source.commit,'Pinned source commit changed');const files=[];
 for(const name of manifest.files){assert(/^[a-zA-Z0-9_./-]+\.asm$/.test(name)&&!name.split('/').includes('..'),'Invalid source path');const bytes=git(['show',source.commit+':'+name]),target=path.join(cache,source.id,name);fs.mkdirSync(path.dirname(target),{recursive:true});fs.writeFileSync(target,bytes,{flag:'wx'});assert(hash(fs.readFileSync(target))===hash(bytes),'Source copy differs');files.push({path:name,sha256:hash(bytes),gitBlob:git(['rev-parse',source.commit+':'+name]).toString().trim(),localCopy:target.replace(/\\/g,'/'),url:source.url+'/blob/'+source.commit+'/'+name});}
 sources.push({id:source.id,commit:source.commit,files});}
fs.writeFileSync(path.join(out,'report.json'),JSON.stringify({status:'pinned_UI_renderer_sources_read_not_OCR_acceptance',manifestSha256:hash(fs.readFileSync(manifestPath)),fontManifestSha256:hash(fs.readFileSync(manifest.fontSourceManifest)),sources,noExternalCodeExecuted:true,redistribution:'Source copies remain ignored cache; only identities are reported'},null,2)+'\n',{flag:'wx'});
console.log('Pinned renderer source copies verified: '+sources.length+' sources, '+manifest.files.length+' files each.');
