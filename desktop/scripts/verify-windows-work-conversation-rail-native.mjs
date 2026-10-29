import assert from 'node:assert/strict';
import { writeFile } from 'node:fs/promises';

const port = Number(process.argv[2]);
const screenshotPath = process.argv[3];
assert.ok(Number.isInteger(port) && port > 0, 'CDP port required');
assert.ok(screenshotPath, 'synthetic screenshot path required');
const tag = `rail-${Date.now()}`;
const chatTitle = `CHAT ${tag}`;
const chatAutoTitle = `CHAT 自动 ${tag}`;
const workTitle = `WORK ${tag}`;
const projectTitle = `项目 ${tag}`;
const projectChatTitle = `WORK 项目 ${tag}`;

async function evaluate(expression) {
  const tabs = await fetch(`http://127.0.0.1:${port}/json/list`).then(response => response.json());
  const tab = tabs.find(item => item.type === 'page' && item.url === 'http://tauri.localhost/');
  if (!tab) throw new Error('isolated Tauri page unavailable');
  const socket = new WebSocket(tab.webSocketDebuggerUrl);
  try {
    await new Promise((resolve, reject) => {
      socket.addEventListener('open', resolve, { once: true });
      socket.addEventListener('error', reject, { once: true });
    });
    return await new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error('CDP evaluation timed out')), 20000);
      socket.addEventListener('message', event => {
        const message = JSON.parse(event.data);
        if (message.id !== 1) return;
        clearTimeout(timer);
        if (message.error || message.result?.exceptionDetails) reject(new Error(`CDP evaluation failed: ${message.result?.exceptionDetails?.exception?.description || message.result?.exceptionDetails?.text || message.error?.message || 'unknown'}`));
        else resolve(message.result?.result?.value);
      });
      socket.send(JSON.stringify({ id: 1, method: 'Runtime.evaluate', params: {
        expression, awaitPromise: true, returnByValue: true,
      } }));
    });
  } finally {
    socket.close();
  }
}

async function captureScreenshot(path) {
  const tabs = await fetch(`http://127.0.0.1:${port}/json/list`).then(response => response.json());
  const tab = tabs.find(item => item.type === 'page' && item.url === 'http://tauri.localhost/');
  if (!tab) throw new Error('isolated Tauri page unavailable');
  const socket = new WebSocket(tab.webSocketDebuggerUrl);
  try {
    await new Promise((resolve, reject) => {
      socket.addEventListener('open', resolve, { once: true });
      socket.addEventListener('error', reject, { once: true });
    });
    const data = await new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error('CDP screenshot timed out')), 20000);
      socket.addEventListener('message', event => {
        const message = JSON.parse(event.data);
        if (message.id !== 1) return;
        clearTimeout(timer);
        if (message.error || !message.result?.data) reject(new Error('CDP screenshot failed'));
        else resolve(message.result.data);
      });
      socket.send(JSON.stringify({ id: 1, method: 'Page.captureScreenshot', params: { format: 'png', captureBeyondViewport: false } }));
    });
    await writeFile(path, Buffer.from(data, 'base64'));
  } finally {
    socket.close();
  }
}

async function until(check, label) {
  const deadline = Date.now() + 30000;
  while (Date.now() < deadline) {
    try {
      const result = await check();
      if (result) return result;
    } catch { /* WebView2 may still be loading or changing areas. */ }
    await new Promise(resolve => setTimeout(resolve, 250));
  }
  throw new Error(`${label} did not become ready`);
}

const js = value => JSON.stringify(value);
const click = selector => evaluate(`(()=>{const e=document.querySelector(${js(selector)});if(!e)return false;e.click();return true})()`);
async function invoke(area, command, args = {}) {
  const result = await evaluate(`window.__TAURI__.core.invoke(${js(command)},${js(args)},${js({ headers: { 'x-nanfeng-data-area': area } })}).catch(error=>({__error:String(error)}))`);
  if (result?.__error) throw new Error(`${command}: ${result.__error}`);
  return result;
}
const rail = area => evaluate(`(()=>{const side=document.querySelector(${js(`.chat-sidebar[aria-label="${area === 'WORK' ? '工作导航' : '对话导航'}"]`)});if(!side)return null;return {history:!!side.querySelector('.chat-history[aria-label="会话列表"]'),search:!!side.querySelector('#chat-search'),local:!!side.querySelector('[aria-label="本地会话"]'),cloud:!!side.querySelector('[aria-label="云端会话"]'),settings:!!side.querySelector('[data-action="show-settings"]'),newChat:!!side.querySelector('[data-action="new-chat"]'),text:side.innerText}})()`);

