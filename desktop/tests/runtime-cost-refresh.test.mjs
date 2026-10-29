import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { readFile } from 'node:fs/promises';
import { renderChatFirstShell } from '../src/chat-shell.mjs';

const source=await readFile(new URL('../src/app.mjs',import.meta.url),'utf8');
const queue=source.slice(source.indexOf('function queueOrdinaryChatRuntimeRefresh(payload)'),source.indexOf('function createChatSubmissionId()'));
const flush=source.slice(source.indexOf('async function flushOrdinaryChatRuntimeRefresh()'),source.indexOf('async function openGenerationIslandTask('));
const load=source.slice(source.indexOf('async function loadDesktopUsageLedger('),source.indexOf('async function loadDesktopContextRecords('));
const data=()=>({summary:{id:'workspace'},exchange:{conversations:[{id:'conversation',title:'合成部分费用',currentLeafId:'answer',messages:[{id:'answer',attemptId:'attempt',role:'assistant',delivery:'COMPLETE',createdAt:'2026-10-22T00:00:00Z',modelSnapshot:{providerId:'RELAYCORE_CLAUDE',modelId:'claude-opus-5.5',displayName:'Opus 5.5 · Kiro正价'},usage:{inputTokens:7,outputTokens:null},blocks:[{kind:'TEXT',text:'合成回答'}]}]}]}});
const row=(id,extra)=>({entryId:id,kind:'FINAL_MEASURED',executionId:'attempt',invocationId:id,conversationId:'conversation',...extra});
const ledger=()=>({records:[row('retrieval',{requestedModelId:'openai/gpt-6-luna',actualModelId:'openai/gpt-6-luna',chargeMicros:500000,currencyCode:'USD',costSource:'PROVIDER_RESPONSE',callMetadata:{role:'RETRIEVAL',modelDisplayName:'Luna · OpenRouter'}}),row('answer',{requestedModelId:'claude-opus-5.5',actualModelId:'claude-opus-5.5',inputTokens:7,outputTokens:null,chargeMicros:null,currencyCode:null,callMetadata:{role:'ANSWER',modelDisplayName:'Opus 5.5 · Kiro正价'}})]});
function harness(invokeOverride){
 const commands=[],paint=[],owner={dataArea:'CHAT',current:data(),selectedConversationId:'conversation',usageLedger:{records:[]}};
 const context=vm.createContext({native:true,state:owner,desktopEntryRouteGeneration:0,ordinaryChatRuntimeRefreshPending:null,ordinaryChatRuntimeRefreshInFlight:false,
  invoke:async(command,args)=>{commands.push(command);return invokeOverride?invokeOverride(command,args):command==='read_desktop_workspace'?data():ledger()},
  claimSubmittedChatRoute:()=>{},scheduleOrdinaryChatRuntimeRefresh:()=>{},loadConversationReadState:async()=>{},
  render:()=>paint.push(renderChatFirstShell({native:true,pane:'chat',data:owner.current,selectedConversationId:'conversation',usageLedger:owner.usageLedger})),
 });
 vm.runInContext(queue+flush+load,context);
 return {owner,context,commands,paint,event:state=>vm.runInContext(`queueOrdinaryChatRuntimeRefresh({workspaceId:'workspace',conversationId:'conversation',state:${JSON.stringify(state)}})`,context),flush:()=>vm.runInContext('flushOrdinaryChatRuntimeRefresh()',context)};
}
test('a completed runtime event reads durable costs before painting the known subtotal plus unknown',async()=>{
 const h=harness();h.event('COMPLETED');await h.flush();assert.deepEqual(h.commands,['read_desktop_workspace','read_desktop_usage_ledger']);assert.equal(h.owner.usageLedger.records.length,2);assert.match(h.paint.at(-1),/¥3\.3602 \+ 未知/);
});
test('a later running delta cannot coalesce away a completed call cost refresh',async()=>{
 const h=harness();h.event('COMPLETED');h.event('RUNNING');await h.flush();assert.equal(h.commands.filter(x=>x==='read_desktop_usage_ledger').length,1);assert.match(h.paint.at(-1),/¥3\.3602 \+ 未知/);
});
test('text-only running deltas avoid a full ledger read',async()=>{
 const h=harness();h.event('RUNNING');await h.flush();assert.deepEqual(h.commands,['read_desktop_workspace']);assert.equal(h.owner.usageLedger.records.length,0);
});
test('late costs cannot replace another data area with the same workspace identity',async()=>{
 let release;const h=harness(command=>command==='read_desktop_workspace'?data():new Promise(done=>{release=done}));h.event('COMPLETED');const pending=h.flush();await new Promise(done=>setImmediate(done));assert.equal(typeof release,'function','terminal path must request costs');const other={dataArea:'WORK',current:data(),usageLedger:{records:[{entryId:'work-record'}]}};h.context.state=other;release(ledger());await pending;assert.equal(other.usageLedger.records[0].entryId,'work-record');assert.equal(h.owner.usageLedger.records.length,0);assert.equal(h.paint.length,0);
});
test('a failed terminal ledger read preserves earlier facts and reports the local refresh failure',async()=>{
 const h=harness(command=>{if(command==='read_desktop_workspace')return data();throw Error('synthetic-ledger-unavailable')});h.event('FAILED');await h.flush();assert.match(h.owner.error,/聊天增量已持久化.*synthetic-ledger-unavailable/);assert.equal(h.owner.usageLedger.records.length,0);assert.equal(h.context.ordinaryChatRuntimeRefreshInFlight,false);assert.equal(h.context.ordinaryChatRuntimeRefreshPending,null);
});
for(const state of ['FAILED','CANCELLED','UNKNOWN','COMPLETED_ACCOUNTING_PENDING','RESPONSE_COMMITTED'])test(`terminal ${state} refreshes received independent costs`,async()=>{const h=harness();h.event(state);await h.flush();assert.equal(h.commands.filter(x=>x==='read_desktop_usage_ledger').length,1)});
