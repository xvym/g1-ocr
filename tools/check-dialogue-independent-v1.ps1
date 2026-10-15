$ErrorActionPreference='Stop'
. ./tools/dialogue-notes-lib.ps1
$base='data/gt/candidates/ocr-dialogue-expansion-v1'
$results=@(foreach($dir in Get-ChildItem $base -Directory|Sort-Object Name){
 $a=Get-Content -Raw (Join-Path $dir.FullName 'manual-pass1.json')|ConvertFrom-Json
 $b=Get-Content -Raw (Join-Path $dir.FullName 'manual-pass2.json')|ConvertFrom-Json
 $v1=Get-Content -Raw (Join-Path $a.source 'verification.json')|ConvertFrom-Json
 $v2=Get-Content -Raw (Join-Path $b.source 'verification.json')|ConvertFrom-Json
 if($a.source -eq $b.source -or $v1.derivedFromContinuousExport -or $v2.derivedFromContinuousExport){throw 'Actual independent source re-decodes required'}
 if(!$v1.allRgbAndPngReadbackHashesMatch -or !$v2.allRgbAndPngReadbackHashesMatch -or $v1.step -ne 1 -or $v2.step -ne 1){throw 'Continuous raw checks required'}
 $command1=Get-Content -Raw (Join-Path $a.source 'decode.command.json')|ConvertFrom-Json
 $command2=Get-Content -Raw (Join-Path $b.source 'decode.command.json')|ConvertFrom-Json
 foreach($c in @($command1,$command2)){if('-i' -notin $c -or '-ss' -in $c -or '-sseof' -in $c -or '-fps_mode' -notin $c -or 'passthrough' -notin $c){throw 'Source-from-start passthrough command required'}}
 $index1=@{};foreach($f in $v1.frames){$index1[[int]$f.frameIndex]=$f}
 $index2=@{};foreach($f in $v2.frames){$index2[[int]$f.frameIndex]=$f}
 $frameChecks=@(for([int]$i=$b.range[0];$i -lt $b.range[1];$i++){
  $f=$index1[$i];$g=$index2[$i];if(!$f -or !$g -or $f.rgbSha256 -ne $g.rgbSha256 -or $f.pts -ne $g.pts -or $f.durationPts -ne $g.durationPts -or $f.timeBase -ne $g.timeBase){throw "RGB/PTS mismatch $($dir.Name):$i"}
  $name='frame-{0:D6}.png' -f $i
  $ha=(Get-FileHash -LiteralPath (Join-Path $a.source $name)).Hash.ToLower()
  $hb=(Get-FileHash -LiteralPath (Join-Path $b.source $name)).Hash.ToLower()
  if($ha -ne $hb){throw "Raw PNG mismatch $($dir.Name):$i"}
  @{frameIndex=$i;pts=$g.pts;rgbSha256=$g.rgbSha256;pngSha256=$ha;matches=$true}
 })
 @{id=$dir.Name;range=$b.range;scoredRange=$b.scoredRange;originalFrames=$frameChecks.Count;firstSource=$a.source;secondSource=$b.source;firstVerificationSha256=(Get-FileHash (Join-Path $a.source 'verification.json')).Hash.ToLower();secondVerificationSha256=(Get-FileHash (Join-Path $b.source 'verification.json')).Hash.ToLower();firstCommand=$command1;secondCommand=$command2;frames=$frameChecks;differentFrames=@();notImageSemantics=$true}
})
Save-DialogueJsonNew 'reports/m2-dialogue-expansion-v1/dialogue-independent-extraction-v1.json' @{status='independent_source_from_start_decode_and_all_original_png_rgb_pts_comparison_pass';events=$results;eventCount=$results.Count;frameReferences=($results|Measure-Object originalFrames -Sum).Sum;differences=0;limitations='Deterministic identity checks only; agent literal double read is separately recorded. Not user approval, OCR or acceptance.'}
Write-Output "Independent original-source checks passed for $($results.Count) items"
