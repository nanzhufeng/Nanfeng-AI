import test from 'node:test';
import assert from 'node:assert/strict';
import { installModalLayerOwner, installActionMenuLayerOwner } from '../src/modal-layer-owner.mjs';
function fixture() {
 const old={window:globalThis.window,document:globalThis.document,MutationObserver:globalThis.MutationObserver,innerWidth:globalThis.innerWidth,innerHeight:globalThis.innerHeight};
 const docEvents=new Map(),rootEvents=new Map(),windowEvents=new Map();let closes=0;
 const add=(map,name,handler)=>{if(!map.has(name))map.set(name,[]);map.get(name).push(handler);};
 const doc={activeElement:null,addEventListener:(name,fn)=>add(docEvents,name,fn)};
 const summary={getBoundingClientRect:()=>({right:480,bottom:60,top:20}),closest:()=>null,focus(){doc.activeElement=this;}};
 const panel={querySelectorAll:()=>items,contains:target=>target===panel||items.includes(target),style:{},getBoundingClientRect:()=>({width:220,height:160}),setAttribute(){},removeAttribute(){},popover:true,showPopover(){this.popover=true;},matches:()=>panel.popover,hidePopover(){this.popover=false;}};
 const menu={offsetWidth:44,offsetHeight:40,getBoundingClientRect:()=>({left:436,top:20,width:44,height:40}),open:true,isConnected:true,matches:selector=>selector.includes('.text-preview-menu'),contains:target=>target===summary||target===menu||items.includes(target),querySelector:selector=>selector==='summary'?summary:selector===':popover-open'?(panel.popover?panel:null):panel,querySelectorAll:()=>items.filter(item=>!item.disabled)};
 const items=[0,1,2].map((_,index)=>{const value={tabIndex:0,getClientRects:()=>[{}],disabled:index===1,focus(){doc.activeElement=this;}};value.closest=selector=>selector==='[inert]'?null:selector==='button'||selector==='.text-preview-menu-item'?value:menu;return value;});
 summary.closest=selector=>selector.includes('.text-preview-menu')?menu:null;
 const outside={};
 const close={click(){closes++;}};
 const layer={children:[],contains:target=>target===layer||target===outside||target===menu||target===summary||items.includes(target),matches:selector=>selector.split(',').some(value=>value.trim()==='.attachment-preview-overlay'),querySelector:selector=>selector==='.text-preview-menu[open]'?(menu.open?menu:null):close,querySelectorAll:()=>[]};
 const root={ownerDocument:doc,children:[layer],addEventListener:(name,fn)=>add(rootEvents,name,fn),querySelectorAll:selector=>selector.includes('.scrim')?[layer]:menu.open?[menu]:[]};
 globalThis.innerWidth=800;globalThis.innerHeight=600;globalThis.document=doc;globalThis.window={addEventListener:(name,fn)=>add(windowEvents,name,fn)};globalThis.MutationObserver=class{observe(){}};doc.activeElement=summary;
 installModalLayerOwner(root);installActionMenuLayerOwner(root);
 function emit(map,name,extra={}) {
  const event={target:summary,key:'',pointerId:1,detail:1,prevented:false,stopped:false,preventDefault(){this.prevented=true;},stopImmediatePropagation(){this.stopped=true;},...extra};
  for(const fn of map.get(name)||[]){fn(event);if(event.stopped)break;}return event;
 }
 return {menu,panel,summary,items,doc,outside,layer,get closes(){return closes;},docEvent:(name,args)=>emit(docEvents,name,args),rootEvent:(name,args)=>emit(rootEvents,name,args),windowEvent:(name,args)=>emit(windowEvents,name,args),restore(){Object.assign(globalThis,old);}};
}
test('first Escape closes only the reader menu; the next closes the containing preview',()=>{
 const f=fixture();try{const first=f.docEvent('keydown',{key:'Escape'});assert.equal(first.stopped,true);assert.equal(f.menu.open,false);assert.equal(f.closes,0);assert.equal(f.doc.activeElement,f.summary);f.docEvent('keydown',{key:'Escape'});assert.equal(f.closes,1);}finally{f.restore();}
});
test('outside dismissal consumes the full pointer gesture instead of activating reader content',()=>{
 const f=fixture();try{assert.equal(f.docEvent('pointerdown',{target:f.outside}).stopped,true);assert.equal(f.menu.open,false);assert.equal(f.docEvent('pointerup',{target:f.outside}).stopped,true);assert.equal(f.docEvent('click',{target:f.outside}).stopped,true);assert.equal(f.docEvent('pointerdown',{target:f.outside}).stopped,false);assert.equal(f.docEvent('click',{target:f.outside}).stopped,false);}finally{f.restore();}
});

