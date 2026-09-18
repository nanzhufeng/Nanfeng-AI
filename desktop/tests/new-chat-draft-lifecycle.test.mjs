import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';
const source = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');
const owner = source.slice(source.indexOf('let ordinaryComposerDraftQueue'), source.indexOf('const MAX_COMPOSER_ATTACHMENT_BYTES'));
function openSession(records = new Map()) {
  const listeners = [];
  const state = {current:{summary:{id:'area-a'}},selectedConversationId:null,composerDraft:'',composerAttachments:[]};
  const context = vm.createContext({ state, native:true, workspace:()=>state.current, render:()=>{},
    Date, setTimeout:()=>1, clearTimeout:()=>{}, app:{addEventListener:(_,callback)=>listeners.push(callback)},
    invoke:async(command,args)=>{
      if(command==='save_desktop_conversation_draft') {
        const a=args.args; const key=a.workspaceId+':'+(a.conversationId||'new');
        records.set(key,{text:a.text,attachments:[],expiresAtMs:a.conversationId?null:Date.now()+3600000});
        return records.get(key);
      }
      return records.get(args.workspaceId+':'+(args.conversationId||'new'))||null;
    },
  });
  vm.runInContext(owner+'\nglobalThis.api={scheduleOrdinaryComposerDraft,flushOrdinaryComposerDraft,loadOrdinaryComposerDraft,resetNewConversationComposer};',context);
  return {state,records,api:context.api,listeners};
}
test('new draft survives switching conversations and a new UI process without being deleted', async()=>{
  const first=openSession(); first.state.composerDraft='  未发送\n第二行';
  first.api.scheduleOrdinaryComposerDraft();
  first.state.selectedConversationId='existing'; first.state.composerDraft='';
  await first.api.loadOrdinaryComposerDraft();
  assert.equal(first.state.composerDraft,'');
  first.state.selectedConversationId=null;
  first.api.resetNewConversationComposer(); await first.api.loadOrdinaryComposerDraft();
  assert.equal(first.state.composerDraft,'  未发送\n第二行');
  const restarted=openSession(first.records);
  await restarted.api.loadOrdinaryComposerDraft();
  assert.equal(restarted.state.composerDraft,'  未发送\n第二行');
  restarted.state.current={summary:{id:'area-b'}};
  await restarted.api.loadOrdinaryComposerDraft();
  assert.equal(restarted.state.composerDraft,'');
});
test('a pending draft load cannot replace text typed after navigation',async()=>{
  const session=openSession(); session.records.set('area-a:new',{text:'old',attachments:[]});
  const loading=session.api.loadOrdinaryComposerDraft();
  session.state.composerDraft='new edit';session.api.scheduleOrdinaryComposerDraft();
  await loading; await session.api.flushOrdinaryComposerDraft();
  assert.equal(session.state.composerDraft,'new edit');
  assert.equal(session.records.get('area-a:new').text,'new edit');
});
