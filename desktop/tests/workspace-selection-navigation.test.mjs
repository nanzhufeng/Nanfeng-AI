import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { workspaceUiEnabled } from '../src/chat-ui-policy.mjs';
import { readFile } from 'node:fs/promises';

const source = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');
const start = source.indexOf('let workspaceSelectionRequestId = 0;');
const block = source.slice(start, source.indexOf('\nfunction connectionCanvas()', start));
const turn = () => new Promise(resolve => setImmediate(resolve));
function harness(pane = 'chat', action = 'select-workspace') {
  let handler, resolveRead;
  const owner = { dataArea: 'CHAT', pane, settingsSection: 'personalization', current: {summary:{id:'old'}}, runtimeInfo: action === 'select-chat-source' ? {platform:'windows'} : {}, error:'' };
  const context = vm.createContext({state:owner,native:true,workspaceUiEnabled,conversationReadStateRequestId:0,p6kManualLink:null,
    app:{addEventListener(_name,fn){handler=fn;}},
    finishConversationBatchEditing(){},async flushOrdinaryComposerDraft(){},
    invoke(name){
      if(name==='read_desktop_workspace')return new Promise(resolve=>{resolveRead=resolve;});
      return Promise.resolve(name==='read_desktop_favorite_conversation_ids'?[]:null);
    },
    selectWorkspaceDefaultConversation(){owner.selectedConversationId=null;},
    resetComposerPresentation(){},applyConversationReadState(){},recomputeConversationFind(){},render(){},async loadOrdinaryComposerDraft(){},
  });
  vm.runInContext(block,context);
  return {owner,context,begin:()=>handler({target:{closest:selector=>selector.includes(`[data-action="${action}"]`)?({dataset:{id:'next'}}):null},stopImmediatePropagation(){}}),
    finish:()=>resolveRead({summary:{id:'next'}})};
}
test('workspace read applies the new workspace without leaving settings opened while it was pending',async()=>{
  const h=harness();const task=h.begin();await turn();
  h.owner.pane='settings';h.owner.settingsSection='data';
  h.finish();await task;
  assert.equal(h.owner.current.summary.id,'next');
  assert.equal(h.owner.pane,'settings');assert.equal(h.owner.settingsSection,'data');
});
test('a normal workspace selection still opens its conversation when no later navigation occurred',async()=>{
  const h=harness('settings');const task=h.begin();await turn();h.finish();await task;
  assert.equal(h.owner.current.summary.id,'next');assert.equal(h.owner.pane,'chat');
});
test('workspace read from an old area cannot replace the active area',async()=>{
  const h=harness();const task=h.begin();await turn();
  const replacement={dataArea:'WORK',pane:'settings',current:{summary:{id:'work'}}};h.context.state=replacement;
  h.finish();await task;assert.equal(replacement.current.summary.id,'work');assert.equal(h.owner.current.summary.id,'old');
});

test('Windows chat-source action uses the guarded source loader',async()=>{
 const h=harness('chat','select-chat-source');const task=h.begin();await turn();
 assert.equal(typeof h.finish,'function');assert.equal(h.owner.current.summary.id,'old');
 h.finish();await task;assert.equal(h.owner.current.summary.id,'next');assert.equal(h.owner.pane,'chat');assert.match(h.owner.status,/聊天记录来源/);assert.doesNotMatch(h.owner.status,/工作区/);
});
test('a delayed chat-source receipt cannot replace a different active data area',async()=>{
 const h=harness('chat','select-chat-source');const task=h.begin();await turn();
 const next={dataArea:'WORK',pane:'settings',current:{summary:{id:'keep-work'}}};h.context.state=next;
 h.finish();await task;assert.equal(next.current.summary.id,'keep-work');assert.equal(h.owner.current.summary.id,'old');
});
