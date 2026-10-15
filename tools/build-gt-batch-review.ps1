param([Parameter(Mandatory=$true)][string]$Batch,[Parameter(Mandatory=$true)][string]$ReviewDirectory)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$reviewRoot=[IO.Path]::GetFullPath((Join-Path $projectRoot $ReviewDirectory))
$allowed=[IO.Path]::GetFullPath((Join-Path $projectRoot 'reports/gt-review'))+[IO.Path]::DirectorySeparatorChar
if(!$reviewRoot.StartsWith($allowed,[StringComparison]::OrdinalIgnoreCase) -or (Test-Path -LiteralPath $reviewRoot)){throw 'Use a new reports/gt-review/<version> directory'}
$batchData=Get-Content -LiteralPath $Batch -Raw | ConvertFrom-Json
New-Item -ItemType Directory $reviewRoot,(Join-Path $reviewRoot 'evidence') | Out-Null
Copy-Item -LiteralPath $Batch -Destination (Join-Path $reviewRoot 'batch.json')
Add-Type -AssemblyName System.Drawing
$items=@(foreach($item in $batchData.items){
  $dir=Join-Path $reviewRoot $item.id
  New-Item -ItemType Directory $dir,(Join-Path $dir 'frames'),(Join-Path $dir 'zoom'),(Join-Path $dir 'annotations'),(Join-Path $dir 'evidence') | Out-Null
  $clip=Get-Content -LiteralPath $item.clip -Raw | ConvertFrom-Json
  $layout=Get-Content -LiteralPath $item.layout -Raw | ConvertFrom-Json
  $gt=@(Get-Content -LiteralPath $item.candidate | ForEach-Object {$_ | ConvertFrom-Json})
  $integrity=Get-Content -LiteralPath $item.integrity -Raw | ConvertFrom-Json
  $export=Get-Content -LiteralPath (Join-Path $item.frames 'verification.json') -Raw | ConvertFrom-Json
  if($export.step -ne 1 -or !$export.allRgbAndPngReadbackHashesMatch -or $export.startFrame -ne $clip.startFrame -or $export.endFrameExclusive -ne $clip.endFrameExclusive){throw 'Continuous frame/clip identity mismatch'}
  foreach($entry in @(@{path=$item.candidate;hash=$integrity.gtSha256},@{path=$item.clip;hash=$integrity.clipSha256},@{path=$item.layout;hash=$integrity.layoutSha256})){if((Get-FileHash -LiteralPath $entry.path).Hash.ToLower() -ne $entry.hash){throw 'Integrity version mismatch'}}
  foreach($path in @($item.clip,$item.candidate,$item.layout)){Copy-Item -LiteralPath $path -Destination (Join-Path $dir ('annotations/'+(Split-Path -Leaf $path)))}
  $parent=Split-Path -Parent $item.candidate
  foreach($name in @('manual-pass1.json','manual-pass2.json','pass1.jsonl','pass2.jsonl','pass-differences.json')){Copy-Item -LiteralPath (Join-Path $parent $name) -Destination (Join-Path $dir "annotations/$name")}
  Copy-Item -LiteralPath $item.integrity -Destination (Join-Path $dir 'evidence/integrity.json')
  foreach($name in @('verification.json','decode.command.json','decode.stderr')){Copy-Item -LiteralPath (Join-Path $item.frames $name) -Destination (Join-Path $dir "evidence/$name")}
  $indexed=@(Get-Content -LiteralPath (Join-Path $item.frames 'verification.json') -Raw | ConvertFrom-Json).frames
  $frameData=@(foreach($frame in $indexed){
    $file='frame-{0:D6}.png' -f $frame.frameIndex
    $src=Join-Path $item.frames $file;$dst=Join-Path $dir "frames/$file"
    Copy-Item -LiteralPath $src -Destination $dst
    if((Get-FileHash -LiteralPath $src).Hash -ne (Get-FileHash -LiteralPath $dst).Hash){throw 'Copied PNG identity mismatch'}
    $crop=$layout.sourceGameRect;$width=$crop[2]-$crop[0];$height=$crop[3]-$crop[1]
    $scale=if($width -ge 600){2}else{3}
    $sourceImage=[Drawing.Image]::FromFile([IO.Path]::GetFullPath($dst))
    $zoom=[Drawing.Bitmap]::new([int]($width*$scale),[int]($height*$scale));$canvas=[Drawing.Graphics]::FromImage($zoom)
    try{$canvas.InterpolationMode=[Drawing.Drawing2D.InterpolationMode]::NearestNeighbor;$canvas.PixelOffsetMode=[Drawing.Drawing2D.PixelOffsetMode]::Half;$canvas.DrawImage($sourceImage,[Drawing.Rectangle]::new(0,0,$width*$scale,$height*$scale),[Drawing.Rectangle]::new($crop[0],$crop[1],$width,$height),[Drawing.GraphicsUnit]::Pixel);$zoom.Save((Join-Path $dir "zoom/$file"),[Drawing.Imaging.ImageFormat]::Png)}finally{$canvas.Dispose();$zoom.Dispose();$sourceImage.Dispose()}
    $states=@($gt | Where-Object {$frame.frameIndex -ge $_.startFrame -and $frame.frameIndex -lt $_.endFrameExclusive})
    $role=if($frame.frameIndex -lt $clip.scoredRange[0]){'热身'}elseif($frame.frameIndex -ge $clip.scoredRange[1]){'结束后上下文（不评分）'}else{'评分'}
    if($role -eq '评分' -and $states.Count -ne 1){throw 'Scored frame coverage mismatch'}
    [ordered]@{index=$frame.frameIndex;pts=$frame.pts;durationPts=$frame.durationPts;timeBase=$frame.timeBase;rgbSha256=$frame.rgbSha256;pngSha256=(Get-FileHash $dst).Hash.ToLower();file=($item.id+"/frames/$file");zoom=($item.id+"/zoom/$file");zoomScale=$scale;sourceCrop=$crop;role=$role;state=$(if($states.Count -eq 1){$states[0]}else{$null})}
  })
  [ordered]@{id=$item.id;name=$item.name;description=$item.description;clip=$clip;layout=$layout;integrity=$integrity;frames=$frameData;states=$gt;keyFrames=$item.keyFrames;differences=(Get-Content (Join-Path $parent 'pass-differences.json') -Raw | ConvertFrom-Json)}
})
foreach($path in @('data/meta/m1-v1/media.jsonl','data/meta/m1-v1/report.json',$batchData.sourceIdentityReport,'reports/m2-expansion-build-v1/source-snapshot-batch-repaired-v1.json','reports/m2-expansion-build-v1/maven-test-ceil-end.log','reports/m2-expansion-build-v1/approved-trial-regression-ceil-end.json','reports/m2-expansion-build-v1/negative-ceil-end/report.json','reports/m2-expansion-shop-grid-measurement-v1.json','reports/m2-expansion-double-read-check-v1.json','reports/m2-expansion-review-builder-repro-v1.json','data/meta/expansion-candidates-v1.json')){Copy-Item -LiteralPath $path -Destination (Join-Path $reviewRoot ('evidence/'+(Split-Path -Leaf $path)))}
Copy-Item -LiteralPath 'data/meta/ocr-scoring-contract-v1.json' -Destination (Join-Path $reviewRoot 'scoring-contract.json')
foreach($clipItem in $items){$ledgerFile='data/meta/m1-v1/'+$clipItem.clip.mediaId+'/frames.jsonl';$rangeRows=@(Get-Content $ledgerFile | ForEach-Object {$_ | ConvertFrom-Json} | Where-Object {$_.frameIndex -ge $clipItem.clip.startFrame -and $_.frameIndex -lt $clipItem.clip.endFrameExclusive});$rangeRows | ConvertTo-Json -Depth 12 | Set-Content (Join-Path $reviewRoot ('evidence/'+$clipItem.id+'-ledger-range.json')) -Encoding UTF8}
$data=[ordered]@{batch=$batchData;items=$items}
$json=$data | ConvertTo-Json -Depth 40 -Compress
$json | Set-Content (Join-Path $reviewRoot 'review-data.json') -Encoding UTF8
$html=@'
<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>日文 GT 扩充合批审阅 v1</title>
<style>*{box-sizing:border-box}body{max-width:1180px;margin:auto;padding:26px;background:#f5f7fa;color:#223;font:16px/1.65 system-ui,"Microsoft YaHei",sans-serif}h1{font-size:27px}h2{font-size:20px}.panel{background:white;border:1px solid #d7dde6;border-radius:8px;padding:20px;margin:18px 0}.badge{background:#fff0cc;color:#725200;padding:5px 10px}.controls{display:flex;flex-wrap:wrap;gap:8px;align-items:center}button,select{font:inherit;padding:6px 12px;border:1px solid #bcc7d5;border-radius:5px;background:white}button{cursor:pointer}input[type=range]{width:100%}img{display:block;max-width:100%;height:auto;margin:12px auto}#full{width:960px}#zoom{image-rendering:pixelated}pre{white-space:pre-wrap;overflow-wrap:anywhere;font:13px/1.5 Consolas,monospace;background:#eef2f6;padding:12px}.jp{font-size:21px;white-space:pre-wrap}table{border-collapse:collapse;width:100%}td,th{text-align:left;vertical-align:top;padding:8px;border-bottom:1px solid #dde2ea}.note{border-left:4px solid #b7831d;padding-left:12px}a{color:#1557a0}.muted{color:#596675}details{margin:15px 0}@media(max-width:650px){body{padding:12px}.panel{padding:12px}table{font-size:12px}}</style></head><body>
<span class="badge">合批审阅 · 未批准 · 未冻结</span><h1>日文 GT 扩充 v1</h1>
<p>本次提交商店、PC 两段完整操作，共 <strong>94 个评分原帧、29 个可见状态、6 次完整状态机会</strong>；连同结束后上下文保留100原帧。两遍均由当前代理直接核读原图，没有运行被测 OCR，也不声称人工逐帧核验。</p>
<p class="note">只申请批准本包所列的两段具体范围。前次批准仅对应35帧试标；本次未覆盖的场景、交易、版本、独立会话和真实窗口验收仍待验证。你负责审阅批准，无需重新逐帧标注。</p>
<section class="panel"><h2>建议审阅方式</h2><p>切换场景，先看关键帧及下方日文，再检查加载/光标/清屏附近。滑块和前后按钮能查看全部原帧。放大图只做最近邻放大，完整原图始终保留。</p><select id="scene"></select><p id="description"></p><div id="keys" class="controls"></div></section>
<section class="panel"><h2>连续原帧与 GT</h2><div class="controls"><button id="prev">上一帧</button><button id="next">下一帧</button><button id="prevState">上一状态</button><button id="nextState">下一状态</button><span id="frameInfo"></span></div><input id="slider" type="range" min="0" step="1"><p id="stateInfo"></p><div id="text" class="jp"></div><p id="cursor"></p><img id="full" alt="完整原始解码帧"><details open><summary id="zoomLabel">游戏区域最近邻放大</summary><img id="zoom" alt="从上述原帧裁剪的游戏区域"></details><details><summary>本帧时间、哈希与注释</summary><pre id="frameJson"></pre></details></section>
<section class="panel"><h2>状态与范围</h2><p id="bounds"></p><table><thead><tr><th>原帧范围</th><th>状态</th><th>实际可见日文</th><th>完整机会</th></tr></thead><tbody id="states"></tbody></table><details><summary>两遍核读与差异依据</summary><pre id="differences"></pre></details><p id="links"></p></section>
<section class="panel"><h2>尚未验证的范围</h2><ul id="pending"></ul><p>四录像总2779.397368秒/66770原帧。两个新增片段约4.1667秒，评分约3.9167秒；只有这些候选范围完成代理双遍，所有片段属于开发集，会话独立性未证实。未选原帧区间及来源说明见 <a href="batch.json">批次身份与覆盖清单</a>。</p><p>当前没有日文 OCR 或中文映射验收结果。结构检查、14项测试通过和用户批准均不能替代产品实测。</p></section>
<section class="panel"><h2>审阅结论</h2><p>确认本版本/范围可以冻结后，可回复“批准 gt-expansion-v1”。如有问题，指出场景与原帧编号即可；代理据原图修订并重新提交。提交后开发暂停，获批后先核对材料哈希与完整性，再冻结批准字节并继续剩余场景的 M1/M2。</p><p class="muted">按 spec.md §3.4 执行本次卡点；实际浏览器呈现仍待验证。源与审阅文件哈希清单 <a href="bundle-manifest.json">bundle-manifest.json</a>；评分契约 <a href="scoring-contract.json">scoring-contract.json</a>。</p></section>
<script id="review-data" type="application/json">__DATA__</script>
<script>
'use strict';
const data=JSON.parse(document.getElementById('review-data').textContent), el=id=>document.getElementById(id);
let active=0, position=0;
data.items.forEach((item,i)=>{const o=document.createElement('option');o.value=i;o.textContent=item.name;el('scene').appendChild(o)});
data.batch.remainingCoverage.forEach(note=>{const li=document.createElement('li');li.textContent=note;el('pending').appendChild(li)});
function item(){return data.items[active]}
function show(){const it=item(),f=it.frames[position],s=f.state;el('slider').value=position;el('frameInfo').textContent=`原帧 ${f.index} · ${f.role} · ${position+1}/${it.frames.length}`;el('stateInfo').textContent=s?`${s.kind} · 完整状态机会：${s.completePage?'是':'否'} · [${s.startFrame},${s.endFrameExclusive})`:'结束后上下文，未赋评分GT';el('text').textContent=s?(s.regions.map(r=>r.text).join('\n')||'目标文字为空'):'上下文不作为下一事件评分';el('cursor').textContent=s?`向下箭头：${s.cursor.downArrowVisible?'可见':'无'}；菜单光标：${JSON.stringify(s.cursor.menuCursors)}`:'';el('full').src=f.file;el('zoom').src=f.zoom;el('zoomLabel').textContent=`游戏区 [${f.sourceCrop.join(',')}) · 最近邻 ${f.zoomScale} 倍；未增加细节`;el('frameJson').textContent=JSON.stringify(f,null,2)}
function choose(){const it=item();position=0;el('description').textContent=it.description;el('slider').max=it.frames.length-1;el('keys').replaceChildren();it.keyFrames.forEach(frame=>{const b=document.createElement('button');b.textContent='原帧 '+frame;b.onclick=()=>{position=it.frames.findIndex(f=>f.index===frame);show()};el('keys').appendChild(b)});el('bounds').textContent=`源 ${it.clip.mediaId}；完整范围 [${it.clip.startFrame},${it.clip.endFrameExclusive})；评分 [${it.clip.scoredRange.join(',')})；结束后上下文 [${it.clip.postContext.join(',')})。${it.clip.selectionReason}`;el('states').replaceChildren();it.states.forEach(s=>{const tr=document.createElement('tr');const vals=[`[${s.startFrame},${s.endFrameExclusive})`,s.kind,s.regions.map(r=>r.text).join('\n')||'目标文字为空',s.completePage?'是':'否'];vals.forEach(value=>{const td=document.createElement('td');td.textContent=value;td.style.whiteSpace='pre-wrap';tr.appendChild(td)});el('states').appendChild(tr)});el('differences').textContent=JSON.stringify(it.differences,null,2);el('links').replaceChildren();[['第一遍笔记','manual-pass1.json'],['第二遍笔记','manual-pass2.json'],['第一遍标注','pass1.jsonl'],['第二遍标注','pass2.jsonl']].forEach(([label,file])=>{const a=document.createElement('a');a.textContent=label+' ';a.href=it.id+'/annotations/'+file;el('links').appendChild(a)});show()}
el('scene').onchange=()=>{active=Number(el('scene').value);choose()};el('slider').oninput=()=>{position=Number(el('slider').value);show()};el('prev').onclick=()=>{position=Math.max(0,position-1);show()};el('next').onclick=()=>{position=Math.min(item().frames.length-1,position+1);show()};
function stepState(direction){const frames=item().frames,current=frames[position].state?.stateId??null;let p=position+direction;while(p>=0&&p<frames.length&&(frames[p].state?.stateId??null)===current)p+=direction;if(p>=0&&p<frames.length){position=p;show()}}el('prevState').onclick=()=>stepState(-1);el('nextState').onclick=()=>stepState(1);choose();
</script></body></html>
'@
$html=$html.Replace('__DATA__',$json.Replace('</','<\/'))
[IO.File]::WriteAllText((Join-Path $reviewRoot 'index.html'),$html,[Text.UTF8Encoding]::new($false))
$entries=@(Get-ChildItem -LiteralPath $reviewRoot -File -Recurse | ForEach-Object {[ordered]@{path=$_.FullName.Substring($reviewRoot.Length+1).Replace('\','/');sha256=(Get-FileHash -LiteralPath $_.FullName).Hash.ToLower();bytes=$_.Length}} | Sort-Object path)
[ordered]@{reviewId=$batchData.reviewId;status='prepared_not_approved';files=$entries;fileCount=$entries.Count;purpose='Exact version identity; approval applies to this manifest hash and payload bytes only. This manifest excludes itself.'} | ConvertTo-Json -Depth 15 | Set-Content (Join-Path $reviewRoot 'bundle-manifest.json') -Encoding UTF8
Write-Output "Prepared $($items.Count) scene bundle with $($entries.Count) files; not frozen"
