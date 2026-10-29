import assert from 'node:assert/strict';
import test from 'node:test';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';
const source=readFileSync(new URL('../src/app.mjs',import.meta.url),'utf8').replaceAll('\r\n','\n');
const start=source.indexOf("app.addEventListener('click', async event => {\n  const target = event.target.closest('[data-action]');\n  const action = target?.dataset.action;\n  if (state.contextMenu");
const end=source.indexOf("  if (action === 'toggle-video-playback')",start);
assert.ok(start>=0&&end>start,'mounted media handler boundary');
const listener=source.slice(start,end)+'});';
function harness() {
 const keys=['imagePreview','pdfPreview','videoPreview','audioPreview','textPreview','archivePreview','previewBoundary'];
 const state=Object.fromEntries(keys.map(key=>[key,{id:key}]));state.contextMenu=null;
 const calls=[];let click,hold;
 const close=key=>{calls.push(key);state[key]=null;};
 const context=vm.createContext({state,native:false,app:{addEventListener(type,handler){assert.equal(type,'click');click=handler;}},
  render(){calls.push('render');},cancelImagePreviewLoad(){calls.push('cancel-image');},
  cancelPdfPreviewLoad(){close('pdfPreview');},cancelArchivePreviewLoad(){close('archivePreview');},
  async closeVideoPreview(){calls.push('video-start');if(hold)await hold;close('videoPreview');},
  async closeAudioPreview(){calls.push('audio-start');if(hold)await hold;close('audioPreview');},
  navigateImagePreview(direction){calls.push(['image',direction]);},navigatePdfPage(direction){calls.push(['pdf',direction]);},
 });vm.runInContext(listener,context);
 return {state,calls,keys,hold(promise){hold=promise;},dispatch(action){let prevented=0;const target={dataset:{action},closest:()=>null};return {done:click({target:{closest:()=>target},preventDefault(){prevented++;}}),prevented:()=>prevented};}};
}
test('every shared media close button reaches its exact existing lifecycle owner',async()=>{
 for(const [kind,key,owner] of [['image','imagePreview','cancel-image'],['pdf','pdfPreview','pdfPreview'],['video','videoPreview','videoPreview'],['audio','audioPreview','audioPreview'],['text','textPreview',null],['archive','archivePreview','archivePreview'],['boundary','previewBoundary',null]]) {
  const h=harness();const action=kind==='boundary'?'close-preview-boundary':`close-${kind}-preview`;
  const event=h.dispatch(action);await event.done;assert.equal(h.state[key],null,action);assert.equal(event.prevented(),1);
  if(owner)assert.equal(h.calls.filter(call=>call===owner).length,1,action);
  for(const other of h.keys.filter(other=>other!==key))assert.deepEqual(h.state[other],{id:other},`${action} must keep ${other}`);
 }
});
test('image and PDF paging buttons share the existing scoped navigators',async()=>{
 for(const [action,expected] of [['image-preview-previous',['image','previous']],['image-preview-next',['image','next']],['pdf-page-previous',['pdf',-1]],['pdf-page-next',['pdf',1]]]) {
  const h=harness();const event=h.dispatch(action);await event.done;assert.deepEqual(h.calls,[expected]);assert.equal(event.prevented(),1);
 }
});
test('video and audio close wait for their existing persistence owner',async()=>{
 for(const kind of ['video','audio']) {
  const h=harness();let release;h.hold(new Promise(resolve=>release=resolve));let finished=false;
  const event=h.dispatch(`close-${kind}-preview`);event.done.then(()=>finished=true);await Promise.resolve();
  assert.equal(finished,false);assert.deepEqual(h.state[`${kind}Preview`],{id:`${kind}Preview`});release();await event.done;assert.equal(h.state[`${kind}Preview`],null);
 }
});
