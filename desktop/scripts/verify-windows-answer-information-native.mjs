import assert from 'node:assert/strict';
import http from 'node:http';
import { readFile, writeFile } from 'node:fs/promises';

const [mode, cdpPortText, providerPortText, output, sendEvidencePath, overflowFlag, registryFlag, autoTask = 'ordinary'] = process.argv.slice(2);
assert.ok(['send', 'reopen'].includes(mode));
assert.ok(['ordinary', 'default', 'complex'].includes(autoTask));
const cdpPort = Number(cdpPortText);
const providerPort = Number(providerPortText);
assert.ok(Number.isInteger(cdpPort) && Number.isInteger(providerPort));
const headers = { headers: { 'x-nanfeng-data-area': 'CHAT' } };
const prompt = autoTask === 'complex' ? '请做完整方案，并说明架构设计与权衡；这是合成原生 Auto 复杂任务。'
  : autoTask === 'default' ? '请概括这段合成文字。' : '合成回答信息语气链';
const answer = '合成语气核验回复';
const expectedModel = autoTask === 'complex' ? 'openai/gpt-6-astra'
  : autoTask === 'default' ? 'deepseek-flash' : 'openai/gpt-5.6-terra';
let requestCount = 0;
let requestFacts = null;
let releaseProvider;
let runningCostSuppressed = false;

const provider = http.createServer((request, response) => {
  const chunks = [];
  request.on('data', chunk => chunks.push(chunk));
  request.on('end', () => {
    requestCount += 1;
    const body = JSON.parse(Buffer.concat(chunks).toString('utf8'));
    requestFacts = {
      method: request.method,
      model: body.model,
      stream: body.stream,
      promptMatched: body.messages?.some(message => message.role === 'user' && String(message.content).includes(prompt)) === true,
      authorizationPresent: /^Bearer\s+\S+$/.test(String(request.headers.authorization || '')),
      searchToolAbsent: !Array.isArray(body.tools),
    };
    response.writeHead(200, { 'content-type': 'text/event-stream; charset=utf-8' });
    response.write(`data: ${JSON.stringify({ model: 'fixture-actual-answer-info', choices: [{ delta: { content: answer } }] })}\n\n`);
    if (mode === 'send') {
      const released = new Promise(resolve => { releaseProvider = resolve; });
      void released.then(() => {
        response.write(`data: ${JSON.stringify({ choices: [{ delta: {} }], usage: { prompt_tokens: 7, completion_tokens: 5 } })}\n\n`);
        response.end('data: [DONE]\n\n');
      });
    } else {
      response.end('data: [DONE]\n\n');
    }
  });
});

async function evaluate(expression) {
  const tabs = await fetch(`http://127.0.0.1:${cdpPort}/json/list`).then(response => response.json());
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
        if (message.error || message.result?.exceptionDetails) reject(new Error('CDP evaluation failed'));
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
async function until(check, label, timeout = 30000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    try {
      const result = await check();
      if (result) return result;
    } catch { /* The WebView2 page can still be loading. */ }
    await new Promise(resolve => setTimeout(resolve, 250));
  }
  throw new Error(`${label} did not become ready`);
}
const js = value => JSON.stringify(value);
const invoke = (command, args = {}) => evaluate(`window.__TAURI__.core.invoke(${js(command)},${js(args)},${js(headers)})`);
const click = selector => evaluate(`(()=>{const e=document.querySelector(${js(selector)});if(!e)return false;e.click();return true})()`);

