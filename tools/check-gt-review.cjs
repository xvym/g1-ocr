'use strict';
const fs=require('fs'),path=require('path'),crypto=require('crypto'),vm=require('vm'),assert=require('assert');
const [rootArg,reportArg]=process.argv.slice(2),root=path.resolve(rootArg),report=path.resolve(reportArg);
if(fs.existsSync(report))throw new Error('Use a new report filename');
const readJson=file=>JSON.parse(fs.readFileSync(file,'utf8').replace(/^\uFEFF/,''));
const sha=file=>crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
const html=fs.readFileSync(path.join(root,'index.html'),'utf8');
const scripts=[...html.matchAll(/<script([^>]*)>([\s\S]*?)<\/script>/g)];
const embedded=JSON.parse(scripts.find(s=>s[1].includes('application/json'))[2]);
assert.deepStrictEqual(embedded,readJson(path.join(root,'review-data.json')));
const executable=scripts.filter(s=>!s[1].includes('application/json')).map(s=>new vm.Script(s[2]));
let imageAssignments=0;
class Element{
  constructor(){this.children=[];this.textContent='';this.value=0;this.style={};}
  appendChild(child){this.children.push(child);return child;}
  replaceChildren(){this.children=[];}
  set src(value){assert(fs.existsSync(path.join(root,value)),`Missing image ${value}`);this._src=value;imageAssignments++;}
  get src(){return this._src;}
}
const ids=new Map([...html.matchAll(/id="([^"]+)"/g)].map(m=>[m[1],new Element()]));
ids.get('review-data').textContent=JSON.stringify(embedded);
const document={getElementById(id){assert(ids.has(id),`Missing DOM id ${id}`);return ids.get(id);},createElement(){return new Element();}};
const ctx=vm.createContext({document,console});executable.forEach(script=>script.runInContext(ctx));
let scoreFrames=0,totalFrames=0;
for(let scene=0;scene<embedded.items.length;scene++){
  const it=embedded.items[scene];
  vm.runInContext(`active=${scene};choose()`,ctx);
  assert.strictEqual(ids.get('states').children.length,it.states.length);
  for(let index=0;index<it.frames.length;index++){
    const f=it.frames[index];
    assert.strictEqual(f.index,it.clip.startFrame+index);
    assert.strictEqual(sha(path.join(root,f.file)),f.pngSha256);
    assert.strictEqual(f.pts,f.index*512);
    const zoom=fs.readFileSync(path.join(root,f.zoom));
    assert.strictEqual(zoom.readUInt32BE(16),(f.sourceCrop[2]-f.sourceCrop[0])*f.zoomScale);
    assert.strictEqual(zoom.readUInt32BE(20),(f.sourceCrop[3]-f.sourceCrop[1])*f.zoomScale);
    vm.runInContext(`position=${index};show()`,ctx);
    assert(ids.get('frameInfo').textContent.includes(String(f.index)));
    if(f.role==='评分'){scoreFrames++;assert(f.state);assert(f.index>=f.state.startFrame&&f.index<f.state.endFrameExclusive);}else assert(!f.state);
    totalFrames++;
  }
  vm.runInContext('position=0;show()',ctx);
  ids.get('next').onclick();assert.strictEqual(ids.get('slider').value,1);
  ids.get('prev').onclick();assert.strictEqual(ids.get('slider').value,0);
  ids.get('nextState').onclick();assert(ids.get('slider').value>0);
  for(const key of ids.get('keys').children)key.onclick();
}
assert.strictEqual(scoreFrames,94);assert.strictEqual(totalFrames,100);
const manifest=readJson(path.join(root,'bundle-manifest.json'));
for(const file of manifest.files){assert.strictEqual(sha(path.join(root,file.path)),file.sha256);assert.strictEqual(fs.statSync(path.join(root,file.path)).size,file.bytes);}
const result={status:'static_content_and_control_logic_checked',reviewRoot:root,manifestSha256:sha(path.join(root,'bundle-manifest.json')),manifestFilesChecked:manifest.files.length,sourceFrames:totalFrames,scoredFrames:scoreFrames,embeddedDataMatches:true,javaScriptCompiled:true,allFrameAndKeyControlsExecutedWithDomStub:true,imagePathAssignments:imageAssignments,realBrowserRendering:'pending; DOM stub checks logic only, not layout, native browser or human review',noOcrRun:true};
fs.writeFileSync(report,JSON.stringify(result,null,2)+'\n',{flag:'wx'});
console.log(JSON.stringify(result));
