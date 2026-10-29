import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { generationRefreshInterval } from '../src/generation-continuity.mjs';
import { readFile } from 'node:fs/promises';

const source = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');
const refresh = source.slice(source.indexOf('async function flushOrdinaryChatRuntimeRefresh()'), source.indexOf('const tauriEvents'));
const polling = source.slice(source.indexOf('async function refreshDesktopRuntimeWhilePending('), source.indexOf('function recomputeConversationFind('));

test('runtime events and the polling backstop share one read; late data cannot replace a newly selected workspace', async () => {
  let release;
  let reads = 0;
  let paints = 0;
  let ticks = 0;
  const state = { current: { summary: { id: 'first' } } };
  const context = vm.createContext({ desktopEntryRouteGeneration: 0, state,
    ordinaryChatRuntimeRefreshPending: { workspaceId: 'first' },
    ordinaryChatRuntimeRefreshInFlight: false,
    invoke: () => { reads++; return new Promise(resolve => { release = resolve; }); },
    claimSubmittedChatRoute: () => {}, loadConversationReadState: async () => {},
    render: () => { paints++; }, scheduleOrdinaryChatRuntimeRefresh: () => {},
    window: { setTimeout: callback => { ticks++; queueMicrotask(callback); } },
    finished: () => ticks >= 2,
  });
  vm.runInContext(refresh + polling, context);
  const first = vm.runInContext('flushOrdinaryChatRuntimeRefresh()', context);
  await vm.runInContext("refreshDesktopRuntimeWhilePending('first', null, finished)", context);
  assert.equal(reads, 1, 'polling must not issue a concurrent snapshot read');
  state.current = { summary: { id: 'second' } };
  release({ summary: { id: 'first' } });
  await first;
  assert.equal(state.current.summary.id, 'second');
  assert.equal(paints, 0, 'late workspace data must not flash back onto the screen');
});

test('a delivered runtime update selects the connected DOM commit path', async () => {
  const paints = [];
  const context = vm.createContext({ desktopEntryRouteGeneration: 0, state: { current: { summary: { id: 'first' } } },
    ordinaryChatRuntimeRefreshPending: { workspaceId: 'first' }, ordinaryChatRuntimeRefreshInFlight: false,
    invoke: async () => ({ summary: { id: 'first' } }), claimSubmittedChatRoute: () => {},
    loadConversationReadState: async () => {}, render: options => paints.push(options),
    scheduleOrdinaryChatRuntimeRefresh: () => {},
  });
  vm.runInContext(refresh, context);
  await vm.runInContext('flushOrdinaryChatRuntimeRefresh()', context);
  assert.equal(paints.length, 1);
  assert.equal(paints[0].runtimeRefresh, true);
});

test('an event queued during polling reads again only after the poll releases its snapshot', async () => {
  let releasePoll;
  let reads = 0;
  let ticks = 0;
  let scheduled = 0;
  const state = { current: { summary: { id: 'first' }, revision: 0 } };
  const context = vm.createContext({ desktopEntryRouteGeneration: 0, state,
    ordinaryChatRuntimeRefreshPending: null, ordinaryChatRuntimeRefreshInFlight: false,
    invoke: () => ++reads === 1
      ? new Promise(resolve => { releasePoll = resolve; })
      : Promise.resolve({ summary: { id: 'first' }, revision: 2 }),
    claimSubmittedChatRoute: () => {}, loadConversationReadState: async () => {},
    render: () => {}, scheduleOrdinaryChatRuntimeRefresh: () => { scheduled++; },
    window: { setTimeout: callback => { ticks++; queueMicrotask(callback); } },
    finished: () => ticks >= 2,
  });
  vm.runInContext(refresh + polling, context);
  const poll = vm.runInContext("refreshDesktopRuntimeWhilePending('first', null, finished)", context);
  await new Promise(resolve => queueMicrotask(resolve));
  assert.equal(reads, 1);
  context.ordinaryChatRuntimeRefreshPending = { workspaceId: 'first' };
  await vm.runInContext('flushOrdinaryChatRuntimeRefresh()', context);
  assert.equal(reads, 1);
  releasePoll({ summary: { id: 'first' }, revision: 1 });
  await poll;
  assert.equal(scheduled, 1);
  await vm.runInContext('flushOrdinaryChatRuntimeRefresh()', context);
  assert.equal(reads, 2);
  assert.equal(state.current.revision, 2);
});

