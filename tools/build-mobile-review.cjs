'use strict';
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),vm=require('node:vm'),cp=require('node:child_process');
const source=path.resolve(process.argv[2]||'reports/gt-review/gt-all-scenes-v2');
const output=path.resolve(process.argv[3]||'reports/gt-review/gt-all-scenes-v2-mobile-v1');
const expectedManifest=process.argv[4]||'e51b7d9ebccae9e784f0b60a4a73a352b164e0d9807bd068afb2aad7c54b2dd3';
const hash=b=>crypto.createHash('sha256').update(b).digest('hex');
const must=(condition,message)=>{if(!condition)throw Error(message)};
must(!fs.existsSync(output),'Refusing to overwrite an existing companion directory');
must(!output.startsWith(source+path.sep),'Companion must be outside the immutable source package');
const manifestBytes=fs.readFileSync(path.join(source,'bundle-manifest.json'));
must(hash(manifestBytes)===expectedManifest,'Source review manifest identity changed');
const manifestText=manifestBytes.toString('utf8'),manifest=JSON.parse(manifestText);
const tracked=new Map(cp.execFileSync('git',['ls-files','--stage','-z','--',source],{maxBuffer:10000000}).toString().split('\0').filter(Boolean).map(line=>{const match=line.match(/^\d+ ([0-9a-f]+) \d\t(.+)$/);must(match,'Unexpected Git index entry');return[match[2],match[1]]}));
const gitOid=bytes=>crypto.createHash('sha1').update(Buffer.from('blob '+bytes.length+'\0')).update(bytes).digest('hex');
const transportVariants={};
for(const f of manifest.files){
 const target=path.resolve(source,f.path);must(target.startsWith(source+path.sep),'Unsafe source path');const bytes=fs.readFileSync(target);must(bytes.length===f.bytes&&hash(bytes)===f.sha256,'Source file identity changed: '+f.path);
 const indexedOid=tracked.get(path.relative(process.cwd(),target).replaceAll('\\','/'));if(!indexedOid)continue; // Local review ZIP retains exact approved bytes; no Git transport variant is asserted.
 if(gitOid(bytes)!==indexedOid){const text=bytes.toString('utf8'),normalized=Buffer.from(text.replace(/\r\n/g,'\n'));must(gitOid(normalized)===indexedOid,'Git source differs beyond newline transport: '+f.path);const crlf=[];let ordinal=0;for(let i=0;i<text.length;i++)if(text[i]==='\n'){if(i&&text[i-1]==='\r')crlf.push(ordinal);ordinal++;}transportVariants[f.path]={bytes:normalized.length,sha256:hash(normalized),gitBlobOid:indexedOid,crlfOrdinals:crlf.length===ordinal?'all':crlf};}
}
let html=fs.readFileSync(path.join(source,'index.html'),'utf8');
const runtime=fs.readFileSync(path.join(__dirname,'mobile-review-assets.js'),'utf8');
function replaceOnce(before,after){must(html.split(before).length===2,'Expected exactly one HTML anchor: '+before.slice(0,90));html=html.replace(before,()=>after)}
replaceOnce('<title>全部场景日文 GT · '+manifest.reviewId+'</title>','<title>手机离线审阅 · '+manifest.reviewId+'</title>');
replaceOnce('<style>','<style>\n[hidden]{display:none!important}');
const heading=html.match(/<h1>[^<]+<\/h1>/);must(heading,'Missing review heading');replaceOnce(heading[0],heading[0].replace('</h1>',' · 手机离线入口</h1>'));
replaceOnce('<section class="card"><h2>覆盖与范围</h2>',`<section class="card"><h2>打开手机里的资料包</h2><p>选择已下载的完整审阅目录 ZIP，或包含该目录的 GitHub 仓库 ZIP。无需解压，文件只在本机读取。连接后即可逐帧看图和打开附件。</p><label for="reviewZip">选择资料 ZIP</label> <input id="reviewZip" type="file" accept=".zip,application/zip,application/x-zip-compressed"><p id="assetStatus" role="status" aria-live="polite">尚未选择资料包，图片暂不显示。</p><noscript>请用支持 JavaScript 的浏览器打开本文件。</noscript></section><section class="card"><h2>覆盖与范围</h2>`);
replaceOnce('页面无需网络或本地服务，直接打开本机 index.html。','此入口使用你选择的本地 ZIP 读取原审阅包。原 GT、原帧、范围、缺口及批准对象仍是 '+manifest.reviewId+'；本入口仅改善文件访问。安卓实机呈现仍待验证。');
replaceOnce('<script>\n\'use strict\';',`<section class="card" id="assetDetail" hidden><h2>原审阅附件</h2><p id="assetName"></p><img id="assetImage" alt="审阅原图" hidden><pre id="assetText"></pre></section>\n<script id="asset-manifest" type="application/json">${manifestText.replace(/</g,'\\u003c')}</script>\n<script>\n${runtime}\n</script>\n<script>\n'use strict';\nconst mobileAssets=new MobileReviewAssets(document.getElementById('asset-manifest').textContent,'${expectedManifest}',document.getElementById('assetStatus'),document,${JSON.stringify(transportVariants)});\nlet mobileSelection=0;\ndocument.getElementById('reviewZip').onchange=async event=>{const request=++mobileSelection,file=event.target.files[0];if(file){try{await mobileAssets.open(file)}catch(error){if(request===mobileSelection)mobileAssets.status(error.message,true)}}};\nwindow.addEventListener('pagehide',()=>mobileAssets.dispose());\nfor(const anchor of document.querySelectorAll('a[href]'))mobileAssets.link(anchor,anchor.getAttribute('href'));`);
replaceOnce("a.href=path;return a",'mobileAssets.link(a,path);return a');
replaceOnce('img.src=f.file;', 'mobileAssets.image(img,f.file);');
replaceOnce("el('full').src=f.file;el('zoom').src=f.zoom;el('originalLink').href=f.file;", "mobileAssets.image(el('full'),f.file);mobileAssets.image(el('zoom'),f.zoom);mobileAssets.link(el('originalLink'),f.file);");
for(const script of html.matchAll(/<script([^>]*)>([\s\S]*?)<\/script>/g)){if(!script[1].includes('application/json'))new vm.Script(script[2]);}
must(!/\bfetch\s*\(|XMLHttpRequest|\bsrc\s*=\s*f\.(file|zoom)|a\.href=path/.test(html),'Unexpected network or relative asset access');
fs.mkdirSync(output,{recursive:true});
fs.writeFileSync(path.join(output,'index.html'),html);
fs.writeFileSync(path.join(output,'git-transport.json'),JSON.stringify(transportVariants,null,2)+'\n');
const identity={kind:'read_only_access_companion',reviewId:manifest.reviewId,sourceDirectory:path.relative(process.cwd(),source).replaceAll('\\','/'),sourceManifestSha256:expectedManifest,sourceFilesVerified:manifest.files.length,sourceBytesVerified:manifest.files.reduce((s,f)=>s+f.bytes,0),sourcePayloadSha256:hash(fs.readFileSync(path.join(source,'review-data.json'))),gitNewlineVariants:Object.keys(transportVariants).length,companionHtmlSha256:hash(Buffer.from(html)),builderSha256:hash(fs.readFileSync(__filename)),runtimeSha256:hash(Buffer.from(runtime)),gtOrApprovalScopeChanged:false,actualAndroidRendering:'unverified'};
fs.writeFileSync(path.join(output,'source-identity.json'),JSON.stringify(identity,null,2)+'\n');
fs.writeFileSync(path.join(output,'README.md'),`# 手机离线审阅入口\n\n1. 将本目录的 index.html 下载到手机；它可以放在任意目录。\n2. 用支持 JavaScript 的浏览器打开这个 HTML，页面标题应含“手机离线入口”。\n3. 点击“选择资料 ZIP”，选择已经下载的完整 gt-all-scenes-v2 目录 ZIP，或包含它的 GitHub 仓库 ZIP。无需解压；之前下载、包含原审阅包的旧仓库 ZIP 也能使用。\n4. 等待“资料包已连接”，再切换场景、原帧与状态；点击资料链接会在页面下方显示原附件。\n\n文件只在本机读取，页面没有上传或网络请求。原图和附件读取时按原审阅清单核对 SHA-256。大型 ZIP 只读取目录和当前需要的文件，不整包解压到内存。支持普通 ZIP 的存储和 DEFLATE 压缩；不支持加密、分卷和 ZIP64。若浏览器提示不支持解压，可更新浏览器，或重新用“仅存储/不压缩”打包原审阅目录。\n\n本入口对应 gt-all-scenes-v2，原清单 SHA-256 为 ${expectedManifest}。原 GT、原帧、范围、评分契约和待验证缺口均未修改；本入口不授予批准或冻结。安卓实机显示尚待验证。\n`);
const readmePath=path.join(output,'README.md');
fs.writeFileSync(readmePath,fs.readFileSync(readmePath,'utf8').replaceAll('gt-all-scenes-v2',manifest.reviewId).replace('无需解压；之前下载、包含原审阅包的旧仓库 ZIP 也能使用。','无需解压；资料 ZIP 须对应本审阅版本。'));
console.log(JSON.stringify(identity,null,2));
