param([Parameter(Mandatory=$true)][string]$Frames,[Parameter(Mandatory=$true)][string]$Output,[int]$Start,[int]$End,[int]$X,[int]$Y,[int]$Width,[int]$Height,[int]$Threshold=100,[int]$ArrowX=-1,[int]$ArrowY=-1,[int]$ArrowWidth=0,[int]$ArrowHeight=0)
$ErrorActionPreference='Stop'
if(Test-Path $Output){throw 'New output required'}
Add-Type -AssemblyName System.Drawing
$drawingReferences=@([Drawing.Bitmap].Assembly.Location,[Drawing.Color].Assembly.Location,[Security.Cryptography.SHA256].Assembly.Location)+@([Drawing.Bitmap].GetInterfaces()|ForEach-Object{$_.Assembly.Location}|Where-Object{$_ -notlike '*System.Private.CoreLib.dll'}|Sort-Object -Unique)
if(!('DialoguePixelNavigation' -as [type])){Add-Type -ReferencedAssemblies $drawingReferences -TypeDefinition @'
using System; using System.Drawing; using System.Security.Cryptography;
public static class DialoguePixelNavigation {
 public static string[] Scan(string file,int x,int y,int w,int h,int threshold,int ax,int ay,int aw,int ah){
  using(var b=new Bitmap(file))using(var sha=SHA256.Create()){
   byte[] mask=new byte[w*h]; int count=0,arrow=0;
   for(int j=0;j<h;j++)for(int i=0;i<w;i++){var c=b.GetPixel(x+i,y+j);byte bit=(byte)(Math.Max(c.R,Math.Max(c.G,c.B))<threshold?1:0);mask[j*w+i]=bit;count+=bit;}
   if(ax>=0)for(int j=0;j<ah;j++)for(int i=0;i<aw;i++){var c=b.GetPixel(ax+i,ay+j);if(Math.Max(c.R,Math.Max(c.G,c.B))<threshold)arrow++;}
   return new[]{BitConverter.ToString(sha.ComputeHash(mask)).Replace("-","").ToLowerInvariant(),count.ToString(),arrow.ToString()};
  }
 }
}
'@}
$v=Get-Content -Raw (Join-Path $Frames 'verification.json')|ConvertFrom-Json
if($v.step -ne 1 -or !$v.allRgbAndPngReadbackHashesMatch){throw 'Continuous verified source required'}
New-Item -ItemType Directory -Path $Output|Out-Null
$records=@();$runs=@();$last='';$current=$null
foreach($f in $v.frames|Where-Object{$_.frameIndex -ge $Start -and $_.frameIndex -lt $End}){
 $path=Join-Path $Frames ('frame-{0:D6}.png' -f $f.frameIndex)
 $s=[DialoguePixelNavigation]::Scan([IO.Path]::GetFullPath($path),$X,$Y,$Width,$Height,$Threshold,$ArrowX,$ArrowY,$ArrowWidth,$ArrowHeight)
 $r=@{frameIndex=$f.frameIndex;pts=$f.pts;maskSha256=$s[0];darkPixelCount=[int]$s[1];arrowDarkPixelCount=[int]$s[2]};$records+=$r
 if($s[0] -ne $last){$current=@{startFrame=$f.frameIndex;endFrameExclusive=($f.frameIndex+1);maskSha256=$s[0];darkPixelCount=[int]$s[1];arrowDarkPixelCount=[int]$s[2]};$runs+=$current;$last=$s[0]}else{$current.endFrameExclusive=$f.frameIndex+1}
}
$rpt=@{purpose='Thresholded pixel-mask changes for navigation only. No character recognition, no assignment of GT/same semantic state; all original frames remain authoritative';input=$Frames;inputVerificationSha256=(Get-FileHash (Join-Path $Frames 'verification.json')).Hash.ToLower();range=@($Start,$End);rect=@($X,$Y,($X+$Width),($Y+$Height));threshold=$Threshold;runs=$runs;frames=$records}
[IO.File]::WriteAllText([IO.Path]::GetFullPath((Join-Path $Output 'scan.json')),($rpt|ConvertTo-Json -Depth 15),[Text.UTF8Encoding]::new($false))
$runs|Select-Object startFrame,endFrameExclusive,darkPixelCount,arrowDarkPixelCount|Format-Table -AutoSize
