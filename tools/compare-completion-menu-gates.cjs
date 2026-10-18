'use strict';
// Read-only, source-time experiment. Policy execution is completed before GT evaluation.
const fs = require('fs'), path = require('path'), crypto = require('crypto'), assert = require('assert/strict');
const { createPolicy } = require('./completion-trigger-policy.cjs');
const { createMenuGate } = require('./completion-menu-gate-policy.cjs');
const [attribution, out] = process.argv.slice(2);
assert(attribution && out && !fs.existsSync(out), 'Supply the v18 attribution and a NEW output directory');
const json = p => JSON.parse(fs.readFileSync(p, 'utf8'));
const rows = p => fs.readFileSync(p, 'utf8').trim().split(/\r?\n/).filter(Boolean).map(JSON.parse);
function sha(p) { const h = crypto.createHash('sha256'), fd = fs.openSync(p, 'r'), b = Buffer.alloc(1048576); try { let n; while ((n = fs.readSync(fd, b, 0, b.length, null))) h.update(b.subarray(0, n)); } finally { fs.closeSync(fd); } return h.digest('hex'); }
const inputs = new Map();
function bind(p, expected) { p = p.replaceAll('\\', '/'); const h = sha(p); if (expected) assert.equal(h, expected, p); inputs.set(p, h); return h; }
const protocolPath = 'tools/completion-menu-gate-protocol.json', protocol = json(protocolPath);
for (const p of [protocolPath, __filename, 'tools/completion-trigger-policy.cjs', 'tools/completion-menu-gate-policy.cjs', 'spec.md', '.agent/PLANS.md']) bind(p);
const planAtStart = { path: '.agent/EXECPLAN.md', sha256: sha('.agent/EXECPLAN.md') };
const sourceReport = json(attribution + '/report.json');
const previous = json(protocol.previousExperiment + '/report.json');
for(const i of previous.inputs) bind(i.path,i.sha256);
for(const f of json(protocol.previousExperiment+'/manifest.json').files) bind(protocol.previousExperiment+'/'+f.file,f.sha256);
bind(protocol.previousExperiment+'/manifest.json');
const sourceAuditPath='reports/m3-grid-refinement-v1/ui-source-audit-v5/report.json';bind(sourceAuditPath);
const pcSourceEvidence=json(sourceAuditPath).sources.map(s=>({id:s.id,commit:s.commit,...s.files.find(f=>f.path==='engine/menus/players_pc.asm')}));
for(const s of pcSourceEvidence)bind(s.localCopy,s.sha256);
for (const i of sourceReport.inputs) bind(i.path, i.sha256);
for (const f of json(attribution + '/manifest.json').files) bind(attribution + '/' + f.file, f.sha256);
bind(attribution + '/manifest.json');
assert.equal(sourceReport.inputCounts.pages, 116);
assert.equal(sourceReport.allSemanticIntegrityPassed, false);
const run = sourceReport.run, frozenPath = sourceReport.inputs.find(x => x.sha256 === sourceReport.freezeSha256 && x.path.endsWith('/freeze.json')).path;
const frozenDir = path.dirname(frozenPath), freeze = json(frozenPath), originalScore = json(run + '/comparison/report.json');
const pageDeclarations = rows(attribution + '/pages.jsonl'), attributedFrames = rows(attribution + '/frames.jsonl');
const atByRef = new Map(attributedFrames.map(x => [x.clip + ':' + x.frame, x]));
const variants = [{id:'arrow',stableMs:null},{id:'arrow_stable_100',stableMs:100},
  {id:'separate_message_100',stableMs:100,mode:'message'},{id:'blanket_hollow_100',stableMs:100,mode:'blanket'},
  {id:'separate_pc_100',stableMs:100,mode:'pc'}];