test('reader menu owns a backdrop gesture before the containing preview can close',()=>{
 const f=fixture();try{for(const type of ['pointerdown','pointerup','click'])assert.equal(f.docEvent(type,{target:f.layer,button:0}).stopped,true,type);assert.equal(f.menu.open,false);assert.equal(f.closes,0);}finally{f.restore();}
});
test('reader arrows/Home/End skip disabled actions and Tab closes to its trigger',()=>{
 const f=fixture();try{f.rootEvent('keydown',{key:'ArrowDown'});assert.equal(f.doc.activeElement,f.items[0]);f.rootEvent('keydown',{key:'ArrowDown',target:f.items[0]});assert.equal(f.doc.activeElement,f.items[2]);f.rootEvent('keydown',{key:'Home',target:f.items[2]});assert.equal(f.doc.activeElement,f.items[0]);f.rootEvent('keydown',{key:'End',target:f.items[0]});assert.equal(f.doc.activeElement,f.items[2]);f.rootEvent('keydown',{key:'Tab',target:f.items[2]});assert.equal(f.menu.open,false);assert.equal(f.doc.activeElement,f.summary);}finally{f.restore();}
});
test('choosing a menu action closes its presentation without consuming that action',()=>{
 const f=fixture();try{const event=f.rootEvent('click',{target:f.items[0]});assert.equal(f.menu.open,false);assert.equal(f.panel.popover,false);assert.equal(event.stopped,false);}finally{f.restore();}
});
test('losing window activation closes the reader menu without requesting focus',()=>{
 const f=fixture();try{f.doc.activeElement=f.items[0];f.windowEvent('blur');assert.equal(f.menu.open,false);assert.equal(f.panel.popover,false);assert.equal(f.doc.activeElement,f.items[0]);assert.equal(f.closes,0);}finally{f.restore();}
});

test('reader content scrolling dismisses its action menu before a subsequent explicit opening',()=>{
 const f=fixture();try{f.rootEvent('toggle',{target:f.menu});f.docEvent('scroll',{target:f.outside});assert.equal(f.menu.open,false);assert.equal(f.closes,0);f.menu.open=true;f.rootEvent('toggle',{target:f.menu});assert.equal(f.menu.open,true);}finally{f.restore();}
});

test('older WebView without popover API opens fixed menu without unsupported selectors',()=>{
 const f=fixture();try{delete f.panel.showPopover;f.panel.matches=()=>{throw new Error('unsupported selector');};const query=f.menu.querySelector;f.menu.querySelector=selector=>{if(selector.includes(':popover-open'))throw new Error('unsupported selector');return query(selector);};assert.doesNotThrow(()=>f.rootEvent('toggle',{target:f.menu}));assert.equal(f.panel.style.position,'absolute');assert.equal(f.panel.style.left,'-176px');f.docEvent('keydown',{key:'Escape'});assert.equal(f.menu.open,false);}finally{f.restore();}
});

test('capable WebView uses manual popover and closes it before leaving the reader',()=>{
 const f=fixture();try{f.panel.popover=false;f.rootEvent('toggle',{target:f.menu});assert.equal(f.panel.popover,true);assert.equal(f.panel.style.left,'260px');f.docEvent('keydown',{key:'Escape'});assert.equal(f.panel.popover,false);assert.equal(f.closes,0);}finally{f.restore();}
});