test('a polling snapshot arriving after command settlement cannot repaint older data', async () => {
  let releasePoll;
  let settled = false;
  let paints = 0;
  const state = { current: { summary: { id: 'first' }, revision: 2 } };
  const context = vm.createContext({ desktopEntryRouteGeneration: 0, state,
    ordinaryChatRuntimeRefreshPending: null, ordinaryChatRuntimeRefreshInFlight: false,
    invoke: () => new Promise(resolve => { releasePoll = resolve; }),
    render: () => { paints++; }, scheduleOrdinaryChatRuntimeRefresh: () => {},
    window: { setTimeout: callback => queueMicrotask(callback) },
    finished: () => settled,
  });
  vm.runInContext(polling, context);
  const poll = vm.runInContext("refreshDesktopRuntimeWhilePending('first', null, finished)", context);
  await new Promise(resolve => queueMicrotask(resolve));
  settled = true;
  releasePoll({ summary: { id: 'first' }, revision: 1 });
  await poll;
  assert.equal(state.current.revision, 2);
  assert.equal(paints, 0);
});

test('same workspace ID in another data area cannot consume a queued CHAT event', async () => {
  let reads = 0;
  const context = vm.createContext({ desktopEntryRouteGeneration: 0,
    state: { dataArea: 'WORK', current: { summary: { id: 'shared-id' } } },
    ordinaryChatRuntimeRefreshPending: { workspaceId: 'shared-id', dataArea: 'CHAT' },
    ordinaryChatRuntimeRefreshInFlight: false,
    invoke: () => { reads++; return Promise.resolve({ summary: { id: 'shared-id' } }); },
    claimSubmittedChatRoute: () => {}, loadConversationReadState: async () => {},
    render: () => {}, scheduleOrdinaryChatRuntimeRefresh: () => {},
  });
  vm.runInContext(refresh, context);
  await vm.runInContext('flushOrdinaryChatRuntimeRefresh()', context);
  assert.equal(reads, 0);
});

test('polling stops when its CHAT owner is replaced by WORK with the same workspace ID', async () => {
  let reads = 0;
  let ticks = 0;
  const context = vm.createContext({ desktopEntryRouteGeneration: 0,
    state: { dataArea: 'CHAT', current: { summary: { id: 'shared-id' } } },
    ordinaryChatRuntimeRefreshPending: null, ordinaryChatRuntimeRefreshInFlight: false,
    invoke: () => { reads++; return Promise.resolve({ summary: { id: 'shared-id' } }); },
    render: () => {}, scheduleOrdinaryChatRuntimeRefresh: () => {},
    window: { setTimeout: callback => { ticks++; queueMicrotask(callback); } },
    finished: () => ticks >= 2,
  });
  vm.runInContext(polling, context);
  const poll = vm.runInContext("refreshDesktopRuntimeWhilePending('shared-id', null, finished)", context);
  context.state = { dataArea: 'WORK', current: { summary: { id: 'shared-id' } } };
  await poll;
  assert.equal(reads, 0);
});


const queuedRuntime = source.slice(source.indexOf('function queueOrdinaryChatRuntimeRefresh('), source.indexOf('function createChatSubmissionId('));
const submittedRoute = source.slice(source.indexOf('function claimSubmittedChatRoute('), source.indexOf('async function flushOrdinaryChatRuntimeRefresh()'));

function queuedSubmissionHarness() {
  const state = { dataArea: 'CHAT', current: { summary: { id: 'workspace' } },
    selectedConversationId: null, pendingCreatedConversationRouteWorkspaceId: 'workspace',
    pendingChatSubmission: { workspaceId: 'workspace', conversationId: null, clientSubmissionId: 'send-A' },
    composerDraft: '', composerAttachments: [] };
  let reads = 0;
  const context = vm.createContext({ desktopEntryRouteGeneration: 0, state, ordinaryChatRuntimeRefreshPending: null,
    ordinaryChatRuntimeRefreshInFlight: false, scheduleOrdinaryChatRuntimeRefresh() {},
    invoke: async () => { reads++; return { summary: { id: 'workspace' } }; },
    loadConversationReadState: async () => {}, render() {} });
  vm.runInContext(queuedRuntime + submittedRoute + refresh, context);
  return { state, context, reads: () => reads,
    queue: payload => { context.payload = payload; vm.runInContext('queueOrdinaryChatRuntimeRefresh(payload)', context); },
    flush: () => vm.runInContext('flushOrdinaryChatRuntimeRefresh()', context) };
}

