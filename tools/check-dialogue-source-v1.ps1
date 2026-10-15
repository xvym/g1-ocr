param([string]$ReportPath='reports/m2-dialogue-expansion-v1/source-identity-v1.json')
$ErrorActionPreference='Stop'
$out='reports/m2-dialogue-expansion-v1'
New-Item -ItemType Directory -Force -Path $out|Out-Null
$sources=@(Get-Content data/meta/m1-v1/media.jsonl|ForEach-Object{$_|ConvertFrom-Json})
$sourceChecks=@(foreach($s in $sources){$actual=(Get-FileHash -LiteralPath $s.path).Hash.ToLower();if($actual -ne $s.sha256){throw 'Source changed'};@{mediaId=$s.id;path=$s.path;sha256=$actual;frameCount=$s.frameCount;timeBase=$s.timeBase;durationSeconds=$s.durationSeconds;matches=$true}})
$root='reports/gt-review/gt-all-scenes-v1'
$manifest=Get-Content -Raw "$root/bundle-manifest.json"|ConvertFrom-Json
$mh=(Get-FileHash "$root/bundle-manifest.json").Hash.ToLower()
if($mh -ne 'f999098f9ec4401a55fb10641ad2a1a342d24447af4ce40267e5bb60669f3246'){throw 'Old manifest identity changed'}
foreach($f in $manifest.files){$actual=(Get-FileHash -LiteralPath (Join-Path $root $f.path)).Hash.ToLower();if($actual -ne $f.sha256){throw "Old review changed: $($f.path)"}}
$report=@{sources=$sourceChecks;oldReviewManifestSha256=$mh;oldReviewFilesChecked=$manifest.files.Count;oldReviewUnchanged=$true;framesLedgerSha256=(Get-FileHash data/meta/m1-v1/frames.jsonl).Hash.ToLower();frozenEnvelopeSha256=(Get-FileHash data/gt/frozen/ocr-trial-v2/freeze.json).Hash.ToLower();method='Read-only SHA256 recheck, no OCR or semantic verification';status='identities_match_not_gt_approval'}
$p=$ReportPath;if(Test-Path $p){throw 'Do not overwrite evidence'}
[IO.File]::WriteAllText([IO.Path]::GetFullPath($p),($report|ConvertTo-Json -Depth 12),[Text.UTF8Encoding]::new($false))
Write-Output "4 original videos and $($manifest.files.Count) old review files unchanged"
