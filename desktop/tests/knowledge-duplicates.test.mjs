import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { readKnowledgeDuplicates, renderKnowledgeDuplicates } from '../src/knowledge-duplicates.mjs';
import { renderAndroidSettingsShell } from '../src/android-settings-shell.mjs';
function harness() {
  const dialog={kind:'knowledge',item:{id:'saved',revision:3,status:'ACTIVE'},draft:{'knowledge-body':'unsaved edit'}};
  const owner={dataArea:'WORK',current:{summary:{id:'workspace'}},dialog};
  let state=owner,resolve,reject;const calls=[];let renders=0;
  const options={getState:()=>state,render:()=>renders++,invoke:(command,args,area)=>{calls.push({command,args,area});return new Promise((yes,no)=>{resolve=yes;reject=no;});}};
  return {owner,dialog,calls,options,setState:v=>state=v,resolve:()=>resolve({anchorId:'saved',anchorRevision:3,candidates:[]}),reject:()=>reject(Error('HIGH_SENSITIVITY')),get renders(){return renders;}};
}
test('explicit saved-version query preserves the editor draft and does not mutate',async()=>{
  const h=harness();const pending=readKnowledgeDuplicates(h.options);assert.equal(h.dialog.duplicateLoading,true);await readKnowledgeDuplicates(h.options);assert.equal(h.calls.length,1);
  assert.deepEqual(h.calls[0].args,{workspaceId:'workspace',knowledgeId:'saved',expectedRevision:3});assert.equal(h.calls[0].area,h.owner);
  h.resolve();await pending;assert.equal(h.dialog.draft['knowledge-body'],'unsaved edit');assert.equal(h.dialog.duplicateLoading,false);assert.deepEqual(h.dialog.duplicateResult.candidates,[]);
});
for(const route of ['area','workspace','dialog','revision'])test(`late ${route} reply cannot project candidates into the current editor`,async()=>{
  const h=harness();const pending=readKnowledgeDuplicates(h.options);
  if(route==='area')h.setState({dataArea:'CHAT',dialog:{kind:'knowledge'}});
  if(route==='workspace')h.owner.current.summary.id='other';
  if(route==='dialog')h.owner.dialog={kind:'knowledge',item:{id:'other'}};
  if(route==='revision')h.dialog.item.revision=4;
  h.resolve();await pending;assert.equal(h.dialog.duplicateResult,null);assert.equal(h.renders,1);assert.equal(h.dialog.draft['knowledge-body'],'unsaved edit');
});
test('safe failure retains unsaved text without exposing rejection body',async()=>{
  const h=harness();const pending=readKnowledgeDuplicates(h.options);h.reject();await pending;
  assert.match(h.dialog.duplicateError,/草稿均保留/);assert.equal(h.dialog.draft['knowledge-body'],'unsaved edit');assert.equal(h.dialog.duplicateResult,null);
});
test('candidate projection is inert and shows reasons without body or private identities',()=>{
  const escape=v=>String(v).replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;');
  const html=renderKnowledgeDuplicates({item:{status:'ACTIVE'},duplicateResult:{candidates:[{knowledgeId:'private-id',title:'<script>inert</script>',revision:2,reasons:['EXACT_CONTENT','SAME_NORMALIZED_TITLE'],body:'not projected'}]}},escape);
  assert.ok(html.includes('&lt;script&gt;'));assert.ok(html.includes('规范化内容相同'));assert.ok(!html.includes('private-id'));assert.ok(!html.includes('not projected'));assert.ok(!html.includes('data-action='));
});
test('new/deleted knowledge has no query entry and feature review records the contextual proposal',async()=>{
  assert.equal(renderKnowledgeDuplicates({item:{status:'DELETED'}},String),'');
  const html=renderAndroidSettingsShell({page:'feature-review'});assert.ok(html.includes('只读重复线索'));assert.ok(html.includes('待您判断'));
  const source=await readFile(new URL('../src/app.mjs',import.meta.url),'utf8');assert.ok(source.includes('readKnowledgeDuplicates({getState:()=>state,invoke,render})'));assert.ok(source.includes('renderKnowledgeDuplicates(state.dialog, escape)'));
});
