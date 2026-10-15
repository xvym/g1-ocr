param(
  [Parameter(Mandatory=$true)][string]$ReviewDirectory,
  [Parameter(Mandatory=$true)][string]$Clip,
  [Parameter(Mandatory=$true)][string]$Candidate,
  [Parameter(Mandatory=$true)][string]$Layout,
  [Parameter(Mandatory=$true)][string]$Frames,
  [Parameter(Mandatory=$true)][string]$Integrity
)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$reviewRoot=[System.IO.Path]::GetFullPath((Join-Path $projectRoot $ReviewDirectory))
$allowedRoot=[System.IO.Path]::GetFullPath((Join-Path $projectRoot 'reports/gt-review'))+[System.IO.Path]::DirectorySeparatorChar
if(!$reviewRoot.StartsWith($allowedRoot,[System.StringComparison]::OrdinalIgnoreCase) -or (Test-Path -LiteralPath $reviewRoot)){throw 'Use a new reports/gt-review/<id> directory'}
$clipData=Get-Content -LiteralPath (Join-Path $projectRoot $Clip) -Raw | ConvertFrom-Json
$gtData=@(Get-Content -LiteralPath (Join-Path $projectRoot $Candidate) | ForEach-Object {$_ | ConvertFrom-Json})
$exportData=Get-Content -LiteralPath (Join-Path $projectRoot "$Frames/verification.json") -Raw | ConvertFrom-Json
$integrityData=Get-Content -LiteralPath (Join-Path $projectRoot $Integrity) -Raw | ConvertFrom-Json
if($exportData.step -ne 1 -or !$exportData.allRgbAndPngReadbackHashesMatch -or $exportData.startFrame -ne $clipData.startFrame -or $exportData.endFrameExclusive -ne $clipData.endFrameExclusive){throw 'Review frames must be the complete verified clip'}
$candidateHash=(Get-FileHash -LiteralPath (Join-Path $projectRoot $Candidate) -Algorithm SHA256).Hash.ToLower()
if($candidateHash -ne $integrityData.gtSha256 -or (Get-FileHash -LiteralPath (Join-Path $projectRoot $Clip) -Algorithm SHA256).Hash.ToLower() -ne $integrityData.clipSha256 -or (Get-FileHash -LiteralPath (Join-Path $projectRoot $Layout) -Algorithm SHA256).Hash.ToLower() -ne $integrityData.layoutSha256){throw 'Integrity report version mismatch'}
New-Item -ItemType Directory -Path $reviewRoot | Out-Null
New-Item -ItemType Directory -Path (Join-Path $reviewRoot 'frames'),(Join-Path $reviewRoot 'zoom'),(Join-Path $reviewRoot 'annotations'),(Join-Path $reviewRoot 'evidence') | Out-Null
Copy-Item -LiteralPath (Join-Path $projectRoot $Clip) -Destination (Join-Path $reviewRoot 'annotations/clip.json')
Copy-Item -LiteralPath (Join-Path $projectRoot $Candidate) -Destination (Join-Path $reviewRoot 'annotations/ocr-candidate.jsonl')
Copy-Item -LiteralPath (Join-Path $projectRoot $Layout) -Destination (Join-Path $reviewRoot 'annotations/layout.json')
Copy-Item -LiteralPath (Join-Path $projectRoot $Integrity) -Destination (Join-Path $reviewRoot 'evidence/candidate-integrity.json')
Copy-Item -LiteralPath (Join-Path $projectRoot "$Frames/verification.json") -Destination (Join-Path $reviewRoot 'evidence/export-verification.json')
$candidateParent=Split-Path -Parent (Join-Path $projectRoot $Candidate)
foreach($passFile in @('pass1.jsonl','pass2.jsonl','pass-differences.json')){Copy-Item -LiteralPath (Join-Path $candidateParent $passFile) -Destination (Join-Path $reviewRoot "annotations/$passFile")}
Copy-Item -LiteralPath (Join-Path $projectRoot 'data/meta/m1-v1/media.jsonl') -Destination (Join-Path $reviewRoot 'evidence/media.jsonl')
Copy-Item -LiteralPath (Join-Path $projectRoot 'data/meta/m1-v1/report.json') -Destination (Join-Path $reviewRoot 'evidence/full-ledger-report.json')
Copy-Item -LiteralPath (Join-Path $projectRoot 'data/meta/coverage-observations-v1.json') -Destination (Join-Path $reviewRoot 'evidence/coverage.json')
Copy-Item -LiteralPath (Join-Path $projectRoot 'data/meta/ocr-scoring-contract-v1.json') -Destination (Join-Path $reviewRoot 'annotations/scoring-contract.json')
Copy-Item -LiteralPath (Join-Path $projectRoot 'data/meta/clips-candidates-v1.json') -Destination (Join-Path $reviewRoot 'evidence/original-navigation-candidate.json')
Copy-Item -LiteralPath (Join-Path $projectRoot 'reports/m0-20261014/independent-ledger-check.json') -Destination (Join-Path $reviewRoot 'evidence/independent-ledger-check.json')
Copy-Item -LiteralPath (Join-Path $projectRoot 'reports/m2-validator-negative-v1/report.json') -Destination (Join-Path $reviewRoot 'evidence/negative-integrity-checks.json')
Add-Type -AssemblyName System.Drawing
$reviewFrames=foreach($sourceFrame in $exportData.frames){
  $fileName='frame-{0:D6}.png' -f $sourceFrame.frameIndex
  $sourcePng=Join-Path $projectRoot "$Frames/$fileName"
  $reviewPng=Join-Path $reviewRoot "frames/$fileName"
  Copy-Item -LiteralPath $sourcePng -Destination $reviewPng
  if((Get-FileHash -LiteralPath $sourcePng -Algorithm SHA256).Hash -ne (Get-FileHash -LiteralPath $reviewPng -Algorithm SHA256).Hash){throw 'Copied original PNG differs'}
  $fullImage=[System.Drawing.Image]::FromFile($reviewPng)
  $cropImage=[System.Drawing.Bitmap]::new(1080,324)
  $cropCanvas=[System.Drawing.Graphics]::FromImage($cropImage)
  try{
    $cropCanvas.InterpolationMode=[System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
    $cropCanvas.PixelOffsetMode=[System.Drawing.Drawing2D.PixelOffsetMode]::Half
    $cropCanvas.DrawImage($fullImage,[System.Drawing.Rectangle]::new(0,0,1080,324),[System.Drawing.Rectangle]::new(300,324,360,108),[System.Drawing.GraphicsUnit]::Pixel)
    $cropImage.Save((Join-Path $reviewRoot "zoom/$fileName"),[System.Drawing.Imaging.ImageFormat]::Png)
  }finally{$cropCanvas.Dispose();$cropImage.Dispose();$fullImage.Dispose()}
  $associatedState=@($gtData | Where-Object {$sourceFrame.frameIndex -ge $_.startFrame -and $sourceFrame.frameIndex -lt $_.endFrameExclusive})
  $role=if($sourceFrame.frameIndex -lt $clipData.scoredRange[0]){'warmup'}elseif($sourceFrame.frameIndex -ge $clipData.scoredRange[1]){'post_context'}else{'scored'}
  if($role -eq 'scored' -and $associatedState.Count -ne 1){throw 'Scored frame must have exactly one state'}
  [ordered]@{frameIndex=$sourceFrame.frameIndex;pts=$sourceFrame.pts;durationPts=$sourceFrame.durationPts;timeBase=$sourceFrame.timeBase;rgbSha256=$sourceFrame.rgbSha256;file="frames/$fileName";zoom="zoom/$fileName";crop=@(300,324,360,108);zoomScale=3;role=$role;state=$(if($associatedState.Count -eq 1){$associatedState[0]}else{$null})}
}
$reviewMedia=@(Get-Content -LiteralPath (Join-Path $projectRoot 'data/meta/m1-v1/media.jsonl') | ForEach-Object {$_ | ConvertFrom-Json})
$reviewData=[ordered]@{reviewId=(Split-Path -Leaf $reviewRoot);version='trial-ocr-v2';candidateSha256=$candidateHash;clip=$clipData;integrity=$integrityData;frames=$reviewFrames;states=$gtData;media=$reviewMedia;differences=(Get-Content -LiteralPath (Join-Path $candidateParent 'pass-differences.json') -Raw | ConvertFrom-Json)}
$reviewJson=$reviewData | ConvertTo-Json -Depth 30 -Compress
$reviewJson | Set-Content -LiteralPath (Join-Path $reviewRoot 'review-data.json') -Encoding utf8
$reviewHtml=@'
<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>日文 GT 首次试标审阅</title>
<style>*{box-sizing:border-box}body{margin:0 auto;max-width:1180px;padding:28px;font:16px/1.55 system-ui,"Microsoft YaHei",sans-serif;color:#202b37;background:#f5f7fa}h1{font-size:28px;margin:8px 0}h2{font-size:20px;margin-top:28px}.badge{display:inline-block;background:#fff0d0;color:#6e4b00;border-radius:5px;padding:4px 10px}.panel{background:white;border:1px solid #d8dee8;padding:20px;border-radius:8px;margin:16px 0}.muted{color:#566373}code,pre,.hash{font:13px/1.6 ui-monospace,Consolas,monospace;overflow-wrap:anywhere;white-space:pre-wrap}pre{background:#f1f4f8;padding:12px}a{color:#1458a8}button,select{font:inherit;border:1px solid #bcc7d5;background:#fff;padding:6px 12px;border-radius:5px}button{cursor:pointer}input[type=range]{width:100%;margin:12px 0}#full{display:block;width:100%;max-width:960px;height:auto;margin:auto;background:#000}#zoom{display:block;width:100%;height:auto;image-rendering:pixelated;border:1px solid #d8dee8}.controls{display:flex;gap:10px;flex-wrap:wrap;align-items:center}.role{font-weight:700}.note{border-left:4px solid #bd7d08;padding-left:12px}table{width:100%;border-collapse:collapse;font-size:14px}th,td{border-bottom:1px solid #d8dee8;text-align:left;padding:10px;vertical-align:top}th{background:#f1f4f8}td.jp{white-space:pre-wrap;font-family:"Yu Gothic",monospace}details{margin:14px 0}.links{display:flex;gap:16px;flex-wrap:wrap}#text{font-size:21px;white-space:pre-wrap}.hidden{display:none}@media(max-width:650px){body{padding:14px}table{font-size:12px}.panel{padding:12px}}</style></head><body>
<span class="badge">等待用户审阅 · 候选 GT 未冻结</span><h1>日文 GT 首次试标</h1><p>审阅版本 <strong id="version"></strong>。当前仅有代理双遍核读及结构检查，没有运行 OCR，没有独立验收结论，也不声称人工逐帧核验。</p>
<div class="panel"><h2 style="margin-top:0">本次拟冻结范围</h2><p id="scope"></p><p class="hash" id="candidate-hash"></p><p>只有一页完整可见内容：先出现文本框，逐字加载为完整页，再滚动替换。下一页的加载只作为结束后上下文，未纳入完整事件或评分机会。</p><p class="note">第二遍修订了 5296–5297 帧：上方标记被裁掉，原帧只显示「ホ」「シ」基础字形。候选 GT 如实登记为「セキチク ホケモン シム」，不按上一帧或完整句意补回「ポ」「ジ」。</p></div>
<div class="panel"><h2 style="margin-top:0">逐帧查看（全部 39 帧）</h2><div class="controls"><button id="prev" type="button">← 上一帧</button><button id="next" type="button">下一帧 →</button><select id="frame-select" aria-label="选择原始帧"></select><label><input id="show-gt" type="checkbox" checked> 显示候选标注</label></div><input id="slider" type="range" min="0" max="38" value="0" aria-label="原始帧序号"><p id="frame-title" class="role"></p><p id="time" class="muted"></p><a id="full-link" target="_blank"><img id="full" alt="完整原始 RGB 帧，无损 PNG"></a><p>原帧目标区域 [300,324,660,432)，最近邻放大 3 倍。点击完整原帧可单独打开；其他文字/边框不会被裁剪图取代。</p><img id="zoom" alt="文本框区域最近邻放大"><div id="gt-panel"><p id="text"></p><p id="state-note"></p><details><summary>该帧的完整标注 / 坐标 / 证据</summary><pre id="state-json"></pre></details></div><details><summary>原时间和 RGB 像素身份</summary><pre id="frame-json"></pre></details></div>
<div class="panel"><h2 style="margin-top:0">14 个状态，35 个评分原帧</h2><p>半开区间 [start,endExclusive)，空态、加载态和滚动态均保留。点击任意状态回到其首帧。</p><table><thead><tr><th>原帧 / PTS</th><th>状态 / 完整页</th><th>当时实际可见文字</th></tr></thead><tbody id="state-table"></tbody></table></div>
<div class="panel"><h2 style="margin-top:0">选片、会话与覆盖边界</h2><p>从原视频导航 5040、5280、5520 帧发现候选；随后完整保留 5100–5399 共 300 个原帧，在不看 OCR 的条件下逐帧确认本页边界。最终片段为 5262–5300；热身 5262–5263；评分 5264–5298；后文 5299–5300。开始前有无文本框状态，结束时旧首行已消失、滚动已落定，下一页文字尚在加载。</p><p>完整事件起点：5268 文本框出现，5275 首次文字；5286 首次完整页与箭头；5296 滚动开始；5298 完成替换。评分未避开加载或滚动。5320 的清屏不属于这次完整页面机会，仍保留在 300 帧候选上下文中，未作清屏验收。</p><p>四媒体共 66,770 帧、约 46 分 19 秒。本 clip 39 帧 / 1.625 秒，评分 35 帧 / 约 1.458 秒，仅有 1 个完整页面机会。其余时段均未验证。导航可见对话、战斗、技能选择、背包、状态、PC、商店，但这些场景尚未形成冻结 GT。</p><p>video-01 的 HOME 图标可见日版红版信息；其他三来源的确切版本待验证。四媒体的独立会话关系未证明，trial 只分到 development，没有独立 acceptance 集。video-04 开头已在游戏中，事件前史未知，该开头候选标为 boundary_unknown。无需补录。</p><details><summary>四个原媒体身份、版本和帧账本</summary><pre id="media-json"></pre></details><details><summary>两遍差异及原图处理依据</summary><pre id="difference-json"></pre></details><details><summary>拟冻结的 clip 与边界说明</summary><pre id="clip-json"></pre></details></div>
<div class="panel"><h2 style="margin-top:0">完整资料与复现</h2><div class="links"><a href="annotations/ocr-candidate.jsonl">候选完整 GT</a><a href="annotations/layout.json">坐标与字格</a><a href="annotations/scoring-contract.json">评分契约</a><a href="annotations/pass1.jsonl">第一遍记录</a><a href="annotations/pass2.jsonl">第二遍记录</a><a href="annotations/pass-differences.json">双遍差异</a><a href="evidence/export-verification.json">39 帧与哈希</a><a href="evidence/candidate-integrity.json">完整性报告</a><a href="evidence/coverage.json">覆盖与缺口</a><a href="evidence/original-navigation-candidate.json">原始选片候选</a><a href="bundle-manifest.json">审阅包文件哈希</a></div><p>原媒体只读引用项目 testpic/video/，没有复制或重编码。原时间 = 整数 PTS × time_base。所有 PNG 对应完整原帧；放大只用于辅助核读。产品源码与数据版本见项目唯一 ExecPlan。</p><pre>在项目根目录重新导出到新的空目录：
./tools/validate.ps1 export data/meta/m1-v1 video-02 5262 5301 data/frames/video-02/recheck-new-version 1
./tools/validate.ps1 gt-check data/meta/m1-v1 data/meta/clip-trial-v2.json data/gt/candidates/ocr-trial-v1/ocr-candidate-v2.jsonl data/gt/candidates/ocr-trial-v1/layout-v2.json reports/recheck-new-version.json</pre><p>用户批准只表示允许使用这一版本继续，不代表用户已逐帧核验或 OCR 已通过。版本、边界、来源或范围改变时需重新审阅。当前没有冻结或后续开发执行。</p></div>
<script>const review=__REVIEW_DATA__;let selected=0;const $=id=>document.getElementById(id);const frames=review.frames;
$('version').textContent=review.reviewId+' / '+review.version;$('candidate-hash').textContent='候选 GT SHA-256: '+review.candidateSha256;
$('scope').textContent=review.clip.mediaId+' · 片段 ['+review.clip.startFrame+','+review.clip.endFrameExclusive+') · 评分 ['+review.clip.scoredRange.join(',')+') · 35 帧 / 14 状态 / 1 次完整页机会';
frames.forEach((frame,index)=>{const option=document.createElement('option');option.value=index;option.textContent=frame.frameIndex+' · '+frame.role;$('frame-select').appendChild(option)});
const names={no_textbox:'无文本框',textbox_empty:'空文本框',loading:'逐字加载',complete_waiting:'完整页，等待翻页',scrolling:'滚动，标记被裁切',scroll_settled_next_page_context:'滚动完成，旧上行已移除'};
function display(index){selected=Math.max(0,Math.min(frames.length-1,index));const frame=frames[selected];$('slider').value=selected;$('frame-select').value=selected;$('full').src=frame.file;$('full-link').href=frame.file;$('zoom').src=frame.zoom;const scope=frame.role==='scored'?'评分':frame.role==='warmup'?'真实前史热身':'结束后上下文，不评分';$('frame-title').textContent='原帧 '+frame.frameIndex+' · '+scope;const parts=frame.timeBase.split('/').map(Number);$('time').textContent='PTS '+frame.pts+' × '+frame.timeBase+' = '+(frame.pts*parts[0]/parts[1]).toFixed(6)+' s；durationPts '+frame.durationPts;$('text').textContent=frame.state?(frame.state.regions.length?frame.state.regions.map(r=>r.text).join('\n'):'（无可见目标文字）'):'（未作为评分 GT；此帧仅用于边界上下文）';$('state-note').textContent=frame.state?(names[frame.state.kind]||frame.state.kind)+(frame.state.renderNote?'；滚动中仅登记实际可见基础字形。':''):'本帧只提供事件边界上下文，不计入评分；后文未作为另一完整事件。';$('state-json').textContent=JSON.stringify(frame.state,null,2);$('frame-json').textContent=JSON.stringify({...frame,state:undefined},null,2);$('prev').disabled=selected===0;$('next').disabled=selected===frames.length-1}
$('prev').onclick=()=>display(selected-1);$('next').onclick=()=>display(selected+1);$('slider').oninput=e=>display(Number(e.target.value));$('frame-select').onchange=e=>display(Number(e.target.value));$('show-gt').onchange=e=>$('gt-panel').classList.toggle('hidden',!e.target.checked);document.addEventListener('keydown',e=>{if(e.key==='ArrowLeft'){e.preventDefault();display(selected-1)}if(e.key==='ArrowRight'){e.preventDefault();display(selected+1)}});
review.states.forEach(state=>{const tr=document.createElement('tr');const a=document.createElement('td');const button=document.createElement('button');button.textContent='['+state.startFrame+','+state.endFrameExclusive+')';button.onclick=()=>{display(frames.findIndex(f=>f.frameIndex===state.startFrame));$('frame-title').scrollIntoView({behavior:'smooth'})};a.appendChild(button);const pts=document.createElement('div');pts.textContent=state.startPts+'–'+state.endPtsExclusive;pts.className='hash';a.appendChild(pts);const b=document.createElement('td');b.textContent=(names[state.kind]||state.kind)+(state.completePage?' · 完整页':'');const c=document.createElement('td');c.className='jp';c.textContent=state.regions.length?state.regions.map(r=>r.text).join('\n'):'（空）';tr.append(a,b,c);$('state-table').appendChild(tr)});
for(const [id,data] of [['media-json',review.media],['difference-json',review.differences],['clip-json',review.clip]])$(id).textContent=JSON.stringify(data,null,2);display(0);</script></body></html>
'@
$reviewHtml.Replace('__REVIEW_DATA__',$reviewJson) | Set-Content -LiteralPath (Join-Path $reviewRoot 'index.html') -Encoding utf8
$bundleFiles=@(Get-ChildItem -LiteralPath $reviewRoot -File -Recurse | Sort-Object FullName | ForEach-Object {[ordered]@{path=$_.FullName.Substring($reviewRoot.Length+1).Replace('\','/');sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLower();bytes=$_.Length}})
[ordered]@{schemaVersion=1;reviewId=$reviewData.reviewId;candidateVersion=$reviewData.version;candidateSha256=$candidateHash;frameCount=$reviewFrames.Count;scoredFrames=$integrityData.sourceFrames;files=$bundleFiles;excludes='This manifest excludes itself to avoid a circular hash; record its SHA-256 in the unique ExecPlan';status='pending_user_review_not_frozen'} | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $reviewRoot 'bundle-manifest.json') -Encoding utf8
Get-FileHash -LiteralPath (Join-Path $reviewRoot 'index.html'),(Join-Path $reviewRoot 'bundle-manifest.json') -Algorithm SHA256

