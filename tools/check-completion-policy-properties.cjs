'use strict';
const assert=require('assert/strict'),fs=require('fs'),crypto=require('crypto');
const {createPolicy}=require('./completion-trigger-policy.cjs');
const [out]=process.argv.slice(2);assert(out&&!fs.existsSync(out));
const o=(pts,text='あ',kind='message_loading',extra={})=>({pts,timeBase:'1/1000',kind,regions:[{role:'dialogue',readingOrder:0,text,gridRect:[1,14,2,15],markCells:[]}],menuCursors:[],detachedMarks:[],...extra});
const cases=[];function test(name,fn){fn();cases.push(name);}
test('No early output before threshold; no duplicate emission on stable plateau',()=>{const p=createPolicy(100);assert(!p.observe(o(0)).ready);assert(!p.observe(o(99)).ready);assert(p.observe(o(100)).fire);assert(!p.observe(o(101)).fire);});
test('Prefix growth and equal-length replacement restart waiting',()=>{const p=createPolicy(100);p.observe(o(0));assert(!p.observe(o(80,'あい')).ready);assert(!p.observe(o(150,'うえ')).ready);assert(!p.observe(o(249,'うえ')).ready);assert(p.observe(o(250,'うえ')).ready);});
test('Line motion, dakuten, cursor and detached-mark changes restart waiting',()=>{for(const change of [x=>x.regions[0].gridRect=[1,15,2,16],x=>x.regions[0].markCells=[{gridColumn:1,gridRow:13,kind:'dakuten'}],x=>x.menuCursors=[{style:'filled_right',gridRect:[1,1,2,2]}],x=>x.detachedMarks=[{kind:'dakuten',gridRect:[1,1,2,2]}]]){const p=createPolicy(100);p.observe(o(0));const x=o(100);change(x);assert(!p.observe(x).ready);}});
test('SourceRect noise and blinking arrows do not reset text timer',()=>{const p=createPolicy(100);let a=o(0);a.regions[0].sourceRect=[0,0,10,10];p.observe(a);let b=o(100);b.regions[0].sourceRect=[1,0,11,10];b.downArrowVisible=true;assert(p.observe(b).ready);});
test('Existing arrow/menu/status completion remains immediate in both rules',()=>{for(const ms of [null,100])for(const kind of ['message_complete','menu_complete','status_page_complete'])assert(createPolicy(ms).observe(o(0,'あ',kind)).ready);});
test('Scrolling, clear, empty and unknown text do not gain stable readiness',()=>{for(const kind of ['scrolling','message_clearing','textbox_empty','submenu_opening']){const p=createPolicy(100);p.observe(o(0,'あ',kind));assert(!p.observe(o(1000,'あ',kind)).ready);}const p=createPolicy(100);p.observe(o(0,'\ufffd'));assert(!p.observe(o(1000,'\ufffd')).ready);});
test('Clock reversal and time-base changes discard history',()=>{const p=createPolicy(100);p.observe(o(100));assert(!p.observe(o(0)).ready);assert(!p.observe(o(100,'あ','message_loading',{timeBase:'1/30'})).ready);});
test('A one-frame short page is never flushed after disappearance',()=>{const p=createPolicy(100);assert(!p.observe(o(0)).ready);assert(!p.observe(o(42,'','textbox_empty')).ready);assert(!p.observe(o(300,'','textbox_empty')).ready);});
test('Zero ms still requires a second actual observation',()=>{const p=createPolicy(0);assert(!p.observe(o(0)).ready);assert(p.observe(o(1)).ready);});
test('Causal prefix invariance under different future suffixes',()=>{const prefix=[o(0),o(40,'あい'),o(100,'あい'),o(140,'あい')];const run=xs=>{const p=createPolicy(100);return xs.map(x=>p.observe(x));};assert.deepEqual(run([...prefix,o(200,'あいう')]).slice(0,4),run([...prefix,o(200,'さし')]).slice(0,4));});
const sha=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const result={status:'policy_contract_properties_passed_synthetic_not_video_acceptance',cases,policySha256:sha('tools/completion-trigger-policy.cjs'),checkerSha256:sha(__filename),productAcceptance:false};
fs.writeFileSync(out,JSON.stringify(result,null,2)+'\n',{flag:'wx'});console.log(JSON.stringify(result));
