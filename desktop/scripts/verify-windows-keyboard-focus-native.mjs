import assert from 'node:assert/strict';
import { readFile, writeFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';

const [port, mode, output] = process.argv.slice(2);
assert.ok(['run', 'cold'].includes(mode));
const tabs = await fetch(`http://127.0.0.1:${Number(port)}/json/list`).then(r => r.json());
const tab = tabs.find(t => t.type === 'page' && t.url === 'http://tauri.localhost/');
assert.ok(tab);
const socket = new WebSocket(tab.webSocketDebuggerUrl);
let serial = 0;
const pending = new Map();
socket.addEventListener('message', event => {
  const message = JSON.parse(event.data), item = pending.get(message.id);
  if (!item) return;
  pending.delete(message.id); clearTimeout(item.timer);
  if (message.error) item.reject(new Error(message.error.message)); else item.resolve(message.result);
});
await new Promise((resolve, reject) => { socket.addEventListener('open', resolve, { once: true }); socket.addEventListener('error', reject, { once: true }); });
const request = (method, params = {}) => new Promise((resolve, reject) => {
  const id = ++serial, timer = setTimeout(() => { pending.delete(id); reject(new Error(`${method} timeout`)); }, 10000);
  pending.set(id, { resolve, reject, timer }); socket.send(JSON.stringify({ id, method, params }));
});
const js = JSON.stringify;
const evaluate = async expression => {
  const result = await request('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true });
  if (result.exceptionDetails) throw new Error(result.exceptionDetails.exception?.description || result.exceptionDetails.text);
  return result.result.value;
};
const invoke = (area, command, args = {}) => evaluate(`window.__TAURI__.core.invoke(${js(command)},${js(args)},{headers:{'x-nanfeng-data-area':${js(area)}}})`);
const until = async (check, label) => { const end = Date.now() + 15000; while (Date.now() < end) { if (await check()) return; await new Promise(r => setTimeout(r, 100)); } throw new Error(label); };
const hash = value => createHash('sha256').update(js(value)).digest('hex');
const key = async (name, code, virtual, modifiers = 0) => {
  await request('Input.dispatchKeyEvent', { type: name === 'Enter' ? 'keyDown' : 'rawKeyDown', key: name, code, windowsVirtualKeyCode: virtual, nativeVirtualKeyCode: virtual, modifiers, ...(name === 'Enter' ? { text: '\r', unmodifiedText: '\r' } : {}) });
  await request('Input.dispatchKeyEvent', { type: 'keyUp', key: name, code, windowsVirtualKeyCode: virtual, nativeVirtualKeyCode: virtual, modifiers });
};
const click = selector => evaluate(`document.querySelector(${js(selector)}).click();true`);
const focus = selector => evaluate(`document.querySelector(${js(selector)}).focus();true`);
const snapshot = () => evaluate(`(()=>{const layer=[...document.querySelectorAll('.scrim,.android-settings-picker-scrim')].at(-1);const items=layer?[...layer.querySelectorAll('button:not(:disabled),input:not(:disabled),textarea:not(:disabled),select:not(:disabled),a[href],[tabindex="0"]')].filter(e=>e.getClientRects().length&&!e.closest('[inert]')):[];const a=document.activeElement;return {layer:Boolean(layer),focusInside:Boolean(layer?.contains(a)),index:items.indexOf(a),count:items.length,action:a?.dataset.action||null,picker:a?.dataset.picker||null,tag:a?.tagName,inertCount:document.querySelectorAll('[inert]').length}})()`);
const record = mode === 'cold' ? JSON.parse(await readFile(output, 'utf8')) : { injection: 'WebView2 CDP', physicalKeyboard: false, normalStartup: false, areas: {}, checks: [] };
const check = (name, pass, detail) => record.checks.push({ name, pass, detail });
try {
  await until(() => evaluate('Boolean(window.__TAURI__?.core?.invoke&&globalThis.__nanfengDesktopWorkState?.current)'), 'IPC and UI ready');
  const runtime = await invoke('CHAT', 'read_desktop_runtime_info');
  assert.equal(runtime.platform, 'windows'); assert.equal(runtime.startupMode, 'UI_SCHEMA_DIAGNOSTIC_ACCEPTANCE'); assert.equal(runtime.automaticWorkSuppressed, true);
  record.runtime = runtime;
  for (const area of ['CHAT', 'WORK']) {
    if (mode === 'cold') {
      const saved = record.areas[area];
      assert.deepEqual(await invoke(area, 'read_desktop_conversation_draft', { workspaceId: saved.workspaceId, conversationId: null }), saved.draft);
      assert.equal(hash((await invoke(area, 'read_desktop_workspace', { workspaceId: saved.workspaceId })).exchange), saved.exchangeHash);
      continue;
    }
    await click(`[data-action=show-${area === 'CHAT' ? 'chat' : 'work'}]`);
    await until(() => evaluate(`globalThis.__nanfengDesktopWorkState.dataArea===${js(area)}&&Boolean(document.querySelector('#chat-composer'))`), 'area composer');
    const workspaceId = (await invoke(area, 'ensure_desktop_local_workspace')).summary.id;
    const before = (await invoke(area, 'read_desktop_workspace', { workspaceId })).exchange;
    await focus('#chat-composer');
    await evaluate(`(()=>{const e=document.querySelector('#chat-composer');e.setSelectionRange(0,e.value.length);return true})()`);
    await request('Input.insertText', { text: `${area} keyboard focus synthetic draft` });
    await until(async () => (await invoke(area, 'read_desktop_conversation_draft', { workspaceId, conversationId: null }))?.text === `${area} keyboard focus synthetic draft`, 'draft persisted');
    await key('Tab', 'Tab', 9); const forward = await snapshot();
    await key('Tab', 'Tab', 9, 8); const backward = await snapshot();
    check(`${area} composer Tab/ShiftTab`, backward.tag === 'TEXTAREA', { forward, backward });
    await click('[data-action=show-settings]');
    await until(() => evaluate('Boolean(document.querySelector("[data-action=open-settings-picker][data-picker=themeColor]"))'), 'settings ready');
    const trigger = '[data-action=open-settings-picker][data-picker=themeColor]';
    await focus(trigger); await key('Enter', 'Enter', 13);
    await until(async () => (await snapshot()).layer, 'picker opened by keyboard');
    let current = await snapshot(); check(`${area} initial modal focus`, current.focusInside, current);
    // Wrap in both directions and traverse every visible modal control.
    await evaluate(`(()=>{const l=document.querySelector('.android-settings-picker-scrim');const b=[...l.querySelectorAll('button')].filter(e=>e.getClientRects().length&&!e.disabled);b[0].focus();return true})()`);
    await key('Tab', 'Tab', 9, 8); current = await snapshot(); check(`${area} ShiftTab wraps`, current.focusInside && current.index === current.count - 1, current);
    await key('Tab', 'Tab', 9); current = await snapshot(); check(`${area} Tab wraps`, current.focusInside && current.index === 0, current);
    for (let i = 0; i < current.count + 1; i++) { await key('Tab', 'Tab', 9); const s = await snapshot(); check(`${area} modal tab ${i}`, s.focusInside, s); }
    await evaluate(`globalThis.__keyboardShortcutLeak=0;globalThis.__keyboardShortcutHandler=e=>{if(e.ctrlKey)globalThis.__keyboardShortcutLeak++};window.addEventListener('keydown',globalThis.__keyboardShortcutHandler);true`);
    await key('o', 'KeyO', 79, 2);
    const shortcutLeaks = await evaluate('globalThis.__keyboardShortcutLeak');
    await evaluate('window.removeEventListener("keydown",globalThis.__keyboardShortcutHandler);true');
    check(`${area} modal CtrlO consumed`, shortcutLeaks === 0 && (await snapshot()).layer, { shortcutLeaks });
    await evaluate(`globalThis.__keyboardEscapeLeak=0;globalThis.__keyboardLeakHandler=e=>{if(e.key==='Escape')globalThis.__keyboardEscapeLeak++};window.addEventListener('keydown',globalThis.__keyboardLeakHandler);true`);
    await key('Escape', 'Escape', 27);
    await until(async () => !(await snapshot()).layer, 'Esc closes picker');
    const after = await snapshot(), leaks = await evaluate('globalThis.__keyboardEscapeLeak');
    await evaluate('window.removeEventListener("keydown",globalThis.__keyboardLeakHandler);true');
    check(`${area} Esc consumed`, leaks === 0, { leaks });
    check(`${area} keyboard trigger restored`, after.action === 'open-settings-picker' && after.picker === 'themeColor', after);
    check(`${area} inert restored`, after.inertCount === 0, after);
    const fontTrigger = '[data-action=open-settings-picker][data-picker=fontSize]';
    await focus(fontTrigger); await key(' ', 'Space', 32);
    await until(async () => (await snapshot()).layer, 'picker opened by Space');
    await key('Escape', 'Escape', 27);
    await until(async () => !(await snapshot()).layer, 'Space picker dismissed');
    const spaceAfter = await snapshot();
    check(`${area} Space trigger restored`, spaceAfter.action === 'open-settings-picker' && spaceAfter.picker === 'fontSize', spaceAfter);
    // Pointer activation follows the same exact invoker identity.
    record.pointerBefore = await evaluate(`(()=>{const s=globalThis.__nanfengDesktopWorkState;return Object.fromEntries(['dataArea','settingsPicker','contextMenu','assistantMessageMenu','composerAddOpen','p6gModelPickerOpen','temporaryModelOpen','profileOpen','dialog','imagePreview','pdfPreview','archivePreview','previewBoundary','sidebarOpen'].map(k=>[k,s[k]??null]))})()`);
    await evaluate(`globalThis.__focusPointerEvents=[];globalThis.__focusPointerHandler=e=>globalThis.__focusPointerEvents.push({type:e.type,action:e.target.closest?.('[data-action]')?.dataset.action||null,connected:e.target.isConnected,trusted:e.isTrusted});for(const type of ['pointerdown','mousedown','pointerup','mouseup','click'])document.addEventListener(type,globalThis.__focusPointerHandler,true);true`);
    const point = await evaluate(`(async()=>{await new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)));const e=document.querySelector(${js(trigger)});e.scrollIntoView({block:'nearest'});const r=e.getBoundingClientRect();const x=r.x+r.width/2,y=r.y+r.height/2;if(!e.contains(document.elementFromPoint(x,y)))throw Error('pointer trigger not hit-testable');return {x,y}})()`);
    await request('Input.dispatchMouseEvent', { type: 'mouseMoved', ...point });
    await request('Input.dispatchMouseEvent', { type: 'mousePressed', ...point, button: 'left', buttons: 1, clickCount: 1 });
    await request('Input.dispatchMouseEvent', { type: 'mouseReleased', ...point, button: 'left', buttons: 0, clickCount: 1 });
    await until(async () => (await snapshot()).layer, 'pointer picker opened');
    await key('Escape', 'Escape', 27);
    await until(async () => !(await snapshot()).layer, 'pointer picker dismissed');
    const pointerAfter = await snapshot();
    check(`${area} pointer exact trigger restored`, pointerAfter.action === 'open-settings-picker' && pointerAfter.picker === 'themeColor', pointerAfter);
    record.pointerEvents = await evaluate('globalThis.__focusPointerEvents');
    await evaluate(`for(const type of ['pointerdown','mousedown','pointerup','mouseup','click'])document.removeEventListener(type,globalThis.__focusPointerHandler,true);true`);
    await click('[data-action=show-chat]');
    await until(() => evaluate('Boolean(document.querySelector("#chat-composer"))'), 'return composer');
    const draft = await invoke(area, 'read_desktop_conversation_draft', { workspaceId, conversationId: null });
    const exchangeHash = hash((await invoke(area, 'read_desktop_workspace', { workspaceId })).exchange);
    check(`${area} no message or workspace mutation`, exchangeHash === hash(before), { exchangeHash });
    check(`${area} draft preserved`, draft.text === `${area} keyboard focus synthetic draft`, { text: draft.text });
    record.areas[area] = { workspaceId, draft, exchangeHash };
  }
  if (mode === 'cold') record.coldReadback = true;
  record.passed = record.checks.every(c => c.pass);
  await writeFile(output, js(record, null, 2));
  console.log(js({ passed: record.passed, coldReadback: record.coldReadback || false, failed: record.checks.filter(c => !c.pass) }));
  if (!record.passed) process.exitCode = 1;
} catch (error) {
  record.error = String(error); record.passed = false;
  try { record.failurePointerEvents = await evaluate('globalThis.__focusPointerEvents||null'); } catch {}
  await writeFile(output, js(record, null, 2));
  throw error;
} finally { socket.close(); }