await until(() => evaluate("Boolean(window.__TAURI__?.core?.invoke&&document.querySelector('.chat-sidebar[aria-label=对话导航]'))"), 'CHAT shell');
const runtime = await invoke('CHAT', 'read_desktop_runtime_info');
assert.equal(runtime.version, '0.6.0-p6d-dev', 'Windows acceptance must not inherit the Mac release version');
assert.equal(runtime.platform, 'windows');
const dynamicStyleAllowed = await evaluate("(()=>{const style=document.createElement('style');style.textContent='.nfai-csp-probe{color:rgb(17, 34, 51)}';const probe=document.createElement('span');probe.className='nfai-csp-probe';document.head.append(style);document.body.append(probe);const applied=getComputedStyle(probe).color==='rgb(17, 34, 51)';probe.remove();style.remove();return applied})()");
assert.equal(dynamicStyleAllowed, true, 'Windows overlay must permit local dynamic styles');
console.error('stage: CHAT ready');
const chatWorkspaces = await until(async () => {
  const rows = await invoke('CHAT', 'list_desktop_workspaces');
  return rows.length ? rows : null;
}, 'CHAT local workspace');
const chatConversation = await invoke('CHAT', 'mutate_desktop_domain', { args: {
  intentId: `${tag}-chat`, workspaceId: chatWorkspaces[0].id, entity: 'conversation', action: 'create',
  fields: { title: chatTitle, projectId: null, firstMessage: 'CHAT 隔离合成内容' },
} });
const chatAutoConversation = await invoke('CHAT', 'mutate_desktop_domain', { args: {
  intentId: `${tag}-chat-auto`, workspaceId: chatWorkspaces[0].id, entity: 'conversation', action: 'create',
  fields: { title: chatAutoTitle, projectId: null, firstMessage: 'CHAT 自动隔离合成内容' },
} });
let chatCatalog = await invoke('CHAT', 'read_desktop_p6g_catalog');
if (!chatCatalog.snapshot?.candidates?.some(item => item.modelId)) {
  await invoke('CHAT', 'upsert_desktop_p6g_catalog_candidate', { args: {
    expectedRevision: chatCatalog.revision, catalogVersion: 'chat-rail-synthetic-v1', policyVersion: 1,
    candidate: { providerFamily: 'OPENAI', providerId: 'OPENROUTER', modelId: 'GPT_5_6_TERRA',
      displayName: 'GPT-5.6 Terra', tiers: ['BALANCED'], capabilities: ['TEXT'], available: true,
      knownCostMicros: null, latencyRank: 1, contextWindowTokens: 4096, maxOutputTokens: 1024 },
  } });
  chatCatalog = await invoke('CHAT', 'read_desktop_p6g_catalog');
}
const chatModelId = chatCatalog.snapshot?.candidates?.find(item => item.modelId)?.modelId;
assert.ok(chatModelId, 'CHAT model catalog has no candidate');
const chatSelection = await invoke('CHAT', 'read_desktop_p6g_selection', {
  workspaceId: chatWorkspaces[0].id, conversationId: chatConversation.objectId,
});
await invoke('CHAT', 'set_desktop_p6g_conversation_override', { args: {
  workspaceId: chatWorkspaces[0].id, conversationId: chatConversation.objectId,
  expectedRevision: chatSelection.conversationOverride.revision, modelId: chatModelId,
} });
console.error('stage: CHAT conversation created');
assert.equal(await click('[data-action="show-work"]'), true, 'WORK entry missing');
await until(() => rail('WORK'), 'WORK rail');
console.error('stage: WORK ready');
const workWorkspaces = await until(async () => {
  const rows = await invoke('WORK', 'list_desktop_workspaces');
  return rows.length ? rows : null;
}, 'WORK local workspace');
const workConversation = await invoke('WORK', 'mutate_desktop_domain', { args: {
  intentId: `${tag}-work`, workspaceId: workWorkspaces[0].id, entity: 'conversation', action: 'create',
  fields: { title: workTitle, projectId: null, firstMessage: 'WORK 隔离合成内容' },
} });
const project = await invoke('WORK', 'mutate_desktop_domain', { args: {
  intentId: `${tag}-project`, workspaceId: workWorkspaces[0].id, entity: 'project', action: 'create',
  fields: { title: projectTitle, description: '隔离验收项目' },
} });
const projectConversation = await invoke('WORK', 'mutate_desktop_domain', { args: {
  intentId: `${tag}-project-chat`, workspaceId: workWorkspaces[0].id, entity: 'conversation', action: 'create',
  fields: { title: projectChatTitle, projectId: project.objectId, firstMessage: 'WORK 项目隔离合成内容' },
} });
let catalog = await invoke('WORK', 'read_desktop_p6g_catalog');
if (!catalog.snapshot?.candidates?.some(item => item.modelId)) {
  await invoke('WORK', 'upsert_desktop_p6g_catalog_candidate', { args: {
    expectedRevision: catalog.revision, catalogVersion: 'work-rail-synthetic-v1', policyVersion: 1,
    candidate: { providerFamily: 'OPENAI', providerId: 'OPENROUTER', modelId: 'GPT_5_6_TERRA',
      displayName: 'GPT-5.6 Terra', tiers: ['BALANCED'], capabilities: ['TEXT'], available: true,
      knownCostMicros: null, latencyRank: 1, contextWindowTokens: 4096, maxOutputTokens: 1024 },
  } });
  catalog = await invoke('WORK', 'read_desktop_p6g_catalog');
}
const workModelId = catalog.snapshot?.candidates?.find(item => item.modelId)?.modelId;
assert.ok(workModelId, 'WORK model catalog has no candidate');
const workSelection = await invoke('WORK', 'read_desktop_p6g_selection', {
  workspaceId: workWorkspaces[0].id, conversationId: workConversation.objectId,
});
await invoke('WORK', 'set_desktop_p6g_conversation_override', { args: {
  workspaceId: workWorkspaces[0].id, conversationId: workConversation.objectId,
  expectedRevision: workSelection.conversationOverride.revision, modelId: workModelId,
} });
console.error('stage: WORK conversation created');
assert.equal(await click('.chat-sidebar[aria-label="工作导航"] [data-action="show-chat"]'), true, 'CHAT return missing');
await until(() => rail('CHAT'), 'CHAT return rail');
assert.equal(await click('.chat-sidebar[aria-label="对话导航"] [data-action="new-chat"]'), true, 'CHAT new-chat button missing');
await until(() => evaluate("Boolean(document.querySelector('[data-action=show-work]'))"), 'WORK entry after CHAT new chat');
assert.equal(await click('[data-action="show-work"]'), true, 'WORK reentry missing');
const work = await until(async () => {
  const state = await rail('WORK');
  return state?.text.includes(workTitle) ? state : null;
}, 'WORK conversation rail');
for (const key of ['history', 'search', 'local', 'cloud', 'settings', 'newChat']) assert.equal(work[key], true, `WORK rail ${key} missing`);
assert.equal(work.text.includes(chatTitle), false, 'CHAT title leaked into WORK rail');
assert.equal(work.text.includes(projectChatTitle), true, 'project conversation missing at WORK root');
assert.equal(await click('.chat-sidebar[aria-label="工作导航"] [data-action="show-projects"]'), true, 'WORK projects entry missing');
await until(() => evaluate(`Boolean(document.querySelector('[data-action=select-work-project][data-project-id=${js(project.objectId)}]'))`), 'WORK project card');
assert.equal(await click(`[data-action="select-work-project"][data-project-id="${project.objectId}"]`), true, 'WORK project select missing');
const scoped = await until(async () => {
  const state = await rail('WORK');
  return state?.text.includes(projectChatTitle) ? state : null;
}, 'project scoped rail');
assert.equal(scoped.text.includes(workTitle), false, 'unscoped WORK title leaked into selected project');
assert.equal(await click('.chat-sidebar[aria-label="工作导航"] [data-action="toggle-conversation-batch-edit"]'), true, 'project batch entry missing');
await until(() => evaluate("document.querySelectorAll('.chat-sidebar[aria-label=工作导航] .chat-history-row.batch-editing').length === 1"), 'project batch scope');
assert.equal(await click('.chat-sidebar[aria-label="工作导航"] [data-action="toggle-conversation-batch-edit"]'), true, 'project batch exit missing');
assert.equal(await click(`.chat-sidebar[aria-label="工作导航"] [data-action="select-chat"][data-id="${projectConversation.objectId}"]`), true, 'project row not selectable');
await until(() => evaluate(`Boolean(document.querySelector('.chat-main')?.innerText.includes(${js('WORK 项目隔离合成内容')}))`), 'project conversation opened');
assert.deepEqual(await until(() => evaluate(`(()=>{const s=globalThis.__nanfengDesktopWorkState;return s?.selectedConversationId===${js(projectConversation.objectId)}&&s.p6gSelection?{modelId:s.p6gSelection.conversationOverride?.modelId||null}:null})()`), 'project model selection'), { modelId: null }, 'project conversation must remain automatic');
assert.equal(await evaluate("document.querySelector('[data-action=toggle-p6g-model-picker]')?.innerText.trim()"), '自动选择', 'project model label must remain automatic');
assert.equal(await click('.chat-sidebar[aria-label="工作导航"] [data-action="show-work"]'), true, 'WORK root return missing');
await until(async () => (await rail('WORK'))?.text.includes(workTitle), 'WORK root rail return');
assert.equal(await click(`.chat-sidebar[aria-label="工作导航"] [data-action="select-chat"][data-id="${workConversation.objectId}"]`), true, 'WORK row not selectable');
await until(() => evaluate(`Boolean(document.querySelector('.chat-sidebar[aria-label="工作导航"]')&&document.querySelector('.chat-main')?.innerText.includes(${js('WORK 隔离合成内容')}))`), 'WORK conversation opened');
await until(() => evaluate(`globalThis.__nanfengDesktopWorkState?.selectedConversationId===${js(workConversation.objectId)}&&globalThis.__nanfengDesktopWorkState?.p6gSelection?.conversationOverride?.modelId===${js(workModelId)}`), 'WORK model selection');
assert.equal(await evaluate("document.querySelector('[data-action=toggle-p6g-model-picker]')?.innerText.trim()"), 'GPT-5.6 Terra', 'WORK model label must show its selected model');
assert.equal(await click('.chat-sidebar[aria-label="工作导航"] [data-action="new-chat"]'), true, 'WORK new-chat button missing');
await until(() => evaluate("Boolean(document.querySelector('.chat-sidebar[aria-label=工作导航]')&&document.querySelector('.chat-main #chat-composer'))"), 'WORK new-chat route');
await captureScreenshot(screenshotPath);

