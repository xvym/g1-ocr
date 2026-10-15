$ErrorActionPreference='Stop'
$dir='data/gt/candidates/ocr-all-scenes-v1/pc-deposit-navigation'
New-Item -ItemType Directory -Path $dir -ErrorAction Stop | Out-Null
function Save-New($p,$v){if(Test-Path -LiteralPath $p){throw 'Evidence exists'};[IO.File]::WriteAllText([IO.Path]::GetFullPath($p),($v|ConvertTo-Json -Depth 30),[Text.UTF8Encoding]::new($false))}
function F($id,$text,$col,$row,$role='menu_label'){@{id=$id;text=$text;col=$col;row=$row;role=$role}}
$full=@((F p1 'どうぐを ひきだす' 2 2),(F p2 'どうぐを あずける' 2 4),(F p3 'どうぐを すてる' 2 6),(F p4 'スイッチを きる' 2 8))
$partial=@((F p1 'どう' 2 2 'occluded_menu_visible_prefix'),(F p2 'どう' 2 4 'occluded_menu_visible_prefix'),(F p3 'どう' 2 6 'occluded_menu_visible_prefix'),(F p4 'スイ' 2 8 'occluded_menu_visible_prefix'))
$sets=@{parent=$full;partial=$partial;drawing=@($full[0],$full[1],$partial[2],$partial[3])}
$items=@{A=@('じてんしゃ','ポケモンのふえ','いいつりざお','ボロのつりざお');Bmix=@('じてんしゃ','いいつりざお','ボロのつりざお','ピーピーエイダー');B=@('ポケモンのふえ','いいつりざお','ボロのつりざお','ピーピーエイダー');Cmix=@('いいつりざお','いいつりざお','ボロのつりざお','ピーピーエイダー');C=@('いいつりざお','ボロのつりざお','ピーピーエイダー','ひでんマシン02');D=@('ボロのつりざお','ピーピーエイダー','ひでんマシン02','ひでんマシン05');Emix=@('ボロのつりざお','ひでんマシン02','ひでんマシン05','ひでんマシン01');E=@('ピーピーエイダー','ひでんマシン02','ひでんマシン05','ひでんマシン01')}
foreach($k in $items.Keys){$fs=@($partial);for($j=0;$j -lt 4;$j++){$name=$items[$k][$j];$f=F "item-$j" $name 6 (4+2*$j) 'item_name';$f.variable=@{role='item_name';type='string';displayValue=$name};$fs+=$f;if($name -eq 'ピーピーエイダー'){$fs+=(F "multiply-$j" '×' 15 (4+2*$j) 'quantity_label');$q=F "quantity-$j" '1' 17 (4+2*$j) 'item_quantity';$q.variable=@{role='item_quantity';type='integer';displayValue='1';numericValue=1};$fs+=$q}};$sets[$k]=$fs}
$sets.firstdraw=@($sets.drawing)+@($sets.A|Where-Object{$_.id -in @('item-1','item-2','item-3')})
$sets.closing=@($partial[0],$partial[1],$full[2],$full[3])+@($sets.E|Where-Object{$_.id -in @('item-0','multiply-0','quantity-0')})
$main=@((F main1 'マサキの パソコン' 2 2),(F main2 'グリーンの パソコン' 2 4),(F main3 'オーキドの パソコン' 2 6),(F main4 'ポケモン リーグ' 2 8),(F main5 'スイッチを きる' 2 10))
$sets.main=$main;$sets.maindraw=@($main[0],$main[1]);$sets.empty=@()
# start,end,fields,prompt,parent-cursor,child-cursor,arrow,pageId,kind
$rows=@(
 @(1634,1638,'parent','なにを しますか？','filled_right:2','',0,'pc-parent-before','menu_complete'),
 @(1638,1649,'parent','なにを しますか？','filled_right:4','',0,'pc-parent-deposit','menu_complete'),
 @(1649,1650,'parent','','outline_right:4','',0,'','message_clear_menu_remains'),
 @(1650,1651,'parent','な','outline_right:4','',0,'','message_loading'),
 @(1651,1652,'parent','なにを','outline_right:4','',0,'','message_loading'),
 @(1652,1654,'parent','なにを あずけ','outline_right:4','',0,'','message_loading'),
 @(1654,1659,'parent','なにを あずけますか？','outline_right:4','',0,'','submenu_opening'),
 @(1659,1660,'firstdraw','なにを あずけますか？','outline_right:4','',1,'','submenu_partial_redraw'),
 @(1660,1661,'A','なにを あずけますか？','outline_right:4','',1,'','submenu_cursor_loading'),
 @(1661,1673,'A','なにを あずけますか？','outline_right:4','4',1,'pc-list-A-first','menu_complete'),
 @(1673,1674,'A','なにを あずけますか？','outline_right:4','',1,'','cursor_transition'),
 @(1674,1688,'A','なにを あずけますか？','outline_right:4','6',1,'pc-list-A-second','menu_complete'),
 @(1688,1689,'A','なにを あずけますか？','outline_right:4','6,8',1,'','dual_cursor_transition'),
 @(1689,1697,'A','なにを あずけますか？','outline_right:4','8',1,'pc-list-A-third','menu_complete'),
 @(1697,1698,'Bmix','なにを あずけますか？','outline_right:4','',1,'','scroll_partial_redraw'),
 @(1698,1706,'B','なにを あずけますか？','outline_right:4','8',1,'pc-list-B-third','menu_complete'),
 @(1706,1707,'Cmix','なにを あずけますか？','outline_right:4','8',1,'','scroll_duplicate_row'),
 @(1707,1708,'C','なにを あずけますか？','outline_right:4','',1,'','cursor_transition'),
 @(1708,1714,'C','なにを あずけますか？','outline_right:4','8',1,'pc-list-C-third','menu_complete'),
 @(1714,1716,'D','なにを あずけますか？','outline_right:4','',1,'','cursor_transition'),
 @(1716,1724,'D','なにを あずけますか？','outline_right:4','8',1,'pc-list-D-third','menu_complete'),
 @(1724,1725,'Emix','なにを あずけますか？','outline_right:4','',1,'','scroll_partial_redraw'),
 @(1725,1731,'E','なにを あずけますか？','outline_right:4','8',1,'pc-list-E-third','menu_complete'),
 @(1731,1732,'closing','なにを あずけますか？','outline_right:4','',0,'','submenu_partial_closing'),
 @(1732,1733,'parent','','','',0,'','message_clear_menu_remains'),
 @(1733,1734,'parent','な','','',0,'','message_loading'),
 @(1734,1736,'parent','なにを','','',0,'','message_loading'),
 @(1736,1737,'parent','なにを しますか？','','',0,'','cursor_transition'),
 @(1737,1747,'parent','なにを しますか？','filled_right:4','',0,'pc-parent-after','menu_complete'),
 @(1747,1749,'empty','','','',0,'','empty_target'),
 @(1749,1750,'maindraw','','main','',0,'','parent_partial_redraw'),
 @(1750,1754,'main','','main','',0,'pc-main-after','menu_complete')
)
$states=@(foreach($r in $rows){$fs=@($sets[$r[2]]);if($r[3]){$fs+=(F prompt $r[3] 1 14 'pc_prompt')};$cs=@();if($r[4] -eq 'main'){$cs+=@{style='filled_right';gridRect=@(1,2,2,3)}}elseif($r[4]){$v=$r[4].Split(':');$rr=[int]$v[1];$cs+=@{style=$v[0];gridRect=@(1,$rr,2,($rr+1))}};if($r[5]){foreach($cr in $r[5].Split(',')){$rr=[int]$cr;$cs+=@{style='filled_right';gridRect=@(5,$rr,6,($rr+1))}}};$s=@{startFrame=$r[0];endFrameExclusive=$r[1];fields=$fs;kind=$r[8];completePage=[bool]$r[7];menuCursors=$cs};if($r[7]){$s.pageOpportunityId=$r[7]};if($r[6]){$s.downArrow=@{style='filled_down';gridRect=@(18,11,19,12)}};if($r[0] -ge 1660 -and $r[0] -lt 1732){$s.detachedMarks=@(@{kind='dakuten';gridRect=@(4,1,5,2)},@{kind='dakuten';gridRect=@(9,1,10,2)})};if($r[0] -eq 1749){$s.detachedMarks=@(@{kind='dakuten';gridRect=@(5,5,6,6)},@{kind='handakuten';gridRect=@(8,5,9,6)})};$s})
$notes=@{mediaId='video-02';mediaSha256='c83de914352e64d207c9ea37318f4503855d6ca7f3b31af6215a0d9b451cf207';source='data/frames/video-02/expansion-context-f1320-f2520-v1';status='first_raw_read_complete_not_frozen';humanPerFrameVerification='not claimed';frozen=$false;states=$states;firstRead='All score frames1634-1753 viewed ordered raw-only sheets015-021, direct1659/1660/1661/1697/1698/1706/1707/1714/1715/1724/1725/1731/1749';scope='Complete deposit submenu operation, parent cursor change, prompt loading, list opening/navigation/scrolling/closing, return main PC hierarchy';notes=@('Occluded parent bases reduced to actual visible two-character prefixes; detached upper dakuten remain separately','Quantity1/PPエイダー/HM02,05,01 direct source reads. No transaction committed in selected operation','1659 partially drawn list leaves top two parent lines fully visible;1731 partial close leaves top item/quantity and lower parent lines;1749 main rows1-2 and detached marks only')}
Save-New "$dir/manual-pass1.json" $notes
Save-New "$dir/layout-v1.json" (Get-Content -Raw 'data/gt/candidates/ocr-all-scenes-v1/battle-result/layout-v1.json' | ConvertFrom-Json)
Save-New 'data/meta/all-scenes-pc-deposit-navigation-clip-v1.json' @{schemaVersion=2;id='c83de914352e-f1632-f1756-pc-deposit-navigation';mediaId=$notes.mediaId;mediaSha256=$notes.mediaSha256;startFrame=1632;endFrameExclusive=1756;warmup=@(1632,1634);scoredRange=@(1634,1754);postContext=@(1754,1756);timeBase='1/12288';set='development';independentSession='unverified';scoreKind='text_and_state';pageOpportunityPolicy='explicit_id';gt="$dir/ocr-candidate-v1.jsonl";boundaryStatus='first_raw_read_complete_second_pending';frozen=$false;eventType=$notes.scope;selectionReason='Predetermined PC context raw navigation; before1638 parent cursor movement through deposit list and1731 closing/1747 return hierarchy, stable main after1750';scopeLimits=@('Selected navigation does not commit item transfer','Other PC Pokemon box operations unselected and unverified')}
