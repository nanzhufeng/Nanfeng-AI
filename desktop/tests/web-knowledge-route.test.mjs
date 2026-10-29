import test from 'node:test';
import assert from 'node:assert/strict';
import { createWebKnowledgeController } from '../src/web-knowledge-tasks.mjs';
const tick = () => new Promise(setImmediate);
const deferred = () => { let resolve; const promise = new Promise(r => resolve = r); return { promise, resolve }; };
const ready = { id:'web-a',workspaceId:'w',requestedUrl:'https://example.org/',generation:1,status:'AWAITING_CONFIRMATION',items:[{ordinal:0,status:'PENDING_CONFIRMATION',candidate:{title:'<script>inert</script>',body:'source text',tags:[]}}] };
function harness(handler = async () => ready) {
  const owner={dataArea:'WORK',current:{summary:{id:'w'}},pane:'knowledge',dialog:{kind:'knowledge'},webKnowledgeTask:structuredClone(ready),webKnowledgeTasks:[ready]};
  let state=owner;const calls=[],timers=new Map(),fields={url:'https://example.org/?ordinary=ephemeral',title:'edited title',body:'edited body',tags:'one, two',project:'project-a'};let next=0;
  const controller=createWebKnowledgeController({getState:()=>state,native:true,invoke:async(command,args,areaOwner)=>{calls.push({command,args,areaOwner});return handler(command,args);},render(){},refresh:async()=>{},escape:s=>String(s).replaceAll('<','&lt;').replaceAll('"','&quot;'),document:{querySelector:s=>({value:fields[s.split('-').at(-1)]||''})},setTimer:fn=>{const id=++next;timers.set(id,()=>{timers.delete(id);return fn();});return id;},clearTimer:id=>timers.delete(id)});
  const event=(action,id='web-a',type='click')=>({type,target:{closest:()=>({dataset:{action,ordinal:'0'},value:id,closest:()=>({dataset:{taskId:id}})})},preventDefault(){},stopImmediatePropagation(){}});
  return {owner,calls,timers,fields,controller,event,setState:s=>state=s};
}
test('opening URL dialog has no network and explicit submit alone queues and processes ephemeral URL',async()=>{
  const finish=deferred();const h=harness(async command=>command==='queue_desktop_web_knowledge'?{...ready,status:'QUEUED',generation:0,items:[]}:command==='process_desktop_web_knowledge'?finish.promise:{...ready,status:'FETCHING',items:[]});
  await h.controller.handle(h.event('select-web-knowledge'));assert.equal(h.calls.length,0);assert.equal(h.owner.dialog.kind,'web-knowledge-url');assert.deepEqual(h.owner.dialog.draft,{});
  await h.controller.handle(h.event('fetch-web-knowledge'));assert.deepEqual(h.calls.map(c=>c.command),['queue_desktop_web_knowledge','process_desktop_web_knowledge']);assert.equal(h.calls[1].args.rawUrl,h.fields.url);assert.equal(h.owner.dialog,null);assert.equal(JSON.stringify(h.owner).includes('ordinary=ephemeral'),false);assert.equal(h.owner.webKnowledgeTask.generation,0);
  await [...h.timers.values()][0]();assert.equal(h.owner.webKnowledgeTask.generation,1);assert.equal(h.owner.webKnowledgeTask.status,'FETCHING');assert.doesNotMatch(h.controller.card(h.owner.webKnowledgeTask),/review-web-knowledge/);
  finish.resolve(ready);await tick();assert.equal(h.owner.webKnowledgeTask.status,'AWAITING_CONFIRMATION');assert.equal(h.timers.size,0);assert.ok(!h.calls.some(c=>c.command.includes('confirm')));assert.ok(h.calls.every(c=>c.areaOwner===h.owner));
});
test('duplicate URL submit cannot create two tasks and closing during queue cannot start HTTP',async()=>{
  const queue=deferred();const h=harness(async()=>queue.promise);await h.controller.handle(h.event('select-web-knowledge'));
  const p=h.controller.handle(h.event('fetch-web-knowledge'));await h.controller.handle(h.event('fetch-web-knowledge'));await h.controller.handle(h.event('fetch-web-knowledge'));assert.equal(h.calls.length,1);
  h.owner.dialog=null;queue.resolve({...ready,status:'QUEUED',generation:0,items:[]});await p;assert.equal(h.calls.length,1);
});
test('cold failed or queued tasks only load persisted facts; retry opens a fresh blank URL draft',async()=>{
  const failed={...ready,status:'FAILED',items:[]};const h=harness(async command=>command.startsWith('list_')?{tasks:[failed],hasMore:false}:failed);
  await h.controller.load();assert.equal(h.calls.length,2);assert.equal(h.timers.size,0);assert.ok(h.calls.every(c=>c.command.startsWith('read_')||c.command.startsWith('list_')));
  await h.controller.handle(h.event('retry-web-knowledge'));assert.equal(h.calls.length,2);assert.equal(h.owner.dialog.retry,true);assert.deepEqual(h.owner.dialog.draft,{});assert.equal(h.owner.dialog.rawUrl,undefined);
});
test('review binds generation and edited content to captured area/domain confirmation',async()=>{
  const h=harness();await h.controller.handle(h.event('review-web-knowledge'));assert.equal(h.calls.length,0);assert.equal(h.owner.dialog.requestedUrl,ready.requestedUrl);
  await h.controller.handle(h.event('confirm-web-knowledge'));assert.equal(h.calls[0].command,'confirm_desktop_web_knowledge_item');assert.deepEqual(h.calls[0].args.args,{workspaceId:'w',taskId:'web-a',generation:1,ordinal:0,candidate:{title:'edited title',body:'edited body',tags:['one','two']},projectId:'project-a'});
  const stale=harness();await stale.controller.handle(stale.event('review-web-knowledge'));stale.owner.webKnowledgeTask.generation=2;await stale.controller.handle(stale.event('confirm-web-knowledge'));assert.equal(stale.calls.length,0);
});
test('cancel uses fresh durable generation while request is pending and a late ready reply cannot revive it',async()=>{
  const finish=deferred();const cancelled={...ready,status:'CANCELLED',items:[]};const h=harness(async command=>command==='queue_desktop_web_knowledge'?{...ready,status:'QUEUED',generation:0,items:[]}:command==='process_desktop_web_knowledge'?finish.promise:command==='read_desktop_web_knowledge'?{...ready,status:'FETCHING',items:[]}:cancelled);
  await h.controller.handle(h.event('select-web-knowledge'));await h.controller.handle(h.event('fetch-web-knowledge'));await h.controller.handle(h.event('cancel-web-knowledge'));
  const cancel=h.calls.find(c=>c.command==='cancel_desktop_web_knowledge');assert.equal(cancel.args.generation,1);assert.equal(h.owner.webKnowledgeTask.status,'CANCELLED');finish.resolve(ready);await tick();assert.equal(h.owner.webKnowledgeTask.status,'CANCELLED');assert.equal(h.timers.size,0);
});
test('late queue or history responses cannot cross workspace owner or selection generation',async()=>{
  const queue=deferred();const h=harness(()=>queue.promise);await h.controller.handle(h.event('select-web-knowledge'));const p=h.controller.handle(h.event('fetch-web-knowledge'));h.setState({current:{summary:{id:'other'}},error:'keep'});queue.resolve(ready);await p;assert.equal(h.calls.length,1);
  const read=deferred();const cold=harness(async command=>command.startsWith('list_')?{tasks:[ready],hasMore:false}:read.promise);const loaded=cold.controller.load();cold.owner.webKnowledgeReadGeneration++;read.resolve(ready);await loaded;assert.equal(cold.timers.size,0);
});
test('an in-flight progress read cannot revive a cancelled task or retain its spinner',async()=>{
  const finish=deferred(),read=deferred();let reads=0;
  const h=harness(async command=>command==='queue_desktop_web_knowledge'?{...ready,status:'QUEUED',generation:0,items:[]}:command==='process_desktop_web_knowledge'?finish.promise:command==='read_desktop_web_knowledge'?(++reads===1?read.promise:{...ready,status:'FETCHING',items:[]}):{...ready,status:'CANCELLED',items:[]});
  await h.controller.handle(h.event('select-web-knowledge'));await h.controller.handle(h.event('fetch-web-knowledge'));
  const polling=[...h.timers.values()][0]();await h.controller.handle(h.event('cancel-web-knowledge'));
  assert.doesNotMatch(h.controller.card(h.owner.webKnowledgeTask),/role="status"/);
  read.resolve(ready);await polling;assert.equal(h.owner.webKnowledgeTask.status,'CANCELLED');assert.equal(h.timers.size,0);
  finish.resolve(ready);await tick();assert.equal(h.owner.webKnowledgeTask.status,'CANCELLED');
});
test('source title and safe URL are escaped in the task card; preview does not invoke native actions',async()=>{
  const h=harness();const card=h.controller.card(ready);assert.match(card,/&lt;script>/);assert.doesNotMatch(card,/<script>/);assert.doesNotMatch(card,/ordinary=ephemeral/);
  const owner=h.owner;let calls=0;const c=createWebKnowledgeController({getState:()=>owner,native:false,invoke:async()=>{calls++;},render(){},refresh:async()=>{},escape:String,document:{}});await c.handle(h.event('select-web-knowledge'));assert.equal(calls,0);assert.ok(owner.error);
});

test('empty persisted web task loads do not poll an absent task or surface a progress error', async () => {
  const h = harness(async command => command.startsWith('list_') ? { tasks: [], hasMore: false } : null);
  for (let n = 0; n < 2; n++) {
    await h.controller.load();
    assert.equal(h.owner.webKnowledgeTask, null);
    assert.deepEqual(h.owner.webKnowledgeTasks, []);
    assert.equal(h.timers.size, 0);
    assert.equal(h.owner.error, undefined);
  }
  assert.deepEqual(h.calls.map(c => c.command), [
    'read_latest_desktop_web_knowledge', 'list_desktop_web_knowledge_tasks',
    'read_latest_desktop_web_knowledge', 'list_desktop_web_knowledge_tasks',
  ]);
});
