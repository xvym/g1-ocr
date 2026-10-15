param([string]$Batch='data/meta/all-scenes-review-v1.json',[string]$ReviewDirectory='reports/gt-review/gt-all-scenes-v1')
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath($ReviewDirectory)
$allowed=[IO.Path]::GetFullPath('reports/gt-review')+[IO.Path]::DirectorySeparatorChar
if(!$root.StartsWith($allowed,[StringComparison]::OrdinalIgnoreCase) -or (Test-Path -LiteralPath $root)){throw 'Use a new review directory inside reports/gt-review'}
function Save($p,$v){[IO.File]::WriteAllText($p,($v|ConvertTo-Json -Depth 45 -Compress),[Text.UTF8Encoding]::new($false))}
New-Item -ItemType Directory -Path $root,"$root/evidence","$root/gaps"|Out-Null
$batchData=Get-Content -Raw $Batch|ConvertFrom-Json
Copy-Item -LiteralPath $Batch -Destination "$root/batch.json"
Add-Type -AssemblyName System.Drawing
$items=@(foreach($it in $batchData.items){
 $dir=Join-Path $root $it.id
 New-Item -ItemType Directory -Path $dir,"$dir/frames","$dir/zoom","$dir/annotations","$dir/evidence"|Out-Null
 $clip=Get-Content -Raw $it.clip|ConvertFrom-Json;$layout=Get-Content -Raw $it.layout|ConvertFrom-Json;$gt=@(Get-Content $it.candidate|ForEach-Object{$_|ConvertFrom-Json});$integrity=Get-Content -Raw $it.integrity|ConvertFrom-Json
 foreach($e in @(@($it.clip,$integrity.clipSha256),@($it.candidate,$integrity.gtSha256),@($it.layout,$integrity.layoutSha256))){if((Get-FileHash -LiteralPath $e[0]).Hash.ToLower() -ne $e[1]){throw 'Integrity version mismatch'}}
 $export=Get-Content -Raw (Join-Path $it.frames 'verification.json')|ConvertFrom-Json
 if($export.step -ne 1 -or !$export.allRgbAndPngReadbackHashesMatch -or $export.startFrame -ne $clip.startFrame -or $export.endFrameExclusive -ne $clip.endFrameExclusive){throw "Export range mismatch $($it.id)"}
 foreach($p in @($it.clip,$it.candidate,$it.layout,$it.contract)){Copy-Item -LiteralPath $p -Destination "$dir/annotations/$(Split-Path -Leaf $p)"}
 $parent=Split-Path -Parent $it.candidate
 foreach($f in Get-ChildItem -LiteralPath $parent -File){if($f.Extension -in @('.json','.jsonl')){Copy-Item -LiteralPath $f.FullName -Destination "$dir/annotations/$($f.Name)"}}
 Copy-Item -LiteralPath $it.integrity -Destination "$dir/evidence/integrity.json"
 foreach($name in @('verification.json','decode.command.json','decode.stderr')){Copy-Item -LiteralPath (Join-Path $it.frames $name) -Destination "$dir/evidence/$name"}
 $crop=if($layout.sourceGameRect){@($layout.sourceGameRect)}else{@(0,0,$layout.sourceFrameSize[0],$layout.sourceFrameSize[1])}
 $w=[int]($crop[2]-$crop[0]);$h=[int]($crop[3]-$crop[1]);$scale=if($w -ge 600){2}else{3}
 $frameData=@(foreach($f in $export.frames){
  $name='frame-{0:D6}.png' -f $f.frameIndex;$src=Join-Path $it.frames $name;$dst="$dir/frames/$name"
  Copy-Item -LiteralPath $src -Destination $dst
  $sha=(Get-FileHash -LiteralPath $dst).Hash.ToLower();if($sha -ne (Get-FileHash -LiteralPath $src).Hash.ToLower()){throw 'PNG copy changed'}
  $b=[Drawing.Bitmap]::FromFile($dst);$z=[Drawing.Bitmap]::new([int]($w*$scale),[int]($h*$scale));$g=[Drawing.Graphics]::FromImage($z)
  try{$g.InterpolationMode='NearestNeighbor';$g.PixelOffsetMode='Half';$g.DrawImage($b,[Drawing.Rectangle]::new(0,0,$w*$scale,$h*$scale),[Drawing.Rectangle]::new($crop[0],$crop[1],$w,$h),[Drawing.GraphicsUnit]::Pixel);$z.Save("$dir/zoom/$name",[Drawing.Imaging.ImageFormat]::Png)}finally{$g.Dispose();$z.Dispose();$b.Dispose()}
  $si=-1;for($j=0;$j -lt $gt.Count;$j++){if($f.frameIndex -ge $gt[$j].startFrame -and $f.frameIndex -lt $gt[$j].endFrameExclusive){if($si -ne -1){throw 'Duplicate frame GT'};$si=$j}}
  $role=if($f.frameIndex -lt $clip.scoredRange[0]){'热身'}elseif($f.frameIndex -ge $clip.scoredRange[1]){'结束后上下文'}else{'评分'}
  if($role -eq '评分' -and $si -eq -1){throw 'Missing frame GT'}
  @{index=$f.frameIndex;pts=$f.pts;durationPts=$f.durationPts;timeBase=$f.timeBase;rgbSha256=$f.rgbSha256;pngSha256=$sha;file="$($it.id)/frames/$name";zoom="$($it.id)/zoom/$name";role=$role;stateIndex=$si}
 })
 $notes=@(Get-ChildItem "$dir/annotations" -File|Where-Object{$_.Name -like 'manual-*' -or $_.Name -like '*differences*' -or $_.Name -like 'pass?.jsonl'}|ForEach-Object{@{name=$_.Name;file="$($it.id)/annotations/$($_.Name)"}})
 @{id=$it.id;name=$it.name;approval=$it.approval;clip=$clip;layout=$layout;integrity=$integrity;frames=$frameData;states=$gt;crop=$crop;zoomScale=$scale;notes=$notes;links=@{gt="$($it.id)/annotations/$(Split-Path -Leaf $it.candidate)";clip="$($it.id)/annotations/$(Split-Path -Leaf $it.clip)";layout="$($it.id)/annotations/$(Split-Path -Leaf $it.layout)";contract="$($it.id)/annotations/$(Split-Path -Leaf $it.contract)";integrity="$($it.id)/evidence/integrity.json"}}
 Write-Host "Copied $($it.id): $($frameData.Count) original frames"
})
$gapData=@(foreach($gap in $batchData.gaps){if($gap.source){$frames=@(for($i=$gap.range[0];$i -lt $gap.range[1];$i++){$name='frame-{0:D6}.png' -f $i;Copy-Item -LiteralPath (Join-Path $gap.source $name) -Destination "$root/gaps/$name";@{index=$i;file="gaps/$name";pngSha256=(Get-FileHash "$root/gaps/$name").Hash.ToLower()}});@{gap=$gap;frames=$frames}}})
foreach($p in @($batchData.mediaLedger,$batchData.sourceIdentityReport,'data/meta/m1-v1/report.json','data/meta/coverage-observations-v1.json','data/meta/expansion-candidates-v1.json','data/meta/all-scenes-exploration-context-v1.json','reports/m2-all-scenes-format-v1/maven-test-two-arrows-v1.log','reports/m2-all-scenes-independent-extraction-v1.json','reports/m2-all-scenes-independent-extraction-v2.json','reports/m2-all-scenes-independent-extraction-v3.json','reports/m2-all-scenes-independent-extraction-v4.json','reports/m2-all-scenes-startup-exact-black-v1.json','reports/m2-all-scenes-skills-raw-shift-navigation-v1.json','reports/m2-all-scenes-final-check-v1/negative-report.json','reports/m2-all-scenes-final-check-v1/source-snapshot-v1.json')){if(!(Test-Path -LiteralPath $p)){throw "Missing evidence $p"};Copy-Item -LiteralPath $p -Destination "$root/evidence/$(Split-Path -Leaf $p)"}
$data=@{batch=$batchData;items=$items;gapFrames=$gapData}
foreach($p in @($batchData.additionalEvidence)){
 if(!(Test-Path -LiteralPath $p)){throw "Missing additional evidence $p"}
 $dst="$root/evidence/$(Split-Path -Leaf $p)"
 if(Test-Path -LiteralPath $dst){if((Get-FileHash -LiteralPath $p).Hash -ne (Get-FileHash -LiteralPath $dst).Hash){throw 'Additional evidence filename collision'}}else{Copy-Item -LiteralPath $p -Destination $dst}
}
Save "$root/review-data.json" $data
$template=Get-Content -Raw (Join-Path $PSScriptRoot 'all-scenes-review-template.html')
$json=$data|ConvertTo-Json -Depth 45 -Compress
[IO.File]::WriteAllText("$root/index.html",$template.Replace('__REVIEW_ID__',$batchData.reviewId).Replace('__DATA__',$json.Replace('</','<\/')),[Text.UTF8Encoding]::new($false))
Write-Host 'Payload prepared; run check-all-scenes-review.cjs for independent checks and final manifest'
