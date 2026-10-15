param([string[]]$Only=@())
$ErrorActionPreference='Stop'
. ./tools/dialogue-notes-lib.ps1
$entries=@(
 @('safari-warden-npc','data/frames/video-02/dialogue-indoor-pass2-v1','indoor-second sheets000-002, all selected frames; two pages, quotation cells, clear4522'),
 @('bill-data-npc','data/frames/video-02/dialogue-indoor-pass2-v1','indoor-second sheets002-005, all selected frames; scroll4587-4589 and three complete pages'),
 @('bill-grandfather','data/frames/video-02/dialogue-indoor-pass2-v1','indoor-second sheets006-009, all selected frames; four pages and scroll4800-4802'),
 @('league-centre-npc','data/frames/video-03/dialogue-centre-pass2-v1','centre-npc-second sheets000-004, every146 original frames1590-1735; clipped marks in both upper scroll rows'),
 @('route-pp-sign','data/frames/video-03/dialogue-route-sign-pass2-v1','sign-second sheets000-005, every172 original frames6682-6853; five pages and no-arrow single-frame full pages6696/6800'),
 @('nurse-greeting','data/frames/video-03/dialogue-centre-pass2-v1','nurse-second sheets000-005, every original frame including all menu/cursor frames1886-1920; menu closes1921 before text clears1922; raw1905 additionally reopened'),
 @('nurse-intake','data/frames/video-03/dialogue-centre-pass2-v1','nurse-second sheets005-014, every original frame including159-frame complete-page wait; raw2043 additionally reopened; preview diagnostic recorded separately'),
 @('nurse-return','data/frames/video-03/dialogue-centre-pass2-v1','nurse-second sheets014-020, every original frame; scroll2114-2116, arrow off2145, close2187'),
 @('gym-sign-continuation','data/frames/video-02/dialogue-sign-completion-pass2-v1','gym-sign-second sheets000-001, every66 frames5262-5327; old approved35-frame reference preserved, only following page scored')
)
foreach($e in $entries){
 if($Only.Count -and $e[0] -notin $Only){continue}
 $id=$e[0];$dir="data/gt/candidates/ocr-dialogue-expansion-v1/$id"
 $d=Get-Content -Raw "$dir/first-snapshots.json"|ConvertFrom-Json
 $p1=Get-Content -Raw "$dir/manual-pass1.json"|ConvertFrom-Json
 $p2=@{mediaId=$p1.mediaId;mediaSha256=$p1.mediaSha256;name=$p1.name;range=$p1.range;scoredRange=$p1.scoredRange;states=(Convert-DialogueSnapshots $d);source=$e[1];status='agent_second_raw_read_complete_not_frozen';frozen=$false;humanPerFrameVerification='not claimed';secondRead=$e[2];notes=@('Fresh source-from-start independent decode, selected raw frames directly reread by current agent; no tested OCR/translation outputs','Literal text, spaces, visible clipped bases, timing and indicators agree with first raw read','Nurse blocks share one natural interaction; overlap1922/2088 is an empty textbox, not another independent sample','No claim of human per-frame verification or independent acceptance')}
 Save-DialogueJsonNew "$dir/manual-pass2.json" $p2
 $layout=if($d.media -eq 'video-02'){@{schemaVersion=2;sourceFrameSize=@(960,544);sourceGameRect=@(300,108,660,432);canonicalGameSize=@(160,144);cellPixels=@(8,8);sourceOrigin=@(300,108);sourceScaleNumerator=9;sourceScaleDenominator=4}}else{@{schemaVersion=2;sourceFrameSize=@(960,544);sourceGameRect=@(180,0,780,544);canonicalGameSize=@(160,144);cellPixels=@(8,8);sourceOrigin=@(180,0);sourceScaleXNumerator=15;sourceScaleXDenominator=4;sourceScaleYNumerator=34;sourceScaleYDenominator=9}}
 $layout.scope='Original game raster, base rows14/16, upward scroll13/15. Nurse menu col15 rows8/10 and filled-right cursor col14 row8. No inferred or OCR-derived coordinates.'
 Save-DialogueJsonNew "$dir/layout-v1.json" $layout
 Save-DialogueJsonNew "$dir/pass-differences-v1.json" @{status='agent_double_raw_read_resolved_not_approved';first='manual-pass1.json';second='manual-pass2.json';agreement='Visible literals, spaces, fields, cursor/arrow, positions and boundaries agree in both raw reads';secondRawEvidence=$e[2];unresolvedScoreDifferences=0;humanPerFrameVerification='not claimed';frozen=$false}
 & ./tools/encode-visible-gt.ps1 -Notes "$dir/manual-pass2.json" -Layout "$dir/layout-v1.json" -Destination "$dir/ocr-candidate-v1.jsonl" -Status agent_double_read_complete_candidate
}
