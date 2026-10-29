import assert from 'node:assert/strict';

const port = Number(process.argv[2]);
const phase = process.argv[3];
const expectedWorkspaceId = process.argv[4] || '';
const earlyWork = phase === 'create' && process.argv.includes('--early-work');
assert.ok(Number.isInteger(port) && port > 0);
assert.ok(['create', 'reopen'].includes(phase));
const js = value => JSON.stringify(value);

async function cdp(method, params = {}) {
  const tabs = await fetch(`http://127.0.0.1:${port}/json/list`).then(response => response.json());
  const tab = tabs.find(item => item.type === 'page' && item.url === 'http://tauri.localhost/');
  assert.ok(tab, 'isolated Tauri page unavailable');
  const socket = new WebSocket(tab.webSocketDebuggerUrl);
  try {
    await new Promise((resolve, reject) => {
      socket.addEventListener('open', resolve, { once: true });
      socket.addEventListener('error', reject, { once: true });
    });
    return await new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error(`CDP ${method} timed out`)), 20000);
      socket.addEventListener('message', event => {
        const message = JSON.parse(event.data);
        if (message.id !== 1) return;
        clearTimeout(timer);
        if (message.error || message.result?.exceptionDetails) reject(new Error(message.result?.exceptionDetails?.text || message.error?.message || 'CDP error'));
        else resolve(message.result);
      });
      socket.send(JSON.stringify({ id: 1, method, params }));
    });
  } finally {
    socket.close();
  }
}
const evaluate = async expression => (await cdp('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true }))?.result?.value;
const click = selector => evaluate(`(()=>{const element=document.querySelector(${js(selector)});if(!element)return false;element.click();return true})()`);
const fill = (selector, value) => evaluate(`(()=>{const element=document.querySelector(${js(selector)});if(!element)return false;element.value=${js(value)};element.dispatchEvent(new Event(element.tagName==='SELECT'?'change':'input',{bubbles:true}));return true})()`);
async function until(check, label) {
  const deadline = Date.now() + 30000;
  while (Date.now() < deadline) {
    try { const value = await check(); if (value) return value; } catch { /* still starting or rendering */ }
    await new Promise(resolve => setTimeout(resolve, 250));
  }
  throw new Error(`${label} did not become ready`);
}
async function invoke(command, args = {}, area = 'WORK') {
  const result = await evaluate(`window.__TAURI__.core.invoke(${js(command)},${js(args)},${js({ headers: { 'x-nanfeng-data-area': area } })}).catch(error=>({__error:String(error)}))`);
  if (result?.__error) throw new Error(`${command}: ${result.__error}`);
  return result;
}
async function redrawEditor(selector, expectedValues, focusedSelector = selector) {
  const caret = await evaluate(`(()=>{const field=document.querySelector(${js(focusedSelector)});if(!field)return -1;field.focus();const caret=Math.max(0,field.value.length-2);field.setSelectionRange(caret,caret);return caret})()`);
  assert.ok(caret >= 0, `${focusedSelector} can receive caret`);
  assert.equal(await evaluate(`(()=>{window.__editorBefore=document.querySelector(${js(selector)});return Boolean(window.__editorBefore)})()`), true);
  // The window resize owner redraws when dismissing a context menu. Trigger
  // that existing path after recording the actual WebView input element.
  assert.equal(await evaluate('(()=>{const owner=globalThis.__nanfengDesktopWorkState;if(!owner)return false;owner.contextMenu={syntheticAcceptance:true};window.dispatchEvent(new Event("resize"));return true})()'), true);
  await until(() => evaluate(`window.__editorBefore !== document.querySelector(${js(selector)}) && Boolean(document.querySelector(${js(selector)}))`), `${selector} DOM replacement`);
  for (const [field, value] of Object.entries(expectedValues)) {
    assert.equal(await evaluate(`document.querySelector(${js(field)})?.value`), value, `${field} was restored after redraw`);
  }
  await until(() => evaluate(`(()=>{const field=document.querySelector(${js(focusedSelector)});return field === document.activeElement && field?.selectionStart === ${caret}})()`), `${focusedSelector} focus and caret after redraw`);
}

await until(() => evaluate('Boolean(window.__TAURI__?.core?.invoke)'), 'native IPC');
if (earlyWork) {
  await until(() => evaluate('Boolean(document.querySelector("[data-action=show-work]"))'), 'early WORK entry');
  const clicked = await evaluate('(()=>{document.querySelector("[data-action=show-work]").click();const owner=globalThis.__nanfengDesktopWorkState;return {area:owner.dataArea,pane:owner.pane,status:owner.status}})()');
  assert.equal(clicked.area, 'CHAT');
  assert.equal(clicked.pane, 'chat');
  assert.match(clicked.status, /正在完成本地启动/);
} else {
  await until(() => evaluate('globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady === true'), 'data areas ready');
  await invoke('ensure_desktop_local_workspace', {}, 'CHAT');
  await until(() => evaluate('Boolean(document.querySelector("[data-action=show-work]"))'), 'WORK entry');
  await click('[data-action="new-chat"]');
  assert.equal(await click('[data-action="show-work"]'), true);
}
await until(() => evaluate('globalThis.__nanfengDesktopWorkState?.dataArea === "WORK"'), 'WORK data area');
await until(() => evaluate('globalThis.__nanfengDesktopWorkState?.current?.summary?.id'), 'WORK workspace ready');
const workspaceId = (await invoke('ensure_desktop_local_workspace')).summary.id;
if (phase === 'reopen') assert.equal(workspaceId, expectedWorkspaceId);

if (phase === 'create') {
  await until(() => evaluate('Boolean(document.querySelector("[data-action=show-projects]"))'), 'project page entry');
  assert.equal(await click('[data-action="show-projects"]'), true);
  await until(() => evaluate('Boolean(document.querySelector(".work-projects-canvas [data-action=new-project]"))'), 'project editor entry').catch(async error => {
    const snapshot = await evaluate('({pane:globalThis.__nanfengDesktopWorkState?.pane,area:globalThis.__nanfengDesktopWorkState?.dataArea,current:globalThis.__nanfengDesktopWorkState?.current?.summary?.id,actions:[...document.querySelectorAll("[data-action]")].map(n=>n.dataset.action).filter(x=>x.includes("project")).slice(0,12),body:document.body?.innerText?.slice(0,350)})');
    throw new Error(`${error.message}: ${JSON.stringify(snapshot)}`);
  });
  assert.equal(await click('[data-action="new-project"]'), true);
  assert.equal(await fill('#project-title', '重绘后的合成项目'), true);
  assert.equal(await fill('#project-description', '说明保留到保存'), true);
  await redrawEditor('#project-title', { '#project-title': '重绘后的合成项目', '#project-description': '说明保留到保存' }, '#project-description');
  assert.equal(await click('[data-action="save-project"]'), true);
  const project = await until(async () => (await invoke('read_desktop_workspace', { workspaceId })).exchange.projects.find(item => item.title === '重绘后的合成项目'), 'project save');
  assert.equal(project.description, '说明保留到保存');

  assert.equal(await click('[data-action="show-knowledge"]'), true);
  await until(() => evaluate('Boolean(document.querySelector(".work-knowledge-canvas [data-action=new-knowledge]"))'), 'knowledge editor entry');
  assert.equal(await click('[data-action="new-knowledge"]'), true);
  await until(() => evaluate(`Boolean(document.querySelector('#knowledge-project option[value=${js(project.id)}]'))`), 'knowledge project option');
  assert.equal(await fill('#knowledge-project', project.id), true);
  assert.equal(await fill('#knowledge-title', '重绘后的合成知识'), true);
  assert.equal(await fill('#knowledge-body', '知识正文保留到保存'), true);
  assert.equal(await fill('#knowledge-tags', '重绘标签'), true);
  await redrawEditor('#knowledge-title', { '#knowledge-project': project.id, '#knowledge-title': '重绘后的合成知识', '#knowledge-body': '知识正文保留到保存', '#knowledge-tags': '重绘标签' }, '#knowledge-body');
  assert.equal(await click('[data-action="save-knowledge"]'), true);
  const knowledge = await until(async () => (await invoke('read_desktop_workspace', { workspaceId })).exchange.knowledge.find(item => item.title === '重绘后的合成知识'), 'knowledge save');
  assert.equal(knowledge.body, '知识正文保留到保存');
  assert.equal(knowledge.projectId, project.id);
  assert.deepEqual(knowledge.tags, ['重绘标签']);

  assert.equal(await click('[data-action="show-memory"]'), true);
  await until(() => evaluate('Boolean(document.querySelector(".work-memory-canvas [data-action=new-memory]"))'), 'memory editor entry');
  assert.equal(await click('[data-action="new-memory"]'), true);
  await until(() => evaluate(`Boolean(document.querySelector('#memory-project option[value=${js(project.id)}]'))`), 'memory project option');
  assert.equal(await fill('#memory-project', project.id), true);
  assert.equal(await fill('#memory-title', '重绘后的合成记忆'), true);
  assert.equal(await fill('#memory-body', '记忆正文保留到保存'), true);
  await redrawEditor('#memory-title', { '#memory-project': project.id, '#memory-title': '重绘后的合成记忆', '#memory-body': '记忆正文保留到保存' }, '#memory-body');
  assert.equal(await click('[data-action="save-memory"]'), true);
  const memory = await until(async () => (await invoke('read_desktop_workspace', { workspaceId })).exchange.memory.find(item => item.title === '重绘后的合成记忆'), 'memory save');
  assert.equal(memory.body, '记忆正文保留到保存');
  assert.equal(memory.scopeId, project.id);
  console.log(JSON.stringify({ phase, workspaceId, projectId: project.id, knowledgeId: knowledge.id, memoryId: memory.id, actualDomReplacements: 3 }));
} else {
  const data = await invoke('read_desktop_workspace', { workspaceId });
  const project = data.exchange.projects.find(item => item.title === '重绘后的合成项目');
  const knowledge = data.exchange.knowledge.find(item => item.title === '重绘后的合成知识');
  const memory = data.exchange.memory.find(item => item.title === '重绘后的合成记忆');
  assert.equal(project?.description, '说明保留到保存');
  assert.equal(knowledge?.body, '知识正文保留到保存');
  assert.equal(knowledge?.projectId, project.id);
  assert.deepEqual(knowledge?.tags, ['重绘标签']);
  assert.equal(memory?.body, '记忆正文保留到保存');
  assert.equal(memory?.scopeId, project.id);
  console.log(JSON.stringify({ phase, workspaceId, projectId: project.id, knowledgeId: knowledge.id, memoryId: memory.id, coldReadback: true }));
}