test('another conversation delta cannot coalesce away the pressed Send acknowledgement', async () => {
  const h = queuedSubmissionHarness();
  h.queue({ dataArea: 'CHAT', workspaceId: 'workspace', conversationId: 'conversation-A', clientSubmissionId: 'send-A' });
  h.queue({ dataArea: 'CHAT', workspaceId: 'workspace', conversationId: 'conversation-C', clientSubmissionId: 'send-C' });
  assert.equal(h.context.ordinaryChatRuntimeRefreshPending.conversationId, 'conversation-C');
  await h.flush();
  assert.equal(h.reads(), 1, 'retain coalesced workspace reading');
  assert.equal(h.state.pendingChatSubmission, null, 'a displayed background delta must not lose Send acknowledgement');
  assert.equal(h.state.selectedConversationId, 'conversation-A');
  h.state.selectedConversationId = 'conversation-B';
  h.state.composerDraft = 'B next message';
  h.queue({ dataArea: 'CHAT', workspaceId: 'workspace', conversationId: 'conversation-A', clientSubmissionId: 'send-A' });
  await h.flush();
  assert.equal(h.state.selectedConversationId, 'conversation-B', 'background update must not navigate back');
  assert.equal(h.state.composerDraft, 'B next message');
});

test('foreign area, workspace, or submission cannot release another Send admission', async () => {
  const h = queuedSubmissionHarness();
  const matching = { workspaceId: 'workspace', conversationId: 'conversation-A', clientSubmissionId: 'send-A' };
  for (const payload of [{ ...matching, dataArea: 'WORK' }, { ...matching, workspaceId: 'other' },
    { ...matching, clientSubmissionId: 'send-other' }]) {
    h.queue(payload);
    assert.equal(h.state.pendingChatSubmission.clientSubmissionId, 'send-A');
    assert.equal(h.state.selectedConversationId, null);
  }
  h.queue({ ...matching, dataArea: 'CHAT' });
  await h.flush();
  assert.equal(h.state.pendingChatSubmission, null);
  assert.equal(h.state.selectedConversationId, 'conversation-A');
});

