import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFile} from 'node:fs/promises';
const source=await readFile(new URL('../src/app.mjs',import.meta.url),'utf8');
const block=source.slice(source.indexOf('async function pickV2WorkspaceExchange()'),source.indexOf('async function pickNanfengKnowledgeExport()'));
const turn=()=>new Promise(resolve=>setImmediate(resolve));
function harness(){
 let pickerResolve,writeResolve,writeReject;const calls=[];let refreshes=0;
 const owner={dataArea:'CHAT',status:'CHAT',error:''};
 const context=vm.createContext({state:owner,native:true,render(){},short:x=>x,
  dialogInvoke:()=>new Promise(resolve=>{pickerResolve=resolve;}),
  invoke:(command,args,areaOwner)=>{calls.push({command,args,areaOwner});return command==='list_desktop_workspace_exchange_v2_committed'?Promise.resolve([]):new Promise((resolve,reject)=>{writeResolve=resolve;writeReject=reject;});},
  refresh:async()=>{refreshes++;},
 });vm.runInContext(block,context);
 return {owner,context,calls,call:code=>vm.runInContext(code,context),picker:()=>pickerResolve('synthetic.nfai-exchange'),finish:()=>writeResolve({semanticHash:'synthetic-hash',assetCount:1}),fail:()=>writeReject(Error('old write')),get refreshes(){return refreshes;}};
}
for(const action of ['pickV2WorkspaceExchange()','reexportV2WorkspaceExchange("v2-id")']){
 test(`${action}: changing area during picker does not import or export from the new area`,async()=>{
  const h=harness();const task=h.call(action);const work={dataArea:'WORK',status:'WORK',error:''};h.context.state=work;h.picker();await turn();
  if(h.calls.length)h.finish();await task;
  assert.equal(h.calls.length,0);assert.equal(work.status,'WORK');
 });
 test(`${action}: in-flight work uses its originating area and ignores late failure on the new page`,async()=>{
  const h=harness();const task=h.call(action);h.picker();await turn();
  assert.equal(h.calls[0].areaOwner,h.owner);
  const work={dataArea:'WORK',status:'WORK',error:''};h.context.state=work;h.fail();await task;
  assert.equal(work.status,'WORK');assert.equal(work.error,'');assert.equal(h.refreshes,0);
 });
}
test('normal v2 import still refreshes the committed list and reports the receipt',async()=>{
 const h=harness();const task=h.call('pickV2WorkspaceExchange()');h.picker();await turn();h.finish();await task;
 assert.equal(h.refreshes,1);assert.ok(h.owner.status.includes('synthetic-hash'));assert.equal(h.owner.error,'');
});