const streams = [], decisionRows = [];
// This phase reads actual/timeline only. No frozen state or page is passed to the policy.
for (const item of freeze.items) {
  const actualPath = run + '/actual/' + item.id + '.jsonl', timelinePath = run + '/' + item.id + '-timeline.jsonl';
  bind(actualPath); bind(timelinePath);
  const actual = rows(actualPath), timeline = rows(timelinePath);
  assert.equal(actual.length, timeline.length);
  const policies = variants.map(v => v.mode?createMenuGate(v.mode):createPolicy(v.stableMs)), decisions = [];
  for (let i = 0; i < actual.length; i++) {
    const a = actual[i], t = timeline[i];
    assert.equal(a.sourceFrameIndex, t.sourceFrameIndex); assert.equal(a.sourcePts, t.sourcePts); assert.equal(a.timeBase, t.timeBase);
    if (i) assert.equal(t.sourceFrameIndex, timeline[i - 1].sourceFrameIndex + 1);
    const observation = { pts: a.sourcePts, timeBase: a.timeBase, regions: a.rawRegions,
      kind: a.visibleState.kind, menuCursors: a.visibleState.cursor.menuCursors || [], detachedMarks: a.visibleState.detachedMarks || [], boxes:a.diagnosticBoxes };
    const decisionsAtFrame = policies.map(p => p.observe(observation));
    decisions.push(decisionsAtFrame);
    decisionRows.push({ clip: item.id, frame: a.sourceFrameIndex, pts: a.sourcePts, timeBase: a.timeBase, scored: t.scored,
      decisions: Object.fromEntries(variants.map((v, k) => [v.id, decisionsAtFrame[k]])) });
  }
  streams.push({ item, actual, timeline, decisions });
}
const ms = (pts, tb) => { const [n, d] = tb.split('/').map(Number); return pts * n * 1000 / d; };
const deltaMs = (pts, start, tb) => ms(pts - start, tb);
const edits = score => score.substitutions + score.deletions + score.insertions;
const events = [], frameRows = [], grouped = new Map(), statesByRef = new Map(), metaByClip = new Map();
for (const stream of streams) {
  const { item, actual, timeline, decisions } = stream;
  const resolve = p => p.file ? path.join(frozenDir, p.file) : p.approvedSource;
  const gt = rows(resolve(item.payloads.find(p => p.kind === 'gt'))), clip = json(resolve(item.payloads.find(p => p.kind === 'clip')));
  metaByClip.set(item.id, clip);
  const sm = new Map(); for (const s of gt) for (let f = s.startFrame; f < s.endFrameExclusive; f++) { assert(!sm.has(f)); sm.set(f, s); }
  for (let i = 0; i < actual.length; i++) {
    const a = actual[i], t = timeline[i]; if (!t.scored) continue;
    const key = item.id + ':' + a.sourceFrameIndex, state = sm.get(a.sourceFrameIndex), af = atByRef.get(key);
    assert(state && af); assert.equal(state.kind, af.expectedKind); assert.equal(state.completePage === true, af.pageId !== null);
    const row = { clip: item.id, media: clip.mediaId, frame: a.sourceFrameIndex, pts: a.sourcePts, timeBase: a.timeBase,
      durationPts: t.durationPts, expectedKind: state.kind, completePage: state.completePage === true, pageId: af.pageId,
      originalFrame: af.originalFrame, expectedText: state.regions.map(r => ({ role: r.role, gridRect: r.gridRect, text: r.text })),
      actualText: a.rawRegions.map(r => ({ role: r.role, gridRect: r.gridRect, text: r.text })),
      rawTextCorrect: edits(af.raw.score) === 0, finalTextCorrect: edits(af.final.score) === 0,
      rawStrictExact: af.raw.score.exact, finalStrictExact: af.final.score.exact,
      decisions: Object.fromEntries(variants.map((v, k) => [v.id, decisions[i][k]])) };
    statesByRef.set(key, state); frameRows.push(row);
    const source = clip.mediaId + ':' + row.frame;
    if (!grouped.has(source)) grouped.set(source, []); grouped.get(source).push(row);
    variants.forEach((v, k) => {
      if (!decisions[i][k].fire) return;
      const classification = state.completePage ? 'complete_page' : ['loading', 'message_loading'].includes(state.kind) ? 'residual_false_trigger' : 'other_noncomplete_trigger';
      events.push({ variant: v.id, stableMs: v.stableMs, ...row, decisions: undefined, decision: decisions[i][k], classification,
        processingMsAtTrigger: a.processingNanos / 1000000 });
    });
  }
}
assert.equal(grouped.size, 3361); assert.equal(frameRows.length, 3368); assert.equal(decisionRows.length, 3577);
const unique = [...grouped.values()].map(refs => {
  for (const r of refs) { assert.equal(r.pageId, refs[0].pageId); assert.equal(r.expectedKind, refs[0].expectedKind); }
  return { ...refs[0], references: refs.map(r => ({ clip: r.clip, frame: r.frame })),
    rawTextCorrect: refs.every(r => r.rawTextCorrect), finalTextCorrect: refs.every(r => r.finalTextCorrect),
    decisions: Object.fromEntries(variants.map(v => [v.id, { ready: refs.every(r => r.decisions[v.id].ready),
      fire: refs.some(r => r.decisions[v.id].fire), reason: refs.map(r => r.decisions[v.id].reason).join('|') }])) };
});
function quantiles(xs) { if (!xs.length) return { n: 0, p50: null, p95: null, p99: null, max: null }; xs = xs.slice().sort((a,b) => a-b); const q = p => xs[Math.ceil(xs.length * p) - 1]; return { n: xs.length, p50: q(.5), p95: q(.95), p99: q(.99), max: xs.at(-1) }; }
function uniqueEvents(v) { const map = new Map(); for (const e of events.filter(e => e.variant === v.id)) { const k = e.media + ':' + e.frame + ':' + e.decision.signature; if (!map.has(k)) map.set(k, e); } return [...map.values()]; }
const pages = pageDeclarations.map(p => {
  const frames = unique.filter(f => f.pageId === p.id).sort((a,b) => a.frame-b.frame);
  assert.equal(frames.length, p.frames); const first = frames[0];
  const durationMs = frames.reduce((n, f) => n + ms(f.durationPts, f.timeBase), 0), end = frames.at(-1);
  const vresults = {};
  for (const v of variants) {
    const hit = frames.find(f => f.decisions[v.id].ready), raw = frames.find(f => f.decisions[v.id].ready && f.rawTextCorrect), final = frames.find(f => f.decisions[v.id].ready && f.finalTextCorrect);
    const detail = f => f ? { frame: f.frame, pts: f.pts, delayMs: deltaMs(f.pts, first.pts, first.timeBase),
      reason: f.decisions[v.id].reason, clips: f.references.map(r => r.clip) } : null;
    vresults[v.id] = { ready: detail(hit), rawTextCorrect: detail(raw), finalTextCorrect: detail(final),
      readyWithin200: !!hit && deltaMs(hit.pts, first.pts, first.timeBase) <= 200,
      rawWithin200: !!raw && deltaMs(raw.pts, first.pts, first.timeBase) <= 200,
      finalWithin200: !!final && deltaMs(final.pts, first.pts, first.timeBase) <= 200 };
  }
  return { id: p.id, number: p.number, media: p.media, clips: p.clips, sceneNames: p.sceneNames,
    completeFrameRanges: p.completeFrameRanges, firstCompletePts: first.pts, timeBase: first.timeBase,
    durationMs, spanMs: deltaMs(end.pts + end.durationPts, first.pts, first.timeBase), short: durationMs < 200 - 1e-7,
    completeFrames: frames.length, existingRawStrictExact: p.raw.exact, existingFinalStrictExact: p.final.exact,
    originalFrame: first.originalFrame, variants: vresults };
});
assert.equal(pages.length, 116);
const transitionFamily = kind => /loading|^loading$/.test(kind) ? 'loading' : /scroll/.test(kind) ? 'scrolling' : /clear|empty|no_textbox|non_target|black_frame/.test(kind) ? 'clearing_empty_nontarget' : /redraw|cursor|opening|closing|transition/.test(kind) ? 'other_transition' : 'other';
const transitions = {};
for (const f of unique.filter(f => !f.completePage)) {
  const k = transitionFamily(f.expectedKind);
  if (!transitions[k]) transitions[k] = { frames: 0, kinds: {}, variants: Object.fromEntries(variants.map(v => [v.id, { readyFrames: 0, newTriggers: 0 }])) };
  const t = transitions[k]; t.frames++; t.kinds[f.expectedKind] = (t.kinds[f.expectedKind] || 0) + 1;
  for (const v of variants) { if (f.decisions[v.id].ready) t.variants[v.id].readyFrames++; if (f.decisions[v.id].fire) t.variants[v.id].newTriggers++; }
}
const summary = variants.map(v => {
  const es = uniqueEvents(v), get = k => pages.map(p => p.variants[v.id][k]).filter(Boolean), short = pages.filter(p => p.short);
  const ready = get('ready'), raw = get('rawTextCorrect'), final = get('finalTextCorrect');
  return { ...v, newTriggers: es.length, residualFalseTriggers: es.filter(e => e.classification === 'residual_false_trigger').length,
    residualTextCorrect: es.filter(e => e.classification === 'residual_false_trigger' && e.rawTextCorrect).length,
    otherNoncompleteTriggers: es.filter(e => e.classification === 'other_noncomplete_trigger').length,
    readyPages: ready.length, readyMisses: pages.length - ready.length, rawTextCorrectPages: raw.length, finalTextCorrectPages: final.length,
    readyWithin200: pages.filter(p => p.variants[v.id].readyWithin200).length,
    rawWithin200: pages.filter(p => p.variants[v.id].rawWithin200).length, finalWithin200: pages.filter(p => p.variants[v.id].finalWithin200).length,
    readyOver200: ready.filter(x => x.delayMs > 200 + 1e-7).length,
    shortPages: short.length, shortReadyMisses: short.filter(p => !p.variants[v.id].ready).length,
    shortRawTextMisses: short.filter(p => !p.variants[v.id].rawTextCorrect).length,
    shortFinalTextMisses: short.filter(p => !p.variants[v.id].finalTextCorrect).length,
    readinessDelayMs: quantiles(ready.map(x => x.delayMs)), rawTextDelayMs: quantiles(raw.map(x => x.delayMs)),
    finalTextDelayMs: quantiles(final.map(x => x.delayMs)),
    residualAffectedClips: [...new Set(es.filter(e => e.classification === 'residual_false_trigger').map(e => e.clip))],
    processingMsAtTrigger: quantiles(es.map(e => e.processingMsAtTrigger)) };
});
const primaryId = protocol.primaryVariant;
const affected31 = sourceReport.summary.final.familyImpacts.find(f => f.id === 'complete_loading').pageIds;
const comparison = { baseline: 'arrow_stable_100', arrowReference:'arrow', primary: primaryId,
  rescuedReadyPages: pages.filter(p => !p.variants.arrow.ready && p.variants[primaryId].ready).map(p => p.id),
  rescuedFinalTextPages: pages.filter(p => !p.variants.arrow.finalTextCorrect && p.variants[primaryId].finalTextCorrect).map(p => p.id),
  regressedReadyPages: pages.filter(p => p.variants.arrow.ready && !p.variants[primaryId].ready).map(p => p.id),
  completeLoading31: { pages: affected31.length,
    baselineReady: pages.filter(p => affected31.includes(p.id) && p.variants.arrow.ready).length,
    primaryReady: pages.filter(p => affected31.includes(p.id) && p.variants[primaryId].ready).length,
    primaryFinalTextWithin200: pages.filter(p => affected31.includes(p.id) && p.variants[primaryId].finalWithin200).length } };
