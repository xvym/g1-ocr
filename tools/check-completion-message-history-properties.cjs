'use strict';
const fs=require('fs'),assert=require('assert/strict'),crypto=require('crypto');
const {createMessageHistory:createMenuGate}=require('./completion-message-history-policy.cjs');
const [out]=process.argv.slice(2);assert(out&&!fs.existsSync(out));
function obs(pts,text='あい',role='dialogue_line',kind='menu_complete'){
  return {pts,timeBase:'1/1000',kind,regions:text?[{role,readingOrder:4,text,gridRect:[1,14,3,15],markCells:[]}]:[],
    menuCursors:[{style:'filled_right',gridRect:[1,4,2,5]}],detachedMarks:[],
    boxes:[{left:0,top:12,right:20,bottom:18,lines:[],symbols:[]}]};
}
const hollow=o=>{o.menuCursors[0].style='outline_right';return o;};
const arrow=o=>{o.boxes[0].symbols=[{column:18,row:16,kind:'down_arrow'}];return o;};
const tests=[];function test(name,fn){fn();tests.push(name);}
test('A filled parent selection cannot certify a growing new message',()=>{const p=createMenuGate();for(const [t,text]of [[0,'あ'],[40,'あい'],[80,'あいう']])assert(!p.observe(obs(t,text)).ready);assert(p.observe(arrow(obs(90,'あいうえ'))).ready);});
test('Menu-only pages keep immediate v18 readiness',()=>{const o=obs(0);o.regions=[{role:'item_name',readingOrder:0,text:'あ',gridRect:[6,4,7,5]}];o.boxes=[];assert(createMenuGate().observe(o).ready);});
test('Independent message100ms confirmation and no repeated plateau emission',()=>{const p=createMenuGate();assert(!p.observe(obs(0)).ready);assert(!p.observe(obs(99)).ready);assert(p.observe(obs(100)).fire);assert(!p.observe(obs(101)).fire);});
test('Suspended PC parent stays blocked; current active child releases gate',()=>{const p=createMenuGate();p.observe(hollow(obs(0,'あい','pc_prompt')));assert(!p.observe(hollow(obs(1000,'あい','pc_prompt'))).ready);const o=hollow(obs(1001,'あい','pc_prompt'));o.menuCursors.push({style:'filled_right',gridRect:[5,4,6,5]});assert(p.observe(o).ready);});
test('A main-message arrow releases PC suspension, an unrelated list arrow does not',()=>{const p=createMenuGate();p.observe(hollow(obs(0,'あい','pc_prompt')));const o=hollow(obs(1000,'あい','pc_prompt'));o.boxes.push({left:4,top:2,right:20,bottom:13,symbols:[{kind:'down_arrow',row:11,column:18}]});assert(!p.observe(o).ready);assert(p.observe(arrow(hollow(obs(1001,'あい','pc_prompt')))).ready);});
test('Merchant hollow menu is a legitimate counterexample to a blanket gate',()=>{for(const [mode,expected]of [['pc',true]]){const p=createMenuGate(mode);p.observe(hollow(obs(0,'あい','dialogue_line','message_loading')));assert.equal(p.observe(hollow(obs(100,'あい','dialogue_line','message_loading'))).ready,expected);}});
test('Equal-length replacement, marks and message position changes revoke confirmation',()=>{for(const change of [o=>o.regions[0].text='うえ',o=>o.regions[0].markCells=[{gridColumn:1,gridRow:13,kind:'dakuten'}],o=>o.regions[0].gridRect=[1,16,3,17]]){const p=createMenuGate();p.observe(obs(0));p.observe(obs(100));const o=obs(101);change(o);assert(!p.observe(o).ready);}});
test('Changing only menu selection does not restart unchanged message clock',()=>{const p=createMenuGate();p.observe(obs(0));const o=obs(100);o.menuCursors[0].gridRect=[1,6,2,7];assert(p.observe(o).ready);});
test('Unknown glyphs never become confirmed merely by waiting',()=>{const p=createMenuGate();p.observe(obs(0,'\ufffd'));assert(!p.observe(obs(500,'\ufffd')).ready);});
test('Clear/scroll/partial drawing and clip-end silence never publish',()=>{for(const kind of ['textbox_empty','scrolling','message_clearing','submenu_partial_redraw','submenu_cursor_loading']){const p=createMenuGate();p.observe(obs(0));p.observe(obs(100));assert(!p.observe(obs(1000,'あい','dialogue_line',kind)).ready);}const p=createMenuGate();assert(!p.observe(obs(0)).ready);assert(!p.observe(obs(200,'','dialogue_line','textbox_empty')).ready);});
test('Reversed or changed clocks discard a previous confirmation',()=>{const p=createMenuGate();p.observe(obs(100));p.observe(obs(200));assert(!p.observe(obs(0)).ready);const o=obs(1000);o.timeBase='1/30';assert(!p.observe(o).ready);});
test('One-frame opportunity is not invented or flushed after disappearance',()=>{const p=createMenuGate();assert(!p.observe(obs(0)).ready);assert(!p.observe(obs(42,'','dialogue_line','textbox_empty')).ready);});
test('Different future suffixes and extraneous GT/identity properties do not affect prefix',()=>{const run=xs=>{const p=createMenuGate();return xs.map(o=>p.observe(o));};const a=obs(0),b=obs(100);assert.deepEqual(run([a,b,obs(101,'うえ')]).slice(0,2),run([{...a,gt:'ignore',clip:'ignore',sourceFrameIndex:999},b,obs(101,'おか')]).slice(0,2));});
test('Confirmed unchanged messages survive menu-only transitions but cannot publish there',()=>{for(const k of JSON.parse(fs.readFileSync('tools/completion-message-history-protocol.json')).holdKinds){const p=createMenuGate();p.observe(obs(0));assert(p.observe(obs(100)).ready);assert(!p.observe(obs(101,'あい','dialogue_line',k)).ready);assert(p.observe(obs(102)).ready);}});
test('Unsafe transitions never accumulate a fresh message confirmation',()=>{const p=createMenuGate();p.observe(obs(0,'あい','dialogue_line','submenu_cursor_loading'));p.observe(obs(1000,'あい','dialogue_line','submenu_cursor_loading'));assert(!p.observe(obs(1001)).ready);});
test('Message clearing and scrolling revoke existing completion even with identical text',()=>{for(const k of ['scrolling','message_clearing','textbox_empty','unrecognized_state']){const p=createMenuGate();p.observe(obs(0));p.observe(obs(100));p.observe(obs(101,'あい','dialogue_line',k));assert(!p.observe(obs(102)).ready);}});
test('Only unrelated menu fields may change global reading order',()=>{const p=createMenuGate();p.observe(obs(0));p.observe(obs(100));const o=obs(101);o.regions[0].readingOrder=20;assert(p.observe(o).ready);});
test('Loose message-area marks revoke confirmation',()=>{const p=createMenuGate();p.observe(obs(0));p.observe(obs(100));const o=obs(101);o.detachedMarks=[{kind:'dakuten',gridRect:[1,13,2,14]}];assert(!p.observe(o).ready);});
const sha=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const result={status:'menu_message_gate_properties_passed_synthetic_not_acceptance',cases:tests,policySha256:sha('tools/completion-message-history-policy.cjs'),checkerSha256:sha(__filename),productAcceptance:false};
fs.writeFileSync(out,JSON.stringify(result,null,2)+'\n',{flag:'wx'});console.log(JSON.stringify(result));
