import test from 'node:test';
import assert from 'node:assert/strict';
import { preserveSettingsPickerFocus } from '../src/modal-layer-owner.mjs';

function fixture(key='memoryEnabled') {
  const root={body:{isConnected:true},controls:[],querySelectorAll(){return this.controls;}};
  const control=(key,extra={})=>({isConnected:true,disabled:false,dataset:{key},tagName:'BUTTON',
    matches:selector=>selector.includes('toggle-product-setting'),
    focus(options){root.activeElement=this;this.options=options;},
    click(){throw new Error('Focus restoration must never toggle or save');},...extra});
  const original=control(key);original.focus();
  const replacement=control(key);root.controls=[control('unrelated'),replacement];
  const repaint=()=>{original.isConnected=false;root.activeElement=root.body;};
  return {root,original,replacement,repaint,control};
}

for(const key of ['memoryEnabled','reminderSuggestions','unreadIndicators']) {
  test(`${key}: native receipt repaint keeps the exact switch without triggering it`,async()=>{
    const f=fixture(key);preserveSettingsPickerFocus(f.root);f.repaint();await Promise.resolve();
    assert.equal(f.root.activeElement,f.replacement);assert.deepEqual(f.replacement.options,{preventScroll:true});
  });
}
test('switch restoration refuses stale owners, missing or disabled controls, and an existing new focus',async()=>{
  for(const reason of ['stale','missing','disabled','moved']) {
    const f=fixture();let current=true;preserveSettingsPickerFocus(f.root,()=>current);f.repaint();
    if(reason==='stale')current=false;if(reason==='missing')f.root.controls=[];if(reason==='disabled')f.replacement.disabled=true;
    const other={isConnected:true};if(reason==='moved')f.root.activeElement=other;
    await Promise.resolve();assert.equal(f.root.activeElement,reason==='moved'?other:f.root.body,reason);
  }
});
test('consecutive same-page switch repaints retain focus without crossing setting keys',async()=>{
  const f=fixture();preserveSettingsPickerFocus(f.root);f.repaint();await Promise.resolve();
  assert.equal(f.root.activeElement,f.replacement);preserveSettingsPickerFocus(f.root);
  f.replacement.isConnected=false;const next=f.control('memoryEnabled');f.root.controls=[f.control('unreadIndicators'),next];f.root.activeElement=f.root.body;
  await Promise.resolve();assert.equal(f.root.activeElement,next);
});