const baseEventKeys = new Set(uniqueEvents(variants.find(v=>v.id==='arrow_stable_100')).map(e => e.media + ':' + e.frame + ':' + e.classification));
comparison.additionalResidualTriggers = uniqueEvents(variants.find(v=>v.id===primaryId)).filter(e=>e.classification==='residual_false_trigger'&&!baseEventKeys.has(e.media+':'+e.frame+':'+e.classification)).map(e=>({media:e.media,frame:e.frame,clip:e.clip,kind:e.expectedKind}));
comparison.additionalOtherNoncompleteTriggers = uniqueEvents(variants.find(v=>v.id===primaryId)).filter(e=>e.classification==='other_noncomplete_trigger'&&!baseEventKeys.has(e.media+':'+e.frame+':'+e.classification)).map(e=>({media:e.media,frame:e.frame,clip:e.clip,kind:e.expectedKind}));
comparison.shortReadyMisses = pages.filter(p=>p.short&&!p.variants[primaryId].ready).map(p=>({id:p.id,clips:p.clips,durationMs:p.durationMs}));
comparison.previousRescuedPages=previous.comparison.rescuedReadyPages;
comparison.retainedPreviousRescues=pages.filter(p=>comparison.previousRescuedPages.includes(p.id)&&p.variants[primaryId].finalWithin200).map(p=>p.id);
comparison.lostPreviousRescues=comparison.previousRescuedPages.filter(id=>!comparison.retainedPreviousRescues.includes(id));
comparison.newReadyMisses=pages.filter(p=>p.variants.arrow_stable_100.ready&&!p.variants[primaryId].ready).map(p=>p.id);
comparison.newFinalTextMisses=pages.filter(p=>p.variants.arrow_stable_100.finalTextCorrect&&!p.variants[primaryId].finalTextCorrect).map(p=>p.id);
comparison.removedResidualTriggers=uniqueEvents(variants.find(v=>v.id==='arrow_stable_100')).filter(e=>e.classification==='residual_false_trigger'&&!uniqueEvents(variants.find(v=>v.id===primaryId)).some(n=>n.media===e.media&&n.frame===e.frame&&n.classification===e.classification)).map(e=>({media:e.media,frame:e.frame,clip:e.clip}));
comparison.perVariantRetainedRescues=Object.fromEntries(variants.map(v=>[v.id,comparison.previousRescuedPages.filter(id=>pages.find(p=>p.id===id).variants[v.id].finalWithin200)]));
for(const id of ['arrow','arrow_stable_100']){const s=summary.find(s=>s.id===id);const old=previous.summary.find(s=>s.id===id);assert.deepEqual(s,old,'Baseline changed: '+id);}
const inputCounts = { clips: streams.length, retainedReferences: decisionRows.length, scoredReferences: frameRows.length,
  uniqueScoredFrames: unique.length, completeFrames: unique.filter(f => f.completePage).length,
  noncompleteFrames: unique.filter(f => !f.completePage).length, pages: pages.length, shortPages: pages.filter(p => p.short).length };
