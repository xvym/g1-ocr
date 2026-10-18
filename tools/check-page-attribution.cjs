'use strict';
// Independent cross-check from saved Java frame scores and frozen page declarations.
// Does not import the attribution generator or execute the tested OCR pipeline.
const fs = require('fs'), path = require('path'), crypto = require('crypto'), assert = require('assert/strict'), vm = require('vm');
const [directory, output] = process.argv.slice(2); assert(directory && output && !fs.existsSync(output));
const json = p => JSON.parse(fs.readFileSync(p, 'utf8')), rows = p => fs.readFileSync(p, 'utf8').trim().split(/\r?\n/).filter(Boolean).map(JSON.parse);
function sha(p) { const h = crypto.createHash('sha256'), fd = fs.openSync(p, 'r'), b = Buffer.alloc(1024 * 1024); try { let n; while ((n = fs.readSync(fd, b, 0, b.length, null))) h.update(b.subarray(0, n)); } finally { fs.closeSync(fd); } return h.digest('hex'); }
const report = json(directory + '/report.json'), pages = rows(directory + '/pages.jsonl'), attributed = rows(directory + '/frames.jsonl'), manifest = json(directory + '/manifest.json');
for (const f of manifest.files) { assert.equal(sha(directory + '/' + f.file), f.sha256); assert.equal(fs.statSync(directory + '/' + f.file).size, f.bytes); }
for (const i of report.inputs) assert.equal(sha(i.path), i.sha256, i.path);
const freeze = json(report.inputs.find(x => x.path.endsWith('/freeze.json') && x.sha256 === report.freezeSha256).path), old = json(report.run + '/comparison/report.json');
assert.equal(pages.length, 116); assert.equal(attributed.length, 3368); assert.equal(new Set(pages.map(p => p.id)).size, 116);
assert.equal(report.allSemanticIntegrityPassed, false); assert.equal(report.productAcceptance, false);
const canonical = x => JSON.stringify(x, Object.keys(x).sort());
const expectedAliases = new Map(), refByKey = new Map(), originalFrames = new Map(), oldPageCorrect = { raw: new Map(), final: new Map() };
for (const item of freeze.items) {
  const resolve = p => p.file ? path.join(path.dirname(report.inputs.find(x => x.sha256 === report.freezeSha256).path), p.file) : p.approvedSource;
  const clip = json(resolve(item.payloads.find(p => p.kind === 'clip'))), states = rows(resolve(item.payloads.find(p => p.kind === 'gt'))), actual = new Map(rows(report.run + '/actual/' + item.id + '.jsonl').map(x => [x.sourceFrameIndex, x]));
  const diffs = rows(report.run + '/comparison/' + item.id + '-frames.jsonl'), declaration = new Map();
  for (const s of states) for (let n = s.startFrame; n < s.endFrameExclusive; n++) declaration.set(n, s);
  for (const d of diffs) {
    const s = declaration.get(d.sourceFrameIndex), key = item.id + ':' + d.sourceFrameIndex, source = clip.mediaId + ':' + d.sourceFrameIndex;
    const alias = s.completePage ? clip.mediaId + ':' + item.id + ':' + (s.pageOpportunityId || s.stateId) : null;
    assert(!refByKey.has(key)); refByKey.set(key, { d, alias, source, state: s, actual: actual.get(d.sourceFrameIndex) });
    if (!originalFrames.has(source)) originalFrames.set(source, []); originalFrames.get(source).push({ d, alias });
    if (alias) { if (!expectedAliases.has(alias)) expectedAliases.set(alias, new Set()); expectedAliases.get(alias).add(source); }
    for (const stage of ['raw', 'final']) if (alias) oldPageCorrect[stage].set(alias, (oldPageCorrect[stage].get(alias) ?? true) && d[stage].exact);
  }
}
assert.equal(refByKey.size, 3368); assert.equal(originalFrames.size, 3361);
const aliasToPage = new Map(), pageMap = new Map(pages.map(p => [p.id, p])), pageSources = new Map();
for (const p of pages) {
  const expectedSources = new Set(); for (const alias of p.aliases) { assert(!aliasToPage.has(alias)); aliasToPage.set(alias, p.id); assert(expectedAliases.has(alias)); for (const s of expectedAliases.get(alias)) expectedSources.add(s); }
  const listedSources = new Set(p.frameBindings.map(x => p.media + ':' + x.frame)); assert.deepEqual(listedSources, expectedSources); assert.equal(listedSources.size, p.frames); pageSources.set(p.id, expectedSources);
  const ranges = new Set(); for (const [a, b] of p.completeFrameRanges) for (let n = a; n < b; n++) ranges.add(p.media + ':' + n); assert.deepEqual(ranges, expectedSources);
  for (const stage of ['raw', 'final']) {
    const ds = [...expectedSources].flatMap(s => originalFrames.get(s).map(x => x.d[stage]));
    assert.equal(p[stage].exact, ds.every(d => d.exact)); assert.equal(p[stage].textExact, ds.every(d => d.substitutions + d.deletions + d.insertions === 0));
    assert.equal(p[stage].dimensions.includes('geometry'), ds.some(d => !d.regionGeometry)); assert.equal(p[stage].dimensions.includes('symbols'), ds.some(d => !d.symbols)); assert.equal(p[stage].dimensions.includes('state'), ds.some(d => !d.state));
  }
}
assert.equal(aliasToPage.size, expectedAliases.size);
for (const arr of originalFrames.values()) { const pids = new Set(arr.map(x => x.alias ? aliasToPage.get(x.alias) : null)); assert.equal(pids.size, 1, 'Overlapping page merge differs'); }
for (const r of attributed) { const old = refByKey.get(r.clip + ':' + r.frame); assert(old); assert.deepEqual(r.raw.score, old.d.raw); assert.deepEqual(r.final.score, old.d.final); assert.equal(r.pageId, old.alias ? aliasToPage.get(old.alias) : null); }
for (const stage of ['raw', 'final']) {
  const exact = pages.filter(p => p[stage].exact).length, text = pages.filter(p => p[stage].textExact).length;
  assert.equal(exact, old.aggregate[stage].contentExactPages); assert.equal(exact, report.summary[stage].strictExactPages); assert.equal(text, report.summary[stage].textExactPagesDiagnostic);
  assert.equal(report.summary[stage].familyCombinations.reduce((n, x) => n + x.pages, 0), 116 - exact);
  assert.equal(report.summary[stage].dimensionCombinations.reduce((n, x) => n + x.pages, 0), 116 - exact);
  for (const impact of report.summary[stage].familyImpacts) { const affected = pages.filter(p => p[stage].families.includes(impact.id)), sole = affected.filter(p => p[stage].families.length === 1); assert.equal(affected.length, impact.affectedPages); assert.equal(sole.length, impact.soleBlockerPages); }
  for (const s of report.projectionAndCompletion[stage]) assert.equal(s.additionalExactPagesUpperBound, pages.filter(p => !p[stage].exact && p[stage].families.every(f => s.assumedFixedFamilies.includes(f))).length);
}
// Check the especially consequential sourceRect-only classification against full saved observations.
function pick(v, ks) { return Object.fromEntries(ks.filter(k => v && v[k] != null).map(k => [k, v[k]])); }
function noSource(x) { if (Array.isArray(x)) return x.map(noSource); if (x && typeof x === 'object') return Object.fromEntries(Object.entries(x).filter(([k]) => k !== 'sourceRect').map(([k, v]) => [k, noSource(v)])); return x; }
function syms(v) { return (v == null ? [] : Array.isArray(v) ? v : [v]).map(x => { const s = pick(x, ['kind', 'style', 'gridRect', 'sourceRect']); if (s.style === 'outlined_right') s.style = 'outline_right'; return s; }); }
function cur(v) { const c = v?.cursor || {}, r = { menuCursors: syms(c.menuCursors), downArrowVisible: c.downArrowVisible === true }; if (r.downArrowVisible) r.downArrow = pick(c.downArrow || c, ['style', 'gridRect', 'sourceRect']); r.additionalDownArrows = syms(c.additionalDownArrows); return r; }
let sourceOnlyReferencesChecked = 0;
for (const r of attributed) for (const stage of ['raw', 'final']) if (r[stage].families.length === 1 && r[stage].families[0] === 'source_rect') {
  const { state: s, actual: a } = refByKey.get(r.clip + ':' + r.frame), ar = stage === 'final' ? a.correctedRegions || a.rawRegions : a.rawRegions;
  const regionFields = ['role', 'readingOrder', 'text', 'gridRect', 'markCells', 'logicalRect', 'renderUnits'];
  const gt = s.regions.map(x => pick(x, regionFields)).sort((a, b) => canonical(a).localeCompare(canonical(b))), observed = ar.map(x => pick(x, regionFields)).sort((a, b) => canonical(a).localeCompare(canonical(b)));
  assert.deepEqual(gt, observed); assert.deepEqual(noSource(cur(s)), noSource(cur(a.visibleState))); assert.deepEqual(noSource(syms(s.detachedMarks)), noSource(syms(a.visibleState.detachedMarks))); assert.equal(s.kind, a.visibleState.kind); sourceOnlyReferencesChecked++;
}
const html = fs.readFileSync(directory + '/index.html', 'utf8'); assert.equal((html.match(/<tr class="page"/g) || []).length, 116);
let linksChecked = 0; for (const [, href] of html.matchAll(/href="([^"]+)"/g)) { assert(!/^(https?:|javascript:)/.test(href)); assert(fs.existsSync(path.resolve(directory, href.replaceAll('&amp;', '&'))), href); linksChecked++; }
const tableRows = [...html.matchAll(/<tr class="page" data-search="([^"]*)" data-pass="(true|false)"/g)].map(m => ({ hidden: false, dataset: { search: m[1].replaceAll('&quot;', '"').replaceAll('&amp;', '&'), pass: m[2] } }));
assert.equal(tableRows.length, 116); const controls = { search: { value: '', addEventListener() {} }, pass: { value: '', addEventListener() {} }, count: {} };
const sandbox = { document: { getElementById: id => controls[id], querySelectorAll: () => tableRows } }; const context = vm.createContext(sandbox);
const scripts = [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)]; assert.equal(scripts.length, 1); new vm.Script(scripts[0][1]).runInContext(context);
for (const [pass, expected] of [['', 116], ['false', 109], ['true', 7]]) { controls.pass.value = pass; vm.runInContext('update()', context); assert.equal(tableRows.filter(r => !r.hidden).length, expected); }
controls.pass.value = ''; controls.search.value = 'impossible-search-no-match'; vm.runInContext('update()', context); assert.equal(tableRows.filter(r => !r.hidden).length, 0);
const result = { status: 'page_attribution_cross_check_passed_not_product_acceptance', report: { path: directory + '/report.json', sha256: sha(directory + '/report.json') }, checker: { path: path.relative(process.cwd(), __filename).replaceAll('\\', '/'), sha256: sha(__filename) }, pages: pages.length, aliasesBeforeOverlapDeduplication: aliasToPage.size, scoredReferences: refByKey.size, uniqueScoredFrames: originalFrames.size, rawStrictPages: report.summary.raw.strictExactPages, finalStrictPages: report.summary.final.strictExactPages, rawTextOnlyDiagnosticPages: report.summary.raw.textExactPagesDiagnostic, finalTextOnlyDiagnosticPages: report.summary.final.textExactPagesDiagnostic, sourceOnlyReferencesChecked, htmlRows: tableRows.length, linksChecked, htmlFilters: 'all/failed/passed/no-match verified in DOM stub', actualBrowserRendering: 'not verified', immutableInputHashesRechecked: report.inputs.length, productAcceptance: false };
fs.writeFileSync(output, JSON.stringify(result, null, 2) + '\n', { flag: 'wx' }); console.log(JSON.stringify(result));
