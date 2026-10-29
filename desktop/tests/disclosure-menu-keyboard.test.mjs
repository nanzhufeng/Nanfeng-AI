import test from 'node:test';
import assert from 'node:assert/strict';
import { installActionMenuLayerOwner, installModalLayerOwner, preserveSettingsPickerFocus } from '../src/modal-layer-owner.mjs';

function fixture(kind = 'memory-reference-menu') {
  const old = Object.fromEntries(['window','document','MutationObserver','innerWidth','innerHeight','CSS'].map(k => [k, globalThis[k]]));
  const docEvents = new Map(), rootEvents = new Map(), windowEvents = new Map(); let notify, dialog = null;
  const add = (map, name, fn) => map.set(name, [...(map.get(name) || []), fn]);
  const doc = { activeElement: null, addEventListener: (name, fn) => add(docEvents,name,fn) };
  const summary = { getBoundingClientRect: () => ({right:480,bottom:60,top:20}), focus() { doc.activeElement=this; } };
  const wrongSummary = { focus() { doc.activeElement=this; } };
  const panel = { style:{}, hidden:false, setAttribute(){}, removeAttribute(){},
    getBoundingClientRect:()=>({width:220,height:160}), contains:n=>items.includes(n)||n===panel,
    querySelectorAll:()=>items };
  const menu = { open:false, isConnected:true, offsetWidth:44, offsetHeight:40,
    getBoundingClientRect:()=>({left:436,top:20,width:44,height:40}),
    matches:s=>s.includes('.'+kind), contains:n=>n===summary||n===menu||items.includes(n),
    hasAttribute:n=>kind==='conversation-lifecycle-actions'&&n==='data-lifecycle-action-disclosure',
    getAttribute:n=>n==='data-lifecycle-action-disclosure'?'conversation-b':null,
    querySelector:s=>s==='summary'?summary:panel, querySelectorAll:()=>items.filter(n=>!n.disabled) };
  const items = ['first','disabled','last'].map((id,index) => ({id,disabled:index===1,tabIndex:0,
    getClientRects:()=>[{}], closest:s=>s==='[data-action]'||s==='button[data-action]'||s==='button'?items[index]:s==='[inert]'?null:menu,
    hasAttribute:n=>n==='data-action'||n==='data-id', getAttribute:n=>n==='data-action'?'open-test-dialog':n==='data-id'?'conversation-b':null,
    focus(){doc.activeElement=this;}, scrollIntoView(o){this.scroll=o;} }));
  summary.closest=s=>s.includes('.'+kind)?menu:null;
  const root = {ownerDocument:doc,children:[menu],addEventListener:(name,fn)=>add(rootEvents,name,fn),
    querySelectorAll:s=>s.includes('.scrim')?(dialog?[dialog]:[]):menu.open?[menu]:[],
    querySelector:s=>s===`[data-lifecycle-action-disclosure="conversation-b"] > summary`?summary
      :s===`.${kind} > summary`?(kind==='conversation-lifecycle-actions'?wrongSummary:summary):null};
  globalThis.window={addEventListener:(name,fn)=>add(windowEvents,name,fn)};globalThis.document=doc;globalThis.innerWidth=800;globalThis.innerHeight=600;globalThis.CSS={escape:s=>s};
  globalThis.MutationObserver=class{constructor(cb){notify=cb;}observe(){}disconnect(){}};
  installModalLayerOwner(root);installActionMenuLayerOwner(root);doc.activeElement=summary;
  const emit=(map,type,extra={})=>{const e={target:doc.activeElement,key:'',prevented:false,stopped:false,preventDefault(){this.prevented=true;},stopImmediatePropagation(){this.stopped=true;},...extra};for(const fn of map.get(type)||[]){fn(e);if(e.stopped)break;}return e;};
  const key=(key,extra={})=>{const e=emit(docEvents,'keydown',{key,...extra});return e.stopped?e:emit(rootEvents,'keydown',{key,...extra});};
  return {doc,root,menu,summary,items,key,blur:()=>emit(windowEvents,'blur'),click:target=>emit(rootEvents,'click',{target}),
    openDialog(){const cancel={tabIndex:0,disabled:false,getClientRects:()=>[{}],closest:()=>null,focus(){doc.activeElement=this;}};dialog={matches:()=>false,children:[],contains:n=>n===cancel,querySelector:()=>null,querySelectorAll:()=>[cancel]};root.children=[menu,dialog];notify();},
    closeDialog(){dialog=null;root.children=[menu];notify();},
    restore(){Object.assign(globalThis,old);} };
}

