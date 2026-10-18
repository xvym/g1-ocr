'use strict';
// Diagnostic policy only. Its interface deliberately excludes GT, clip IDs and future frames.
const assert = require('assert/strict');
const READY = new Set(['message_complete', 'message_fading', 'menu_complete', 'status_page_complete']);
const project = (o, keys) => Object.fromEntries(keys.filter(k => o[k] != null).map(k => [k, o[k]]));
function signature(o) {
  return JSON.stringify({
    regions: o.regions.map(r => project(r, ['role', 'readingOrder', 'text', 'gridRect', 'markCells', 'logicalRect', 'renderUnits'])),
    cursors: o.menuCursors.map(s => project(s, ['style', 'gridRect'])),
    detachedMarks: o.detachedMarks.map(s => project(s, ['kind', 'gridRect']))
  });
}
function createPolicy(stableMs = null) {
  assert(stableMs === null || Number.isFinite(stableMs) && stableMs >= 0);
  let previous = null, since = 0n, samples = 0, emitted = false, lastPts = null, unit = null;
  return {
    observe(o) {
      const pts = BigInt(o.pts), [n, d] = o.timeBase.split('/').map(BigInt);
      assert(n > 0n && d > 0n);
      const changedClock = unit !== o.timeBase || lastPts !== null && pts <= lastPts;
      if (changedClock) { previous = null; samples = 0; emitted = false; }
      unit = o.timeBase; lastPts = pts;
      const key = signature(o), hasText = o.regions.some(r => r.text.length > 0);
      const eligible = hasText && (READY.has(o.kind) || o.kind === 'message_loading');
      const changed = previous !== key;
      if (changed || !eligible || samples === 0) { since = pts; samples = 0; emitted = false; }
      previous = key;
      if (eligible) samples++;
      const stable = stableMs !== null && o.kind === 'message_loading' && samples >= 2 &&
        !o.regions.some(r => r.text.includes('\ufffd')) &&
        (pts - since) * n * 1000n >= BigInt(stableMs) * d;
      const ready = hasText && (READY.has(o.kind) || stable);
      const fire = ready && !emitted;
      if (fire) emitted = true;
      return { ready, fire, reason: ready ? READY.has(o.kind) ? 'v18_complete_state' : 'text_stable' : 'not_ready',
        stableSincePts: String(since), samples, stableAgeMs: Number((pts - since) * n) * 1000 / Number(d),
        invalidate: changed || !eligible, signature: key };
    }
  };
}
module.exports = { createPolicy, signature };
