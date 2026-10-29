import assert from 'node:assert/strict';
import { readFile, writeFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';

const [port, mode, output] = process.argv.slice(2);
assert.ok(['run', 'cold'].includes(mode));
let tabs;
for (let attempt = 0; attempt < 100; attempt++) {
  try { tabs = await fetch(`http://127.0.0.1:${Number(port)}/json/list`).then(r => r.json()); if (tabs.some(t => t.type === 'page' && t.url === 'http://tauri.localhost/')) break; } catch {}
  await new Promise(r => setTimeout(r, 100));
}
const tab = tabs?.find(t => t.type === 'page' && t.url === 'http://tauri.localhost/');
assert.ok(tab, 'WebView2 startup');
const socket = new WebSocket(tab.webSocketDebuggerUrl);
const pending = new Map(); let serial = 0;
socket.addEventListener('message', event => { const m = JSON.parse(event.data), p = pending.get(m.id); if (!p) return; pending.delete(m.id); clearTimeout(p.timer); m.error ? p.reject(new Error(m.error.message)) : p.resolve(m.result); });
await new Promise((resolve, reject) => { socket.addEventListener('open', resolve, { once: true }); socket.addEventListener('error', reject, { once: true }); });
const request = (method, params = {}) => new Promise((resolve, reject) => { const id = ++serial, timer = setTimeout(() => { pending.delete(id); reject(new Error(method + ' timeout')); }, 10000); pending.set(id, { resolve, reject, timer }); socket.send(JSON.stringify({ id, method, params })); });
const js = JSON.stringify;
const evaluate = async expression => { const r = await request('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true }); if (r.exceptionDetails) throw new Error(js(r.exceptionDetails)); return r.result.value; };
const invoke = (area, command, args = {}) => evaluate(`window.__TAURI__.core.invoke(${js(command)},${js(args)},{headers:{'x-nanfeng-data-area':${js(area)}}})`);
const until = async (test, name) => { const end = Date.now() + 15000; while (Date.now() < end) { if (await test()) return; await new Promise(r => setTimeout(r, 100)); } throw new Error(name); };
const hash = value => createHash('sha256').update(js(value)).digest('hex');
const pointer = async selector => { const p = await evaluate(`(()=>{const e=document.querySelector(${js(selector)});if(!e)throw Error('missing '+${js(selector)});e.scrollIntoView({block:'center'});const r=e.getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2}})()`); await request('Input.dispatchMouseEvent', { type: 'mousePressed', ...p, button: 'left', clickCount: 1 }); await request('Input.dispatchMouseEvent', { type: 'mouseReleased', ...p, button: 'left', clickCount: 1 }); await new Promise(r => setTimeout(r, 150)); };
const record = mode === 'run' ? { checks: [], areas: {}, injection: 'WebView2 CDP', physicalKeyboard: false, systemClipboard: false, productionStream: false } : JSON.parse(await readFile(output, 'utf8'));
const check = (name, pass, detail) => { record.checks.push({ name, pass, detail }); assert.ok(pass, name + ': ' + js(detail)); };
try {
  await until(() => evaluate('Boolean(document.querySelector("#chat-composer")&&globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'), 'ready');
  record.runtime = await invoke('CHAT', 'read_desktop_runtime_info');
  assert.equal(record.runtime.startupMode, 'UI_SCHEMA_DIAGNOSTIC_ACCEPTANCE'); assert.equal(record.runtime.automaticWorkSuppressed, true);
  if (mode === 'run') {
    const { packageExchange, semanticHash, sha256, preflightPackage } = await import('../../protocol/scripts/exchange-lib.mjs');
    const exchange = JSON.parse(await readFile(new URL('../../protocol/fixtures/nfai.exchange.v1.golden.json', import.meta.url), 'utf8'));
    const asset = await readFile(new URL('../../protocol/fixtures/assets/golden-note.txt', import.meta.url));
    record.code = 'const emoji = "南枫😀 é 👩‍💻";\nconst html = "<script>synthetic</script> & <img src=x>";\n\tconsole.log(emoji);';
    record.tail = '未闭合围栏🔎 原文末尾\nconst tail = "终点";';
    record.text = '# 合成 Markdown 验收\n\n中文😀 é 👩‍💻，**粗体**、*斜体*、`行内代码`。\n\n> 引用正文\n\n- 列表甲\n- 列表乙\n\n| 名称 | 数值 |\n| --- | --- |\n| Unicode | 南枫😀 |\n| 短行 |\n| 长行 | 两列 | 第三列 |\n\n[来源 [一]](https://example.com/path_(synthetic))\n\n[换行来源]\n(https://example.com/line-break)\n\n<script>window.__markdownUnsafe=true</script>\n\n```javascript\n' + record.code + '\n```\n\n查找唯一🔎词\n\n```text\n' + record.tail;
    const blocks = exchange.conversations[0].messages[1].blocks; blocks[0].ordinal = 1; blocks.unshift({ kind: 'TEXT', ordinal: 0, text: record.text }); exchange.export.semanticHash = semanticHash(exchange);
    const packaged = packageExchange(exchange, new Map([[`assets/${sha256(asset)}`, asset]])); preflightPackage(packaged);
    record.fixture = output + '.nfai-exchange'; await writeFile(record.fixture, packaged);
    for (const area of ['CHAT', 'WORK']) { const p = await invoke(area, 'stage_preflight_selected_exchange', { selectedPath: record.fixture }); const imported = await invoke(area, 'import_staged_exchange_as_new_workspace', { args: { stagingId: p.stagingId, workspaceTitle: `Synthetic ${area} Markdown` } }); record.areas[area] = { workspaceId: imported.summary.id, conversationId: imported.exchange.conversations[0].id }; }
    await request('Page.reload'); await until(() => evaluate('Boolean(document.querySelector("#chat-composer"))'), 'reload');
  }
  for (const area of ['CHAT', 'WORK']) {
    const saved = record.areas[area], workspace = () => invoke(area, 'read_desktop_workspace', { workspaceId: saved.workspaceId }), draft = () => invoke(area, 'read_desktop_conversation_draft', { workspaceId: saved.workspaceId, conversationId: saved.conversationId });
    if (mode === 'cold') { check(`${area} cold full exchange`, hash((await workspace()).exchange) === saved.exchangeHash); check(`${area} cold full draft`, js(await draft()) === js(saved.draft)); continue; }
    await pointer(`[data-action=show-${area === 'CHAT' ? 'chat' : 'work'}]`); await until(() => evaluate(`globalThis.__nanfengDesktopWorkState.dataArea===${js(area)}`), 'area'); await pointer(`[data-action="select-chat"][data-id="${saved.conversationId}"]`); await until(() => evaluate('document.querySelectorAll(".chat-message.assistant pre code").length===2'), 'render');
    const before = (await workspace()).exchange; saved.draft = await draft();
    const dom = await evaluate(`(()=>{const m=document.querySelector('.chat-message.assistant');return {codes:[...m.querySelectorAll('pre code')].map(e=>e.textContent),bold:m.querySelector('strong')?.textContent,italic:m.querySelector('em')?.textContent,quote:m.querySelector('blockquote')?.textContent,list:m.querySelectorAll('li').length,table:m.querySelector('table')?.textContent,tableRows:[...m.querySelectorAll('table tr')].map(e=>e.children.length),sources:[...m.querySelectorAll('[data-sources]')].map(e=>JSON.parse(e.dataset.sources)),unsafe:!!window.__markdownUnsafe,scripts:m.querySelectorAll('script').length,text:m.textContent}})()`);
    check(`${area} code Unicode exact`, dom.codes[0] === record.code && dom.codes[1] === record.tail, dom.codes);
    check(`${area} structured Markdown`, dom.bold === '粗体' && dom.italic === '斜体' && dom.quote.includes('引用正文') && dom.list === 2 && dom.table.includes('南枫😀'), dom);
    check(`${area} escaped raw HTML`, !dom.unsafe && dom.scripts === 0 && dom.text.includes('<script>window.__markdownUnsafe=true</script>'));
    check(`${area} balanced source URL`, js(dom.sources).includes('https://example.com/path_(synthetic)') && js(dom.sources).includes('https://example.com/line-break'), dom.sources);
    check(` ragged table preserved`, dom.tableRows.length===4&&dom.tableRows.every(n=>n===3)&&dom.table.includes('短行')&&dom.table.includes('第三列'),dom.tableRows);
    // Capture the actual existing handler without changing the user's system clipboard.
    await evaluate(`window.__markdownCopies=[];window.__markdownClipboardDescriptor=Object.getOwnPropertyDescriptor(navigator.clipboard,'writeText');Object.defineProperty(navigator.clipboard,'writeText',{configurable:true,value:async text=>{window.__markdownCopies.push(text)}});true`);
    for (let index = 0; index < 2; index++) {
      await evaluate(`document.querySelectorAll('.chat-message.assistant .chat-markdown-code-copyable')[${index}].querySelector('[data-action="copy-markdown-block"]').focus({preventScroll:true});true`);
      await request('Input.dispatchKeyEvent', { type: 'keyDown', key: 'Enter', code: 'Enter', windowsVirtualKeyCode: 13, text: '\r' });
      await request('Input.dispatchKeyEvent', { type: 'keyUp', key: 'Enter', code: 'Enter', windowsVirtualKeyCode: 13 });
      await new Promise(r => setTimeout(r, 100));
    }
    const copies = await evaluate('window.__markdownCopies'); check(`${area} actual code copy handlers exact`, copies[0] === record.code && copies[1] === record.tail, copies);
    await pointer('.chat-message.assistant [data-action="copy-message"]');
    const fullCopy = await evaluate('window.__markdownCopies.at(-1)'); check(`${area} full message copy preserves original`, fullCopy === record.text + '\n' + 'golden-note.txt · text/plain', { length: fullCopy.length, expected: record.text.length });
    await evaluate(`(()=>{const d=window.__markdownClipboardDescriptor;if(d)Object.defineProperty(navigator.clipboard,'writeText',d);else delete navigator.clipboard.writeText;return true})()`);
    await request('Input.dispatchKeyEvent', {type:'rawKeyDown',key:'f',code:'KeyF',windowsVirtualKeyCode:70,modifiers:2}); await request('Input.dispatchKeyEvent', {type:'keyUp',key:'f',code:'KeyF',windowsVirtualKeyCode:70,modifiers:2}); await until(()=>evaluate('Boolean(document.querySelector("#conversation-find-input"))'),'find opened'); await pointer('#conversation-find-input'); await request('Input.insertText', { text: '查找唯一🔎词' });
    await until(() => evaluate('Boolean(document.querySelector(".chat-message.assistant.find-active"))'), 'find original');
    check(`${area} original Unicode find`, await evaluate(`document.querySelector('.conversation-find').textContent.includes('1 / 1')&&document.querySelector('.chat-message.assistant.find-active').textContent.includes('查找唯一🔎词')`));
    await pointer('[data-action="close-conversation-find"]');
    check(`${area} complete exchange unchanged`, hash((await workspace()).exchange) === hash(before)); check(`${area} complete draft unchanged`, js(await draft()) === js(saved.draft)); saved.exchangeHash = hash(before);
  }
} catch (error) { record.error = String(error); process.exitCode = 1; } finally { await writeFile(output, js(record, null, 2)); socket.close(); }
