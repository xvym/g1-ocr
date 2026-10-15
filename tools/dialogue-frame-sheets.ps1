param([Parameter(Mandatory=$true)][string]$Frames,[Parameter(Mandatory=$true)][string]$Output,[int]$Start=-1,[int]$End=-1,[int]$Step=1,[int]$X=0,[int]$Y=0,[int]$Width=0,[int]$Height=0,[int]$Columns=4,[int]$Rows=8,[int]$DisplayWidth=0)
$ErrorActionPreference='Stop'
if(Test-Path -LiteralPath $Output){throw 'Use a new evidence directory'}
$v=Get-Content -Raw (Join-Path $Frames 'verification.json')|ConvertFrom-Json
if(!$v.allRgbAndPngReadbackHashesMatch){throw 'Verified export required'}
if($Step -eq 1 -and $v.step -ne 1){throw 'All-frame view requires continuous original export'}
$selected=@($v.frames|Where-Object{($Start -lt 0 -or $_.frameIndex -ge $Start) -and ($End -lt 0 -or $_.frameIndex -lt $End)})
if(!$selected.Count){throw 'Empty range'}
$shown=@(for($i=0;$i -lt $selected.Count;$i+=$Step){$selected[$i]})
Add-Type -AssemblyName System.Drawing
New-Item -ItemType Directory -Path $Output|Out-Null
$sample=[Drawing.Bitmap]::FromFile((Join-Path $Frames ('frame-{0:D6}.png' -f $shown[0].frameIndex)))
if(!$Width){$Width=$sample.Width};if(!$Height){$Height=$sample.Height};$sample.Dispose()
if(!$DisplayWidth){$DisplayWidth=$Width};$displayHeight=[int]($Height*$DisplayWidth/$Width)
$font=[Drawing.Font]::new('Consolas',12);$per=$Columns*$Rows;$entries=@()
try{for($offset=0;$offset -lt $shown.Count;$offset+=$per){
 $canvas=[Drawing.Bitmap]::new([int]($Columns*$DisplayWidth),[int]($Rows*($displayHeight+24)));$g=[Drawing.Graphics]::FromImage($canvas)
 try{$g.Clear([Drawing.Color]::White);$g.InterpolationMode='NearestNeighbor';$g.PixelOffsetMode='Half'
  for($j=0;$j -lt $per -and ($offset+$j) -lt $shown.Count;$j++){
   $f=$shown[$offset+$j];$name='frame-{0:D6}.png' -f $f.frameIndex;$p=Join-Path $Frames $name;$b=[Drawing.Bitmap]::FromFile($p)
   try{$tx=($j%$Columns)*$DisplayWidth;$ty=[int][Math]::Floor($j/$Columns)*($displayHeight+24);$g.DrawImage($b,[Drawing.Rectangle]::new($tx,$ty,$DisplayWidth,$displayHeight),[Drawing.Rectangle]::new($X,$Y,$Width,$Height),[Drawing.GraphicsUnit]::Pixel);$label=if($DisplayWidth -lt 250){"f=$($f.frameIndex)"}else{"f=$($f.frameIndex) pts=$($f.pts)"};$g.DrawString($label,$font,[Drawing.Brushes]::Black,$tx,($ty+$displayHeight));$entries+=@{frameIndex=$f.frameIndex;pts=$f.pts;sheet=[int]($offset/$per);original=$p;rgbSha256=$f.rgbSha256}}finally{$b.Dispose()}
  }
  $canvas.Save((Join-Path $Output ('sheet-{0:D3}.png' -f [int]($offset/$per))),[Drawing.Imaging.ImageFormat]::Png)
 }finally{$g.Dispose();$canvas.Dispose()}
}}finally{$font.Dispose()}
$report=@{frames=$Frames;inputVerificationSha256=(Get-FileHash (Join-Path $Frames 'verification.json')).Hash.ToLower();crop=@($X,$Y,($X+$Width),($Y+$Height));step=$Step;displayWidth=$DisplayWidth;allFramesShown=($Step -eq 1 -and $v.step -eq 1);count=$entries.Count;entries=$entries;purpose='Deterministic original-pixel navigation/display only; no text inference, no GT assignment, no approval'}
[IO.File]::WriteAllText([IO.Path]::GetFullPath((Join-Path $Output 'report.json')),($report|ConvertTo-Json -Depth 15),[Text.UTF8Encoding]::new($false))
Write-Output "Displayed $($entries.Count) source frames; step=$Step"
