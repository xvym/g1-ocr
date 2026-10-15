param([Parameter(Mandatory=$true)][string]$Source,[Parameter(Mandatory=$true)][int]$Start,[Parameter(Mandatory=$true)][int]$End,[Parameter(Mandatory=$true)][string]$Destination)
$ErrorActionPreference='Stop'
if(Test-Path $Destination){throw 'Never overwrite preserved frames'}
$v=Get-Content -Raw (Join-Path $Source 'verification.json')|ConvertFrom-Json
if($v.step -ne 1 -or !$v.allRgbAndPngReadbackHashesMatch -or $Start -lt $v.startFrame -or $End -gt $v.endFrameExclusive -or $Start -ge $End){throw 'Continuous verified parent does not enclose slice'}
$frames=@($v.frames|Where-Object{$_.frameIndex -ge $Start -and $_.frameIndex -lt $End})
if($frames.Count -ne $End-$Start){throw 'Missing slice frames'}
New-Item -ItemType Directory -Path $Destination|Out-Null
$checks=@(foreach($f in $frames){$name='frame-{0:D6}.png' -f $f.frameIndex;$from=Join-Path $Source $name;$to=Join-Path $Destination $name;Copy-Item -LiteralPath $from -Destination $to;$a=(Get-FileHash -LiteralPath $from).Hash.ToLower();$b=(Get-FileHash -LiteralPath $to).Hash.ToLower();if($a -ne $b){throw 'Copy changed raw PNG'};@{frameIndex=$f.frameIndex;pngSha256=$a;copyIdentical=$true}})
$v.startFrame=$Start;$v.endFrameExclusive=$End;$v.frames=$frames
$v|Add-Member -NotePropertyName derivedFromContinuousExport -NotePropertyValue @{path=$Source;verificationSha256=(Get-FileHash (Join-Path $Source 'verification.json')).Hash.ToLower();method='Byte-identical contiguous subset of source-from-start re-decode with every RGB/PNG already checked; this slice is not a new decode';copies=$checks}
function Save($p,$o){[IO.File]::WriteAllText([IO.Path]::GetFullPath($p),($o|ConvertTo-Json -Depth 30),[Text.UTF8Encoding]::new($false))}
Save (Join-Path $Destination 'verification.json') $v
Save (Join-Path $Destination 'decode.command.json') @{action='slice-verified-frames.ps1';source=$Source;startFrame=$Start;endFrameExclusive=$End;destination=$Destination;parentCommand=(Get-Content -Raw (Join-Path $Source 'decode.command.json')|ConvertFrom-Json);note='The original FFmpeg command ran against the parent range. Slicing copied every selected PNG; no re-encoding/resampling/new timestamps.'}
Copy-Item -LiteralPath (Join-Path $Source 'decode.stderr') -Destination (Join-Path $Destination 'decode.stderr')
Write-Output "Preserved byte-identical original-frame subset [$Start,$End)"