const report = { schemaVersion: 1, status: 'saved_v18_causal_trigger_comparison_not_OCR_or_UI_acceptance', command: process.argv.slice(1).join(' '),
  protocol, protocolSha256: sha(protocolPath), previousExperiment:protocol.previousExperiment, previousReportSha256:sha(protocol.previousExperiment+'/report.json'),pcSourceEvidence,
  attribution, run, frozen: frozenDir, freezeSha256: sha(frozenPath), planAtStart,
  inputCounts, summary, comparison, transitions, existingAggregateUnchanged: originalScore.aggregate,
  sources: sourceReport.sources, coverageAndGaps: sourceReport.frozenCoverageAndGaps, limitations: [
    'All four videos remain the only media; only selected frozen clips are covered. Unselected time and missing dimensions remain unverified.',
    'Uses saved raw v18 text and existing status hints, not pixel replay, new OCR, translation, wall-clock scheduling or actual UI rendering.',
    'Arrow and original100ms baselines exactly reproduce the previous experiment. New gates independently confirm message fields; menu cursors alone cannot certify a new message.',
    'The PC suspension gate uses current pc_prompt classification and current parent/child cursors. Merchant hollow-only messages are a counterexample to a blanket rule. Unknown/uncovered PC operations remain unverified.',
    'Excluding sourceRect drift and arrow blinking from a temporal signature is diagnostic design, not normalization of existing strict scores.',
    'Correct-text page counts keep existing strict role/grid-start/order matching, but do not certify source rectangles, all frames, symbols or completion state.',
    'A page can be detected correctly and still have had earlier residual false triggers; both metrics must be considered together.',
    'Delay is a source-time lower bound from frame observations; processing, capture delivery, queue, translation and UI costs are excluded. Misses are not assigned zero delay.',
    'No timer event is invented between frames or at clip end, and policies reset at each existing replay boundary with its real warmup.',
    'GT semantic qualification and independent acceptance remain unverified. This parameter sweep uses development data and is not independent validation.'
  ], productAcceptance: false, uiLatencyAcceptance: false, allSemanticIntegrityPassed: false,
  inputs: [...inputs].map(([path, sha256]) => ({ path, sha256 })) };
