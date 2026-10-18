'use strict';
// Offline diagnosis of an existing run. Never invokes or modifies OCR, GT or the scorer.
const fs = require('fs'), path = require('path'), crypto = require('crypto'), assert = require('assert/strict');
const [frozen, run, out] = process.argv.slice(2);
assert(frozen && run && out && !fs.existsSync(out), 'Use frozen/run paths and a new output directory');
const json = p => JSON.parse(fs.readFileSync(p, 'utf8'));
const rows = p => fs.readFileSync(p, 'utf8').trim().split(/\r?\n/).filter(Boolean).map(JSON.parse);
const inputs = new Map();
function sha(p) { const h = crypto.createHash('sha256'), fd = fs.openSync(p, 'r'), b = Buffer.alloc(1024 * 1024); try { let n; while ((n = fs.readSync(fd, b, 0, b.length, null))) h.update(b.subarray(0, n)); } finally { fs.closeSync(fd); } return h.digest('hex'); }
function bind(p, expected) { const h = sha(p); if (expected) assert.equal(h, expected, 'Input SHA mismatch: ' + p); inputs.set(p, h); return { path: p, sha256: h }; }
function read(p, expected) { bind(p, expected); return json(p); }
function readRows(p, expected) { bind(p, expected); return rows(p); }
const eq = (a, b) => { try { assert.deepEqual(a, b); return true; } catch { return false; } };
const fields = (v, names) => Object.fromEntries(names.filter(k => v && v[k] != null).map(k => [k, v[k]]));
// Match BigDecimal.stripTrailingZeros().toString() used by the frozen Java scorer.
function javaDecimal(n) { const text = String(n); if (!Number.isInteger(n) || !n || n % 10) return text; const sign = n < 0 ? '-' : '', digits = String(Math.abs(n)), significant = digits.replace(/0+$/, ''); return sign + significant[0] + (significant.length > 1 ? '.' + significant.slice(1) : '') + 'E+' + (digits.length - 1); }
const regionKey = r => [r.role, javaDecimal(r.gridRect[1]), javaDecimal(r.gridRect[0]), r.readingOrder].join(':');
function mapRegions(rs) { const m = new Map(); for (const r of rs) { const k = regionKey(r); assert(!m.has(k), 'Duplicate strict region key'); m.set(k, r); } return m; }
function edits(a, b) {
  a = Array.from(a); b = Array.from(b); const d = Array.from({ length: a.length + 1 }, () => new Int32Array(b.length + 1));
  for (let i = 0; i <= a.length; i++) d[i][0] = i; for (let j = 0; j <= b.length; j++) d[0][j] = j;
  for (let i = 1; i <= a.length; i++) for (let j = 1; j <= b.length; j++) d[i][j] = Math.min(d[i - 1][j - 1] + (a[i - 1] !== b[j - 1]), d[i - 1][j] + 1, d[i][j - 1] + 1);
  const e = { substitutions: 0, deletions: 0, insertions: 0 }; let i = a.length, j = b.length;
  while (i || j) { if (i && j && d[i][j] === d[i - 1][j - 1] + (a[i - 1] !== b[j - 1])) { if (a[i - 1] !== b[j - 1]) e.substitutions++; i--; j--; } else if (i && d[i][j] === d[i - 1][j] + 1) { e.deletions++; i--; } else { e.insertions++; j--; } }
  return e;
}
const errorCount = e => e.substitutions + e.deletions + e.insertions;
function symbols(v) { return (v == null ? [] : Array.isArray(v) ? v : [v]).map(x => { const s = fields(x, ['kind', 'style', 'gridRect', 'sourceRect']); if (s.style === 'outlined_right') s.style = 'outline_right'; return s; }); }
function cursor(v) { const c = v?.cursor || {}, r = { menuCursors: symbols(c.menuCursors), downArrowVisible: c.downArrowVisible === true }; if (r.downArrowVisible) r.downArrow = fields(c.downArrow || c, ['style', 'gridRect', 'sourceRect']); r.additionalDownArrows = symbols(c.additionalDownArrows); return r; }
function withoutSource(v) { if (Array.isArray(v)) return v.map(withoutSource); if (v && typeof v === 'object') return Object.fromEntries(Object.entries(v).filter(([k]) => k !== 'sourceRect').map(([k, x]) => [k, withoutSource(x)])); return v; }
function visible(s) { return { kind: s.kind, regions: s.regions.map(r => fields(r, ['role', 'readingOrder', 'text', 'gridRect', 'sourceRect', 'markCells', 'logicalRect', 'renderUnits'])), cursor: cursor(s), detachedMarks: symbols(s.detachedMarks) }; }
const families = {
  source_rect: '源像素矩形/投影不一致', logical_geometry: '字格占位或逻辑几何不一致', marks: '区域附标不一致', render_units: '紧凑字段渲染单位不一致',
  field_keys: '字段/阅读顺序/区域键不一致', question_encoding: '问号全半角编码差异', kana_alias: '平片同形假名差异', glyph_text: '其他文字差异',
  symbol_structure: '光标/箭头/孤立标记结构差异', complete_loading: '完整消息判为加载中', other_state: '其他显示状态差异', missing_output: '缺输出'
};
const dimensions = ['text', 'fields', 'geometry', 'symbols', 'state', 'missing'];
const dimensionNames = { text: '文字', fields: '字段', geometry: '区域几何', symbols: '符号', state: '状态', missing: '缺输出' };
function diagnose(s, rs, v, missing) {
  const score = { characters: 0, substitutions: 0, deletions: 0, insertions: 0, detachedExpected: 0, detachedActual: 0, exact: true, regionGeometry: true, symbols: true, state: true, differences: [] };
  const causes = new Set(), details = [], actual = mapRegions(rs), expected = mapRegions(s.regions);
  function fail(reason) { score.exact = false; score.differences.push(reason); }
  for (const [key, e] of expected) {
    const a = actual.get(key); actual.delete(key); score.characters += Array.from(e.text).length;
    const ed = edits(e.text, a?.text || ''); for (const k of ['substitutions', 'deletions', 'insertions']) score[k] += ed[k];
    if (errorCount(ed)) { fail('text:' + key); const family = !a ? 'field_keys' : e.text.replaceAll('？', '?') === a.text.replaceAll('？', '?') ? 'question_encoding' : e.text.replaceAll('ヘ', 'へ').replaceAll('リ', 'り') === a.text.replaceAll('ヘ', 'へ').replaceAll('リ', 'り') ? 'kana_alias' : 'glyph_text'; causes.add(family); details.push({ family, key, expected: e.text, actual: a?.text || '', edits: ed }); }
    if (!a) { score.regionGeometry = false; fail('geometry_or_missing_field:' + key); causes.add('field_keys'); details.push({ family: 'field_keys', key, expected: 'present', actual: 'missing' }); continue; }
    const names = ['gridRect', 'sourceRect', 'markCells', 'logicalRect', 'renderUnits'];
    if (!eq(fields(e, names), fields(a, names))) {
      score.regionGeometry = false; fail('geometry_or_missing_field:' + key);
      for (const k of names) if (!eq(fields(e, [k]), fields(a, [k]))) { const family = k === 'sourceRect' ? 'source_rect' : k === 'markCells' ? 'marks' : k === 'renderUnits' ? 'render_units' : 'logical_geometry'; causes.add(family); details.push({ family, key, property: k, expected: e[k] ?? null, actual: a[k] ?? null }); }
    }
  }
  for (const [key, a] of actual) { score.insertions += Array.from(a.text).length; score.regionGeometry = false; fail('extra_region:' + key); causes.add('field_keys'); details.push({ family: 'field_keys', key, expected: 'absent', actual: a.text }); }
  const es = { cursor: cursor(s), detachedMarks: symbols(s.detachedMarks) }, as = { cursor: cursor(v), detachedMarks: symbols(v?.detachedMarks) };
  score.detachedExpected = es.detachedMarks.length; score.detachedActual = as.detachedMarks.length; score.symbols = eq(es, as);
  if (!score.symbols) { fail('cursor_or_detached_mark'); const family = eq(withoutSource(es), withoutSource(as)) ? 'source_rect' : 'symbol_structure'; causes.add(family); details.push({ family, property: 'symbols', expected: es, actual: as }); }
  score.state = s.kind === v?.kind;
  if (!score.state) { fail('visible_state_kind'); const family = s.kind === 'message_complete' && v?.kind === 'message_loading' ? 'complete_loading' : 'other_state'; causes.add(family); details.push({ family, property: 'kind', expected: s.kind, actual: v?.kind ?? null }); }
  if (missing) { fail('missing_output'); causes.add('missing_output'); }
  const dims = []; if (errorCount(score)) dims.push('text'); if (causes.has('field_keys')) dims.push('fields'); if (!score.regionGeometry) dims.push('geometry'); if (!score.symbols) dims.push('symbols'); if (!score.state) dims.push('state'); if (missing) dims.push('missing');
  assert.equal(score.exact, causes.size === 0); return { score, dimensions: dims, families: [...causes].sort(), details };
}
class Union { constructor() { this.parents = new Map(); } root(k) { if (!this.parents.has(k)) this.parents.set(k, k); let p = this.parents.get(k); if (p !== k) this.parents.set(k, p = this.root(p)); return p; } join(a, b) { this.parents.set(this.root(a), this.root(b)); } }
const freeze = read(frozen + '/freeze.json'), scoreReport = read(run + '/comparison/report.json'); assert.equal(sha(frozen + '/freeze.json'), scoreReport.freezeSha256);
assert.equal(freeze.allSemanticIntegrityPassed, false); assert.equal(scoreReport.productPassed, false);
const previousCheck = read('reports/m3-grid-refinement-v1/money-clear-regression-v1.json'); assert.equal(previousCheck.comparisonSha256, sha(run + '/comparison/report.json'));
const buildPath = 'reports/m3-grid-refinement-v1/build-evidence-v18', build = read(buildPath + '/class-identity.json'), snapshot = read(buildPath + '/source-snapshot.json', build.sourceSnapshotSha256);
for (const c of build.classes) bind(c.path, c.sha256);
for (const s of snapshot.files.filter(x => /^(app|verification)\//.test(x.path))) bind(s.path, s.sha256);
const planContextAtStart = { path: '.agent/EXECPLAN.md', sha256: sha('.agent/EXECPLAN.md'), role: 'Execution context; will be updated after verified diagnosis, not an immutable scoring input' };
for (const p of ['spec.md', '.agent/PLANS.md', 'verification/src/main/java/io/github/g1ocr/verification/OcrScoring.java', 'verification/src/main/java/io/github/g1ocr/verification/ComparisonAggregate.java', __filename]) bind(path.isAbsolute(p) ? path.relative(process.cwd(), p).replaceAll('\\', '/') : p);
const batch = read('data/meta/all-scenes-review-v2.json'), media = readRows('data/meta/m1-v1/media.jsonl'), ledgers = new Map();
for (const m of media) { bind(m.path, m.sha256); const p = 'data/meta/m1-v1/' + m.id + '/frames.jsonl'; ledgers.set(m.id, readRows(p, m.framesSha256)); }
assert.equal(media.length, 4); assert.equal(freeze.items.length, 38);
const pagesUnion = new Union(), statesUnion = new Union(), unique = new Map(), references = [], payloadBindings = [], clipMeta = new Map(); let retainedReferences = 0;
function payload(item, kind) { const p = item.payloads.find(p => p.kind === kind); const filename = p.file ? path.join(frozen, p.file) : p.approvedSource; return { p, filename }; }
for (const item of freeze.items) {
  for (const p of item.payloads) bind(p.file ? path.join(frozen, p.file) : p.approvedSource, p.sha256);
  if (item.priorFreeze) bind(item.priorFreeze.path, item.priorFreeze.sha256);
  const gp = payload(item, 'gt'), cp = payload(item, 'clip'), ss = rows(gp.filename), clip = json(cp.filename), ledger = ledgers.get(clip.mediaId), c = scoreReport.clips.find(c => c.clipId === item.id);
  assert(c); assert.equal(c.gtSha256, gp.p.sha256); const ap = run + '/actual/' + item.id + '.jsonl', actual = readRows(ap, c.actualSha256), timeline = readRows(run + '/' + item.id + '-timeline.jsonl'), diffs = readRows(run + '/comparison/' + item.id + '-frames.jsonl');
  const dm = new Map(diffs.map(d => [d.sourceFrameIndex, d])), sm = new Map(); for (const s of ss) for (let n = s.startFrame; n < s.endFrameExclusive; n++) { assert(!sm.has(n)); sm.set(n, s); }
  assert.equal(actual.length, timeline.length); assert.equal(timeline.length, clip.endFrameExclusive - clip.startFrame); assert.equal(diffs.length, clip.scoredRange[1] - clip.scoredRange[0]); retainedReferences += timeline.length;
  const name = batch.items.find(x => x.id === item.id); clipMeta.set(item.id, { clip, name: name.name, frames: name.frames, gt: gp.filename, actual: ap }); payloadBindings.push({ clip: item.id, gt: gp.filename, gtSha256: gp.p.sha256, actual: ap, actualSha256: c.actualSha256 });
  for (let i = 0; i < timeline.length; i++) {
    const t = timeline[i], a = actual[i], l = ledger[t.sourceFrameIndex]; assert.equal(t.sourceFrameIndex, clip.startFrame + i); assert.equal(a.sourceFrameIndex, t.sourceFrameIndex); assert.equal(a.sourcePts, t.sourcePts);
    for (const [tk, lk] of [['sourcePts', 'pts'], ['durationPts', 'durationPts'], ['timeBase', 'timeBase'], ['rgbSha256', 'rgbSha256']]) assert.deepEqual(t[tk], l[lk]);
    assert.equal(t.scored, t.sourceFrameIndex >= clip.scoredRange[0] && t.sourceFrameIndex < clip.scoredRange[1]); if (!t.scored) continue;
    const s = sm.get(t.sourceFrameIndex), d = dm.get(t.sourceFrameIndex); assert(s && d); assert.equal(d.stateId, s.stateId); assert.equal(s.mediaSha256, media.find(m => m.id === clip.mediaId).sha256); assert.equal(d.sourcePts, t.sourcePts);
    const raw = diagnose(s, a.rawRegions, a.visibleState, false), final = diagnose(s, a.correctedRegions || a.rawRegions, a.visibleState, false); assert.deepEqual(raw.score, d.raw); assert.deepEqual(final.score, d.final);
    const stateAlias = clip.mediaId + ':' + item.id + ':' + s.stateId, pageAlias = s.completePage ? clip.mediaId + ':' + item.id + ':' + (s.pageOpportunityId || s.stateId) : null;
    statesUnion.root(stateAlias); if (pageAlias) pagesUnion.root(pageAlias);
    const r = { clip: item.id, media: clip.mediaId, frame: t.sourceFrameIndex, pts: t.sourcePts, durationPts: t.durationPts, timeBase: t.timeBase, rgbSha256: t.rgbSha256, stateId: s.stateId, stateAlias, pageAlias, expectedKind: s.kind, actualKind: a.visibleState.kind, originalFrame: name.frames + '/frame-' + String(t.sourceFrameIndex).padStart(6, '0') + '.png', expectedText: s.regions.map(x => ({ key: regionKey(x), text: x.text })), raw, final };
    references.push(r); const key = clip.mediaId + ':' + r.frame, old = unique.get(key);
    if (old) { assert.deepEqual(old.visible, visible(s)); assert.equal(old.refs[0].pageAlias === null, pageAlias === null); statesUnion.join(old.refs[0].stateAlias, stateAlias); if (pageAlias) pagesUnion.join(old.refs[0].pageAlias, pageAlias); old.refs.push(r); } else unique.set(key, { visible: visible(s), refs: [r] });
  }
}
assert.equal(retainedReferences, 3577); assert.equal(references.length, 3368); assert.equal(unique.size, 3361);
function merged(stage, refs) {
  let chosen = refs[0][stage].score; for (const r of refs) if (errorCount(r[stage].score) > errorCount(chosen)) chosen = r[stage].score;
  const score = { ...chosen }; for (const k of ['exact', 'regionGeometry', 'symbols', 'state']) score[k] = refs.every(r => r[stage].score[k]);
  return { score, families: [...new Set(refs.flatMap(r => r[stage].families))].sort(), dimensions: dimensions.filter(d => refs.some(r => r[stage].dimensions.includes(d))) };
}
const allUnique = [...unique.values()].map(u => ({ ...u.refs[0], refs: u.refs, raw: merged('raw', u.refs), final: merged('final', u.refs) }));
const pageGroups = new Map(); for (const u of allUnique) if (u.pageAlias) { const id = pagesUnion.root(u.pageAlias); if (!pageGroups.has(id)) pageGroups.set(id, []); pageGroups.get(id).push(u); }
function intervals(items) { const a = [...new Set(items)].sort((a, b) => a - b), result = []; for (const n of a) { const prev = result.at(-1); if (prev && prev[1] === n) prev[1]++; else result.push([n, n + 1]); } return result; }
function stageSummary(us, stage) {
  return { exact: us.every(u => u[stage].score.exact), textExact: us.every(u => !errorCount(u[stage].score)), failedFrames: us.filter(u => !u[stage].score.exact).length, editErrors: us.reduce((n, u) => n + errorCount(u[stage].score), 0),
    dimensions: dimensions.filter(d => us.some(u => u[stage].dimensions.includes(d))), families: [...new Set(us.flatMap(u => u[stage].families))].sort(),
    dimensionFrames: Object.fromEntries(dimensions.map(d => [d, us.filter(u => u[stage].dimensions.includes(d)).length])),
    familyFrames: Object.fromEntries(Object.keys(families).map(f => [f, us.filter(u => u[stage].families.includes(f)).length]).filter(([, n]) => n)) };
}
const pages = [...pageGroups.values()].map(us => {
  us.sort((a, b) => a.frame - b.frame); const first = us[0], aliases = [...new Set(us.flatMap(u => u.refs.map(r => r.pageAlias)))].sort(), clips = [...new Set(us.flatMap(u => u.refs.map(r => r.clip)))].sort();
  const evidence = {}; for (const stage of ['raw', 'final']) for (const family of Object.keys(families)) { const u = us.find(u => u[stage].families.includes(family)); if (u) { const r = u.refs.find(r => r[stage].families.includes(family)); evidence[stage + ':' + family] = { clip: r.clip, frame: r.frame, pts: r.pts, originalFrame: r.originalFrame, details: r[stage].details.filter(d => d.family === family) }; } }
  for (const e of Object.values(evidence)) e.originalPngSha256 = bind(e.originalFrame).sha256;
  return { id: first.media + ':f' + first.frame, media: first.media, aliases, clips, sceneNames: clips.map(c => clipMeta.get(c).name), completeFrameRanges: intervals(us.map(u => u.frame)),
    completePtsRanges: intervals(us.map(u => u.frame)).map(([a, b]) => ({ startPts: us.find(u => u.frame === a).pts, endPtsExclusive: us.find(u => u.frame === b - 1).pts + us.find(u => u.frame === b - 1).durationPts, timeBase: first.timeBase })),
    frames: us.length, frameReferences: us.reduce((n, u) => n + u.refs.length, 0), raw: stageSummary(us, 'raw'), final: stageSummary(us, 'final'), expectedTextAtFirstCompleteFrame: first.expectedText,
    statePairs: [...new Set(us.filter(u => !u.raw.score.state).map(u => u.expectedKind + ' ← ' + u.actualKind))], evidence,
    frameBindings: us.map(u => ({ frame: u.frame, pts: u.pts, durationPts: u.durationPts, rgbSha256: u.rgbSha256, refs: u.refs.map(r => ({ clip: r.clip, stateId: r.stateId, pageAlias: r.pageAlias })) })) };
}).sort((a, b) => a.media.localeCompare(b.media) || a.completeFrameRanges[0][0] - b.completeFrameRanges[0][0]);
assert.equal(pages.length, 116);
const pageByAlias = new Map(); pages.forEach((p, i) => { p.number = i + 1; for (const a of p.aliases) pageByAlias.set(a, p.id); });
function aggregate(stage) {
  const states = new Map(); for (const u of allUnique) { const k = statesUnion.root(u.stateAlias); if (!states.has(k)) states.set(k, []); states.get(k).push(u); }
  const characters = allUnique.reduce((n, u) => n + u[stage].score.characters, 0), editErrors = allUnique.reduce((n, u) => n + errorCount(u[stage].score), 0);
  let equalStateErrors = 0, equalStateCharacters = 0; for (const us of states.values()) { equalStateErrors += us.reduce((n, u) => n + errorCount(u[stage].score), 0) / us.length; equalStateCharacters += us.reduce((n, u) => n + u[stage].score.characters, 0) / us.length; }
  const expected = scoreReport.aggregate[stage]; const result = { characters, editErrors, failedFrames: allUnique.filter(u => !u[stage].score.exact).length, emptyFrames: allUnique.filter(u => !u[stage].score.characters).length, emptyFalsePositiveFrames: allUnique.filter(u => !u[stage].score.characters && u[stage].score.insertions).length, uniqueContinuousStates: states.size, equalStateErrors, equalStateCharacters, pageOpportunities: pages.length, contentExactPages: pages.filter(p => p[stage].exact).length, frameCharacterAccuracy: 1 - editErrors / characters, equalStateCharacterAccuracy: 1 - equalStateErrors / equalStateCharacters };
  for (const k of Object.keys(expected)) { if (typeof expected[k] === 'number' && !Number.isInteger(expected[k])) assert(Math.abs(result[k] - expected[k]) < 1e-10, k); else assert.deepEqual(result[k], expected[k], k); } return result;
}
const aggregateVerified = { raw: aggregate('raw'), final: aggregate('final') };
function combinations(stage, property, universe) {
  const histogram = new Map(); for (const p of pages.filter(p => !p[stage].exact)) { const key = p[stage][property].join('+'); const a = histogram.get(key) || []; a.push(p.id); histogram.set(key, a); }
  return [...histogram].map(([key, ids]) => ({ blockers: key.split('+'), pages: ids.length, pageIds: ids })).sort((a, b) => b.pages - a.pages || a.blockers.length - b.blockers.length);
}
function impacts(stage, property, labels) { return Object.entries(labels).map(([id, label]) => { const affected = pages.filter(p => p[stage][property].includes(id)), sole = affected.filter(p => p[stage][property].length === 1); return { id, label, affectedPages: affected.length, soleBlockerPages: sole.length, uniqueCompleteFrames: allUnique.filter(u => u.pageAlias && u[stage][property].includes(id)).length, allScoredUniqueFrames: allUnique.filter(u => u[stage][property].includes(id)).length, pageIds: affected.map(p => p.id), soleBlockerPageIds: sole.map(p => p.id) }; }).sort((a, b) => b.affectedPages - a.affectedPages); }
function removalScenarios(stage) { const ds = dimensions.filter(d => pages.some(p => p[stage].dimensions.includes(d))), result = []; for (let mask = 1; mask < 2 ** ds.length; mask++) { const removed = ds.filter((_, i) => mask & (1 << i)), hits = pages.filter(p => !p[stage].exact && p[stage].dimensions.every(d => removed.includes(d))); result.push({ diagnosticAssumption: removed, additionalExactPagesUpperBound: hits.length, pageIds: hits.map(p => p.id) }); } return result.sort((a, b) => b.additionalExactPagesUpperBound - a.additionalExactPagesUpperBound || a.diagnosticAssumption.length - b.diagnosticAssumption.length); }
const summary = {};
for (const stage of ['raw', 'final']) summary[stage] = { strictExactPages: pages.filter(p => p[stage].exact).length, strictFailedPages: pages.filter(p => !p[stage].exact).length,
  textExactPagesDiagnostic: pages.filter(p => p[stage].textExact).length, dimensionImpacts: impacts(stage, 'dimensions', dimensionNames), familyImpacts: impacts(stage, 'families', families),
  dimensionCombinations: combinations(stage, 'dimensions'), familyCombinations: combinations(stage, 'families'), removalScenarios: removalScenarios(stage) };
const priorityDefinitions = [
  { id: 'source_rect', impactPriority: 'P0', execution: '现有几何路线3/3，保持局部阻塞', action: '影响最大。区域和符号的源矩形差异合并观察；不得只修区域框而遗漏光标投影，也不得重启已耗尽的离散/双线性/面积模型或重置预算。后续方案需先说明新的可辨识性证据及授权范围。' },
  { id: 'complete_loading', impactPriority: 'P1', execution: '当前首个可推进项：可辨识性审计', action: '对全部受影响完整事件检查当前/真实历史像素与已审计日文打印规则能否判定无等待箭头页面完整。先用仅此项阻塞的7页建立正反例；不能凭静止、固定等待或未来帧认定完整。没有证据时如实报告限制。' },
  { id: 'question_encoding', impactPriority: 'P2', execution: '既有问号规范选择仍待答复，仅阻塞该项', action: '17页受影响，单独修正该类当前不能增加严格通过页。保留全半角差异计错；依已完成来源审计统一编码需明确规范决定和版本化处理，不能当作字形识别改善。' },
  { id: 'symbol_structure', impactPriority: 'P3', execution: '与所涉页面其他阻塞合并处理', action: '只剩4页结构差异；源矩形相同结构的偏差已归source_rect。原坐标失败未解时，继续单帧符号补丁难以推动整页通过。' },
  { id: 'other_state', impactPriority: 'P3', execution: '先区分契约/标注疑点与实现错误', action: '7页受影响。其中仅该类阻塞的2页是已记录6208/6466提前淡出标签疑点，不能将潜在GT错误当作必须迎合的算法目标。其余加载/等待/菜单标签逐项对照，未经证据确认不改GT。' },
  { id: 'render_units', impactPriority: 'P4', execution: '独立诊断后按原预算处理', action: '1页紧凑渲染单位差异，先明确序列化/字段语义；同时存在源矩形错误，收益不能重复计数。' },
  { id: 'glyph_text', impactPriority: 'P4', execution: '3541稀疏标记问题3/3，保持局部阻塞', action: '完整页内只剩1页此类文字差异并伴附标/源矩形差异。所有加载/过渡文字错误另保留，不以完整页统计删去。' }
];
const priorities = priorityDefinitions.map(x => { const raw = summary.raw.familyImpacts.find(y => y.id === x.id), final = summary.final.familyImpacts.find(y => y.id === x.id); return { ...x, label: families[x.id], rawAffectedPages: raw.affectedPages, finalAffectedPages: final.affectedPages, rawSoleBlockerPages: raw.soleBlockerPages, finalSoleBlockerPages: final.soleBlockerPages, pageIds: final.pageIds }; });
const projectionAndCompletion = {};
for (const stage of ['raw', 'final']) projectionAndCompletion[stage] = ['source_rect', 'complete_loading', 'source_rect+complete_loading'].map(key => { const assumedFixedFamilies = key.split('+'), gained = pages.filter(p => !p[stage].exact && p[stage].families.every(f => assumedFixedFamilies.includes(f))); return { assumedFixedFamilies, additionalExactPagesUpperBound: gained.length, totalExactPagesUpperBound: summary[stage].strictExactPages + gained.length, pageIds: gained.map(p => p.id) }; });
const transitions = allUnique.filter(u => !u.pageAlias);
const report = { schemaVersion: 1, status: 'existing_v18_page_attribution_verified_not_product_acceptance', scope: 'All frozen r14 page opportunities; complete frames grouped exactly as existing scorer. All remaining scored frames separately retained in frames.jsonl and transitionSummary.',
  command: 'node tools/attribute-ocr-pages.cjs ' + [frozen, run, out].join(' '), freezeSha256: sha(frozen + '/freeze.json'), run, buildEvidence: buildPath,
  diagnosticRules: ['Existing strict scores remain authoritative and every saved raw/final per-frame score is independently recalculated and compared.', 'Text-only diagnostic retains strict role/grid-start/reading-order keys. No coordinate/text normalization is used to grant a pass.', 'Fine families classify observed mismatches, not newly verified image semantics or guaranteed implementation root causes.', 'Counterfactual counts assume an entire mismatch class truly fixed with all other outputs unchanged. They are upper bounds, not new measured passes.', 'Overlapping original frames and pages are deduplicated; all repeated observations must agree for a pass.', 'A source_rect family includes sourceRect-only symbol differences when symbol structure is identical. Structural and source differences together remain symbol_structure.'],
  inputCounts: { clips: freeze.items.length, retainedFrameReferences: retainedReferences, scoredFrameReferences: references.length, uniqueScoredFrames: unique.size, duplicateScoredReferences: references.length - unique.size, pages: pages.length, completeUniqueFrames: allUnique.filter(u => u.pageAlias).length, nonCompleteUniqueFrames: transitions.length, compiledClasses: build.classes.length },
  aggregateVerified, summary, priorities, projectionAndCompletion, planContextAtStart, transitionSummary: { uniqueFrames: transitions.length, raw: stageSummary(transitions, 'raw'), final: stageSummary(transitions, 'final') },
  sources: media.map(m => ({ id: m.id, path: m.path, sha256: m.sha256, frames: m.frameCount, timeBase: m.timeBase, durationSeconds: m.durationSeconds })),
  frozenCoverageAndGaps: freeze.summary, limitations: freeze.limitations, allSemanticIntegrityPassed: false, independentAcceptance: scoreReport.independentAcceptance, uiPresentationAndLatency: scoreReport.uiPresentationAndLatency, productAcceptance: false, payloadBindings, inputs: [...inputs].map(([p, h]) => ({ path: p, sha256: h })) };
for (const [p, h] of inputs) assert.equal(sha(p), h, 'Input changed during diagnosis: ' + p);
// Produce data before presentation; output is append-only by requiring a new directory.
fs.mkdirSync(out, { recursive: true });
function priorityTable() {
  return '<h2>主要问题与修复优先级</h2><p>影响排序与允许执行的先后分开：源矩形是最大瓶颈，既有几何路线已到上限；下一可执行项是无箭头完整页的在线可辨识性审计。以下“单独阻塞”按最终输出计算，属于其余错误保持不变的诊断上限。</p><table><thead><tr><th>优先级</th><th>差异家族</th><th>影响页 / 单独阻塞页</th><th>执行边界与建议</th></tr></thead><tbody>' + priorities.map(p => '<tr><td>' + esc(p.impactPriority) + '</td><td>' + esc(p.label) + '</td><td>' + p.finalAffectedPages + ' / ' + p.finalSoleBlockerPages + '</td><td><b>' + esc(p.execution) + '</b><br>' + esc(p.action) + '</td></tr>').join('') + '</tbody></table><p>若所有源矩形差异均被真实修复且其余错误不变，最终严格精确页上限为 ' + projectionAndCompletion.final[0].totalExactPagesUpperBound + '/116；若再真实解决无箭头完成判定，上限为 ' + projectionAndCompletion.final[2].totalExactPagesUpperBound + '/116。此为条件分析，实测仍7/116。</p>';
}
const write = (p, v) => fs.writeFileSync(out + '/' + p, p === 'index.html' ? v.replace('<h2>全部页面', priorityTable() + '<h2>全部页面') : v, { flag: 'wx' });
write('report.json', JSON.stringify(report, null, 2) + '\n'); write('pages.jsonl', pages.map(JSON.stringify).join('\n') + '\n');
write('frames.jsonl', references.map(r => JSON.stringify({ ...r, pageId: r.pageAlias ? pageByAlias.get(r.pageAlias) : null })).join('\n') + '\n');
const esc = x => String(x).replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;');
const labels = (xs, dict) => xs.length ? xs.map(x => dict[x] || x).join('、') : '—';
const href = p => esc(path.relative(out, p).replaceAll('\\', '/'));
function table(stage) { return summary[stage].familyImpacts.filter(x => x.affectedPages).map(x => '<tr><td>' + esc(x.label) + '</td><td>' + x.affectedPages + '</td><td>' + x.soleBlockerPages + '</td><td>' + x.uniqueCompleteFrames + '</td></tr>').join(''); }
const body = pages.map(p => '<tr class="page" data-search="' + esc(JSON.stringify([p.number, p.media, p.clips, p.aliases, p.raw.families, p.final.families])) + '" data-pass="' + p.final.exact + '"><td>' + p.number + '</td><td><b>' + esc(p.id) + '</b><br>' + esc(p.sceneNames.join(' / ')) + '<br><small>' + esc(p.clips.join(' / ')) + '</small></td><td>' + esc(p.completeFrameRanges.map(x => '[' + x.join(',') + ')').join(' ∪ ')) + '<br>' + p.frames + ' 原帧</td><td>' + (p.raw.exact ? '通过' : '失败') + ' / ' + (p.final.exact ? '通过' : '失败') + '</td><td>' + (p.raw.textExact ? '文字全对' : p.raw.editErrors + ' 编辑错') + ' / ' + (p.final.textExact ? '文字全对' : p.final.editErrors + ' 编辑错') + '</td><td>' + esc(labels(p.final.families, families)) + '</td><td><details><summary>全部归因与定位</summary><p>原始维度：' + esc(labels(p.raw.dimensions, dimensionNames)) + '<br>最终维度：' + esc(labels(p.final.dimensions, dimensionNames)) + '</p><p>' + esc(p.statePairs.join('; ') || '状态一致') + '</p><pre>' + esc(p.expectedTextAtFirstCompleteFrame.map(x => x.key + '：' + x.text).join('\n')) + '</pre>' + Object.entries(p.evidence).filter(([k]) => k.startsWith('final:')).map(([k, e]) => '<details><summary>' + esc(families[k.slice(6)]) + ' · 原帧 ' + e.frame + '</summary><a href="' + href(e.originalFrame) + '">完整原帧</a> · <a href="' + href(clipMeta.get(e.clip).actual) + '">v18 全部输出</a> · <a href="' + href(clipMeta.get(e.clip).gt) + '">冻结 GT</a><pre>' + esc(JSON.stringify(e, null, 2)) + '</pre></details>').join('') + '<details><summary>完整页身份、区间、统计及逐帧绑定</summary><pre>' + esc(JSON.stringify({ ...p, evidence: undefined }, null, 2)) + '</pre></details></details></td></tr>').join('\n');
write('index.html', '<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>v18 · 全部116页错误归因</title><style>body{font:15px/1.55 system-ui,sans-serif;color:#17212b;background:#f6f8fb;margin:0;padding:28px}main{max-width:1600px;margin:auto}h1{font-size:28px}table{border-collapse:collapse;width:100%;background:white;margin:18px 0}th,td{border:1px solid #dce2ea;text-align:left;vertical-align:top;padding:10px}th{background:#eaf0f7}small{color:#516175}pre{white-space:pre-wrap;word-break:break-all;font:12px/1.45 monospace;max-width:650px}summary{cursor:pointer}input,select{font:inherit;padding:8px}a{color:#075ca9}.note{background:#fff5d6;padding:14px;border-left:4px solid #c98e11}[hidden]{display:none!important}</style><main><h1>v18 · 全部116页错误归因</h1><p>r14 冻结范围 / v18 已保存输出。严格通过 7/116；原始文字全对 ' + summary.raw.textExactPagesDiagnostic + '/116，辅助后文字全对 ' + summary.final.textExactPagesDiagnostic + '/116。文字诊断保留原严格区域匹配键。</p><p class="note">本页是确定性离线诊断附件。原 GT、OCR、评分和失败分母保持。差异家族是观察到的症状；“单独阻塞”仅表示其他维度已匹配，不承诺修复效果。正式整页验收仍缺实际 UI 呈现、≤200ms 时延和独立会话证据。完整帧以外的加载/滚动/清屏等全部评分帧也保存在 frames.jsonl；未选范围继续待验证。</p><p><a href="report.json">完整汇总、输入SHA及覆盖缺口</a> · <a href="pages.jsonl">116页完整数据</a> · <a href="frames.jsonl">3368条评分引用全部诊断</a></p><h2>最终输出的差异家族（可重叠）</h2><table><thead><tr><th>观察到的差异</th><th>受影响页</th><th>仅该家族阻塞页</th><th>完整状态原帧</th></tr></thead><tbody>' + table('final') + '</tbody></table><h2>全部页面（按录像、原帧顺序）</h2><p><input id="search" placeholder="页号、场景ID、差异家族" aria-label="搜索页面"> <select id="pass" aria-label="按最终结果筛选"><option value="">全部116页</option><option value="false">109个严格失败页</option><option value="true">7个严格通过页</option></select> <span id="count">116 / 116</span></p><table><thead><tr><th>序号</th><th>原始页面/场景</th><th>完整帧半开区间</th><th>严格 原始/最终</th><th>文字 原始/最终</th><th>最终全部阻塞</th><th>证据</th></tr></thead><tbody>' + body + '</tbody></table></main><script>const q=document.getElementById("search"),s=document.getElementById("pass"),rs=[...document.querySelectorAll("tr.page")];function update(){let n=0;for(const r of rs){r.hidden=!(r.dataset.search.toLowerCase().includes(q.value.toLowerCase())&&(!s.value||r.dataset.pass===s.value));if(!r.hidden)n++;}document.getElementById("count").textContent=n+" / 116";}q.addEventListener("input",update);s.addEventListener("change",update);</script></html>\n');
const files = fs.readdirSync(out).sort().map(file => ({ file, sha256: sha(out + '/' + file), bytes: fs.statSync(out + '/' + file).size })); write('manifest.json', JSON.stringify({ files, inputsVerifiedBeforeAndAfter: true }, null, 2) + '\n');
console.log(JSON.stringify({ output: out, reportSha256: sha(out + '/report.json'), counts: report.inputCounts, raw: { strict: summary.raw.strictExactPages, textOnlyDiagnostic: summary.raw.textExactPagesDiagnostic }, final: { strict: summary.final.strictExactPages, textOnlyDiagnostic: summary.final.textExactPagesDiagnostic } }));
