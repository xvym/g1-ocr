/* ZIP / identity / UI-logic checks. No browser or Android rendering is claimed. */
'use strict';
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),zlib=require('node:zlib'),vm=require('node:vm'),assert=require('node:assert/strict');
const {ReviewZip,MobileReviewAssets,crc32,sha256Fallback}=require('./mobile-review-assets.js');
const source=path.resolve('reports/gt-review/gt-all-scenes-v2');
const companion=path.resolve(process.argv[2]||'reports/gt-review/gt-all-scenes-v2-mobile-v1');
const reportDirectory=path.resolve(process.argv[3]||'reports/m2-mobile-review-v1');
const fixtureDirectory=path.resolve(process.argv[4]||'.cache/mobile-review-v1');
const existingFixture=process.argv[5]&&path.resolve(process.argv[5]);
const hash=b=>crypto.createHash('sha256').update(b).digest('hex');
const manifestBytes=fs.readFileSync(path.join(source,'bundle-manifest.json')),manifest=JSON.parse(manifestBytes),manifestHash=hash(manifestBytes);
const identity=JSON.parse(fs.readFileSync(path.join(companion,'source-identity.json')));
assert.equal(manifestHash,'e51b7d9ebccae9e784f0b60a4a73a352b164e0d9807bd068afb2aad7c54b2dd3');
assert.equal(identity.sourceManifestSha256,manifestHash);
assert(!fs.existsSync(reportDirectory),'Refusing to overwrite check evidence');
if(!existingFixture)assert(!fs.existsSync(fixtureDirectory),'Refusing to overwrite ZIP fixture');
fs.mkdirSync(reportDirectory,{recursive:true});if(!existingFixture)fs.mkdirSync(fixtureDirectory,{recursive:true});
const checks=[];
function pass(name,details={}){checks.push({name,status:'pass',...details});}
function header(entry,offset){
 const name=Buffer.from(entry.name),bytes=entry.bytes,packed=entry.method===8?zlib.deflateRawSync(bytes):bytes;
 const local=Buffer.alloc(30+name.length);local.writeUInt32LE(0x04034b50);local.writeUInt16LE(20,4);local.writeUInt16LE(0x800,6);local.writeUInt16LE(entry.method||0,8);local.writeUInt32LE(crc32(bytes),14);local.writeUInt32LE(packed.length,18);local.writeUInt32LE(bytes.length,22);local.writeUInt16LE(name.length,26);name.copy(local,30);
 const central=Buffer.alloc(46+name.length);central.writeUInt32LE(0x02014b50);central.writeUInt16LE(20,4);central.writeUInt16LE(20,6);central.writeUInt16LE(0x800,8);central.writeUInt16LE(entry.method||0,10);central.writeUInt32LE(crc32(bytes),16);central.writeUInt32LE(packed.length,20);central.writeUInt32LE(bytes.length,24);central.writeUInt16LE(name.length,28);central.writeUInt32LE(offset,42);name.copy(central,46);
 return {local,central,packed};
}
function endRecord(count,directorySize,offset){const b=Buffer.alloc(22);b.writeUInt32LE(0x06054b50);b.writeUInt16LE(count,8);b.writeUInt16LE(count,10);b.writeUInt32LE(directorySize,12);b.writeUInt32LE(offset,16);return b;}
function smallZip(entries){const parts=[],directory=[];let offset=0;for(const entry of entries){const h=header(entry,offset);parts.push(h.local,h.packed);directory.push(h.central);offset+=h.local.length+h.packed.length;}const table=Buffer.concat(directory);return Buffer.concat([...parts,table,endRecord(entries.length,table.length,offset)]);}
function makeFullStoredZip(target){
 const prefix='g1-ocr-main/reports/gt-review/gt-all-scenes-v2/',fd=fs.openSync(target,'wx'),directory=[];let offset=0;
 function write(bytes){let position=0;while(position<bytes.length)position+=fs.writeSync(fd,bytes,position,bytes.length-position);}
 try{for(const name of [...manifest.files.map(f=>f.path),'bundle-manifest.json']){const bytes=fs.readFileSync(path.join(source,name)),h=header({name:prefix+name,bytes},offset);write(h.local);write(h.packed);directory.push(h.central);offset+=h.local.length+h.packed.length;}const table=Buffer.concat(directory);write(table);write(endRecord(directory.length,table.length,offset));}
 finally{fs.closeSync(fd);}
 return {prefix,bytes:fs.statSync(target).size,entries:directory.length};
}
function gitTransportZip(base,archive,variants,prefix){
 const parts=[],directory=[];let offset=base.size;
 for(const entry of archive.entries.values()){
  const relative=entry.name.startsWith(prefix)?entry.name.slice(prefix.length):null;
  if(relative&&variants[relative]){const bytes=Buffer.from(fs.readFileSync(path.join(source,relative),'utf8').replace(/\r\n/g,'\n'));assert.equal(hash(bytes),variants[relative].sha256);const h=header({name:entry.name,bytes,method:8},offset);parts.push(h.local,h.packed);directory.push(h.central);offset+=h.local.length+h.packed.length;}
  else{const h=header({name:entry.name,bytes:Buffer.alloc(0)},entry.offset),c=h.central;c.writeUInt16LE(entry.flags,8);c.writeUInt16LE(entry.method,10);c.writeUInt32LE(entry.crc,16);c.writeUInt32LE(entry.packed,20);c.writeUInt32LE(entry.bytes,24);directory.push(c);}
 }
 const table=Buffer.concat(directory);
 // A normal ZIP update appends replacement entries and a new final directory.
 return new Blob([base,...parts,table,endRecord(directory.length,table.length,offset)]);
}
class Element{
 constructor(){this.children=[];this.style={};this.attributes={};this.value='';this.checked=false;this.hidden=false;this.textContent='';}
 appendChild(element){this.children.push(element);return element;}
 replaceChildren(){this.children=[];}
 setAttribute(key,value){this.attributes[key]=String(value);}
 getAttribute(key){return this.attributes[key];}
 removeAttribute(key){delete this.attributes[key];delete this[key];}
 scrollIntoView(){}
}
async function main(){
 for(const length of [0,1,55,56,63,64,65,127,128,1024,65537]){const bytes=crypto.randomBytes(length);assert.equal(sha256Fallback(bytes),hash(bytes));}
 assert.equal(crc32(Buffer.from('123456789')),0xcbf43926);pass('SHA-256 fallback and CRC-32 vectors');
 const tiny=smallZip([{name:'日本語.txt',bytes:Buffer.from('日本語 UTF-8'),method:8},{name:'stored.txt',bytes:Buffer.from('stored'),method:0}]);
 const zip=await ReviewZip.open(new Blob([tiny]));assert.equal(Buffer.from(await zip.read('日本語.txt',Buffer.byteLength('日本語 UTF-8'))).toString(),'日本語 UTF-8');assert.equal(Buffer.from(await zip.read('stored.txt',6)).toString(),'stored');pass('Ordinary DEFLATE and stored ZIP / UTF-8 paths');
 await assert.rejects(()=>ReviewZip.open(new Blob([tiny.subarray(0,-1)])),/尾部/);
 const badCrc=Buffer.from(tiny);badCrc[30+Buffer.byteLength('日本語.txt')+2]^=1;const corruptZip=await ReviewZip.open(new Blob([badCrc]));await assert.rejects(()=>corruptZip.read('日本語.txt',Buffer.byteLength('日本語 UTF-8')));
 await assert.rejects(()=>zip.read('stored.txt',7),/大小/);
 await assert.rejects(()=>ReviewZip.open(new Blob([smallZip([{name:'../escape',bytes:Buffer.from('x')}])])),/路径/);
 const duplicate=smallZip([{name:'same',bytes:Buffer.from('a')},{name:'same',bytes:Buffer.from('b')}]);await assert.rejects(()=>ReviewZip.open(new Blob([duplicate])),/重复/);
 const zip64=Buffer.from(tiny);zip64.writeUInt16LE(0xffff,zip64.length-12);await assert.rejects(()=>ReviewZip.open(new Blob([zip64])),/分卷|ZIP64/);
 const encrypted=Buffer.from(tiny);const centralStart=encrypted.readUInt32LE(encrypted.length-6);encrypted.writeUInt16LE(0x801,centralStart+8);const encryptedZip=await ReviewZip.open(new Blob([encrypted]));await assert.rejects(()=>encryptedZip.read('日本語.txt',Buffer.byteLength('日本語 UTF-8')),/加密/);
 const large=Buffer.from(tiny);large.writeUInt32LE(17*1024*1024,centralStart+24);const largeZip=await ReviewZip.open(new Blob([large]));await assert.rejects(()=>largeZip.read('日本語.txt',17*1024*1024),/大小/);pass('Reject truncated, corrupted, oversized, encrypted, traversal, duplicate and ZIP64 inputs');
 const html=fs.readFileSync(path.join(companion,'index.html'),'utf8'),scripts=[...html.matchAll(/<script([^>]*)>([\s\S]*?)<\/script>/g)];assert.equal(scripts.length,4);
 const data=JSON.parse(scripts[0][2]);assert.deepEqual(data,JSON.parse(fs.readFileSync(path.join(source,'review-data.json'))));assert.equal(scripts[1][2],manifestBytes.toString());assert.equal(hash(Buffer.from(html)),identity.companionHtmlSha256);assert.equal(hash(Buffer.from(scripts[2][2].trim())),hash(fs.readFileSync('tools/mobile-review-assets.js','utf8').trim()));
 const elements=new Map([...html.matchAll(/\bid="([^"]+)"/g)].map(m=>[m[1],new Element()]));elements.get('review-data').textContent=scripts[0][2];elements.get('asset-manifest').textContent=scripts[1][2];
 const anchors=[...html.matchAll(/<a\b[^>]*href="([^"]+)"[^>]*>/g)].map(m=>{const e=new Element();e.attributes.href=m[1];return e;});
 const document={getElementById:id=>{assert(elements.has(id),'Missing DOM id '+id);return elements.get(id);},createElement:()=>new Element(),createElementNS:()=>new Element(),querySelectorAll:()=>anchors};
 const urls=new Map();let maximumLiveUrls=0,serial=0;
 const urlApi={createObjectURL:blob=>{const url='blob:check-'+(++serial);urls.set(url,blob);maximumLiveUrls=Math.max(maximumLiveUrls,urls.size);return url;},revokeObjectURL:url=>urls.delete(url)};
 const context=vm.createContext({document,window:{addEventListener(){}},console,URL:urlApi,Blob,TextEncoder,TextDecoder,DataView,Uint8Array,Uint32Array,DecompressionStream});
 new vm.Script(scripts[2][2]).runInContext(context);new vm.Script(scripts[3][2]).runInContext(context,{timeout:15000});
 const assets=vm.runInContext('mobileAssets',context);assert.equal(elements.get('full').src,undefined);assert.equal(elements.get('full').hidden,true);assert.equal(urls.size,0);assert(anchors.every(a=>a.href==='#assetDetail'));pass('Original payload / manifest unchanged; no relative-image reads before user selection');
 const archivePath=existingFixture||path.join(fixtureDirectory,'repository-layout-stored.zip');const archiveIdentity=existingFixture?{prefix:'g1-ocr-main/reports/gt-review/gt-all-scenes-v2/',bytes:fs.statSync(archivePath).size,entries:7637,reusedReadOnlyFixture:true}:makeFullStoredZip(archivePath);const archiveFile=await fs.openAsBlob(archivePath);
 const ranges=[];const limitedFile={size:archiveFile.size,slice(start,end=archiveFile.size){ranges.push([start,end]);assert(end-start<=32*1024*1024,'Unbounded archive read');return archiveFile.slice(start,end);}};
 await assets.open(limitedFile);await assets.queue;assert(assets.archive);assert(elements.get('full').src.startsWith('blob:'));assert.equal(assets.prefix,archiveIdentity.prefix);assert.equal(assets.expected.size,7637);pass('Real full-size ZIP with GitHub-style prefix and bounded reads',archiveIdentity);
 const parsed=assets.archive;for(const f of manifest.files){assert.equal(parsed.entries.get(assets.prefix+f.path).bytes,f.bytes);}
 let frames=0,states=0;
 for(let i=0;i<data.items.length;i++){
  vm.runInContext(`active=${i};choose()`,context);
  for(let j=0;j<data.items[i].frames.length;j++){
   vm.runInContext(`position=${j};show()`,context);
   const item=data.items[i],frame=item.frames[j],state=frame.stateIndex<0?null:item.states[frame.stateIndex];assert.equal(assets.bindings.get(elements.get('full')),frame.file);assert.equal(assets.bindings.get(elements.get('zoom')),frame.zoom);assert.equal(elements.get('full').hidden,true);assert.equal(elements.get('text').textContent,state?(state.regions.map(r=>r.text).join('\n')||'目标文字为空'):'上下文：未赋评分 GT');frames++;
  }
  for(let k=0;k<data.items[i].states.length;k++){elements.get('statePick').value=String(k);elements.get('statePick').onchange();assert.equal(assets.bindings.get(elements.get('full')),data.items[i].frames.find(f=>f.index===data.items[i].states[k].startFrame).file);states++;}
  await assets.queue;
  for(const id of ['full','zoom']){const element=elements.get(id),expected=assets.expected.get(assets.bindings.get(element));assert.equal(hash(Buffer.from(await urls.get(element.src).arrayBuffer())),expected.sha256);}
  vm.runInContext('position=0;show()',context);elements.get('prev').onclick();assert.equal(Number(elements.get('slider').value),0);vm.runInContext('position=current().frames.length-1;show()',context);elements.get('next').onclick();assert.equal(Number(elements.get('slider').value),data.items[i].frames.length-1);elements.get('boxes').checked=true;elements.get('boxes').onchange();await assets.queue;
 }
 const stats=vm.runInContext('collectStats()',context);assert.equal(stats.frames,3361);assert.equal(stats.newFrames,3326);assert.equal(stats.pages,117);assert.equal(frames,3577);assert.equal(states,717);pass('All 38 scenes / 3577 frame controls / 717 state jumps; source bytes on scene samples',{frames,states,sceneImageSamples:76});
 const gt=data.items[0].links.gt;await assets.view(gt);assert.equal(elements.get('assetText').textContent,fs.readFileSync(path.join(source,gt),'utf8'));assert.equal(elements.get('assetDetail').hidden,false);
 const gap=data.gapFrames[0].frames[0].file;await assets.view(gap);assert.equal(hash(Buffer.from(await urls.get(elements.get('assetImage').src).arrayBuffer())),assets.expected.get(gap).sha256);assert(maximumLiveUrls<=8);pass('Attachments / gap images / stale image suppression / bounded object URLs',{maximumLiveUrls});
 const transportNames=Object.keys(assets.transportVariants);
 assert.equal(transportNames.length,363);
 const transportFile=gitTransportZip(archiveFile,assets.archive,assets.transportVariants,assets.prefix);
 await assets.open(transportFile);await assets.queue;
 for(const name of transportNames){const restored=await assets.read(name);assert.equal(hash(restored),assets.expected.get(name).sha256);assert(Buffer.from(restored).equals(fs.readFileSync(path.join(source,name))));}
 pass('Git-normalized ZIP texts / ordinary DEFLATE / exact original-byte restoration',{normalizedFiles:transportNames.length,allOriginalHashesRestored:true});
 // Same declared length and matching ZIP CRC still must fail the original SHA-256.
 const changed=Buffer.from('tampered'),original=Buffer.from('original');const fakeManifest=JSON.stringify({files:[{path:'sample.txt',bytes:original.length,sha256:hash(original)}]});const fakeZip=smallZip([{name:'bundle-manifest.json',bytes:Buffer.from(fakeManifest)},{name:'sample.txt',bytes:changed}]);
 const isolated=new MobileReviewAssets(fakeManifest,hash(Buffer.from(fakeManifest)),new Element(),document);await isolated.open(new Blob([fakeZip]));await assert.rejects(()=>isolated.read('sample.txt'),/SHA-256/);
 await assert.rejects(()=>assets.open(new Blob([tiny])),/没有找到/);assert.equal(assets.archive,null);assert.equal(urls.size,0);assert.equal(elements.get('full').hidden,true);pass('Reject changed asset SHA-256 / wrong review ZIP; clear stale images on reselection');
 assets.dispose();
 const postManifest=fs.readFileSync(path.join(source,'bundle-manifest.json'));assert.equal(hash(postManifest),manifestHash);for(const f of manifest.files){const bytes=fs.readFileSync(path.join(source,f.path));assert.equal(bytes.length,f.bytes);assert.equal(hash(bytes),f.sha256);}
 pass('All 7636 source files unchanged after checks');
 const report={status:'zip_identity_and_control_logic_checks_pass',reviewId:'gt-all-scenes-v2',sourceManifestSha256:manifestHash,companionHtmlSha256:hash(Buffer.from(html)),runtimeSha256:hash(fs.readFileSync('tools/mobile-review-assets.js')),checkerSha256:hash(fs.readFileSync(__filename)),checks,largestArchiveRead:Math.max(...ranges.map(r=>r[1]-r[0])),actualBrowserRendering:'unverified',actualAndroidRendering:'unverified',limitations:'These are deterministic file, ZIP and DOM-stub logic checks; no real browser, Android device, OCR or product acceptance is claimed.'};
 fs.writeFileSync(path.join(reportDirectory,'check.json'),JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify(report,null,2));
}
main().catch(error=>{fs.writeFileSync(path.join(reportDirectory,'failure.json'),JSON.stringify({status:'failed',message:error.message,stack:error.stack,checks},null,2)+'\n');console.error(error);process.exitCode=1;});