function createRuntimeSchedulerContext(bindings) { return vm.createContext({ setTimeout, clearTimeout, ordinaryChatRuntimeVisibilityQuery:null, ...bindings }); }
const schedule = source.slice(source.indexOf('async function scheduleOrdinaryChatRuntimeRefresh()'), source.indexOf('async function syncPersistedConversationContinuation('));
test('display deadlines do not accumulate observers on a stalled native query', { timeout:2000 }, async()=>{
 let observations=0,releaseRead;const timers=[];
 class ObservedPromise extends Promise {
  static all(values) {
   const result=super.all(values),then=result.then.bind(result);
   result.then=(...args)=>{observations++;return then(...args);};return result;
  }
 }
 const context=createRuntimeSchedulerContext({Promise:ObservedPromise,state:{pdfPreview:{}},document:{hidden:false},generationRefreshInterval,
  ordinaryChatRuntimeRefreshTimer:null,ordinaryChatRuntimeRefreshInFlight:false,ordinaryChatRuntimeVisibilityReadPending:false,
  window:{__TAURI__:{window:{getCurrentWindow:()=>({isVisible:()=>new Promise(done=>releaseRead=done),isMinimized:async()=>false})}},
   setTimeout:(callback,delay)=>{timers.push({callback,delay});return timers.length;}},flushOrdinaryChatRuntimeRefresh:()=>{},
 });vm.runInContext(schedule,context);
 try {
  await context.scheduleOrdinaryChatRuntimeRefresh();const initial=observations;
  for(let tick=0;tick<3;tick++){timers.at(-1).callback();await context.scheduleOrdinaryChatRuntimeRefresh();}
  assert.equal(observations,initial,'each timed-out display wait must detach instead of attaching another native-query observer');
 } finally { releaseRead(true);await new Promise(done=>setTimeout(done,0)); }
});
test('an unanswered native query is shared across display deadlines rather than accumulating IPC', { timeout:2000 }, async()=>{
 const releases=[],timers=[];let reads=0;
 const context=createRuntimeSchedulerContext({state:{pdfPreview:{}},document:{hidden:false},generationRefreshInterval,
  ordinaryChatRuntimeRefreshTimer:null,ordinaryChatRuntimeRefreshInFlight:false,ordinaryChatRuntimeVisibilityReadPending:false,
  window:{__TAURI__:{window:{getCurrentWindow:()=>({isVisible:()=>{reads++;return new Promise(done=>releases.push(done));},isMinimized:async()=>false})}},
   setTimeout:(callback,delay)=>{timers.push({callback,delay});return timers.length;}},flushOrdinaryChatRuntimeRefresh:()=>{},
 });vm.runInContext(schedule,context);
 try {
  await context.scheduleOrdinaryChatRuntimeRefresh();assert.equal(timers.length,1);
  timers[0].callback();await context.scheduleOrdinaryChatRuntimeRefresh();
  assert.equal(timers.length,2);assert.equal(reads,1,'each display deadline must reuse the same unresolved native query');
 } finally { for(const release of releases)release(true);await new Promise(done=>setTimeout(done,0)); }
 assert.equal(context.ordinaryChatRuntimeVisibilityQuery,null,'settled native query must release its slot');
});
for (const [name,preview,hidden,heldRead,expected] of [
 ['ordinary',false,false,'visible',80],
 ['PDF preview',true,false,'visible',500],
 ['media visibility',true,false,'minimized',500],
 ['hidden preview',true,true,'visible',1000],
]) test(`an unanswered native ${heldRead} read cannot freeze ${name} runtime refresh`, { timeout:2000 }, async()=>{
 let releaseRead,reads=0;const timers=[];
 const held=()=>{reads++;return new Promise(done=>releaseRead=done);};
 const context=createRuntimeSchedulerContext({state:preview?{pdfPreview:{}}:{},document:{hidden},generationRefreshInterval,
  setTimeout,clearTimeout,ordinaryChatRuntimeRefreshTimer:null,ordinaryChatRuntimeRefreshInFlight:false,ordinaryChatRuntimeVisibilityReadPending:false,
  window:{__TAURI__:{window:{getCurrentWindow:()=>({isVisible:heldRead==='visible'?held:async()=>true,isMinimized:heldRead==='minimized'?held:async()=>false})}},
   setTimeout:(callback,delay)=>{timers.push({callback,delay});return timers.length;}},flushOrdinaryChatRuntimeRefresh:()=>{},
 });
 vm.runInContext(schedule,context);const first=context.scheduleOrdinaryChatRuntimeRefresh();
 try {
  await context.scheduleOrdinaryChatRuntimeRefresh();assert.equal(reads,1);
  await new Promise(done=>setTimeout(done,150));
  assert.equal(timers.length,1,'durable display refresh must not wait forever for window state');
  assert.equal(timers[0].delay,expected);assert.equal(context.ordinaryChatRuntimeVisibilityReadPending,false);
 } finally { releaseRead(heldRead==='visible');await first; }
 assert.equal(timers.length,1,'late window state must not duplicate the already scheduled refresh');
});
for(const [name,state,hidden,expected] of [
 ['normal',{},false,80],
 ...['imagePreview','pdfPreview','textPreview','archivePreview','videoPreview','audioPreview'].map(key=>[key,{[key]:{}},false,500]),
 ['hidden',{},true,1000],['hidden preview',{pdfPreview:{}},true,1000],
]) test(`runtime scheduler uses ${name} cadence and coalesces events`,()=>{
 const timers=[];const context=createRuntimeSchedulerContext({ desktopEntryRouteGeneration: 0,state,document:{hidden},generationRefreshInterval,
   ORDINARY_CHAT_RUNTIME_REFRESH_INTERVAL_MS:80,ordinaryChatRuntimeRefreshTimer:null,ordinaryChatRuntimeRefreshInFlight:false,ordinaryChatRuntimeVisibilityReadPending:false,
   window:{setTimeout:(callback,delay)=>{timers.push({callback,delay});return timers.length;}},flushOrdinaryChatRuntimeRefresh:()=>{},
 });
 vm.runInContext(schedule,context);context.scheduleOrdinaryChatRuntimeRefresh();context.scheduleOrdinaryChatRuntimeRefresh();
 assert.equal(timers.length,1);assert.equal(timers[0].delay,expected);
 context.ordinaryChatRuntimeRefreshTimer=null;context.ordinaryChatRuntimeRefreshInFlight=true;
 context.scheduleOrdinaryChatRuntimeRefresh();assert.equal(timers.length,1,'in-flight read must stay serialized');
});

