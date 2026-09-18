import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import vm from 'node:vm';
const source = await readFile(new URL('../src/app.mjs',import.meta.url),'utf8');
const invocation = source.slice(source.indexOf('const invoke = async'),source.indexOf('async function switchDataArea'));
function harness() {
  let release;
  const calls=[];
  const ctx=vm.createContext({window:{addEventListener:()=>{}},tauriBridge:{invoke:(command,args,options)=>{calls.push(options);return new Promise(resolve=>release=resolve);}}});
  vm.runInContext(`let state={dataArea:'CHAT'}; ${invocation}; globalThis.api={invoke,owner:()=>state,switch:()=>{state={dataArea:'WORK'};}}`,ctx);
  return {api:ctx.api,calls,finish:result=>release(result)};
}
test('a queued persistence operation keeps its original physical database after area switch',async()=>{
  const h=harness(); const original=h.api.owner();h.api.switch();
  const request=h.api.invoke('save_desktop_conversation_draft',{},original);
  assert.equal(h.calls[0].headers['x-nanfeng-data-area'],'CHAT');
  h.finish({ok:true});
  await assert.rejects(request,error=>error.code==='STALE_DATA_AREA');
});
test('a completed source-area request cannot enter a destination error or restore-draft branch',async()=>{
  const h=harness();const request=h.api.invoke('submit_desktop_ordinary_chat',{});h.api.switch();h.finish({state:'COMPLETED'});
  await assert.rejects(request,error=>error.code==='STALE_DATA_AREA');
});
