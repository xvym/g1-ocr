param([string]$ReviewDirectory='reports/gt-review/gt-all-scenes-v1')
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Drawing
$drawingReferences=@([Drawing.Bitmap].Assembly.Location,[Drawing.Color].Assembly.Location)+@([Drawing.Bitmap].GetInterfaces()|ForEach-Object{$_.Assembly.Location}|Where-Object{$_ -notlike '*System.Private.CoreLib.dll'}|Sort-Object -Unique)
Add-Type -ReferencedAssemblies $drawingReferences -TypeDefinition @'
using System;
using System.Drawing;
public static class ReviewZoomCheck {
 public static long Compare(string original,string zoom,int left,int top,int scale) {
  using(var a=new Bitmap(original)) using(var b=new Bitmap(zoom)) {
   long differences=0;
   for(int y=0;y<b.Height;y++)for(int x=0;x<b.Width;x++)
    if(a.GetPixel(left+x/scale,top+y/scale).ToArgb()!=b.GetPixel(x,y).ToArgb())differences++;
   return differences;
  }
 }
}
'@
$data=Get-Content -Raw "$ReviewDirectory/review-data.json"|ConvertFrom-Json
$rows=@(foreach($p in @(@('startup-nontarget',110),@('exploration-npc',9854),@('status-pages',526),@('skills-turn',17503),@('pc-deposit-navigation',1659),@('approved-trial',5296))){$it=$data.items|Where-Object id -eq $p[0];$f=$it.frames|Where-Object index -eq $p[1];$raw=[IO.Path]::GetFullPath((Join-Path $ReviewDirectory $f.file));$zoom=[IO.Path]::GetFullPath((Join-Path $ReviewDirectory $f.zoom));$diff=[ReviewZoomCheck]::Compare($raw,$zoom,$it.crop[0],$it.crop[1],$it.zoomScale);if($diff){throw "Nearest-neighbor pixel mismatch $($it.id)"};@{scene=$it.id;sourceFrame=$f.index;scale=$it.zoomScale;crop=$it.crop;differentPixels=$diff;status='generated_zoom_pixels_match_exact_nearest_neighbor_of_original'}})
$output=Join-Path $ReviewDirectory 'evidence/zoom-pixel-check-v1.json'
if(Test-Path -LiteralPath $output){throw 'Evidence already exists'}
[IO.File]::WriteAllText([IO.Path]::GetFullPath($output),($rows|ConvertTo-Json -Depth 8),[Text.UTF8Encoding]::new($false))
Write-Output 'Six profiles/transition frames: exact nearest-neighbor pixel differences 0'
