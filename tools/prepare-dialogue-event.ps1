param([Parameter(Mandatory=$true)][string]$Id)
$ErrorActionPreference='Stop'
$dir="data/gt/candidates/ocr-dialogue-expansion-v1/$Id"
$manual=Get-Content -Raw "$dir/manual-pass2.json"|ConvertFrom-Json
$frames="data/frames/$($manual.mediaId)/dialogue-$Id-reviewed-v1"
& ./tools/slice-verified-frames.ps1 -Source $manual.source -Start $manual.range[0] -End $manual.range[1] -Destination $frames
$clipPath="data/meta/dialogue-$Id-clip-v1.json"
if(Test-Path $clipPath){throw 'Never overwrite clip selection'}
$clip=@{schemaVersion=2;id=($manual.mediaSha256.Substring(0,12)+"-f$($manual.range[0])-f$($manual.range[1])-$Id");mediaId=$manual.mediaId;mediaSha256=$manual.mediaSha256;startFrame=$manual.range[0];endFrameExclusive=$manual.range[1];warmup=@($manual.range[0],$manual.scoredRange[0]);scoredRange=$manual.scoredRange;postContext=@($manual.scoredRange[1],$manual.range[1]);timeBase=if($manual.mediaId -eq 'video-01'){'1/30'}else{'1/12288'};set='development';independentSession='unverified';scoreKind='text_and_state';pageOpportunityPolicy='explicit_id';gt="$dir/ocr-candidate-v1.jsonl";boundaryStatus='agent_double_read_complete_candidate_not_frozen';frozen=$false;eventType=$manual.name;selectionReason='Original-source navigation selected this naturally bounded speech block before OCR exists. All original pre-loading, visible loading/complete/scroll/clear and ending after-state frames retained; details and corrected boundaries in both manual reads and differences.';scopeLimits=@('Other dialogue and unselected source intervals remain unverified','Not independent acceptance; candidate GT only, no approval or OCR pass')}
[IO.File]::WriteAllText([IO.Path]::GetFullPath($clipPath),($clip|ConvertTo-Json -Depth 20),[Text.UTF8Encoding]::new($false))
$report="reports/m2-dialogue-expansion-v1/$Id-integrity-v1.json"
& java '-Dfile.encoding=UTF-8' -cp 'verification/target/classes;.cache/m2/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar' io.github.g1ocr.verification.VideoTool gt-check data/meta/m1-v1 $clipPath "$dir/ocr-candidate-v1.jsonl" "$dir/layout-v1.json" $report
if($LASTEXITCODE){throw "GT integrity failed $Id"}
