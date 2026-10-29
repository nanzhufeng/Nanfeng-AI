import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFileSync} from 'node:fs';
import {invokeForAuthorizedDataAreaOperation} from '../src/data-area-invoke.mjs';
const source=readFileSync(new URL('../src/app.mjs',import.meta.url),'utf8').replaceAll('\r\n','\n');
const start=source.indexOf('async function readInlineTitleConflict('),end=source.indexOf('\ninlineTitleEditor = createInlineTitleEditor(',start);
assert.ok(start>=0&&end>start);const production=source.slice(start,end);
function harness(reply){const calls=[],owner={dataArea:'CHAT',current:{summary:{id:'local'}}};const context=vm.createContext({state:owner,tauriBridge:{invoke:async(command,args,options)=>{calls.push({command,args,area:options.headers['x-nanfeng-data-area']});return typeof reply==='function'?reply():reply;}},invokeForAuthorizedDataAreaOperation});vm.runInContext(production+'\nglobalThis.readConflict=readInlineTitleConflict;',context);return {context,calls,owner};}

test('conflict snapshot read uses the original workspace and data area, returning only the requested active title',async()=>{
 const h=harness({exchange:{conversations:[{id:'other',revision:99,title:'other'},{id:'wanted',revision:4,title:'真实当前标题'}]}});const result=await h.context.readConflict({id:'wanted',workspaceId:'original'},{isCurrent:()=>true});
 assert.equal(result.id,'wanted');assert.equal(result.revision,4);assert.equal(result.title,'真实当前标题');assert.deepEqual(JSON.parse(JSON.stringify(h.calls)),[{command:'read_desktop_workspace',args:{workspaceId:'original'},area:'CHAT'}]);assert.equal(h.owner.current.summary.id,'local');
});

test('a stale conflict read is skipped before IPC and discarded after an owner switch',async()=>{
 let release;const pending=new Promise(r=>release=r),h=harness(()=>pending);
 assert.equal(await h.context.readConflict({id:'wanted'},{isCurrent:()=>false}),null);assert.equal(h.calls.length,0);
 const reading=h.context.readConflict({id:'wanted'},{isCurrent:()=>true});h.context.state={dataArea:'WORK',current:{summary:{id:'next'}}};release({exchange:{conversations:[{id:'wanted',revision:4,title:'迟到标题'}]}});assert.equal(await reading,null);assert.equal(h.calls.length,1);assert.equal(h.context.state.current.summary.id,'next');
});

for(const row of [null,{id:'wanted',revision:4,title:'归档标题',archived:true},{id:'wanted',revision:4,title:'已移除标题',deleted:true}])test(`unavailable conflict target is not offered as an overwrite snapshot: ${row?.title||'missing'}`,async()=>{
 const h=harness({exchange:{conversations:row?[row]:[]}});assert.equal(await h.context.readConflict({id:'wanted'},{isCurrent:()=>true}),null);
});
