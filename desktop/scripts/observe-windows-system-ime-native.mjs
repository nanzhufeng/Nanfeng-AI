import assert from 'node:assert/strict';
import {readFile, writeFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';

// Read-only IPC observation after separately targeted system keyboard actions.
// This collector deliberately does not classify an untrusted end as accepted.
const [port, output, expectedPath] = process.argv.slice(2);
assert.ok(port && output, 'port and a new evidence output are required');
const expected = expectedPath ? JSON.parse(await readFile(expectedPath, 'utf8')) : null;
const expectedIds = expected ? Object.fromEntries(Object.entries(expected.areas).map(([area,value]) => [area,value.workspaceId])) : {};
let page;
for (const deadline = Date.now() + 30000; Date.now() < deadline;) {
  try {
    page = (await fetch(`http://127.0.0.1:${port}/json/list`, {signal: AbortSignal.timeout(2000)}).then(r => r.json()))
      .find(p => p.type === 'page' && p.url === 'http://tauri.localhost/');
    if (page) break;
  } catch {}
  await new Promise(resolve => setTimeout(resolve, 200));
}
assert.ok(page, 'isolated WebView2 ready');
const socket = new WebSocket(page.webSocketDebuggerUrl);
await new Promise((resolve, reject) => {socket.addEventListener('open', resolve, {once:true}); socket.addEventListener('error', reject, {once:true});});
let nextId = 0;
const pending = new Map();
socket.addEventListener('message', event => {
  const message = JSON.parse(event.data), waiter = pending.get(message.id);
  if (!waiter) return;
  pending.delete(message.id); clearTimeout(waiter.timer);
  if (message.error) waiter.reject(new Error(message.error.message)); else waiter.resolve(message.result);
});
const evaluate = expression => new Promise((resolve, reject) => {
  const id = ++nextId, timer = setTimeout(() => {pending.delete(id); reject(new Error('observation timeout'));}, 15000);
  pending.set(id, {timer, resolve: result => result.exceptionDetails ? reject(new Error(result.exceptionDetails.exception?.description || result.exceptionDetails.text)) : resolve(result.result?.value), reject});
  socket.send(JSON.stringify({id, method:'Runtime.evaluate', params:{expression, awaitPromise:true, returnByValue:true}}));
});
const hash = value => createHash('sha256').update(JSON.stringify(value)).digest('hex');
try {
  let ready = false;
  for (const deadline = Date.now() + 30000; Date.now() < deadline;) {
    try { ready = await evaluate('Boolean(window.__TAURI__?.core?.invoke)'); } catch {}
    if (ready) break;
    await new Promise(resolve => setTimeout(resolve, 200));
  }
  assert.equal(ready, true, 'native IPC ready after navigation');
  const observed = await evaluate(`(async()=>{
    const result={areas:{},route:globalThis.__nanfengDesktopWorkState?.dataArea,active:document.activeElement?.id,
      systemEvents:globalThis.__systemImeEvents??[],workEvents:globalThis.__workImeEvents??[]};
    for(const area of ['CHAT','WORK']){
      const headers={headers:{'x-nanfeng-data-area':area}};
      const id=globalThis.__imeBaseline?.[area]?.summary?.id??${JSON.stringify(expectedIds)}[area];
      if(!id)throw new Error('preexisting isolated workspace identity required');
      const current=await window.__TAURI__.core.invoke('read_desktop_workspace',{workspaceId:id},headers);
      const draft=await window.__TAURI__.core.invoke('read_desktop_conversation_draft',{workspaceId:id,conversationId:null},headers);
      result.areas[area]={workspaceId:id,exchange:current.exchange,draft};
    }
    return result;
  })()`);
  for (const area of ['CHAT','WORK']) observed.areas[area].exchangeSha256 = hash(observed.areas[area].exchange);
  observed.trustedCompositionEnd = [...observed.systemEvents, ...observed.workEvents].some(e => e.type === 'compositionend' && e.trusted);
  observed.strictImeAcceptance = false;
  observed.classification = 'OBSERVATION_ONLY_NOT_F39_COMPLETE';
  // wx prevents overwriting any prior receipt, including failed runs.
  await writeFile(output, JSON.stringify(observed, null, 2), {flag:'wx'});
  if (expectedPath) {
    for (const area of ['CHAT','WORK']) {
      assert.equal(observed.areas[area].workspaceId, expected.areas[area].workspaceId);
      assert.equal(observed.areas[area].exchangeSha256, expected.areas[area].exchangeSha256);
      assert.deepEqual(observed.areas[area].draft, expected.areas[area].draft, `${area} exact draft cold reopen`);
    }
  }
  console.log(JSON.stringify({captured:true, exactColdReadback:Boolean(expectedPath), strictImeAcceptance:false,
    areas:Object.fromEntries(Object.entries(observed.areas).map(([area,value])=>[area,{text:value.draft?.text,conversations:value.exchange.conversations.length,exchangeSha256:value.exchangeSha256}]))}));
} finally {socket.close();}
