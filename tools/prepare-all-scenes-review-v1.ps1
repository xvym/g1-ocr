$ErrorActionPreference='Stop'
function Save-New($p,$v){if(Test-Path -LiteralPath $p){throw "Existing evidence: $p"};[IO.File]::WriteAllText([IO.Path]::GetFullPath($p),($v|ConvertTo-Json -Depth 35),[Text.UTF8Encoding]::new($false))}
$items=@()
$defs=@(
 @('startup-nontarget','启动、非目标和黑帧',1,1,'video-01','all-scenes-startup-pass2-v1'),
 @('exploration-npc','探索 NPC 两页对话',1,1,'video-01','all-scenes-exploration-npc-pass2-v1'),
 @('battle-result','战斗倒下与经验提示',1,1,'video-02','all-scenes-battle-result-pass2-v1'),
 @('status-pages','状态双页 · 原始窗口尺寸',1,1,'video-02','all-scenes-status-pages-pass2-v1'),
 @('pc-deposit-navigation','PC 层级、道具列表与滚动',3,3,'video-02','all-scenes-pc-deposit-navigation-pass2-v1'),
 @('pc-exit','PC 关闭与文字失效',2,1,'video-02','all-scenes-pc-exit-pass2-v1'),
 @('status-alt-pages','状态双页 · 放大窗口尺寸',1,1,'video-03','all-scenes-status-alt-pages-pass2-v1'),
 @('party-list','队伍列表显示、选择与清屏',2,1,'video-03','all-scenes-party-list-pass2-v1'),
 @('shop-buy-navigation','商店买入列表、价格与滚动',2,1,'video-03','all-scenes-shop-buy-navigation-pass2-v1'),
 @('shop-sell-entry','商店卖出列表开启与金额清空',2,1,'video-03','all-scenes-shop-sell-entry-pass2-v1'),
 @('shop-rejection','商店拒绝出售的完整否定提示',2,1,'video-03','all-scenes-shop-rejection-pass2-v1'),
 @('backpack-opening','主菜单与背包开启',2,1,'video-04','all-scenes-backpack-opening-pass2-v1'),
 @('backpack-navigation','背包光标、滚动和单帧页面',2,2,'video-04','all-scenes-backpack-pass2-v1'),
 @('skills-turn','技能选择、PP 与完整一回合',2,2,'video-04','all-scenes-skills-turn-pass2-v1')
)
foreach($r in $defs){$id=$r[0];$dir="data/gt/candidates/ocr-all-scenes-v1/$id";$lv=if($id -eq 'pc-deposit-navigation'){2}else{1};$item=@{id=$id;name=$r[1];clip="data/meta/all-scenes-$id-clip-v$($r[2]).json";candidate="$dir/ocr-candidate-v$($r[3]).jsonl";layout="$dir/layout-v$lv.json";frames="data/frames/$($r[4])/$($r[5])";integrity="reports/m2-all-scenes-final-check-v1/$id.json";approval='candidate_unapproved_not_frozen';contract='data/meta/ocr-scoring-contract-all-scenes-v5.json'};$items+=$item}
$items+=@{id='shop-exit';name='商店提问、短页与退出';clip='data/meta/expansion-shop-clip-v2.json';candidate='data/gt/candidates/ocr-expansion-v1/shop-exit/ocr-candidate-v2.jsonl';layout='data/gt/candidates/ocr-expansion-v1/shop-exit/layout-v2.json';frames='data/frames/video-03/expansion-shop-exit-pass2-v1';integrity='reports/m2-all-scenes-final-check-v1/shop-exit.json';approval='candidate_unapproved_not_frozen';contract='data/meta/ocr-scoring-contract-all-scenes-v5.json'}
$items+=@{id='pc-connect';name='PC 光标与连接确认';clip='data/meta/expansion-pc-clip-v1.json';candidate='data/gt/candidates/ocr-expansion-v1/pc-connect/ocr-candidate-v1.jsonl';layout='data/gt/candidates/ocr-expansion-v1/pc-connect/layout-v1.json';frames='data/frames/video-02/expansion-pc-connect-pass2-v1';integrity='reports/m2-all-scenes-final-check-v1/pc-connect.json';approval='candidate_unapproved_not_frozen';contract='data/meta/ocr-scoring-contract-all-scenes-v5.json'}
$frozen='data/gt/frozen/ocr-trial-v2'
$items+=@{id='approved-trial';name='已批准参考：主文本框加载与滚动';clip="$frozen/clip-trial-v2.json";candidate="$frozen/ocr-candidate-v2.jsonl";layout="$frozen/layout-v2.json";frames='data/frames/video-02/trial-pass2-v1';integrity='reports/m2-all-scenes-final-check-v1/approved-trial.json';approval='already_approved_frozen_reference_only';contract="$frozen/ocr-scoring-contract-v1.json";freeze="$frozen/freeze.json"}
foreach($it in $items){foreach($key in @('clip','candidate','layout','frames','contract')){if(!(Test-Path -LiteralPath $it[$key])){throw "Missing $key for $($it.id): $($it[$key])"}};if(!(Test-Path -LiteralPath $it.integrity)){& java '-Dfile.encoding=UTF-8' -cp 'verification/target/classes;.cache/m2/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar' io.github.g1ocr.verification.VideoTool gt-check data/meta/m1-v1 $it.clip $it.candidate $it.layout $it.integrity;if($LASTEXITCODE){throw "Integrity failed: $($it.id)"}}}
$coverage=@(
 @{scene='主文本框';handledBy=@('approved-trial','exploration-npc');result='加载、短状态、完整页、真实裁切滚动和清屏已双遍；既有35帧批准仅为参考'},
 @{scene='探索/对话';handledBy=@('exploration-npc');result='户外NPC两页完整事件；2倍缩放；原6000-6660上下文已纠正为开场介绍，未选时段未验证'},
 @{scene='战斗提示';handledBy=@('battle-result','skills-turn');result='倒下、经验、己方/敌方攻击和落空，保留HUD及其失效；未宣称整场战斗通过'},
 @{scene='技能选择';handledBy=@('skills-turn');result='技能/PP/类型和单帧先变字段、完整回合、整幅栅格晃动'},
 @{scene='背包';handledBy=@('backpack-opening','backpack-navigation');result='打开/选择/滚动/单帧完整机会/重复行/双光标/孤立浊点；退出5359仍不能可靠核清，附原帧待验证'},
 @{scene='宝可梦状态菜单';handledBy=@('status-pages','status-alt-pages','party-list');result='两种显示尺寸双页/新旧混排/18空帧及队伍六行/选择/半清屏；所有可见数字/名字/PP/HP/ID保留'},
 @{scene='PC';handledBy=@('pc-connect','pc-deposit-navigation','pc-exit');result='连接提示、菜单层级、道具列表进入/遮挡/滚动/退出和PC关闭；未选宝可梦盒操作和实际转移未验证'},
 @{scene='商店';handledBy=@('shop-buy-navigation','shop-sell-entry','shop-rejection','shop-exit');result='买/卖列表、价格/金额/数量、菜单开启/遮挡、拒售否定/清屏/退出；选定操作没有成功交易，不宣称余额变化验收'},
 @{scene='非目标/黑帧/无效帧';handledBy=@('startup-nontarget');result='300帧非目标含55精确黑帧，其他候选空态全部计入；真实窗口采集失败输入不在离线素材证据中，待实测'},
 @{scene='缩放/字形/变量';handledBy=@('exploration-npc','status-pages','status-alt-pages','skills-turn','shop-buy-navigation','backpack-navigation');result='2倍、9/4、独立15/4与34/9轴；浊点/半浊点/小假名/省略号/紧凑HP:/:L/No./IDNo/；玩家/物种/招式/道具/等级/HP/PP/经验/金额/数量均按可见值登记'}
)
$gaps=@(
 @{id='backpack-exit-occluded';status='pending_not_proposed_for_freeze';mediaId='video-04';range=@(5358,5363);uncertainFrames=@(5359);source='data/frames/video-04/all-scenes-backpack-pass2-v1';layout='data/gt/candidates/ocr-all-scenes-v1/backpack-navigation/layout-v1.json';reason='5359退出重绘时玩家精灵遮住やめる末字基础字形，残余道具行也被背景/菜单改写；仅凭5358前帧和5360后帧不能恢复当帧答案';checks=@('首遍原上下文逐帧及直接放大','第二次原视频重解码及原像素光标条带','再次查看5358-5362独立原图，仍不能可靠区分被遮基础字形');impact='整个背包退出事件不冻结/不宣称通过；该原帧保留，不能计作空文字成功'},
 @{id='versions-and-sessions';status='pending';reason='只有video01 HOME红版图标有版本证据；其余版本/来源关系与独立会话无法证实，全部候选development，独立acceptance集合为空；M4继续待验证，不要求补录'},
 @{id='unselected-source';status='unverified';reason='未选原帧范围按四录像总账本补集逐段列出；候选通过不等于整个录像、版本或游戏通过'},
 @{id='real-window-and-ui';status='pending';reason='真实投屏前台/后台/遮挡/最小化/无效帧、Windows10兼容、实际UI延迟和长时稳定性尚无实测证据'},
 @{id='review-browser-render';status='pending';reason='内置浏览器曾明确拒绝file协议且禁止绕过；本包仅做静态/控件检查，实际浏览器呈现未验证，请直接在本机打开入口文件'}
)
Save-New 'data/meta/all-scenes-review-v1.json' @{schemaVersion=3;reviewId='gt-all-scenes-v1';status='prepared_candidates_not_submitted_yet';items=$items;coverage=$coverage;gaps=$gaps;mediaLedger='data/meta/m1-v1/media.jsonl';sourceIdentityReport='reports/m2-all-scenes-final-check-v1/source-identity-v1.json';totalSourceFrames=66770;totalSourceSeconds=2779.397368;approvalScope='All candidate items only with contract-v5; approved-trial is unchanged already-frozen reference with original contract-v1';outputIndependence='No tested OCR or production translation exists';humanPerFrameVerification='not claimed';sessionPartition='development only; independence unverified; acceptance empty'}
