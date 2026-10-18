$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Drawing
$root=Get-Location
$notes='reports/m3-grid-refinement-v1/authorized-correction-safari-warden-npc-notes-v1'
$first='data/frames/video-02/ocr-observation-recheck-safari-warden-npc-v1'
$second='data/frames/video-02/authorized-correction-safari-warden-npc-pass2-v1'
$a=Get-Content "$first/verification.json" -Raw | ConvertFrom-Json
$b=Get-Content "$second/verification.json" -Raw | ConvertFrom-Json
if($a.exportedFrames -ne 83 -or $b.exportedFrames -ne 83){throw 'Incomplete event'}
$pairs=@()
for($i=0;$i -lt 83;$i++){
 $x=$a.frames[$i]; $y=$b.frames[$i]
 if(($x|ConvertTo-Json -Compress) -ne ($y|ConvertTo-Json -Compress)){throw 'Decode mismatch'}
 $name='frame-{0:D6}.png' -f $x.frameIndex
 $h1=(Get-FileHash "$first/$name" -Algorithm SHA256).Hash.ToLower()
 $h2=(Get-FileHash "$second/$name" -Algorithm SHA256).Hash.ToLower()
 if($h1 -ne $h2){throw 'PNG mismatch'}
 $pairs+=@{frame=$x.frameIndex;pts=$x.pts;rgbSha256=$x.rgbSha256;pngSha256=$h1}
}
$measurements=@()
for($f=4496;$f -lt 4522;$f++){
 $path="$second/"+('frame-{0:D6}.png' -f $f)
 $bmp=[Drawing.Bitmap]::new([string](Join-Path $root $path))
 $cells=@()
 for($col=1;$col -le 10;$col++){
  $count=0;$minX=999;$maxX=-1;$minY=999;$maxY=-1
  for($y=360;$y -lt 378;$y++){for($x=(300+18*$col);$x -lt (318+18*$col);$x++){
   $p=$bmp.GetPixel($x,$y);$mean=($p.R+$p.G+$p.B)/3
   if($mean -lt 80){$count++;$minX=[Math]::Min($x,$minX);$maxX=[Math]::Max($x,$maxX);$minY=[Math]::Min($y,$minY);$maxY=[Math]::Max($y,$maxY)}
  }}
  $cells+=@{column=$col;sourceRect=@((300+18*$col),360,(318+18*$col),378);darkPixelCount=$count;darkBoundsInclusive=@($minX,$minY,$maxX,$maxY)}
 }
 $bmp.Dispose()
 $measurements+=@{frame=$f;pngSha256=(Get-FileHash $path -Algorithm SHA256).Hash.ToLower();cells=$cells}
}
$result=@{status='deterministic_original_pixel_measurement_not_OCR';layout='data/gt/frozen/ocr-all-scenes-v2/safari-warden-npc/layout-v1.json';layoutSha256=(Get-FileHash 'data/gt/frozen/ocr-all-scenes-v2/safari-warden-npc/layout-v1.json').Hash.ToLower();method='Fixed recorded grid origin (300,108), 18 source pixels per cell. Count original RGB arithmetic mean <80 in base row14; threshold is a descriptive ink count, not a recognition or scoring rule. Visual reading supplies identities.';pairedOriginalFrames=$pairs;measurements=$measurements;effectiveFreezeChanged=$false;independentAcceptance=$false}
[IO.File]::WriteAllText((Join-Path $root "$notes/space-cell-evidence.json"),($result|ConvertTo-Json -Depth 20),[Text.UTF8Encoding]::new($false))
$measurements | ForEach-Object { '{0}: {1}' -f $_.frame,(($_.cells | ForEach-Object { '{0}={1}' -f $_.column,$_.darkPixelCount }) -join ' ') }
