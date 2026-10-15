$ErrorActionPreference='Stop'
$dir='data/gt/candidates/ocr-all-scenes-v1/exploration-npc'
function Save-New($path,$value){if(Test-Path -LiteralPath $path){throw "Existing evidence: $path"};[IO.File]::WriteAllText([IO.Path]::GetFullPath($path),($value|ConvertTo-Json -Depth 30),[Text.UTF8Encoding]::new($false))}
$layout=@{schemaVersion=2;sourceFrameSize=@(800,480);sourceGameRect=@(240,96,560,384);canonicalGameSize=@(160,144);cellPixels=@(8,8);sourceOrigin=@(240,96);sourceScaleNumerator=2;sourceScaleDenominator=1;scope='video01 outdoor NPC textbox, original two-times game raster'}
$specs=@(
 @(9838,9840,'','', 'empty_target'),
 @(9840,9850,'','', 'textbox_empty'),
 @(9850,9851,'わたし','', 'message_loading'),
 @(9851,9853,'わたしも','ポケ', 'message_loading'),
 @(9853,9854,'わたしも','ポケモンを', 'message_loading'),
 @(9854,9856,'わたしも','ポケモンを そだ', 'message_loading'),
 @(9856,9857,'わたしも','ポケモンを そだててる', 'message_loading'),
 @(9857,9877,'わたしも','ポケモンを そだててるの！', 'message_complete'),
 @(9877,9888,'','', 'textbox_empty'),
 @(9888,9890,'つよ','', 'message_loading'),
 @(9890,9892,'つよく なれば','ガ', 'message_loading'),
 @(9892,9894,'つよく なれば','ガードマ', 'message_loading'),
 @(9894,9895,'つよく なれば','ガードマンの', 'message_loading'),
 @(9895,9897,'つよく なれば','ガードマンの かわり', 'message_loading'),
 @(9897,9898,'つよく なれば','ガードマンの かわりに な', 'message_loading'),
 @(9898,9922,'つよく なれば','ガードマンの かわりに なるしね', 'message_complete'),
 @(9922,9926,'','', 'empty_target')
)
$states=@(foreach($r in $specs){$fields=@();if($r[2]){$fields+=@{id='message-top';role='dialogue';text=$r[2];col=1;row=14}};if($r[3]){$fields+=@{id='message-bottom';role='dialogue';text=$r[3];col=1;row=16}};$s=@{startFrame=$r[0];endFrameExclusive=$r[1];kind=$r[4];fields=$fields;completePage=($r[4] -eq 'message_complete');menuCursors=@()};if($r[0] -eq 9857){$s.pageOpportunityId='outdoor-npc-first';$s.downArrow=@{style='filled_down';gridRect=@(18,16,19,17)}};if($r[0] -eq 9898){$s.pageOpportunityId='outdoor-npc-second'};$s})
$manual=@{mediaId='video-01';mediaSha256='f53c237ad19a3367a4d04c4cbd890e07a7faf3038a9a91e776765e3769cbc572';source='data/frames/video-01/all-scenes-exploration-npc-pass2-v1';status='agent_second_raw_read_complete_not_frozen';frozen=$false;humanPerFrameVerification='not claimed';secondRead='All94 independently redecoded source frames9836-9929 raw-only viewed ordered sheets000-004; first context ordered sheets011-016 separately read';states=$states;notes=@('Initial predetermined6000-6660 context is introduction rather than outdoor exploration; new context9600-10200 registered from source navigation before scoring','No tested OCR or production translation exists. Source controls/console UI outside game not target','All17 states include27 empty target frames. First-page arrow remains visible; second page has no arrow. No historical or future text restored')}
Save-New "$dir/manual-pass2.json" $manual
Save-New "$dir/layout-v1.json" $layout
Save-New "$dir/pass-differences-v1.json" @{first='manual-pass1.json';second='manual-pass2.json';status='agent_double_raw_read_comparison_complete_not_approved';agreement='Both raw reads agree on literal two-page text, visible marks, integer positions and all loading/clear boundaries. Second pass supplies formal interval transcription and confirms first-page arrow/second-page absence, no unresolved score differences';humanPerFrameVerification='not claimed';frozen=$false}
Save-New 'data/meta/all-scenes-exploration-npc-clip-v1.json' @{schemaVersion=2;id='f53c237ad19a-f9836-f9930-exploration-npc';mediaId=$manual.mediaId;mediaSha256=$manual.mediaSha256;startFrame=9836;endFrameExclusive=9930;warmup=@(9836,9838);scoredRange=@(9838,9926);postContext=@(9926,9930);timeBase='1/30';set='development';independentSession='unverified';scoreKind='text_and_state';pageOpportunityPolicy='explicit_id';gt="$dir/ocr-candidate-v1.jsonl";boundaryStatus='agent_double_read_complete_candidate_not_frozen';frozen=$false;eventType='complete outdoor NPC two-page conversation';selectionReason='Source navigation frame9900 shows outdoor conversation; new context9600-10200 registered before extraction. Score begins before textbox opening9840, includes all loading/full/clear and four empty afterframes after9922';scopeLimits=@('Not all source01 exploration or introduction scenes','Other conversations and source console overlays outside this selected event remain unverified')}
& ./tools/encode-visible-gt.ps1 -Notes "$dir/manual-pass2.json" -Layout "$dir/layout-v1.json" -Destination "$dir/ocr-candidate-v1.jsonl" -Status agent_double_read_complete_candidate
