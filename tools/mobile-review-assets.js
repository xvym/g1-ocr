/* Local, user-selected ZIP access for the review companion. No network access. */
(function (scope) {
  'use strict';
  const ensure = (ok, message) => { if (!ok) throw new Error(message); };
  const decoder = new TextDecoder('utf-8');
  const MAX_ENTRY_BYTES = 16 * 1024 * 1024;
  const MAX_DIRECTORY_BYTES = 32 * 1024 * 1024;
  const crcTable = Array.from({length: 256}, (_, n) => {
    for (let k = 0; k < 8; k++) n = n & 1 ? 0xedb88320 ^ (n >>> 1) : n >>> 1;
    return n >>> 0;
  });
  function crc32(bytes) {
    let crc = 0xffffffff;
    for (const b of bytes) crc = crcTable[(crc ^ b) & 255] ^ (crc >>> 8);
    return (crc ^ 0xffffffff) >>> 0;
  }
  // The fallback also works in local-document contexts without crypto.subtle.
  function sha256Fallback(bytes) {
    const constants = [0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,0xd192e819,0xd6990624,0xf40e3585,0x106aa070,0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2];
    const state = [0x6a09e667,0xbb67ae85,0x3c6ef372,0xa54ff53a,0x510e527f,0x9b05688c,0x1f83d9ab,0x5be0cd19];
    const padded = new Uint8Array(Math.ceil((bytes.length + 9) / 64) * 64);
    padded.set(bytes); padded[bytes.length] = 0x80;
    const view = new DataView(padded.buffer), bitLength = bytes.length * 8;
    view.setUint32(padded.length - 8, Math.floor(bitLength / 0x100000000));
    view.setUint32(padded.length - 4, bitLength >>> 0);
    const rotate = (x, n) => (x >>> n) | (x << (32 - n));
    const words = new Uint32Array(64);
    for (let offset = 0; offset < padded.length; offset += 64) {
      for (let i = 0; i < 16; i++) words[i] = view.getUint32(offset + i * 4);
      for (let i = 16; i < 64; i++) {
        const a = words[i - 15], b = words[i - 2];
        words[i] = (words[i - 16] + (rotate(a, 7) ^ rotate(a, 18) ^ (a >>> 3)) + words[i - 7] + (rotate(b, 17) ^ rotate(b, 19) ^ (b >>> 10))) >>> 0;
      }
      let [a,b,c,d,e,f,g,h] = state;
      for (let i = 0; i < 64; i++) {
        const first = (h + (rotate(e, 6) ^ rotate(e, 11) ^ rotate(e, 25)) + ((e & f) ^ (~e & g)) + constants[i] + words[i]) >>> 0;
        const second = ((rotate(a, 2) ^ rotate(a, 13) ^ rotate(a, 22)) + ((a & b) ^ (a & c) ^ (b & c))) >>> 0;
        h=g; g=f; f=e; e=(d+first)>>>0; d=c; c=b; b=a; a=(first+second)>>>0;
      }
      [a,b,c,d,e,f,g,h].forEach((value, i) => { state[i] = (state[i] + value) >>> 0; });
    }
    return state.map(n => n.toString(16).padStart(8, '0')).join('');
  }
  async function sha256(bytes) {
    if (scope.crypto && scope.crypto.subtle) {
      const digest = await scope.crypto.subtle.digest('SHA-256', bytes);
      return Array.from(new Uint8Array(digest), b => b.toString(16).padStart(2, '0')).join('');
    }
    return sha256Fallback(bytes);
  }
  class ReviewZip {
    static async open(file) {
      ensure(file && file.size >= 22, '所选文件不是完整 ZIP。');
      const tail = new Uint8Array(await file.slice(Math.max(0, file.size - 65557)).arrayBuffer());
      const tailView = new DataView(tail.buffer);
      let end = -1;
      for (let i = tail.length - 22; i >= 0; i--) {
        if (tailView.getUint32(i, true) === 0x06054b50 && i + 22 + tailView.getUint16(i + 20, true) === tail.length) { end = i; break; }
      }
      ensure(end >= 0, 'ZIP 尾部不完整，请检查下载是否完成。');
      const count = tailView.getUint16(end + 10, true), directoryBytes = tailView.getUint32(end + 12, true), directoryOffset = tailView.getUint32(end + 16, true);
      ensure(tailView.getUint16(end + 4, true) === 0 && tailView.getUint16(end + 6, true) === 0 && tailView.getUint16(end + 8, true) === count, '不支持分卷 ZIP，请选择普通 ZIP。');
      ensure(count !== 0xffff && directoryBytes !== 0xffffffff && directoryOffset !== 0xffffffff, '此入口不支持 ZIP64；请只打包 gt-all-scenes-v2 目录。');
      ensure(directoryBytes <= MAX_DIRECTORY_BYTES && directoryOffset + directoryBytes === file.size - tail.length + end, 'ZIP 目录越界或格式不受支持。');
      const directory = new Uint8Array(await file.slice(directoryOffset, directoryOffset + directoryBytes).arrayBuffer());
      const view = new DataView(directory.buffer), entries = new Map();
      let offset = 0;
      for (let i = 0; i < count; i++) {
        ensure(offset + 46 <= directory.length && view.getUint32(offset, true) === 0x02014b50, 'ZIP 文件目录损坏。');
        const nameLength = view.getUint16(offset + 28, true), extraLength = view.getUint16(offset + 30, true), commentLength = view.getUint16(offset + 32, true);
        const next = offset + 46 + nameLength + extraLength + commentLength;
        ensure(next <= directory.length, 'ZIP 文件名或目录记录不完整。');
        const name = decoder.decode(directory.subarray(offset + 46, offset + 46 + nameLength));
        ensure(name && !name.startsWith('/') && !name.includes('\\') && !name.split('/').includes('..') && !entries.has(name), 'ZIP 含重复或不受支持的路径。');
        entries.set(name, {name, flags:view.getUint16(offset + 8, true), method:view.getUint16(offset + 10, true), crc:view.getUint32(offset + 16, true), packed:view.getUint32(offset + 20, true), bytes:view.getUint32(offset + 24, true), offset:view.getUint32(offset + 42, true)});
        offset = next;
      }
      ensure(offset === directory.length, 'ZIP 目录长度不一致。');
      return new ReviewZip(file, entries, directoryOffset);
    }
    constructor(file, entries, directoryOffset) { this.file=file; this.entries=entries; this.directoryOffset=directoryOffset; }
    async read(name, expectedBytes) {
      const entry = this.entries.get(name);
      ensure(entry, '资料包中缺少：' + name);
      ensure(entry.bytes === expectedBytes && expectedBytes <= MAX_ENTRY_BYTES && entry.packed <= MAX_ENTRY_BYTES, '文件大小与审阅版本不符：' + name);
      ensure(!(entry.flags & 1) && (entry.method === 0 || entry.method === 8), '不支持加密或此种压缩方式：' + name);
      ensure(entry.offset + 30 <= this.directoryOffset, 'ZIP 文件位置越界：' + name);
      const local = new DataView(await this.file.slice(entry.offset, entry.offset + 30).arrayBuffer());
      ensure(local.getUint32(0, true) === 0x04034b50 && local.getUint16(8, true) === entry.method && local.getUint16(6, true) === entry.flags, 'ZIP 文件头不一致：' + name);
      const nameLength = local.getUint16(26, true), start = entry.offset + 30 + nameLength + local.getUint16(28, true);
      ensure(start + entry.packed <= this.directoryOffset, 'ZIP 文件内容不完整：' + name);
      const localName = decoder.decode(await this.file.slice(entry.offset + 30, entry.offset + 30 + nameLength).arrayBuffer());
      ensure(localName === name, 'ZIP 本地文件名不一致：' + name);
      const packed = this.file.slice(start, start + entry.packed);
      let bytes;
      if (entry.method === 0) {
        ensure(entry.packed === expectedBytes, 'ZIP 未压缩文件长度不一致：' + name);
        bytes = new Uint8Array(await packed.arrayBuffer());
      } else {
        ensure(typeof DecompressionStream !== 'undefined', '此浏览器不支持 ZIP 解压，请更新浏览器，或使用“仅存储/不压缩”的 ZIP。');
        let stream;
        try { stream = new DecompressionStream('deflate-raw'); }
        catch (_) { throw new Error('此浏览器不支持 ZIP 解压，请更新浏览器，或使用“仅存储/不压缩”的 ZIP。'); }
        const reader = packed.stream().pipeThrough(stream).getReader();
        bytes = new Uint8Array(expectedBytes);
        let offset = 0;
        try {
          for (;;) {
            const {done, value} = await reader.read();
            if (done) break;
            ensure(offset + value.length <= expectedBytes, 'ZIP 解压长度超过审阅文件大小：' + name);
            bytes.set(value, offset); offset += value.length;
          }
          ensure(offset === expectedBytes, 'ZIP 解压文件不完整：' + name);
        } finally { await reader.cancel().catch(() => {}); reader.releaseLock(); }
      }
      ensure(crc32(bytes) === entry.crc, 'ZIP 文件校验失败：' + name);
      return bytes;
    }
  }
  class MobileReviewAssets {
    constructor(manifestText, manifestSha256, statusElement, document, transportVariants={}) {
      this.manifestText=manifestText; this.manifestSha256=manifestSha256; this.statusElement=statusElement; this.document=document;
      const manifest = JSON.parse(manifestText);
      this.expected = new Map(manifest.files.map(f => [f.path, f]));
      this.expected.set('bundle-manifest.json', {bytes:new TextEncoder().encode(manifestText).length, sha256:manifestSha256});
      this.transportVariants=transportVariants;
      this.bindings=new Map(); this.urls=new Map(); this.tokens=new WeakMap(); this.queue=Promise.resolve(); this.generation=0;
    }
    status(message, failed=false) { this.statusElement.textContent=message; this.statusElement.style.color=failed?'#a32020':''; }
    clearImage(element) {
      element.hidden=true; element.removeAttribute('src');
      if (this.urls.has(element)) { URL.revokeObjectURL(this.urls.get(element)); this.urls.delete(element); }
    }
    async open(file) {
      const generation=++this.generation;
      this.archive=null;
      for (const element of this.bindings.keys()) { this.tokens.set(element, {}); this.clearImage(element); }
      const detail=this.document.getElementById('assetDetail');
      detail.hidden=true; this.document.getElementById('assetText').textContent='';
      this.status('正在检查资料包版本与文件目录……');
      const archive=await ReviewZip.open(file), manifestInfo=this.expected.get('bundle-manifest.json');
      const candidates=[...archive.entries.values()].filter(e => (e.name==='bundle-manifest.json'||e.name.endsWith('/bundle-manifest.json')) && e.bytes===manifestInfo.bytes);
      let prefix=null;
      for (const candidate of candidates) {
        const bytes=await archive.read(candidate.name, manifestInfo.bytes);
        if (await sha256(bytes) === this.manifestSha256) {
          ensure(prefix===null, 'ZIP 中有多份相同审阅包，请只保留一份。');
          prefix=candidate.name.slice(0, -'bundle-manifest.json'.length);
        }
      }
      ensure(prefix!==null, '没有找到对应的 gt-all-scenes-v2 资料，请选择完整审阅目录 ZIP 或包含它的仓库 ZIP。');
      for (const [name, info] of this.expected) {
        const entry=archive.entries.get(prefix+name);
        const variant=this.transportVariants[name];
        ensure(entry && (entry.bytes===info.bytes || (variant && entry.bytes===variant.bytes)), '资料包文件缺失或大小不符：'+name);
        ensure(!(entry.flags&1) && (entry.method===0||entry.method===8), '资料包压缩方式不受支持：'+name);
      }
      if (generation!==this.generation) return;
      this.archive=archive; this.prefix=prefix;
      this.status('资料包已连接。图片和附件在打开时逐个核对原审阅清单 SHA-256；现在可以切换场景和帧。');
      for (const [element, name] of this.bindings) this.image(element, name);
    }
    async read(name) {
      ensure(this.archive, '请先选择已下载的资料 ZIP。');
      const info=this.expected.get(name);
      ensure(info, '文件未列在原审阅清单中：'+name);
      const entry=this.archive.entries.get(this.prefix+name),variant=this.transportVariants[name];
      let bytes;
      if (entry && variant && entry.bytes===variant.bytes && variant.bytes!==info.bytes) {
        const transported=await this.archive.read(this.prefix+name, variant.bytes);
        ensure(await sha256(transported)===variant.sha256, 'Git 文本文件 SHA-256 与已核实的下载版本不符：'+name);
        let line=0; const ordinals=variant.crlfOrdinals==='all'?null:new Set(variant.crlfOrdinals);
        bytes=new TextEncoder().encode(decoder.decode(transported).replace(/\n/g,()=>{const restore=ordinals===null||ordinals.has(line);line++;return restore?'\r\n':'\n';}));
      } else bytes=await this.archive.read(this.prefix+name, info.bytes);
      ensure(await sha256(bytes)===info.sha256, '文件 SHA-256 与原审阅版本不符：'+name);
      return bytes;
    }
    image(element, name) {
      this.bindings.set(element, name);
      const token={}; this.tokens.set(element, token); this.clearImage(element);
      if (!this.archive) return;
      const generation=this.generation;
      this.queue=this.queue.then(async () => {
        if (generation!==this.generation || this.tokens.get(element)!==token) return;
        const bytes=await this.read(name);
        if (generation!==this.generation || this.tokens.get(element)!==token) return;
        const url=URL.createObjectURL(new Blob([bytes], {type:'image/png'}));
        this.urls.set(element, url);
        element.onerror=()=>{ if (this.tokens.get(element)===token) { this.clearImage(element); this.status('图片未能显示：'+name, true); } };
        element.src=url; element.hidden=false;
      }).catch(error => { if (generation===this.generation && this.tokens.get(element)===token) this.status(error.message, true); });
    }
    link(element, name) {
      element.href='#assetDetail'; element.reviewPath=name;
      element.onclick=event => { event.preventDefault(); this.view(name).catch(error=>this.status(error.message,true)); };
    }
    async view(name) {
      const generation=this.generation, token={}; this.detailToken=token;
      const detail=this.document.getElementById('assetDetail'), image=this.document.getElementById('assetImage');
      this.bindings.delete(image); this.tokens.set(image, {}); this.clearImage(image);
      detail.hidden=true; this.document.getElementById('assetText').textContent='';
      const bytes=await this.read(name);
      if (generation!==this.generation || this.detailToken!==token) return;
      this.document.getElementById('assetName').textContent=name;
      if (name.endsWith('.png')) { this.bindings.set(image,name); this.image(image,name); await this.queue; }
      else this.document.getElementById('assetText').textContent=decoder.decode(bytes);
      if (generation!==this.generation || this.detailToken!==token) return;
      detail.hidden=false; if (detail.scrollIntoView) detail.scrollIntoView({block:'start'});
    }
    dispose() { ++this.generation; this.archive=null; for (const element of this.urls.keys()) this.clearImage(element); }
  }
  scope.MobileReviewAssets=MobileReviewAssets;
  if (typeof module!=='undefined' && module.exports) module.exports={ReviewZip,MobileReviewAssets,crc32,sha256Fallback,sha256};
})(globalThis);