await new Promise((resolve, reject) => provider.listen(providerPort, '127.0.0.1', error => error ? reject(error) : resolve()));
try {
  await until(() => evaluate("Boolean(window.__TAURI__?.core?.invoke&&document.querySelector('#chat-composer'))"), 'chat UI');
  let registryVersion = null;
  if (registryFlag === 'registry') {
    const registry = mode === 'send' ? await invoke('refresh_desktop_openrouter_registry')
      : await invoke('read_desktop_openrouter_registry_status');
    assert.equal(registry.status, 'VERIFIED');
    assert.ok(registry.mappedPresetIds.includes('GPT_5_6_TERRA'));
    registryVersion = registry.catalogVersion;
  }
  if (mode === 'send') {
    const settings = await invoke('read_desktop_model_service_settings');
    const openrouter = settings.find(item => item.providerId === 'OPENROUTER');
    assert.ok(openrouter && !openrouter.enabled && !openrouter.credentialStored, 'root must begin without provider credentials');
    await invoke('save_desktop_model_service_settings', { args: {
      providerId: 'OPENROUTER', enabled: true, presetId: 'GPT_5_6_TERRA',
      expectedRevision: openrouter.revision,
      apiKey: `nfai-synthetic-answer-info-${crypto.randomUUID()}`,
    } });
    if (autoTask === 'complex' || autoTask === 'default') {
      const deepseek = settings.find(item => item.providerId === 'DEEPSEEK');
      assert.ok(deepseek && !deepseek.enabled && !deepseek.credentialStored);
      await invoke('save_desktop_model_service_settings', { args: {
        providerId: 'DEEPSEEK', enabled: true, presetId: 'DEEPSEEK_V4_FLASH',
        expectedRevision: deepseek.revision,
        apiKey: `nfai-synthetic-auto-deepseek-${crypto.randomUUID()}`,
      } });
    }
    await evaluate('location.reload()');
    await until(() => evaluate("Boolean(document.querySelector('#chat-composer')&&window.__nanfengDesktopWorkState?.modelServiceSettings?.some(x=>x.providerId==='OPENROUTER'&&x.enabled))"), 'reloaded chat UI');
    assert.equal(await click('[data-action="toggle-composer-add"]'), true);
    assert.equal(await click('[data-action="open-composer-tone-picker"]'), true);
    assert.equal(await click('[data-action="select-conversation-tone"][data-tone="direct"]'), true);
    await until(() => evaluate("window.__nanfengDesktopWorkState?.conversationPreferences?.toneOverride==='direct'"), 'direct tone');
    assert.equal(await click('[data-action="toggle-composer-add"]'), true);
    const searchEnabled = await evaluate("document.querySelector('[data-action=toggle-conversation-web-search]')?.getAttribute('aria-checked')");
    if (searchEnabled === 'true') assert.equal(await click('[data-action="toggle-conversation-web-search"]'), true);
    await until(() => evaluate("document.querySelector('[data-action=toggle-conversation-web-search]')?.getAttribute('aria-checked')==='false'"), 'search disabled');
    assert.equal(await click('[data-action="close-composer-add"]'), true);
    const typed = await evaluate(`(()=>{const e=document.querySelector('#chat-composer');e.value=${js(prompt)};e.dispatchEvent(new Event('input',{bubbles:true}));return e.value})()`);
    assert.equal(typed, prompt);
    assert.equal(await click('[data-action="save-local-message"]'), true);
    await until(() => evaluate(`(()=>{const answer=[...document.querySelectorAll('.chat-message.assistant')].find(e=>e.textContent.includes(${js(answer)}));return Boolean(answer?.querySelector('.chat-runtime-state.running'))})()`), 'visible running reply');
    const runningCost = await evaluate(`(()=>{const answer=[...document.querySelectorAll('.chat-message.assistant')].find(e=>e.textContent.includes(${js(answer)}));return answer?.querySelector('.chat-message-metadata-cost')?.textContent??null})()`);
    assert.equal(runningCost, null, 'a live reply without pricing must not claim an unknown final amount');
    runningCostSuppressed = true;
    assert.equal(typeof releaseProvider, 'function');
    releaseProvider();
    releaseProvider = null;
  }
  await until(() => evaluate(`Boolean([...document.querySelectorAll('.chat-message.assistant')].some(e=>e.textContent.includes(${js(answer)})&&!e.querySelector('.chat-runtime-state.running')))`), 'completed answer', 40000);
  const prior = mode === 'reopen' ? JSON.parse(await readFile(sendEvidencePath, 'utf8')) : null;
  const records = mode === 'send' ? await until(async () => {
    const rows = await invoke('read_desktop_ordinary_chat_context_records');
    return rows.some(row => row.selectedSources?.some(source => source.kind === '对话风格' && source.title === '直言不讳')) ? rows : null;
  }, 'durable context record') : await invoke('read_desktop_ordinary_chat_context_records');
  const record = mode === 'send'
    ? records.find(row => row.selectedSources?.some(source => source.kind === '对话风格' && source.title === '直言不讳'))
    : await invoke('read_desktop_ordinary_chat_context_record', {
      workspaceId: prior.workspaceId, conversationId: prior.conversationId,
      assistantMessageId: prior.assistantMessageId,
    });
  assert.ok(record, 'answer-bound context record missing');
  if (mode === 'send') assert.equal(records.length, 1);
  if (overflowFlag === 'overflow') assert.ok(!records.some(row => row.assistantMessageId === record.assistantMessageId));
  const style = record.selectedSources.find(source => source.kind === '对话风格');
  assert.equal(style.title, '直言不讳');
  assert.equal(requestCount, mode === 'send' ? 1 : 0);
  if (mode === 'send') {
    assert.deepEqual(requestFacts, {
      method: 'POST', model: expectedModel, stream: true,
      promptMatched: true, authorizationPresent: true, searchToolAbsent: true,
    });
  }
  const route = await evaluate("(()=>({workspaceId:window.__nanfengDesktopWorkState?.current?.summary?.id,conversationId:window.__nanfengDesktopWorkState?.selectedConversationId}))()");
  assert.equal(route.conversationId, record.conversationId);
  const exactRecord = await invoke('read_desktop_ordinary_chat_context_record', {
    workspaceId: route.workspaceId, conversationId: route.conversationId, assistantMessageId: record.assistantMessageId,
  });
  assert.equal(exactRecord?.assistantMessageId, record.assistantMessageId);
  const otherAreaRecord = await evaluate(`window.__TAURI__.core.invoke('read_desktop_ordinary_chat_context_record',${js({
    workspaceId: route.workspaceId, conversationId: route.conversationId, assistantMessageId: record.assistantMessageId,
  })},{headers:{'x-nanfeng-data-area':'WORK'}})`);
  assert.equal(otherAreaRecord, null);
  const openedMenu = await evaluate(`(()=>{const e=[...document.querySelectorAll('.chat-message.assistant [data-action="open-assistant-message-menu"]')].find(x=>x.dataset.messageId===${js(record.assistantMessageId)});if(!e)return false;e.click();return true})()`);
  assert.equal(openedMenu, true);
  assert.equal(await click('[data-action="show-assistant-answer-information"]'), true);
  const dialog = await until(() => evaluate("(()=>{const e=document.querySelector('.answer-information-dialog');return e?{title:e.querySelector('h2')?.textContent,style:e.querySelector('.answer-information-list dd')?.textContent}:null})()"), 'answer information dialog');
  assert.equal(dialog.title, '本次回答信息');
  assert.equal(dialog.style, '直言不讳');
  await writeFile(output, JSON.stringify({ mode, autoTask, registryVersion, requestCount, requestFacts, runningCostSuppressed, recordCount: records.length,
    workspaceId: route.workspaceId, conversationId: record.conversationId, assistantMessageId: record.assistantMessageId,
    recordStyle: style.title, dialogTitle: dialog.title, dialogStyle: dialog.style }, null, 2) + '\n');
  console.log('NATIVE_ANSWER_INFORMATION_OK', mode);
} finally {
  if (releaseProvider) releaseProvider();
  await new Promise(resolve => provider.close(resolve));
}