for (const kind of ['memory-reference-menu','conversation-lifecycle-actions']) {
  test(`${kind}: keyboard opens, wraps and skips disabled actions without executing them`, () => {
    const f=fixture(kind);try{
      assert.equal(f.key('ArrowDown').prevented,true);assert.equal(f.menu.open,true);assert.equal(f.doc.activeElement,f.items[0]);
      f.key('ArrowDown');assert.equal(f.doc.activeElement,f.items[2]);f.key('ArrowDown');assert.equal(f.doc.activeElement,f.items[0]);
      f.key('ArrowUp');assert.equal(f.doc.activeElement,f.items[2]);f.key('Home');assert.equal(f.doc.activeElement,f.items[0]);f.key('End');assert.equal(f.doc.activeElement,f.items[2]);
      f.key('Tab');assert.equal(f.menu.open,false);assert.equal(f.doc.activeElement,f.summary);
    }finally{f.restore();}
  });
  test(`${kind}: Enter/Space open from the summary, repeats do not toggle twice`, () => {
    const f=fixture(kind);try{
      f.key('Enter');assert.equal(f.menu.open,true);assert.equal(f.doc.activeElement,f.items[0]);
      f.summary.focus();f.key('Enter',{repeat:true});assert.equal(f.menu.open,true);
      f.key(' ');assert.equal(f.menu.open,false);f.key(' ');assert.equal(f.menu.open,true);assert.equal(f.doc.activeElement,f.items[0]);
    }finally{f.restore();}
  });
  test(`${kind}: an action dialog returns to the exact visible summary, not its hidden action`, () => {
    const f=fixture(kind);try{
      f.menu.open=true;f.items[0].focus();f.key('Enter');f.click(f.items[0]);assert.equal(f.menu.open,false);
      f.openDialog();f.closeDialog();assert.equal(f.doc.activeElement,f.summary);
    }finally{f.restore();}
  });
}
test('composition and modified arrows retain their native meaning',()=>{
  const f=fixture();try{for(const extra of [{isComposing:true},{keyCode:229},{ctrlKey:true},{altKey:true},{metaKey:true},{shiftKey:true}]){assert.equal(f.key('ArrowDown',extra).prevented,false);assert.equal(f.menu.open,false);}}finally{f.restore();}
});

test('losing window activation closes a memory menu without requesting focus',()=>{const f=fixture();try{f.key('ArrowDown');const before=f.doc.activeElement;f.blur();assert.equal(f.menu.open,false);assert.equal(f.doc.activeElement,before);}finally{f.restore();}});

for (const kind of ['memory-reference-menu','conversation-lifecycle-actions']) {
  test(`${kind}: a same-page repaint retains the returned summary using its exact identity`, async () => {
    const f=fixture(kind);try{
      f.summary.tagName='SUMMARY';f.summary.isConnected=true;f.summary.matches=()=>false;f.summary.focus();f.doc.body={isConnected:true};
      const replacement={isConnected:true,focus(options){f.doc.activeElement=this;this.options=options;}};
      const selector=kind==='conversation-lifecycle-actions'?'[data-lifecycle-action-disclosure="conversation-b"] > summary':'.memory-reference-menu > summary';
      f.doc.querySelector=s=>s===selector?replacement:null;
      preserveSettingsPickerFocus(f.doc);f.summary.isConnected=false;f.doc.activeElement=f.doc.body;
      await Promise.resolve();assert.equal(f.doc.activeElement,replacement);assert.deepEqual(replacement.options,{preventScroll:true});
    }finally{f.restore();}
  });
}
