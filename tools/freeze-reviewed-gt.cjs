'use strict';
// Offline evidence operation. Never imports or executes the tested OCR pipeline.
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const cp = require('child_process');
const root = process.cwd();
const hash = p => crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const json = p => JSON.parse(fs.readFileSync(p, 'utf8'));
const assert = (ok, why) => { if (!ok) throw Error(why); };
function local(p) {
  const full = path.resolve(root, p), relative = path.relative(root, full);
  assert(relative && !relative.startsWith('..') && !path.isAbsolute(relative), 'Path outside workspace: ' + p);
  return full;
}
function write(p, x) { fs.writeFileSync(p, JSON.stringify(x, null, 2) + '\n', {flag: 'wx'}); }
function identity(p) { return {path: path.relative(root, p).replace(/\\/g, '/'), sha256: hash(p), bytes: fs.statSync(p).size}; }
const args = process.argv.slice(2);
assert(args.length === 4, 'Usage: node tools/freeze-reviewed-gt.cjs <approval.json> <review-dir> <new-frozen-dir> <new-check-dir>');
const [approvalPath, review, frozen, check] = args.map(local);
assert(!fs.existsSync(frozen) && !fs.existsSync(check), 'Frozen/check output must not exist');
fs.mkdirSync(check, {recursive: true});
try {
  const approval = json(approvalPath), manifestPath = path.join(review, 'bundle-manifest.json');
  assert(hash(manifestPath) === approval.bundleManifestSha256, 'Approved bundle manifest mismatch');
  const manifest = json(manifestPath), data = json(path.join(review, 'review-data.json'));
  const summary = json(path.join(review, 'summary.json')), batch = data.batch;
  assert(approval.message && approval.date && approval.basis && approval.reviewId === manifest.reviewId && batch.reviewId === approval.reviewId, 'Approval version/evidence mismatch');
  assert(manifest.files.length === manifest.fileCount && batch.items.length === data.items.length, 'Manifest/item count mismatch');
  const listed = new Map();
  for (const f of manifest.files) {
    const p = local(path.join(review, f.path));
    assert(p.startsWith(review + path.sep) && !listed.has(f.path), 'Invalid/duplicate bundle entry');
    assert(fs.statSync(p).size === f.bytes && hash(p) === f.sha256, 'Bundle changed: ' + f.path);
    listed.set(f.path, f);
  }
  console.log('Approved bundle verified: ' + listed.size + ' files');
  const overlapAudit = require('./check-gt-overlaps.cjs').audit(path.join(review, 'batch.json'));
  write(path.join(check, 'overlap-metadata.json'), overlapAudit);
  assert(overlapAudit.conflicts.length === 0, 'Cross-clip field/order/state conflict; approved payload needs an affected-scope amendment, not silent normalization');
  const media = fs.readFileSync(local(batch.mediaLedger), 'utf8').trim().split(/\r?\n/).map(JSON.parse);
  assert(media.length === 4, 'Exactly four original sources required');
  for (const m of media) {
    assert(hash(local(m.path)) === m.sha256, 'Original media changed: ' + m.id);
    assert(hash(local('data/meta/m1-v1/' + m.id + '/frames.jsonl')) === m.framesSha256, 'Frame ledger changed: ' + m.id);
  }
  const checked = [];
  const cpPath = ['verification/target/classes', 'app/target/classes', '.cache/m2/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar'].map(local).join(path.delimiter);
  for (const item of batch.items) {
    const reviewed = data.items.find(x => x.id === item.id);
    assert(reviewed, 'Missing reviewed item');
    const payloads = [];
    for (const [key, srcKey] of [['gt','candidate'], ['clip','clip'], ['layout','layout'], ['contract','contract']]) {
      const approvedFile = reviewed.links[key], src = local(item[srcKey]);
      assert(listed.has(approvedFile) && hash(src) === listed.get(approvedFile).sha256, 'Current payload differs from approved version: ' + item.id + '/' + key);
      payloads.push({kind:key, approvedSource:item[srcKey], bundleFile:approvedFile, sha256:hash(src), bytes:fs.statSync(src).size});
    }
    for (const frame of reviewed.frames) {
      const p = local(path.join(item.frames, 'frame-' + String(frame.index).padStart(6, '0') + '.png'));
      assert(hash(p) === frame.pngSha256, 'Retained source PNG changed: ' + item.id + '/' + frame.index);
    }
    const report = path.join(check, item.id + '-integrity.json');
    const command = ['-Dfile.encoding=UTF-8', '-cp', cpPath, 'io.github.g1ocr.verification.VideoTool', 'gt-check', 'data/meta/m1-v1', item.clip, item.candidate, item.layout, report];
    const result = cp.spawnSync('java', command, {encoding:'utf8', maxBuffer:4*1024*1024});
    fs.writeFileSync(path.join(check, item.id + '.log'), result.stdout + result.stderr, {flag:'wx'});
    assert(!result.error && result.status === 0 && fs.existsSync(report), 'Integrity recheck failed: ' + item.id);
    const integrity = json(report);
    for (const field of ['sourceFrames','states','completePageOpportunities','frameWeightedExpectedCharacters','gtSha256','clipSha256','layoutSha256','sourceFrameLedgerSha256']) {
      assert(integrity[field] === reviewed.integrity[field], 'Reviewed integrity differs: ' + item.id + '/' + field);
    }
    checked.push({id:item.id, payloads, report:identity(report), command, priorFreeze:item.freeze ? identity(local(item.freeze)) : null});
    console.log('GT integrity rechecked: ' + item.id + ' (' + integrity.sourceFrames + ' frames)');
  }
  // Freeze only after every approved item, retained frame and original source has passed.
  const evidence = {status:'approved_version_identity_and_integrity_rechecked', approval:identity(approvalPath), bundle:identity(manifestPath), filesChecked:listed.size, media, items:checked, summary};
  write(path.join(check, 'identity.json'), evidence);
  fs.mkdirSync(frozen, {recursive:true});
  const entries = [];
  for (const c of checked) {
    if (c.priorFreeze) { entries.push({id:c.id, priorFreeze:c.priorFreeze, payloads:c.payloads}); continue; }
    const dir = path.join(frozen, c.id); fs.mkdirSync(dir);
    const payloads = c.payloads.map(p => {
      const destination = path.join(dir, path.basename(p.approvedSource));
      fs.copyFileSync(local(p.approvedSource), destination, fs.constants.COPYFILE_EXCL);
      assert(hash(destination) === p.sha256, 'Frozen copy mismatch');
      return {...p, file:path.relative(frozen,destination).replace(/\\/g,'/')};
    });
    entries.push({id:c.id, payloads, integrityCheck:c.report});
  }
  for (const name of ['batch.json','summary.json']) fs.copyFileSync(path.join(review,name), path.join(frozen,name), fs.constants.COPYFILE_EXCL);
  fs.copyFileSync(approvalPath, path.join(frozen,'approval.json'), fs.constants.COPYFILE_EXCL);
  const envelope = {schemaVersion:1, freezeVersion:'ocr-' + approval.reviewId, status:'frozen_approved_scope_only', frozenAt:new Date().toISOString(), authority:'Immutable envelope is authoritative; approved payload candidate flags and bytes are retained unchanged.', approval, approvedBundle:identity(manifestPath), identityCheck:identity(path.join(check,'identity.json')), items:entries, summary, limitations:batch.gaps, sessionPartition:batch.sessionPartition, productAcceptance:false};
  write(path.join(frozen,'freeze.json'), envelope);
  write(path.join(check,'result.json'), {status:'approved_gt_frozen', freeze:identity(path.join(frozen,'freeze.json')), newItems:entries.filter(x=>!x.priorFreeze).length, summary});
  console.log('Frozen ' + entries.filter(x=>!x.priorFreeze).length + ' new entries; unique scored frames=' + summary.uniqueScoredOriginalFrames + '. Not OCR acceptance.');
} catch (error) {
  write(path.join(check,'failure.json'), {status:'failed_not_frozen', error:error.stack});
  throw error;
}
