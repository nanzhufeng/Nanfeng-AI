import test from 'node:test';
import assert from 'node:assert/strict';
import { recordDomainConflict, renderDomainConflict, reloadDomainEditor } from '../src/domain-editor-conflict.mjs';
function harness() {
  const item={id:'k',revision:1,title:'old',body:'old',status:'ACTIVE'};
  const dialog={kind:'knowledge',item,draft:{'knowledge-body':'unsaved'}};
  const owner={current:{summary:{id:'w'}},dialog};let state=owner,resolve;let renders=0,calls=0;
  const latest={summary:{id:'w'},exchange:{knowledge:[{...item,revision:2,title:'latest',body:'saved elsewhere'}]}};
  recordDomainConflict(dialog,{entity:'knowledge',objectId:'k'},latest);
  return {owner,dialog,latest,setState:v=>state=v,resolve:()=>resolve(latest),options:{getState:()=>state,render:()=>renders++,invoke:()=>{calls++;return new Promise(yes=>resolve=yes);}},get renders(){return renders},get calls(){return calls}};
}
test('conflict records latest saved facts without replacing unsaved text',()=>{
  const h=harness();assert.equal(h.dialog.item.revision,1);assert.equal(h.dialog.draft['knowledge-body'],'unsaved');assert.equal(h.dialog.conflict.latestItem.revision,2);
  const html=renderDomainConflict(h.dialog,String);assert.ok(html.includes('替换本次草稿'));assert.ok(html.includes('saved elsewhere'));
});
test('explicit reload adopts a freshly read active revision without writing',async()=>{
  const h=harness();const pending=reloadDomainEditor(h.options);await reloadDomainEditor(h.options);assert.equal(h.calls,1);h.resolve();await pending;
  assert.equal(h.dialog.item.revision,2);assert.deepEqual(h.dialog.draft,{});assert.equal(h.dialog.conflict,null);
});
for(const boundary of ['area','workspace','dialog','draft'])test(`late reload preserves ${boundary} boundary`,async()=>{
  const h=harness();const pending=reloadDomainEditor(h.options);
  if(boundary==='area')h.setState({dialog:{kind:'other'}});
  if(boundary==='workspace')h.owner.current.summary.id='other';
  if(boundary==='dialog')h.owner.dialog={kind:'other'};
  if(boundary==='draft')h.dialog.draft['knowledge-body']='newer edit';
  h.resolve();await pending;assert.equal(h.dialog.item.revision,1);assert.ok(h.dialog.conflict);assert.equal(h.dialog.draft['knowledge-body'],boundary==='draft'?'newer edit':'unsaved');
});
test('deleted saved record cannot be reloaded or revived by editor recovery',async()=>{
  const h=harness();h.latest.exchange.knowledge[0].status='DELETED';const pending=reloadDomainEditor(h.options);h.resolve();await pending;
  assert.equal(h.dialog.item.revision,1);assert.equal(h.dialog.draft['knowledge-body'],'unsaved');assert.ok(h.dialog.conflictError);
});
