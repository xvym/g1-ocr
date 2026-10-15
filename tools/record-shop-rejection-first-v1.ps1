$ErrorActionPreference='Stop'
$dir='data/gt/candidates/ocr-all-scenes-v1/shop-rejection'
New-Item -ItemType Directory -Path $dir -ErrorAction Stop | Out-Null
function Save-New($p,$v){if(Test-Path -LiteralPath $p){throw 'Evidence exists'};[IO.File]::WriteAllText([IO.Path]::GetFullPath($p),($v|ConvertTo-Json -Depth 30),[Text.UTF8Encoding]::new($false))}
function F($id,$text,$col,$row,$role){@{id=$id;text=$text;col=$col;row=$row;role=$role}}
$template=Get-Content -Raw 'data/gt/candidates/ocr-all-scenes-v1/shop-sell-entry/manual-pass2.json'|ConvertFrom-Json
$base=@(($template.states|Where-Object startFrame -eq 1070).fields|Where-Object{$_.id -notlike 'item-*' -and $_.id -notlike 'prompt-*'})
$items=@('ひでんマシン05','ひでんマシン01','ひでんマシン03','ひでんマシン04')
for($j=0;$j -lt 4;$j++){$f=F "item-$j" $items[$j] 6 (4+2*$j) 'item_name';$f.variable=@{role='item_name';type='string';displayValue=$items[$j]};$base+=$f}
$parent=@(($template.states|Where-Object startFrame -eq 1044).fields|Where-Object{$_.id -notlike 'prompt-*'})
$top='……そのしなものに おねだんを'
$rows=@(
 @(1408,1413,'どれを うっていただけますか？','', 'filled_right',1,0,'shop-HM05-before','menu_complete'),
 @(1413,1414,'','', 'filled_right',1,0,'','message_clear_menu_remains'),
 @(1414,1415,'…','', 'outline_right',1,0,'','message_loading'),
 @(1415,1417,'……その','', 'outline_right',1,0,'','message_loading'),
 @(1417,1418,'……そのしなも','', 'outline_right',1,0,'','message_loading'),
 @(1418,1419,'……そのしなものに','', 'outline_right',1,0,'','message_loading'),
 @(1419,1420,'……そのしなものに おねだ','', 'outline_right',1,0,'','message_loading'),
 @(1420,1422,$top,'お', 'outline_right',1,0,'','message_loading'),
 @(1422,1424,$top,'おつけするわけ', 'outline_right',1,0,'','message_loading'),
 @(1424,1425,$top,'おつけするわけには', 'outline_right',1,0,'','message_loading'),
 @(1425,1426,$top,'おつけするわけには まいり', 'outline_right',1,0,'','message_loading'),
 @(1426,1428,$top,'おつけするわけには まいりません', 'outline_right',1,0,'shop-rejection-message','message_complete'),
 @(1428,1438,$top,'おつけするわけには まいりません', 'outline_right',1,1,'shop-rejection-message','message_complete'),
 @(1438,1439,'','', 'outline_right',1,0,'','message_clear_menu_remains'),
 @(1439,1440,'','', '',0,0,'','list_removed_parent_remains')
)
$states=@(foreach($r in $rows){$fs=if($r[0] -eq 1439){@($parent)}else{@($base)};if($r[2]){$fs+=(F message-top $r[2] 1 14 'shop_message')};if($r[3]){$fs+=(F message-bottom $r[3] 1 16 'shop_message')};$cs=@(@{style='outline_right';gridRect=@(1,3,2,4)});if($r[4]){$cs+=@{style=$r[4];gridRect=@(5,4,6,5)}};$s=@{startFrame=$r[0];endFrameExclusive=$r[1];fields=$fs;menuCursors=$cs;kind=$r[8];completePage=[bool]$r[7]};if($r[7]){$s.pageOpportunityId=$r[7]};if($r[5]){$s.downArrow=@{style='filled_down';gridRect=@(18,11,19,12)}};if($r[6]){$s.additionalDownArrows=@(@{style='filled_down';gridRect=@(18,11,19,12);renderNote='Original source retains list arrow while separate message wait arrow is visible'});$s.downArrow=@{style='filled_down';gridRect=@(18,16,19,17)}};$s})
$m=@{mediaId=$template.mediaId;mediaSha256=$template.mediaSha256;source=$template.source;status='first_raw_read_complete_not_frozen';frozen=$false;humanPerFrameVerification='not claimed';states=$states;firstRead='All1408-1439 score frames raw-only viewed ordered sheets030-031 and full original key loading frames';notes=@('Ellipsis glyph occupies one full8x8 cell with three dots;1414 is one U+2026, later two U+2026, not six ordinary fullwidth periods','Both list arrow and message arrow1428-1437 independently visible and both encoded','Money22526円 unchanged, selected HM05 transaction refused; no successful sale or balance change claimed','One scoring frame1439 overlaps existing shop-exit; global source frame union deduplicates')}
Save-New "$dir/manual-pass1.json" $m
Save-New "$dir/layout-v1.json" (Get-Content -Raw 'data/gt/candidates/ocr-all-scenes-v1/shop-sell-entry/layout-v1.json'|ConvertFrom-Json)
Save-New 'data/meta/all-scenes-shop-rejection-clip-v1.json' @{schemaVersion=2;id='9c8aee036f72-f1406-f1442-shop-rejection';mediaId=$m.mediaId;mediaSha256=$m.mediaSha256;startFrame=1406;endFrameExclusive=1442;warmup=@(1406,1408);scoredRange=@(1408,1440);postContext=@(1440,1442);timeBase='1/12288';set='development';independentSession='unverified';scoreKind='text_and_state';pageOpportunityPolicy='explicit_id';gt="$dir/ocr-candidate-v1.jsonl";boundaryStatus='first_raw_read_complete_second_pending';frozen=$false;eventType='complete refused-sale message lifecycle';selectionReason='Existing raw shop context shows selection of HM05, refusal text loading/negation/full display then clear1438 and list removal1439; no tested OCR';scopeLimits=@('No completed sale; monetary transaction acceptance unverified','Overlap source1439 with shop-exit is one raw frame, count once')}