for (const [p,h] of inputs) assert.equal(sha(p),h,'Input changed: '+p);
fs.mkdirSync(out, { recursive: true });
const write = (file, data) => fs.writeFileSync(out + '/' + file, data, { flag: 'wx' });
write('report.json', JSON.stringify(report, null, 2) + '\n');
write('pages.jsonl', pages.map(JSON.stringify).join('\n') + '\n');
write('frames.jsonl', frameRows.map(JSON.stringify).join('\n') + '\n');
write('decisions.jsonl', decisionRows.map(JSON.stringify).join('\n') + '\n');
write('events.jsonl', events.map(JSON.stringify).join('\n') + '\n');
const esc = x => String(x).replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;').replaceAll('"','&quot;');
const fmt = x => x == null ? '漏检' : Number(x).toFixed(1);
const href = p => esc(path.relative(out,p).replaceAll('\\','/'));
const value = x => x ? `${x.frame} / ${fmt(x.delayMs)} ms` : '漏检';
const variantLabels={arrow:'现有v18判定',arrow_stable_100:'原100ms规则',separate_message_100:'消息独立确认',blanket_hollow_100:'一律拦空心菜单（反例）',separate_pc_100:'消息独立确认＋PC父菜单门控'};
const variantRows = summary.map(s => `<tr><td>${variantLabels[s.id]}${s.id===primaryId?'（主对比）':''}</td><td>${s.residualFalseTriggers}</td><td>${s.otherNoncompleteTriggers}</td><td>${s.readyPages}/116</td><td>${s.finalTextCorrectPages}/116</td><td>${s.shortReadyMisses}/${s.shortPages}</td><td>${s.shortFinalTextMisses}/${s.shortPages}</td><td>${s.finalWithin200}/116</td><td>${fmt(s.finalTextDelayMs.p50)} / ${fmt(s.finalTextDelayMs.p95)} / ${fmt(s.finalTextDelayMs.p99)} / ${fmt(s.finalTextDelayMs.max)}</td></tr>`).join('');
const pageRows = pages.map(p => `<tr class="page" data-search="${esc(JSON.stringify([p.id,p.clips,p.sceneNames]))}" data-miss="${!p.variants[primaryId].finalTextCorrect}" data-short="${p.short}"><td>${p.number}</td><td>${esc(p.id)}<br>${esc(p.sceneNames.join(' / '))}<br><a href="${href(p.originalFrame)}">首次完整原帧</a></td><td>${fmt(p.durationMs)} ms${p.short?' · 短页':''}</td><td>${value(p.variants.arrow.ready)}</td><td>${value(p.variants[primaryId].ready)}</td><td>${value(p.variants.arrow.finalTextCorrect)}</td><td>${value(p.variants[primaryId].finalTextCorrect)}</td><td><details><summary>全部窗口与页面证据</summary><pre>${esc(JSON.stringify(p,null,2))}</pre></details></td></tr>`).join('');
const falseRows = uniqueEvents(variants.find(v=>v.id===primaryId)).filter(e=>e.classification!=='complete_page').map(e => `<tr><td>${esc(e.media)}:${e.frame}</td><td>${esc(e.clip)}</td><td>${esc(e.expectedKind)}</td><td>${e.classification==='residual_false_trigger'?'残句误触发':'其他过渡误触发'}</td><td>${esc(e.actualText.map(r=>r.text).join('\n'))}</td><td><a href="${href(e.originalFrame)}">原帧</a><details><summary>完整证据</summary><pre>${esc(JSON.stringify(e,null,2))}</pre></details></td></tr>`).join('');
const transitionNames = {loading:'加载',scrolling:'滚动',clearing_empty_nontarget:'清屏、空画面与非目标',other_transition:'菜单绘制、光标等转换',other:'其他非完整状态'};
const transitionRows=Object.entries(transitions).map(([k,t])=>`<tr><td>${transitionNames[k]}</td><td>${t.frames}</td><td>${t.variants.arrow.newTriggers}</td><td>${t.variants[primaryId].newTriggers}</td><td><details><summary>全部状态与窗口</summary><pre>${esc(JSON.stringify(t,null,2))}</pre></details></td></tr>`).join('');
const baseSummary=summary.find(s=>s.id==='arrow_stable_100'),primarySummary=summary.find(s=>s.id===primaryId);
const findings=`<p><b>主要结果：</b>主门控规则保留此前20页收益中的${comparison.retainedPreviousRescues.length}页（逐页核对正确文字与200ms源时间预算）；残句新触发${baseSummary.residualFalseTriggers}→${primarySummary.residualFalseTriggers}，其他非完整转换触发${baseSummary.otherNoncompleteTriggers}→${primarySummary.otherNoncompleteTriggers}，可触发${baseSummary.readyPages}→${primarySummary.readyPages}页，正确文字命中${baseSummary.finalTextCorrectPages}→${primarySummary.finalTextCorrectPages}页。新增可触发页漏检${comparison.newReadyMisses.length}，新增正确文字漏检${comparison.newFinalTextMisses.length}。</p><p>门控先单独确认当前消息，菜单实心光标不再直接确认新消息。当前PC道具父菜单处于空心选择且没有活动子选择时，稳定文字仍不发布；主消息箭头可解除等待。商店空心菜单的合法感谢语用于检验“一律拦空心菜单”的反例。全部原失败与剩余短页保留；此为开发诊断，不是新OCR或UI验收。</p>`;
write('index.html', `<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>v18 · 菜单与消息分开门控</title><style>body{font:15px/1.55 system-ui;background:#f5f7fa;color:#17212b;margin:24px}main{max-width:1700px;margin:auto}table{border-collapse:collapse;background:white;width:100%;margin:18px 0}td,th{border:1px solid #d5deea;padding:9px;vertical-align:top;text-align:left}th{background:#e7eef8}pre{white-space:pre-wrap;word-break:break-all;max-width:640px;font-size:12px}.note{background:#fff2ce;border-left:4px solid #bd8100;padding:15px}input,select{font:inherit;padding:7px}summary{cursor:pointer}a{color:#075ca9}[hidden]{display:none!important}</style><main><h1>v18 · 菜单与消息分开门控</h1><p>全部116页 · ${inputCounts.uniqueScoredFrames}评分原帧 · ${inputCounts.noncompleteFrames}加载/滚动/清屏等非完整帧 · 消息稳定窗口100 ms</p><p class="note">此处两条旧基线精确复现上一对比；新规则把消息确认与菜单选择分开，保留当前及真实过去观测。“输出”指离线候选触发；延迟仅按原帧时间计算，未包含识别、排队、翻译或实际界面呈现。既有严格成绩仍7/116，GT与生产算法未改；本报告不授予验收通过。</p>${findings}<p>残句误触发：在冻结加载态发布新候选。短页：完整帧总时长小于200 ms。“可触发页”与“文字正确页”分开，后者仍未检查全部几何和状态。延迟只统计命中，漏检单列；一次正确命中不能抵消此前残句。</p><p><a href="report.json">完整汇总与输入身份</a> · <a href="pages.jsonl">全部页面</a> · <a href="frames.jsonl">全部评分引用</a> · <a href="events.jsonl">全部触发</a> · <a href="decisions.jsonl">含热身/后文的逐帧判定</a></p><h2>全部规则对比</h2><table><thead><tr><th>规则</th><th>残句误触发</th><th>其他过渡误触发</th><th>可触发页</th><th>最终文字正确页</th><th>短页未触发</th><th>短页文字未命中</th><th>文字正确且≤200 ms</th><th>文字触发延迟 P50/P95/P99/最大 ms</th></tr></thead><tbody>${variantRows}</tbody></table><h2>加载、滚动和清屏覆盖</h2><table><thead><tr><th>范围</th><th>评分原帧</th><th>现有新增触发</th><th>主门控新增触发</th><th>证据</th></tr></thead><tbody>${transitionRows}</tbody></table><h2>全部116页</h2><p><input id="search" aria-label="搜索页面" placeholder="录像、场景或页号"> <select id="filter" aria-label="筛选"><option value="">全部</option><option value="short">短页</option><option value="miss">主门控文字未命中</option></select> <span id="count">116 / 116</span></p><table><thead><tr><th>序号</th><th>页面</th><th>完整持续时间</th><th>现有首次可触发</th><th>主门控首次可触发</th><th>现有文字正确触发</th><th>主门控文字正确触发</th><th>证据</th></tr></thead><tbody>${pageRows}</tbody></table><h2>主门控全部剩余残句与其他过渡误触发</h2><table><thead><tr><th>原帧</th><th>事件</th><th>冻结状态</th><th>分类</th><th>候选文字</th><th>证据</th></tr></thead><tbody>${falseRows}</tbody></table><h2>解释边界</h2><ul>${report.limitations.map(s=>'<li>'+esc(s)+'</li>').join('')}</ul></main><script>const q=document.getElementById('search'),f=document.getElementById('filter'),rs=[...document.querySelectorAll('tr.page')];function update(){let n=0;for(const r of rs){r.hidden=!(r.dataset.search.toLowerCase().includes(q.value.toLowerCase())&&(!f.value||r.dataset[f.value]==='true'));if(!r.hidden)n++;}document.getElementById('count').textContent=n+' / 116';}q.addEventListener('input',update);f.addEventListener('change',update);</script></html>\n`);
write('manifest.json', JSON.stringify({ files: fs.readdirSync(out).sort().map(file=>({ file, sha256:sha(out+'/'+file), bytes:fs.statSync(out+'/'+file).size })), inputsVerifiedBeforeAndAfter: true },null,2)+'\n');
console.log(JSON.stringify({ out, reportSha256: sha(out+'/report.json'), inputCounts, summary, comparison }));