for(const [name,visible,minimized] of [['hidden native window',false,false],['minimized native window',true,true]]) {
 test(`${name} throttles even when WebView document stays visible`,async()=>{
  const timers=[];const context=createRuntimeSchedulerContext({ desktopEntryRouteGeneration: 0,state:{},document:{hidden:false},generationRefreshInterval,
    ORDINARY_CHAT_RUNTIME_REFRESH_INTERVAL_MS:80,ordinaryChatRuntimeRefreshTimer:null,ordinaryChatRuntimeRefreshInFlight:false,ordinaryChatRuntimeVisibilityReadPending:false,
    window:{__TAURI__:{window:{getCurrentWindow:()=>({isVisible:async()=>visible,isMinimized:async()=>minimized})}},setTimeout:(callback,delay)=>{timers.push({callback,delay});return 1;}},flushOrdinaryChatRuntimeRefresh:()=>{},
  });vm.runInContext(schedule,context);await context.scheduleOrdinaryChatRuntimeRefresh();
  assert.equal(timers[0].delay,1000);
 });
}

test('visibility reads coalesce pending events and use current preview state',async()=>{
 let release,reads=0;const timers=[];const state={};
 const context=createRuntimeSchedulerContext({ desktopEntryRouteGeneration: 0,state,document:{hidden:false},generationRefreshInterval,ordinaryChatRuntimeRefreshTimer:null,ordinaryChatRuntimeRefreshInFlight:false,ordinaryChatRuntimeVisibilityReadPending:false,
  window:{__TAURI__:{window:{getCurrentWindow:()=>({isVisible:()=>{reads++;return new Promise(done=>release=done);},isMinimized:async()=>false})}},setTimeout:(callback,delay)=>{timers.push({callback,delay});return 1;}},flushOrdinaryChatRuntimeRefresh:()=>{},
 });vm.runInContext(schedule,context);const first=context.scheduleOrdinaryChatRuntimeRefresh();await context.scheduleOrdinaryChatRuntimeRefresh();
 assert.equal(reads,1);assert.equal(timers.length,0);state.pdfPreview={};release(true);await first;assert.equal(timers[0].delay,500);
});
test('failed native visibility read still schedules durable display refresh',async()=>{
 const timers=[];const context=createRuntimeSchedulerContext({ desktopEntryRouteGeneration: 0,state:{},document:{hidden:false},generationRefreshInterval,ordinaryChatRuntimeRefreshTimer:null,ordinaryChatRuntimeRefreshInFlight:false,ordinaryChatRuntimeVisibilityReadPending:false,
  window:{__TAURI__:{window:{getCurrentWindow:()=>({isVisible:async()=>{throw new Error('window unavailable');},isMinimized:async()=>false})}},setTimeout:(callback,delay)=>{timers.push({callback,delay});return 1;}},flushOrdinaryChatRuntimeRefresh:()=>{},
 });vm.runInContext(schedule,context);await context.scheduleOrdinaryChatRuntimeRefresh();assert.equal(timers[0].delay,80);assert.equal(context.ordinaryChatRuntimeVisibilityReadPending,false);
});

function lateReadHarness({poll=false,markers=false}={}) {
 let resolveRead,rejectRead,resolveMarkers,rejectMarkers;const held=new Promise((ok,no)=>{resolveRead=ok;rejectRead=no;});const markerWait=new Promise((ok,no)=>{resolveMarkers=ok;rejectMarkers=no;});
 const state={dataArea:'CHAT',current:{summary:{id:'source-a'},revision:1},selectedConversationId:'shared-conversation',composerDraft:'original draft',error:'original error'};let paints=0,markerReads=0,settled=false,ticks=0,markReached;const markerStarted=new Promise(ok=>markReached=ok);
 const context=vm.createContext({state,desktopEntryRouteGeneration:0,ordinaryChatRuntimeRefreshPending:{workspaceId:'source-a',dataArea:'CHAT'},ordinaryChatRuntimeRefreshInFlight:false,
  invoke:()=>held,claimSubmittedChatRoute:()=>{},loadConversationReadState:async()=>{markerReads++;markReached();if(markers)await markerWait;},render:()=>paints++,scheduleOrdinaryChatRuntimeRefresh:()=>{},
  window:{setTimeout:callback=>{if(++ticks>1)settled=true;queueMicrotask(callback);}},finished:()=>settled});
 vm.runInContext(refresh+polling,context);
 return {state,context,resolveRead,rejectRead,resolveMarkers,rejectMarkers,markerStarted,paints:()=>paints,markerReads:()=>markerReads,
  run:()=>vm.runInContext(poll?"refreshDesktopRuntimeWhilePending('source-a',null,finished)":'flushOrdinaryChatRuntimeRefresh()',context),
  navigate:(id='source-b')=>{context.desktopEntryRouteGeneration++;state.current={summary:{id},revision:9};state.selectedConversationId='shared-conversation';state.composerDraft='destination draft';state.error='destination error';},
  settle:()=>{settled=true;},snapshot:()=>state.current};
}

