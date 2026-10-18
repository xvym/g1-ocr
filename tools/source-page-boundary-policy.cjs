'use strict';
// Optional source interpretation of exact current two-line messages. No time, IDs, GT or future.
function tokens(s){return s.split(/(<(?:PLAYER|RIVAL)>)/).filter(Boolean).flatMap(t=>/^</.test(t)?[{name:t}]:Array.from(t).map(char=>({char})));}
const nameChar=c=>/[\p{L}\p{N}]/u.test(c);
function match(s,t,prefix){const chars=Array.from(s),todo=[[0,0]],seen=new Set();while(todo.length){const[i,j]=todo.pop(),key=i+':'+j;if(seen.has(key))continue;seen.add(key);if(j===chars.length&&(prefix||i===t.length))return true;if(i===t.length)continue;if(t[i].char!=null){if(chars[j]===t[i].char)todo.push([i+1,j+1]);}else for(let k=1;k<=5&&j+k<=chars.length;k++){if(!nameChar(chars[j+k-1]))break;todo.push([i+1,j+k]);}}return false;}
function create(config){const ps=config.pageBoundaries.map(p=>({...p,u:tokens(p.upper),l:tokens(p.lower)}));return a=>{
 if(a.visibleState.kind!=='message_loading'||a.visibleState.cursor.menuCursors.length||a.rawRegions.length!==2)return null;
 const [u,l]=a.rawRegions;if(u.role!=='dialogue'||l.role!=='dialogue'||u.gridRect[0]!==1||l.gridRect[0]!==1||u.gridRect[1]!==14||l.gridRect[1]!==16||u.text.includes('\ufffd')||l.text.includes('\ufffd'))return null;
 const exact=ps.filter(p=>match(u.text,p.u,false)&&match(l.text,p.l,false));if(!exact.length)return null;
 const extended=ps.filter(p=>match(u.text,p.u,false)&&match(l.text,p.l,true)&&!match(l.text,p.l,false));if(extended.length)return null;
 return{kind:'message_complete',reason:'optional_pinned_source_exact_current_two_line_page_boundary',matchedSources:exact.map(({u,l,...p})=>p),scope:'supported literal source controls; raw image state/text and publication decision remain unchanged'};
 };}
module.exports={create,tokens,match};
