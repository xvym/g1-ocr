$ErrorActionPreference='Stop'
function Save-New($p,$v){if(Test-Path -LiteralPath $p){throw 'Evidence exists'};[IO.File]::WriteAllText([IO.Path]::GetFullPath($p),($v|ConvertTo-Json -Depth 30),[Text.UTF8Encoding]::new($false))}
function F($id,$text,$col,$row,$role='menu_label'){@{id=$id;text=$text;col=$col;row=$row;role=$role}}
$parent=@((F buy 'かいに きた' 2 1),(F sell 'うりに きた' 2 3),(F leave 'べつに いいです' 2 5),(F money-label 'おかね' 14 0 'money_label'))
$money=F money-value '22526円' 13 1 'money';$money.variable=@{role='money';type='integer';displayValue='22526円';numericValue=22526;currency='JPY';suffix='円'}
$partial=@($parent[0],(F sell 'うり' 2 3 'occluded_menu_visible_prefix'),(F leave 'べつ' 2 5 'occluded_menu_visible_prefix'),$parent[3],$money)
$sets=@{parent=@($parent)+@($money);parentNoMoney=$parent}
$buyA=@('ハイパーボール','スーパーボール','かいふくのくすり','まんたんのくすり');$pricesA=@(1200,600,3000,2500)
$buyB=@('スーパーボール','かいふくのくすり','まんたんのくすり','なんでもなおし');$pricesB=@(600,3000,2500,600)
foreach($k in @('A','B')){$names=if($k -eq 'A'){$buyA}else{$buyB};$prices=if($k -eq 'A'){$pricesA}else{$pricesB};$fs=@($partial);for($j=0;$j -lt 4;$j++){$r=4+2*$j;$name=F "item-$j" $names[$j] 6 $r 'item_name';$name.variable=@{role='item_name';type='string';displayValue=$names[$j]};$fs+=$name;$value=[string]$prices[$j]+'円';$price=F "price-$j" $value (19-$value.Length) $r 'item_price';$price.variable=@{role='item_price';type='integer';displayValue=$value;numericValue=$prices[$j];currency='JPY';suffix='円'};$fs+=$price};$fs+=(F prompt 'ゆっくり ごらんになって ください' 1 14 'shop_prompt');$sets[$k]=$fs}
$sell=@($partial);$names=@('じてんしゃ','ポケモンのふえ','いいつりざお','ボロのつりざお');for($j=0;$j -lt 4;$j++){$f=F "item-$j" $names[$j] 6 (4+2*$j) 'item_name';$f.variable=@{role='item_name';type='string';displayValue=$names[$j]};$sell+=$f};$sets.sell=$sell
$sets.sellDraw=@($parent)+@($sell|Where-Object{$_.id -in @('item-1','item-2','item-3')})
$allRows=@{
 'shop-buy-navigation'=@(
 @(820,826,'A','',1,6,0,'buy-super','menu_complete'),
 @(826,833,'A','',1,8,0,'buy-fullrestore','menu_complete'),
 @(833,859,'A','',1,8,1,'buy-fullrestore','menu_complete'),
 @(859,860,'B','',1,0,1,'','scroll_cursor_transition'),
 @(860,864,'B','',1,8,1,'buy-maxpotion','menu_complete')
 );
 'shop-sell-entry'=@(
 @(1044,1048,'parent','そのほかに わたくしどもで|おちからに なれることは？','filled_right:1',0,0,'shop-parent-buy','menu_complete'),
 @(1048,1056,'parent','そのほかに わたくしどもで|おちからに なれることは？','filled_right:3',0,0,'shop-parent-sell','menu_complete'),
 @(1056,1057,'parent','そのほかに わたくしどもで|おちからに なれることは？','outline_right:3',0,0,'','submenu_opening'),
 @(1057,1058,'parent','','outline_right:3',0,0,'','message_clear_menu_remains'),
 @(1058,1059,'parent','どれを','outline_right:3',0,0,'','message_loading'),
 @(1059,1060,'parent','どれを うっ','outline_right:3',0,0,'','message_loading'),
 @(1060,1062,'parent','どれを うっていた','outline_right:3',0,0,'','message_loading'),
 @(1062,1063,'parent','どれを うっていただけま','outline_right:3',0,0,'','message_loading'),
 @(1063,1068,'parentNoMoney','どれを うっていただけますか？','outline_right:3',0,0,'','money_temporarily_cleared'),
 @(1068,1069,'sellDraw','どれを うっていただけますか？','outline_right:3',0,1,'','submenu_partial_redraw'),
 @(1069,1070,'sell','どれを うっていただけますか？','outline_right:3',0,1,'','submenu_cursor_loading'),
 @(1070,1082,'sell','どれを うっていただけますか？','outline_right:3',4,1,'sell-first','menu_complete'),
 @(1082,1092,'sell','どれを うっていただけますか？','outline_right:3',4,0,'sell-first','menu_complete'),
 @(1092,1096,'sell','どれを うっていただけますか？','outline_right:3',6,0,'sell-second','menu_complete'),
 @(1096,1102,'sell','どれを うっていただけますか？','outline_right:3',6,1,'sell-second','menu_complete')
 )
}
foreach($scene in @('shop-buy-navigation','shop-sell-entry')){
 $dir="data/gt/candidates/ocr-all-scenes-v1/$scene";New-Item -ItemType Directory -Path $dir -ErrorAction Stop|Out-Null
 $states=@(foreach($r in $allRows[$scene]){$fields=@($sets[$r[2]]);if($r[3]){$lines=$r[3].Split('|');for($j=0;$j -lt $lines.Count;$j++){$fields+=(F "prompt-$j" $lines[$j] 1 (14+2*$j) 'shop_prompt')}};$cs=@();if($scene -eq 'shop-buy-navigation'){$cs+=@{style='outline_right';gridRect=@(1,1,2,2)}}else{$v=$r[4].Split(':');$rr=[int]$v[1];$cs+=@{style=$v[0];gridRect=@(1,$rr,2,($rr+1))}};if($r[5]){$rr=[int]$r[5];$cs+=@{style='filled_right';gridRect=@(5,$rr,6,($rr+1))}};$s=@{startFrame=$r[0];endFrameExclusive=$r[1];fields=$fields;menuCursors=$cs;kind=$r[8];completePage=[bool]$r[7]};if($r[7]){$s.pageOpportunityId=$r[7]};if($r[6]){$s.downArrow=@{style='filled_down';gridRect=@(18,11,19,12)}};$s})
 $m=@{mediaId='video-03';mediaSha256='9c8aee036f721a3da4d79255589f00f142148a0b2398972c924194ffbd3635b5';source='data/frames/video-03/expansion-context-f800-f1540-v1';status='first_raw_read_complete_not_frozen';frozen=$false;humanPerFrameVerification='not claimed';states=$states;firstRead='All selected score frames directly viewed full original ordered sheets and key full frames; no tested OCR output';notes=@('Money22526円 remains literal; sale-entry briefly clears numeric amount1063-1068, not inferred unchanged','Buy candidate is complete cursor/scroll operation already inside buy list; sell candidate is entry and selection operation, not a completed transaction','Obscured parent fields recorded only visible prefixes; no full hidden labels restored')}
 Save-New "$dir/manual-pass1.json" $m
 $l=Get-Content -Raw 'data/gt/candidates/ocr-all-scenes-v1/status-alt-pages/layout-v1.json'|ConvertFrom-Json;$l.scope='video03 shop menu and layered item list';Save-New "$dir/layout-v1.json" $l
 if($scene -eq 'shop-buy-navigation'){$start=818;$end=866;$score=@(820,864);$reason='Predetermined shop context; before826 selection movement through859 list scroll/860 cursor return, stable after state; no completed purchase';$event='complete buy-list cursor and scroll operation'}else{$start=1042;$end=1104;$score=@(1044,1102);$reason='Before1048 parent cursor change, full prompt replacement,1068 partial list/1069 full list,1070 cursor and1092 selection move through stable second item; no completed sale';$event='complete sale-list entry and selection operation'}
 Save-New "data/meta/all-scenes-$scene-clip-v1.json" @{schemaVersion=2;id=("9c8aee036f72-f$start-f$end-$scene");mediaId=$m.mediaId;mediaSha256=$m.mediaSha256;startFrame=$start;endFrameExclusive=$end;warmup=@($start,$score[0]);scoredRange=$score;postContext=@($score[1],$end);timeBase='1/12288';set='development';independentSession='unverified';scoreKind='text_and_state';pageOpportunityPolicy='explicit_id';gt="$dir/ocr-candidate-v1.jsonl";boundaryStatus='first_raw_read_complete_second_pending';frozen=$false;eventType=$event;selectionReason=$reason;scopeLimits=@('Not all shop input covered or independent acceptance; selected navigation commits no transaction')}
}