test('late runtime read error cannot overwrite another source with the same conversation ID',async()=>{
 const h=lateReadHarness();const done=h.run();h.navigate();h.rejectRead(new Error('synthetic source-a failure'));await done;
 assert.equal(h.state.current.summary.id,'source-b');assert.equal(h.state.error,'destination error');assert.equal(h.state.composerDraft,'destination draft');assert.equal(h.paints(),0);
});

for(const outcome of ['resolve','reject'])test(`runtime ${outcome} cannot replace a newer snapshot of the same source`,async()=>{
 const h=lateReadHarness();const done=h.run();const fresh={summary:{id:'source-a'},revision:9};h.state.current=fresh;h.state.error='fresh owner error';
 if(outcome==='resolve')h.resolveRead({summary:{id:'source-a'},revision:2});else h.rejectRead(new Error('old snapshot failed'));await done;
 assert.equal(h.state.current,fresh);assert.equal(h.state.error,'fresh owner error');assert.equal(h.markerReads(),0);assert.equal(h.paints(),0);
});

for(const outcome of ['resolve','reject'])test(`runtime ${outcome} cannot revive a source after navigation away and back`,async()=>{
 const h=lateReadHarness();const done=h.run();h.navigate();h.navigate('source-a');const fresh=h.snapshot();
 if(outcome==='resolve')h.resolveRead({summary:{id:'source-a'},revision:2});else h.rejectRead(new Error('old route failed'));await done;
 assert.equal(h.state.current,fresh);assert.equal(h.state.error,'destination error');assert.equal(h.state.composerDraft,'destination draft');assert.equal(h.paints(),0);
});

test('runtime read intent becomes stale even before destination workspace commits',async()=>{
 const h=lateReadHarness();const done=h.run();h.context.desktopEntryRouteGeneration++;h.state.error='destination loading';h.resolveRead({summary:{id:'source-a'},revision:2});await done;
 assert.equal(h.state.current.revision,1);assert.equal(h.state.error,'destination loading');assert.equal(h.markerReads(),0);assert.equal(h.paints(),0);
});

for(const outcome of ['resolve','reject'])test(`read-marker ${outcome} after source navigation cannot clear or repaint its destination`,{timeout:2000},async()=>{
 const h=lateReadHarness({markers:true});const done=h.run();h.resolveRead({summary:{id:'source-a'},revision:2});await h.markerStarted;assert.equal(h.markerReads(),1);h.navigate();
 if(outcome==='resolve')h.resolveMarkers();else h.rejectMarkers(new Error('old read marker failed'));await done;
 assert.equal(h.state.error,'destination error');assert.equal(h.state.composerDraft,'destination draft');assert.equal(h.paints(),0);
});

test('polling cannot replace a newer same-source snapshot while the command remains active',async()=>{
 const h=lateReadHarness({poll:true});const done=h.run();await new Promise(ok=>queueMicrotask(ok));const fresh={summary:{id:'source-a'},revision:9};h.state.current=fresh;h.resolveRead({summary:{id:'source-a'},revision:2});await done;
 assert.equal(h.state.current,fresh);assert.equal(h.paints(),0);
});

test('current runtime failure remains actionable on its original snapshot',async()=>{
 const h=lateReadHarness();const done=h.run();h.rejectRead(new Error('current snapshot unavailable'));await done;assert.match(h.state.error,/current snapshot unavailable/);assert.equal(h.paints(),1);
});