assert.equal(await click('.chat-sidebar[aria-label="工作导航"] [data-action="show-chat"]'), true, 'CHAT return missing');
const chat = await until(async () => {
  const state = await rail('CHAT');
  return state?.text.includes(chatTitle) ? state : null;
}, 'CHAT conversation rail');
assert.equal(chat.text.includes(workTitle), false, 'WORK title leaked into CHAT rail');
for (const key of ['history', 'search', 'local', 'cloud', 'settings', 'newChat']) assert.equal(chat[key], true, `CHAT rail ${key} missing`);
assert.equal(await click(`.chat-sidebar[aria-label="对话导航"] [data-action="select-chat"][data-id="${chatAutoConversation.objectId}"]`), true, 'CHAT automatic row not selectable');
await until(() => evaluate(`globalThis.__nanfengDesktopWorkState?.selectedConversationId===${js(chatAutoConversation.objectId)}&&globalThis.__nanfengDesktopWorkState?.p6gSelection?.conversationOverride?.modelId==null`), 'CHAT automatic model selection');
assert.equal(await evaluate("document.querySelector('[data-action=toggle-p6g-model-picker]')?.innerText.trim()"), '自动选择', 'CHAT automatic model label changed');
assert.equal(await click(`.chat-sidebar[aria-label="对话导航"] [data-action="select-chat"][data-id="${chatConversation.objectId}"]`), true, 'CHAT selected row not selectable');
await until(() => evaluate(`globalThis.__nanfengDesktopWorkState?.selectedConversationId===${js(chatConversation.objectId)}&&globalThis.__nanfengDesktopWorkState?.p6gSelection?.conversationOverride?.modelId===${js(chatModelId)}`), 'CHAT saved model selection');
assert.equal(await evaluate("document.querySelector('[data-action=toggle-p6g-model-picker]')?.innerText.trim()"), 'GPT-5.6 Terra', 'CHAT saved model label missing');

console.log(JSON.stringify({ runtimeVersion: runtime.version, dynamicStyleAllowed, workTitle, projectChatTitle, chatTitle, workModelId, chatModelId, workModelSelectionReadBack: true, chatModelSelectionReadBack: true, workRailComplete: true, chatRailComplete: true, projectScoped: true, projectBatchScope: true, workConversationOpened: true, workNewChatStayedInWork: true, crossAreaTitleLeak: false, screenshotPath }));
