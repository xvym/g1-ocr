param([Parameter(Mandatory=$true)][string]$Notes,[Parameter(Mandatory=$true)][string]$Layout,[Parameter(Mandatory=$true)][string]$Destination,[Parameter(Mandatory=$true)][ValidateSet('pass1_read_complete_not_frozen','agent_double_read_complete_candidate')][string]$Status)
$ErrorActionPreference='Stop'
if(Test-Path -LiteralPath $Destination){throw 'Use a new GT version; never overwrite annotations'}
$manual=Get-Content -LiteralPath $Notes -Raw | ConvertFrom-Json
$mapping=Get-Content -LiteralPath $Layout -Raw | ConvertFrom-Json
if($Status -eq 'agent_double_read_complete_candidate' -and $manual.status -ne 'agent_second_raw_read_complete_not_frozen'){throw 'Second raw-read notes required; encoding does not establish image semantics/approval'}
$ledger=@(Get-Content ('data/meta/m1-v1/'+$manual.mediaId+'/frames.jsonl') | ForEach-Object {$_ | ConvertFrom-Json})
function Project-Pixels($l,$t,$r,$b){
  if($mapping.sourceScaleXNumerator){$xn=$mapping.sourceScaleXNumerator;$xd=$mapping.sourceScaleXDenominator;$yn=$mapping.sourceScaleYNumerator;$yd=$mapping.sourceScaleYDenominator}else{$xn=$yn=$mapping.sourceScaleNumerator;$xd=$yd=$mapping.sourceScaleDenominator}
  ,@([int]($mapping.sourceOrigin[0]+[Math]::Floor(($l+$stateOffsetX)*$xn/$xd)),[int]($mapping.sourceOrigin[1]+[Math]::Floor(($t+$stateOffsetY)*$yn/$yd)),[int]($mapping.sourceOrigin[0]+[Math]::Ceiling(($r+$stateOffsetX)*$xn/$xd)),[int]($mapping.sourceOrigin[1]+[Math]::Ceiling(($b+$stateOffsetY)*$yn/$yd)))
}
function Project-Grid($rect){Project-Pixels ($rect[0]*8) ($rect[1]*8) ($rect[2]*8) ($rect[3]*8)}
function Encode-Symbol($symbol){$out=[ordered]@{};foreach($p in $symbol.PSObject.Properties){$out[$p.Name]=$p.Value};$out.sourceRect=Project-Grid $symbol.gridRect;$out}
$rows=foreach($s in $manual.states){
  $stateOffsetX=0;$stateOffsetY=0
  if($s.renderOffsetLogicalPixels){$stateOffsetX=$s.renderOffsetLogicalPixels[0];$stateOffsetY=$s.renderOffsetLogicalPixels[1]}
  $fields=if($s.fieldSet){@($manual.fieldSets.($s.fieldSet))}else{@($s.fields)}
  $order=0;$regions=@(foreach($f in $fields){
    $marks=@();$top=[int]$f.row
    if($f.renderUnits){$rect=$f.gridRect;$source=Project-Pixels $f.logicalRect[0] $f.logicalRect[1] $f.logicalRect[2] $f.logicalRect[3]}
    else{
      for($k=0;$k -lt $f.text.Length;$k++){$char=[string]$f.text[$k];$kind=if('がぎぐげござじずぜぞだぢづでどばびぶべぼゔガギグゲゴザジズゼゾダヂヅデドバビブベボヴ'.Contains($char)){'dakuten'}elseif('ぱぴぷぺぽパピプペポ'.Contains($char)){'handakuten'}else{$null};if($kind){$marks+=@{kind=$kind;gridRow=($f.row-1);gridColumn=($f.col+$k)}}}
      if($f.PSObject.Properties.Name -contains 'markCells'){$marks=@($f.markCells)}
      $rect=@($f.col,$f.row,($f.col+$f.text.Length),($f.row+1));if($marks.Count){$top--};$source=Project-Grid @($rect[0],$top,$rect[2],$rect[3])
    }
    $region=[ordered]@{id=$f.id;role=$f.role;readingOrder=$order++;text=$f.text;gridRect=$rect;sourceRect=$source;markCells=$marks}
    foreach($key in @('variable','logicalRect','renderUnits','renderEvidence','renderNote')){if($f.PSObject.Properties.Name -contains $key){$region[$key]=$f.$key}}
    $region
  })
  $cursors=@(foreach($cursor in $s.menuCursors){Encode-Symbol $cursor})
  $cursorState=[ordered]@{menuCursors=$cursors;downArrowVisible=[bool]$s.downArrow}
  if($s.downArrow){$cursorState.downArrow=Encode-Symbol $s.downArrow}
  if($s.additionalDownArrows){$cursorState.additionalDownArrows=@(foreach($arrow in $s.additionalDownArrows){Encode-Symbol $arrow})}
  $start=$ledger[$s.startFrame];$last=$ledger[$s.endFrameExclusive-1]
  $out=[ordered]@{schemaVersion=2;stateId=($manual.mediaSha256.Substring(0,12)+"-f$($s.startFrame)-f$($s.endFrameExclusive)");mediaId=$manual.mediaId;mediaSha256=$manual.mediaSha256;startFrame=$s.startFrame;endFrameExclusive=$s.endFrameExclusive;startPts=$start.pts;endPtsExclusive=($last.pts+$last.durationPts);timeBase=$start.timeBase;kind=$s.kind;completePage=[bool]$s.completePage;regions=$regions;cursor=$cursorState;layoutRef=(Split-Path -Leaf $Layout);verificationStatus=$Status;evidence=@{manualNotes=$Notes;originalFrames=$manual.source;humanPerFrameVerification='not claimed';outputIndependence='No tested OCR or production translation exists'}}
  if($s.detachedMarks){$out.detachedMarks=@(foreach($mark in $s.detachedMarks){Encode-Symbol $mark})}
  foreach($key in @('pageOpportunityId','renderOffsetLogicalPixels','renderNote','pendingReason')){if($s.PSObject.Properties.Name -contains $key){$out[$key]=$s.$key}}
  $out | ConvertTo-Json -Depth 30 -Compress
}
[IO.File]::WriteAllLines([IO.Path]::GetFullPath($Destination),[string[]]$rows,[Text.UTF8Encoding]::new($false))
Write-Output "Encoded $($rows.Count) manually supplied visible states; no image inference or freezing"
