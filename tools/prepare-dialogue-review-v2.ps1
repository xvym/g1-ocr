$ErrorActionPreference='Stop'
. ./tools/dialogue-notes-lib.ps1
$batch=Get-Content -Raw data/meta/all-scenes-review-v1.json|ConvertFrom-Json
$new=@(foreach($d in Get-ChildItem data/gt/candidates/ocr-dialogue-expansion-v1 -Directory|Sort-Object Name){
 $m=Get-Content -Raw (Join-Path $d.FullName 'manual-pass2.json')|ConvertFrom-Json
 $p='data/gt/candidates/ocr-dialogue-expansion-v1/'+$d.Name
 @{id=$d.Name;name=('新增 · '+$m.name);frames="data/frames/$($m.mediaId)/dialogue-$($d.Name)-reviewed-v1";clip="data/meta/dialogue-$($d.Name)-clip-v1.json";candidate="$p/ocr-candidate-v1.jsonl";layout="$p/layout-v1.json";integrity="reports/m2-dialogue-expansion-v1/$($d.Name)-integrity-v1.json";contract='data/meta/ocr-scoring-contract-all-scenes-v5.json';approval='candidate_unapproved_not_frozen'}
})
if($new.Count -ne 21){throw 'Expected all21 verified expansion entries, not partial submission'}
$batch.items=@($batch.items)+$new
$batch.reviewId='gt-all-scenes-v2'
$batch.sourceIdentityReport='reports/m2-dialogue-expansion-v1/source-identity-v2.json'
$batch.approvalScope='All37 unapproved candidate entries with contract-v5; original approved35-frame reference unchanged with original contract-v1. Supersedes v1 review request only; no new approval or freezing.'
$batch|Add-Member -NotePropertyName expansion -NotePropertyValue @{priorReview='gt-all-scenes-v1';priorManifestSha256='f999098f9ec4401a55fb10641ad2a1a342d24447af4ce40267e5bb60669f3246';addedDialogueBlocks=20;addedReferenceContinuation=1;description='本版保留原17项，新增20个对话事件/自然语段，并补齐同一原道馆路牌的后续页。护士三段共享同一次互动；开场、博物馆与研究所相邻语段均不冒充独立会话。新增21项共54页面机会，保留加载、滚动、清屏、菜单与完整等待。';newItemIds=@($new.id);sameSourceInteractionGroups=@(@('intro-greeting','intro-oak','intro-creatures','intro-pets','intro-research'),@('museum-question','museum-fee'),@('lab-rival-demand','lab-oak-reply'),@('nurse-greeting','nurse-intake','nurse-return'),@('approved-trial','gym-sign-continuation'));limits='Representative dialogue expansion, not exhaustive transcript of four videos; all unselected intervals explicitly unverified'}
$newIds=@($new.id)
foreach($row in $batch.coverage){
 if($row.scene -eq '主文本框'){$row.handledBy=@($row.handledBy)+$newIds;$row.result='原两例及新增21项（20语段+原路牌后续页）双遍；加载、单帧完整、箭头闪烁、滚动裁切、空框、淡出、清屏及治疗等待全原帧保留，旧35帧批准仅为参考'}
 if($row.scene -eq '探索/对话'){$row.handledBy=@($row.handledBy)+$newIds;$row.result='新增开场五语段、博物馆问答/费用、研究所劲敌/博士回复/交付、室内外NPC、中心护士及道路/道馆牌子；全部是开发候选，未选剧情/对话时段仍未验证'}
 if($row.scene -eq '缩放/字形/变量'){$row.handledBy=@($row.handledBy)+@('intro-research','safari-warden-npc','league-centre-npc','nurse-greeting','route-pp-sign','gym-sign-continuation');$row.result+='；新增U+30FB逐格点号、引号、两格省略号、滚动失去浊点/半浊点、单帧页面和选择菜单，各按当帧原像素登记'}
}
$batch|Add-Member -NotePropertyName additionalEvidence -NotePropertyValue @('data/meta/dialogue-expansion-contexts-v1.json','reports/m2-dialogue-expansion-v1/dialogue-independent-extraction-v1.json','reports/m2-dialogue-expansion-v1/dialogue-tool-recovery-v1.json','reports/m2-dialogue-expansion-v1/dialogue-preview-check-v1.json','reports/m2-dialogue-expansion-v1/dialogue-source-snapshot-v1.json')
Save-DialogueJsonNew 'data/meta/all-scenes-review-v2.json' $batch
Write-Output "Prepared unified metadata: $($batch.items.Count) entries, no GT approval or freezing"
