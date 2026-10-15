param(
  [Parameter(Mandatory=$true)][string]$Notes,
  [Parameter(Mandatory=$true)][string]$Destination,
  [Parameter(Mandatory=$true)][string]$MediaId,
  [Parameter(Mandatory=$true)][string]$MediaHash,
  [Parameter(Mandatory=$true)][string]$LayoutRef,
  [Parameter(Mandatory=$true)][ValidateSet('pass1_read_complete_not_frozen','agent_double_read_complete_candidate')][string]$Status
)
$ErrorActionPreference='Stop'
if(Test-Path -LiteralPath $Destination){throw 'Use a new output file; old annotation bytes must be retained'}
$notesData=Get-Content -LiteralPath $Notes -Raw | ConvertFrom-Json
if($Status -eq 'agent_double_read_complete_candidate' -and $notesData.status -ne 'agent_second_raw_read_complete_not_frozen'){throw 'Second-read notes required; encoding does not establish image semantics or approval'}
function Convert-SourceRect($left,$top,$right,$bottom){,@([int](300+$left*18),[int](108+$top*18),[int](300+$right*18),[int](108+$bottom*18))}
$records=foreach($manual in $notesData.states){
  $start=[int]$manual[0];$end=[int]$manual[1];$fields=@($notesData.fields)
  if($manual[3]){$fields+=@{id='dialogue-0';role='dialogue_line';text=$manual[3];col=1;row=14}}
  $order=0
  $regions=@(foreach($field in $fields){
    $marks=@();$top=[int]$field.row
    for($offset=0;$offset -lt $field.text.Length;$offset++){
      $glyph=[string]$field.text[$offset]
      $kind=if('がぎぐげござじずぜぞだぢづでどばびぶべぼゔガギグゲゴザジズゼゾダヂヅデドバビブベボヴ'.Contains($glyph)){'dakuten'}elseif('ぱぴぷぺぽパピプペポ'.Contains($glyph)){'handakuten'}else{$null}
      if($kind){$marks+=@{kind=$kind;gridRow=($field.row-1);gridColumn=($field.col+$offset)}}
    }
    if($marks.Count){$top--}
    $region=[ordered]@{id=$field.id;role=$field.role;readingOrder=$order++;text=$field.text;gridRect=@($field.col,$field.row,($field.col+$field.text.Length),($field.row+1));sourceRect=(Convert-SourceRect $field.col $top ($field.col+$field.text.Length) ($field.row+1));markCells=$marks}
    if($field.variable){$region.variable=$field.variable}
    $region
  })
  $cursor=[ordered]@{downArrowVisible=[bool]$manual[6];menuCursors=@{gridRect=@($manual[4],$manual[5],($manual[4]+1),($manual[5]+1));sourceRect=(Convert-SourceRect $manual[4] $manual[5] ($manual[4]+1) ($manual[5]+1));style='filled_right'}}
  if($manual[6]){$cursor.downArrow=@{gridRect=@(18,16,19,17);sourceRect=(Convert-SourceRect 18 16 19 17);style='filled_down'}}
  [ordered]@{schemaVersion=1;stateId=($MediaHash.Substring(0,12)+"-f$start-f$end");mediaId=$MediaId;mediaSha256=$MediaHash;startFrame=$start;endFrameExclusive=$end;startPts=($start*512);endPtsExclusive=($end*512);timeBase='1/12288';kind=$manual[2];completePage=[bool]$manual[7];regions=$regions;cursor=$cursor;layoutRef=$LayoutRef;evidence=@{notes=$Notes;originalFrames=$notesData.source;humanPerFrameVerification='not claimed';outputIndependence='No tested OCR or production translation exists'};verificationStatus=$Status} | ConvertTo-Json -Depth 20 -Compress
}
[IO.File]::WriteAllLines([IO.Path]::GetFullPath($Destination),[string[]]$records,[Text.UTF8Encoding]::new($false))
Write-Output "Encoded $($records.Count) manually supplied states; no image inference, approval or freezing"
