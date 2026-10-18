'use strict';
const { createPolicy, signature } = require('./completion-trigger-policy.cjs');
const assert = require('assert/strict');
const MESSAGE_ROLES = new Set(['dialogue','dialogue_line','battle_message','pc_prompt','shop_prompt','shop_message','party_prompt']);
const ELIGIBLE = new Set(['message_loading','message_complete','message_fading','menu_complete','status_page_complete']);
const HOLD = new Set(['submenu_partial_redraw','submenu_cursor_loading','scroll_partial_redraw','scroll_duplicate_row','scroll_cursor_transition','cursor_transition','menu_cursor_loading','money_temporarily_cleared','menu_partial_redraw']);
function createMessageHistory() {
  const mode='pc';
  assert(['message','blanket','pc'].includes(mode));
  const messageClock = createPolicy(100);
  let priorMessage=null, confirmed=false, previousWhole=null, emitted=false, lastPts=null, unit=null;
  return { observe(o) {
    const pts=BigInt(o.pts), clockReset=unit!==o.timeBase||lastPts!==null&&pts<=lastPts;
    if(clockReset){priorMessage=null;confirmed=false;previousWhole=null;emitted=false;}
    unit=o.timeBase;lastPts=pts;
    const main=o.boxes.find(b=>b.left===0&&b.top===12&&b.right===20&&b.bottom===18);
    const message=o.regions.filter(r=>MESSAGE_ROLES.has(r.role)&&r.gridRect[1]>=13&&r.gridRect[3]<=17);
    const hasMessage=!!main&&message.some(r=>r.text.length),hasText=o.regions.some(r=>r.text.length);
    const key=signature(o), changed=key!==previousWhole, eligible=hasText&&ELIGIBLE.has(o.kind);
    const messageInput={pts:o.pts,timeBase:o.timeBase,regions:hasMessage?message.map((r,readingOrder)=>({...r,readingOrder})):[],menuCursors:[],detachedMarks:hasMessage?o.detachedMarks.filter(s=>s.gridRect[0]>=1&&s.gridRect[2]<=19&&s.gridRect[1]>=13&&s.gridRect[3]<=17):[],
      kind:hasMessage&&eligible?'message_loading':'textbox_empty'};
    const messageKey=signature(messageInput),m=messageClock.observe(messageInput);
    if(messageKey!==priorMessage||(!eligible&&!HOLD.has(o.kind))||!hasMessage)confirmed=false;
    priorMessage=messageKey;
    const mainArrow=hasMessage&&main.symbols.some(s=>s.kind==='down_arrow');
    const genuineMessageComplete=hasMessage&&['message_complete','message_fading'].includes(o.kind);
    if(hasMessage&&eligible&&(mainArrow||genuineMessageComplete||m.ready))confirmed=true;
    const parentHollow=o.menuCursors.some(s=>s.style==='outline_right'&&s.gridRect[0]===1);
    const activeChild=o.menuCursors.some(s=>s.style==='filled_right'&&s.gridRect[0]>1);
    const onlyHollow=o.menuCursors.length>0&&o.menuCursors.every(s=>s.style==='outline_right');
    const suspendedPc=hasMessage&&message.some(r=>r.role==='pc_prompt')&&parentHollow&&!activeChild;
    const blocked=hasMessage&&!mainArrow&&(mode==='blanket'?onlyHollow:mode==='pc'?suspendedPc:false);
    const menuReady=o.kind==='menu_complete',messageReady=hasMessage&&eligible&&confirmed&&!blocked;
    const ready=eligible&&(hasMessage?messageReady:['message_complete','message_fading','menu_complete','status_page_complete'].includes(o.kind));
    const invalidate=changed||!eligible||blocked;
    if(invalidate)emitted=false;
    const fire=ready&&!emitted;if(fire)emitted=true;previousWhole=key;
    return {ready,fire,reason:blocked?'suspended_parent_without_active_child':ready?hasMessage?mainArrow||genuineMessageComplete?'message_arrow_or_existing_complete':'message_confirmed_separately':'v18_menu_or_status_complete':'not_ready',
      signature:key,invalidate,menuReady,messageReady,hasMessage,mainArrow,parentHollow,activeChild,onlyHollow,suspendedPc,
      stableSincePts:m.stableSincePts,samples:m.samples,stableAgeMs:m.stableAgeMs,messageConfirmed:confirmed,messageSignature:messageKey};
  }};
}
module.exports={createMessageHistory};
