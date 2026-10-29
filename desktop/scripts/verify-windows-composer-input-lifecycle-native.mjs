import assert from 'node:assert/strict';
import { createHash, randomUUID } from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import http from 'node:http';
import { dirname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

if (process.platform !== 'win32') throw new Error('仅在 Windows 隔离候选上验收。');
const desktopRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const rootParent = resolve(process.argv[2] || 'D:\\CodexTemp');
const autoAttachmentKind = undefined;
const autoRetryOnly = autoAttachmentKind === 'auto-retry';
const qwenAttachmentRetry = autoAttachmentKind === 'qwen-attachment-retry-ui';
const autoRetryUi = autoAttachmentKind === 'auto-retry-ui' || qwenAttachmentRetry;
const autoRoutePreview = autoAttachmentKind === 'auto-route-preview';
const autoAttachmentOnly = ['auto-attachment', 'auto-pdf', 'auto-image', 'auto-mixed'].includes(autoAttachmentKind);
const receipt = JSON.parse(await readFile(join(desktopRoot, 'output/windows-acceptance/current.json'), 'utf8'));
assert.equal(receipt.identifier, 'com.nanzhufeng.ai.desktop.windowsacceptance');
const source = resolve(desktopRoot, 'output/windows-acceptance', receipt.sha256, 'NanfengAI-Windows-Acceptance.exe');
assert.equal(resolve(receipt.executable), source);
assert.equal(createHash('sha256').update(await readFile(source)).digest('hex').toUpperCase(), receipt.sha256);
const runExecutable = resolve(desktopRoot, 'output/windows-acceptance-binaries', receipt.sha256, 'nanfeng-ai-desktop-spike.exe');
const ps = (args) => execFileSync('pwsh', ['-NoProfile', ...args], { encoding: 'utf8', timeout: 60000 }).trim();
const quote = value => `'${String(value).replaceAll("'", "''")}'`;
const launchPowerShell = (args) => new Promise((resolveLaunch, reject) => {
  const child = spawn('pwsh', ['-NoProfile', ...args], { stdio: ['ignore', 'pipe', 'pipe'] });
  let stdout = '';
  let stderr = '';
  const timer = setTimeout(() => { child.kill(); reject(new Error(`隔离候选启动超时：${stderr}`)); }, 30000);
  child.stdout.on('data', data => {
    stdout += data.toString();
    const line = stdout.slice(0, stdout.lastIndexOf('\n') + 1).split(/\r?\n/)
      .find(item => item.trim().startsWith('{') && item.includes('"pid"'));
    if (!line) return;
    clearTimeout(timer);
    try { resolveLaunch(JSON.parse(line)); } catch (error) { reject(error); }
    child.stdout.destroy();
    child.stderr.destroy();
    child.unref();
  });
  child.stderr.on('data', data => { stderr += data.toString(); });
  child.on('error', error => { clearTimeout(timer); reject(error); });
  child.on('exit', code => {
    if (!stdout.includes('"pid"')) { clearTimeout(timer); reject(new Error(`隔离候选启动失败 ${code}：${stderr}`)); }
  });
});
const reservePort = async () => {
  const server = http.createServer();
  await new Promise(resolveReady => server.listen(0, '127.0.0.1', resolveReady));
  const port = server.address().port;
  await new Promise(resolveClose => server.close(resolveClose));
  return port;
};
const requests = [];
const provider = http.createServer((request, response) => {
  const chunks = [];
  request.on('data', chunk => chunks.push(chunk));
  request.on('end', () => {
    try {
      const body = JSON.parse(Buffer.concat(chunks).toString('utf8'));
      const index = requests.length + 1;
      requests.push({ model: body.model, method: request.method, path: request.url, body,
        idempotencyKey: request.headers['idempotency-key'] || '' });
      if ((autoRetryOnly || autoRetryUi) && index === 1) {
        response.writeHead(503, { 'content-type': 'application/json' });
        response.end(JSON.stringify({ error: { message: 'synthetic retry trigger' } }));
        return;
      }
      if (body.stream === false) {
        response.writeHead(200, { 'content-type': 'application/json' });
        response.end(JSON.stringify({ model: body.model,
          choices: [{ message: { content: '{"title":"附件联网重试验收"}' }, finish_reason: 'stop' }],
          usage: { prompt_tokens: 8, completion_tokens: 4, cost: 0.000004 } }));
        return;
      }
      response.writeHead(200, { 'content-type': 'text/event-stream; charset=utf-8', 'cache-control': 'no-cache' });
      if (qwenAttachmentRetry) response.write(`data: ${JSON.stringify({ web_search: [{ url: 'https://example.com/synthetic-source', title: 'Synthetic source' }] })}\n\n`);
      response.write(`data: ${JSON.stringify({ model: body.model, choices: [{ delta: { content: `合成回答 ${index}` } }] })}\n\n`);
      response.write(`data: ${JSON.stringify({ choices: [{ delta: {}, finish_reason: 'stop' }], usage: { prompt_tokens: 8, completion_tokens: 4, cost: 0.000004 } })}\n\n`);
      response.end('data: [DONE]\n\n');
    } catch (error) { response.writeHead(400); response.end(String(error)); }
  });
});
await new Promise(resolveReady => provider.listen(0, '127.0.0.1', resolveReady));
const providerPort = provider.address().port;
let launch = null;
let root = '';
const pids = [];
const stop = () => {
  if (!launch) return;
  const pid = Number(launch.pid);
  const literal = value => `'${String(value).replaceAll("'", "''")}'`;
  const script = '$expected=' + literal(runExecutable) + '; $hash=' + literal(receipt.sha256) + '; '
    + '$p=Get-CimInstance Win32_Process -Filter "ProcessId=' + pid + '"; '
    + 'if ($p) { if ([IO.Path]::GetFullPath([string]$p.ExecutablePath) -ne $expected '
    + '-or (Get-FileHash -LiteralPath $expected -Algorithm SHA256).Hash -ne $hash) { throw "PID identity mismatch" }; '
    + 'Stop-Process -Id ' + pid + ' -Force -ErrorAction Stop }';
  ps(['-Command', script]);
  pids.push(pid);
  launch = null;
};
async function start(reopen = false) {
  const port = await reservePort();
  const args = ['-File', join(desktopRoot, 'scripts/start-windows-acceptance.ps1'), '-RootParent', rootParent,
    '-StartupMode', 'normal', '-Visible', '-DebugPort', String(port), '-OrdinaryChatTestEndpoint', `http://127.0.0.1:${providerPort}`];
  if (reopen) args.push('-Root', root);
  launch = await launchPowerShell(args);
  assert.equal(launch.sha256, receipt.sha256);
  assert.equal(resolve(launch.executable), runExecutable);
  root = resolve(launch.root);
  assert.ok(root.startsWith(rootParent + sep));
  const marker = JSON.parse(await readFile(join(root, '.nanfeng-windows-acceptance.json'), 'utf8'));
  assert.equal(marker.identifier, receipt.identifier);
  return port;
}
async function cdp(port, method, params = {}) {
  const tabs = await fetch(`http://127.0.0.1:${port}/json/list`).then(response => response.json());
  const tab = tabs.find(item => item.type === 'page' && item.url === 'http://tauri.localhost/');
  assert.ok(tab, '验收 WebView2 页面未就绪');
  const socket = new WebSocket(tab.webSocketDebuggerUrl);
  try {
    await new Promise((resolveReady, reject) => {
      socket.addEventListener('open', resolveReady, { once: true });
      socket.addEventListener('error', reject, { once: true });
    });
    return await new Promise((resolveResult, reject) => {
      const timer = setTimeout(() => reject(new Error('CDP evaluation timed out')), 30000);
      socket.addEventListener('message', event => {
        const message = JSON.parse(event.data);
        if (message.id !== 1) return;
        clearTimeout(timer);
        if (message.error || message.result?.exceptionDetails) reject(new Error(
          message.result?.exceptionDetails?.exception?.description || message.result?.exceptionDetails?.text || message.error?.message));
        else resolveResult(message.result);
      });
      socket.send(JSON.stringify({ id: 1, method, params }));
    });
  } finally { socket.close(); }
}
async function evaluate(port, expression) {
  return (await cdp(port, 'Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true }))?.result?.value;
}
async function invoke(port, area, command, args = {}) {
  const expression = `window.__TAURI__.core.invoke(${JSON.stringify(command)},${JSON.stringify(args)},${JSON.stringify({ headers: { 'x-nanfeng-data-area': area } })}).catch(error=>({__error:String(error)}))`;
  const result = await evaluate(port, expression);
  if (result?.__error) throw new Error(`${command}: ${result.__error}`);
  return result;
}
async function ready(port) {
  const deadline = Date.now() + 30000;
  while (Date.now() < deadline) {
    try { if (await evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)')) return; } catch { /* Startup is still active. */ }
    await new Promise(resolveWait => setTimeout(resolveWait, 250));
  }
  throw new Error('原生 IPC 未就绪');
}
async function until(check, label) {
  const deadline = Date.now() + 30000;
  while (Date.now() < deadline) {
    try { const value = await check(); if (value) return value; } catch { /* Visible page may be refreshing. */ }
    await new Promise(resolveWait => setTimeout(resolveWait, 200));
  }
  throw new Error(`${label} 未在原生界面出现`);
}
async function clickUi(port, selector) {
  const result = await evaluate(port, `(()=>{const node=document.querySelector(${JSON.stringify(selector)});if(!node)return false;node.click();return true})()`);
  assert.equal(result, true, `找不到可点击控件 ${selector}`);
}
async function normalClose() {
  const pid = Number(launch.pid);
  const literal = value => `'${String(value).replaceAll("'", "''")}'`;
  const script = `$expected=${literal(runExecutable)}; $hash=${literal(receipt.sha256)}; `
    + `$p=Get-CimInstance Win32_Process -Filter "ProcessId=${pid}"; `
    + 'if(!$p -or [IO.Path]::GetFullPath($p.ExecutablePath) -ne $expected -or (Get-FileHash -LiteralPath $expected -Algorithm SHA256).Hash -ne $hash){throw "candidate identity mismatch"}; '
    + `$process=Get-Process -Id ${pid}; $handle=$process.Handle; if(!$process.CloseMainWindow()){throw "close rejected"}; `
    + 'if(!$process.WaitForExit(10000)){throw "normal exit timeout"}; if($process.ExitCode -ne 0){throw "normal exit code"}; '
    + '[pscustomobject]@{pid=$process.Id;exitCode=$process.ExitCode}|ConvertTo-Json -Compress';
  const close = JSON.parse(ps(['-Command', script]));
  pids.push(pid); launch = null;
  return close;
}
const png = 'iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAIAAAD91JpzAAAAFElEQVR4nGP8z8DAwMDAxMDAAAANHQEDasKb6QAAAABJRU5ErkJggg==';
const pngHash = createHash('sha256').update(Buffer.from(png,'base64')).digest('hex');
const areas = {};
const closes = [];
let result;
async function areaUi(port, area) {
  await until(()=>evaluate(port, `Boolean(globalThis.__nanfengDesktopWorkState?.dataArea && document.querySelector('#chat-composer'))`),'native composer mounted');
  if (await evaluate(port, 'globalThis.__nanfengDesktopWorkState?.dataArea') !== area) {
    await until(()=>evaluate(port, `Boolean(document.querySelector('[data-action="${area==='WORK'?'show-work':'show-chat'}"]'))`),'area navigation mounted');
    await clickUi(port, `[data-action="${area==='WORK'?'show-work':'show-chat'}"]`);
  }
  await until(() => evaluate(port, `globalThis.__nanfengDesktopWorkState?.dataArea===${JSON.stringify(area)} && Boolean(document.querySelector('#chat-composer'))`), `area ${area}`);
}
const readDraft = (port, area) => invoke(port, area, 'read_desktop_conversation_draft', {workspaceId:areas[area].workspaceId,conversationId:null});
async function fileEvent(port, kind, name, bytes, type, pathText='') {
  return evaluate(port, `(()=>{const input=document.querySelector('#chat-composer');const dt=new DataTransfer();dt.items.add(new File([Uint8Array.from(atob(${JSON.stringify(bytes)}),c=>c.charCodeAt(0))],${JSON.stringify(name)},{type:${JSON.stringify(type)}}));dt.setData('text/plain',${JSON.stringify(pathText)});const event=${kind==='paste'?'new ClipboardEvent':'new DragEvent'}(${JSON.stringify(kind)},{bubbles:true,cancelable:true,${kind==='paste'?'clipboardData':'dataTransfer'}:dt});input.dispatchEvent(event);return {prevented:event.defaultPrevented,trusted:event.isTrusted};})()`);
}
try {
  let port=await start(); await ready(port);
  assert.match(await evaluate(port, 'navigator.platform'), /^Win/i, 'no real clipboard reads on this Windows event path');
  for (const area of ['CHAT','WORK']) {
    await areaUi(port,area);
    const workspace=await invoke(port,area,'ensure_desktop_local_workspace');
    const text=`${area} 合成输入生命周期`;
    areas[area]={workspaceId:workspace.summary.id,text};
    await invoke(port,area,'save_desktop_conversation_draft',{args:{workspaceId:workspace.summary.id,conversationId:null,text,attachmentIds:[],modelId:'DEEPSEEK_V4_FLASH',toneOverride:null,webSearchOverride:false}});
  }
  closes.push(await normalClose());port=await start(true);await ready(port);
  for (const area of ['CHAT','WORK']) {
    await areaUi(port,area);
    await until(()=>evaluate(port, `globalThis.__nanfengDesktopWorkState?.current?.summary?.id===${JSON.stringify(areas[area].workspaceId)} && document.querySelector('#chat-composer')?.value===${JSON.stringify(areas[area].text)}`),'cold draft recovery');
    const pasted=await fileEvent(port,'paste',`合成-${area}.png`,png,'image/png','C:\\PRIVATE_SYNTHETIC_PATH\\must-not-be-pasted.png');
    assert.equal(pasted.prevented,true);assert.equal(pasted.trusted,false);
    await until(async()=> (await readDraft(port,area)).attachments.length===1,'paste private copy and draft');
    const pastedDraft=await readDraft(port,area);assert.equal(pastedDraft.text,areas[area].text);assert.equal(pastedDraft.attachments[0].sha256,pngHash);
    await fileEvent(port,'drop',`重复-${area}.png`,png,'image/png');
    await until(()=>evaluate(port,'!globalThis.__nanfengDesktopWorkState?.error'),'duplicate image import');
    const material=`${area} INPUT_LIFECYCLE_TEXT_ONLY\n`;
    await fileEvent(port,'drop',`合成-${area}.txt`,Buffer.from(material,'utf8').toString('base64'),'text/plain');
    await until(async()=> (await readDraft(port,area)).attachments.length===2,'drop text draft');
    const draft=await readDraft(port,area);assert.equal(draft.text,areas[area].text);assert.equal(draft.attachments.filter(a=>a.sha256===pngHash).length,1);
    areas[area].attachments=draft.attachments;areas[area].material=material;
    areas[area].textHash=createHash('sha256').update(material).digest('hex');
    const delayed=`${area} DELAYED_INPUT_MUST_NOT_IMPORT`;
    await evaluate(port, `(()=>{const proto=FileReader.prototype;const original=proto.readAsDataURL;window.__inputReaderOriginal=original;proto.readAsDataURL=function(file){if(file.name==='delayed-input.txt'){window.__inputPendingReader={reader:this,file};return;}return original.call(this,file);};window.__inputReadReleased=false;})()`);
    await fileEvent(port,'drop','delayed-input.txt',Buffer.from(delayed).toString('base64'),'text/plain');
    await until(()=>evaluate(port,'Boolean(window.__inputPendingReader)'),'controlled FileReader pending');
    const other=area==='CHAT'?'WORK':'CHAT';const beforeOther=await readDraft(port,other);
    await areaUi(port,other);
    await evaluate(port, `(async()=>{const pending=window.__inputPendingReader;FileReader.prototype.readAsDataURL=window.__inputReaderOriginal;const onload=pending.reader.onload;pending.reader.onload=function(event){onload.call(this,event);window.__inputReadReleased=true;};window.__inputReaderOriginal.call(pending.reader,pending.file);while(!window.__inputReadReleased)await new Promise(resolve=>setTimeout(resolve,10));await new Promise(requestAnimationFrame);await new Promise(requestAnimationFrame);})()`);
    assert.deepEqual(await readDraft(port,other),beforeOther,'late input does not change target area');
    assert.deepEqual((await readDraft(port,area)).attachments,areas[area].attachments,'late input does not alter old draft');
    areas[area].delayedHash=createHash('sha256').update(delayed).digest('hex');
  }
  closes.push(await normalClose());port=await start(true);await ready(port);
  for(const area of ['CHAT','WORK']) {
    const draft=await readDraft(port,area);assert.equal(draft.text,areas[area].text);assert.deepEqual(draft.attachments,areas[area].attachments);
    await areaUi(port,area);
    const image=areas[area].attachments.find(a=>a.sha256===pngHash);
    await until(()=>evaluate(port, `Boolean(document.querySelector('[data-action="remove-composer-attachment"][data-id="${image.id}"]'))`),'cold attachment UI recovery');
    await clickUi(port,`[data-action="remove-composer-attachment"][data-id="${image.id}"]`);
    await until(async()=> (await readDraft(port,area)).attachments.length===1,'remove image occurrence');
    const remaining=await readDraft(port,area);assert.equal(remaining.attachments[0].sha256,areas[area].textHash);
    const settings=(await invoke(port,area,'read_desktop_model_service_settings')).find(s=>s.providerId==='DEEPSEEK');
    await invoke(port,area,'save_desktop_model_service_settings',{args:{providerId:'DEEPSEEK',enabled:true,presetId:'DEEPSEEK_V4_FLASH',expectedRevision:settings.revision,apiKey:`nfai-input-synthetic-${randomUUID()}`}});
  }
  closes.push(await normalClose());port=await start(true);await ready(port);await areaUi(port,'CHAT');
  await until(()=>evaluate(port,'!document.querySelector("[data-action=save-local-message]")?.disabled'),'send text-only material draft');
  await clickUi(port,'[data-action="save-local-message"]');
  const conversation=await until(async()=>{
    const current=await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:areas.CHAT.workspaceId});
    return current.exchange.conversations.find(c=>c.messages.some(m=>m.role==='user'&&m.blocks.some(b=>b.kind==='TEXT'&&b.text===areas.CHAT.text)) && c.messages.some(m=>m.role==='assistant'&&m.delivery==='COMPLETE'));
  },'input material real production send');
  const ordinaryRequests=requests.filter(request=>request.body.stream!==false);
  assert.equal(ordinaryRequests.length,1);assert.equal(ordinaryRequests[0].model,'deepseek-flash');
  assert.ok(JSON.stringify(requests[0].body.messages).includes('CHAT INPUT_LIFECYCLE_TEXT_ONLY'));
  assert.ok(!JSON.stringify(requests[0].body.messages).includes('WORK INPUT_LIFECYCLE_TEXT_ONLY'));
  const user=conversation.messages.find(m=>m.role==='user');assert.equal(user.blocks.filter(b=>b.kind==='ASSET_REF').length,1);
  const otherDraft=await readDraft(port,'WORK');assert.equal(otherDraft.text,areas.WORK.text);assert.equal(otherDraft.attachments.length,1);
  closes.push(await normalClose());port=await start(true);await ready(port);
  const cold=(await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:areas.CHAT.workspaceId})).exchange.conversations.find(c=>c.id===conversation.id);assert.deepEqual(cold.messages,conversation.messages);
  assert.equal(requests.filter(request=>request.body.stream!==false).length,1);closes.push(await normalClose());
  result={candidateSha256:receipt.sha256,root,areas,pngHash,conversationId:conversation.id,providerRequests:requests.length,ordinaryRequests:ordinaryRequests.length,titleRequests:requests.filter(request=>request.body.stream===false).length,requestModel:ordinaryRequests[0].model,closes,pass:true,scope:'Synthetic untrusted DOM paste/drop + real Windows native import/draft/send/store. No OS clipboard or physical drag/drop claim; 24h asset grace not elapsed.'};
}catch(error){result={candidateSha256:receipt.sha256,root,areas,closes,error:String(error.stack||error),pass:false};process.exitCode=1;}
finally{if(launch){result.forcedCleanup=true;stop();}await new Promise(resolveClose=>provider.close(resolveClose));if(root)await writeFile(join(root,'composer-input-lifecycle-evidence.json'),JSON.stringify(result,null,2),{flag:'wx'});}
console.log(JSON.stringify(result));
