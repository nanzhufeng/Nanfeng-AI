import assert from 'node:assert/strict';
import { createHash, randomUUID } from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

if (process.platform !== 'win32') throw new Error('仅在 Windows 隔离候选上验收。');
const desktopRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const rootParent = resolve(process.argv[2] || 'D:\\CodexTemp');
const videoCardMode = process.argv.includes('--video-card');
const audioCardMode = process.argv.includes('--audio-card');
const pdfCardMode = process.argv.includes('--pdf-card');
const archiveCardMode = process.argv.includes('--archive-card');
assert.ok([videoCardMode,audioCardMode,pdfCardMode,archiveCardMode].filter(Boolean).length<=1, '附件格式主链串行验收');
const visibleDeleteMode = process.argv.includes('--visible-delete');
const visibleAnchorMode = process.argv.includes('--visible-anchor');
const verifyInstanceGuard = process.argv.includes('--verify-single-instance-guard');
const keyboardUndoMode = process.argv.includes('--keyboard-undo');
const historyTitleMode = process.argv.includes('--history-title');
const imeSearchMode = process.argv.includes('--ime-search');
const receipt = JSON.parse(await readFile(join(desktopRoot, 'output/windows-acceptance/current.json'), 'utf8'));
assert.equal(receipt.identifier, 'com.nanzhufeng.ai.desktop.windowsacceptance');
const source = resolve(desktopRoot, 'output/windows-acceptance', receipt.sha256, 'NanfengAI-Windows-Acceptance.exe');
assert.equal(resolve(receipt.executable), source);
assert.equal(createHash('sha256').update(await readFile(source)).digest('hex').toUpperCase(), receipt.sha256);
const runningExecutable = resolve(desktopRoot, 'output/windows-acceptance-binaries', receipt.sha256, 'nanfeng-ai-desktop-spike.exe');
const ps = script => execFileSync('pwsh', ['-NoProfile', '-Command', script], { encoding: 'utf8', timeout: 60000 }).trim();
const quote = value => `'${String(value).replaceAll("'", "''")}'`;
const reservePort = async () => {
  const server = (await import('node:http')).createServer();
  await new Promise(done => server.listen(0, '127.0.0.1', done));
  const port = server.address().port;
  await new Promise(done => server.close(done));
  return port;
};
let launch = null;
let root = '';
const pids = [];
const startProcess = args => new Promise((done, fail) => {
  const child = spawn('pwsh', ['-NoProfile', '-File', join(desktopRoot, 'scripts/start-windows-acceptance.ps1'), ...args], {
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  let out = '';
  let err = '';
  const timer = setTimeout(() => { child.kill(); fail(new Error(`启动超时：${err}`)); }, 30000);
  child.stdout.on('data', chunk => {
    out += chunk.toString();
    const line = out.split(/\r?\n/).find(item => item.trim().startsWith('{') && item.includes('"pid"'));
    if (!line) return;
    clearTimeout(timer);
    try { done(JSON.parse(line)); } catch (error) { fail(error); }
    child.stdout.destroy();
    child.stderr.destroy();
    child.unref();
  });
  child.stderr.on('data', chunk => { err += chunk.toString(); });
  child.on('error', error => { clearTimeout(timer); fail(error); });
  child.on('exit', code => { if (!out.includes('"pid"')) { clearTimeout(timer); fail(new Error(`启动失败 ${code}：${err}`)); } });
});
async function start(reopen = false) {
  const port = await reservePort();
  const args = ['-RootParent', rootParent, '-StartupMode', 'normal', '-DebugPort', String(port)];
  if (reopen) args.push('-Root', root);
  launch = await startProcess(args);
  assert.equal(launch.sha256, receipt.sha256);
  assert.equal(resolve(launch.executable), runningExecutable);
  root = resolve(launch.root);
  assert.ok(root.startsWith(rootParent + sep));
  const marker = JSON.parse(await readFile(join(root, '.nanfeng-windows-acceptance.json'), 'utf8'));
  assert.equal(marker.identifier, receipt.identifier);
  return port;
}
function stop() {
  if (!launch) return;
  const pid = Number(launch.pid);
  ps(`$p=Get-CimInstance Win32_Process -Filter "ProcessId=${pid}"; if ($p) { if ([IO.Path]::GetFullPath([string]$p.ExecutablePath) -ne ${quote(runningExecutable)} -or (Get-FileHash -LiteralPath ${quote(runningExecutable)} -Algorithm SHA256).Hash -ne ${quote(receipt.sha256)}) { throw 'PID identity mismatch' }; Stop-Process -Id ${pid} -Force -ErrorAction Stop }`);
  pids.push(pid);
  launch = null;
}
async function cdp(port, method, params) {
  const tabs = await fetch(`http://127.0.0.1:${port}/json/list`).then(response => response.json());
  const tab = tabs.find(item => item.type === 'page' && item.url === 'http://tauri.localhost/');
  assert.ok(tab, 'WebView2 页面未就绪');
  const socket = new WebSocket(tab.webSocketDebuggerUrl);
  try {
    await new Promise((done, fail) => {
      socket.addEventListener('open', done, { once: true });
      socket.addEventListener('error', fail, { once: true });
    });
    return await new Promise((done, fail) => {
      const timer = setTimeout(() => fail(new Error('CDP evaluation timed out')), 30000);
      socket.addEventListener('message', event => {
        const message = JSON.parse(event.data);
        if (message.id !== 1) return;
        clearTimeout(timer);
        if (message.error || message.result?.exceptionDetails) fail(new Error(message.result?.exceptionDetails?.text || message.error?.message));
        else done(message.result);
      });
      socket.send(JSON.stringify({id:1,method,params}));
    });
  } finally { socket.close(); }
}
const evaluate = async (port,expression) => (await cdp(port,'Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true}))?.result?.value;
const undoShortcut = async (port,redo=false) => {
  const params={key:redo?'Z':'z',code:'KeyZ',windowsVirtualKeyCode:90,modifiers:redo?10:2};
  await cdp(port,'Input.dispatchKeyEvent',{type:'rawKeyDown',...params});
  await cdp(port,'Input.dispatchKeyEvent',{type:'keyUp',...params});
};

async function invoke(port, area, command, args = {}) {
  const value = await evaluate(port, `window.__TAURI__.core.invoke(${JSON.stringify(command)},${JSON.stringify(args)},${JSON.stringify({ headers: { 'x-nanfeng-data-area': area } })}).catch(error=>({__error:String(error)}))`);
  if (value?.__error) throw new Error(`${command}: ${value.__error}`);
  return value;
}
async function until(read, label) {
  const deadline = Date.now() + 30000;
  let lastError = null;
  while (Date.now() < deadline) {
    try { const value = await read(); if (value) return value; } catch (error) { lastError = String(error); }
    await new Promise(done => setTimeout(done, 200));
  }
  throw new Error(`${label} 未在原生界面出现${lastError ? `；最后读取错误：${lastError}` : ''}`);
}
const click = (port, selector) => evaluate(port, `(()=>{const node=document.querySelector(${JSON.stringify(selector)});if(!node)return false;node.click();return true})()`);

let result;
try {
  let port = await start();
  try {
    await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'), '原生 IPC');
  } catch (error) {
    let targets;
    try { targets = (await fetch(`http://127.0.0.1:${port}/json/list`).then(response=>response.json())).map(tab=>({type:tab.type,url:tab.url})); }
    catch (readError) { targets = {error:String(readError)}; }
    const diagnostic={pid:launch?.pid,port,targets};
    await writeFile(join(root,'startup-ipc-failure.json'),JSON.stringify(diagnostic,null,2));
    throw new Error(`${error.message}；${JSON.stringify(diagnostic)}`);
  }
  if (verifyInstanceGuard) {
    assert.ok(resolve(launch.launchReceipt).startsWith(rootParent+sep));
    const durable = JSON.parse(await readFile(launch.launchReceipt,'utf8'));
    assert.equal(durable.pid,launch.pid);assert.equal(durable.root,root);assert.equal(durable.sha256,receipt.sha256);
    assert.ok(durable.processStartedAt);
    await assert.rejects(startProcess(['-RootParent',rootParent,'-StartupMode','normal','-DebugPort',String(await reservePort())]), /已有相同候选的隔离正常验收实例/);
    assert.equal(await evaluate(port,'Boolean(window.__TAURI__?.core?.invoke)'),true,'拒绝第二实例不影响原实例');
  }
  const workspaceId = (await invoke(port, 'CHAT', 'ensure_desktop_local_workspace')).summary.id;
  if (imeSearchMode) {
    await until(() => evaluate(port, 'Boolean(document.querySelector("#chat-search"))'), '搜索入口');
    assert.equal(await evaluate(port, `(()=>{document.querySelector('#chat-search').focus();return true})()`), true);
    try {
      await until(() => evaluate(port, 'Boolean(document.querySelector("#full-search-input"))'), '全屏搜索入口');
      await until(() => evaluate(port, 'window.__nanfengDesktopWorkState?.searchLoading === false'), '初始检索结束');
    } catch (error) {
      const diagnostic = await evaluate(port, `({chatSearch:!!document.querySelector('#chat-search'),fullSearch:!!document.querySelector('#full-search-input'),panel:window.__nanfengDesktopWorkState?.searchPanel,loading:window.__nanfengDesktopWorkState?.searchLoading})`);
      throw new Error(`${error.message}: ${JSON.stringify(diagnostic)}`);
    }
    assert.equal(await evaluate(port, `(()=>{const input=document.querySelector('#full-search-input');input.focus();window.__nfaiSearchImeInput=input;input.dispatchEvent(new CompositionEvent('compositionstart',{bubbles:true,data:'南'}));input.value='南';input.dispatchEvent(new InputEvent('input',{bubbles:true,data:'南',isComposing:true}));return true})()`), true);
    await new Promise(done => setTimeout(done, 350));
    assert.equal(await evaluate(port, 'window.__nfaiSearchImeInput?.isConnected && document.querySelector("#full-search-input") === window.__nfaiSearchImeInput'), true, '组词时不能重绘输入框');
    const during = await evaluate(port, `(()=>{const input=window.__nfaiSearchImeInput;input.value='南枫合成检索';input.dispatchEvent(new InputEvent('input',{bubbles:true,data:'南枫合成检索',isComposing:true}));const key=new KeyboardEvent('keydown',{key:'Enter',bubbles:true,cancelable:true,isComposing:true});input.dispatchEvent(key);return {prevented:key.defaultPrevented,query:window.__nanfengDesktopWorkState.chatSearch}})()`);
    assert.equal(during.prevented, false);
    assert.equal(during.query, '南枫合成检索');
    await new Promise(done => setTimeout(done, 350));
    assert.equal(await evaluate(port, 'window.__nfaiSearchImeInput?.isConnected && document.querySelector("#full-search-input") === window.__nfaiSearchImeInput'), true, '组合输入不能提前检索并重绘');
    assert.equal(await evaluate(port, `(()=>{const input=window.__nfaiSearchImeInput;input.dispatchEvent(new CompositionEvent('compositionend',{bubbles:true,data:'南枫合成检索'}));const key=new KeyboardEvent('keydown',{key:'Enter',bubbles:true,cancelable:true});input.dispatchEvent(key);return key.defaultPrevented})()`), false, '组合刚结束的 Enter 不能提交');
    await until(() => evaluate(port, 'window.__nanfengDesktopWorkState?.searchLoading === false && document.querySelector("#full-search-input")?.value === "南枫合成检索"'), '完整候选词检索完成');
    assert.equal(await evaluate(port, 'window.__nanfengDesktopWorkState.searchHistory.includes("南枫合成检索")'), false);
    await new Promise(done => setTimeout(done, 120));
    assert.equal(await evaluate(port, `(()=>{const input=document.querySelector('#full-search-input');const key=new KeyboardEvent('keydown',{key:'Enter',bubbles:true,cancelable:true});input.dispatchEvent(key);return key.defaultPrevented})()`), true);
    await until(() => evaluate(port, 'window.__nanfengDesktopWorkState.searchHistory.includes("南枫合成检索")'), '正常 Enter 搜索历史');
    result = { candidateSha256: receipt.sha256, root, workspaceId, syntheticImeNoPrematureRedraw: true,
      completeQuerySearched: true, normalEnterRecordedHistory: true, pids };
  } else if (historyTitleMode) {
    const title = `W05A-Search-${randomUUID()}`;
    const created = await invoke(port, 'CHAT', 'mutate_desktop_domain', { args: {
      intentId: `search-history-create-${randomUUID()}`, workspaceId, entity: 'conversation', action: 'create',
      objectId: null, expectedRevision: null,
      fields: { title: '合成历史搜索原名', projectId: null, firstMessage: '合成历史搜索正文' },
    } });
    const conversationId = created.objectId;
    await invoke(port, 'CHAT', 'mutate_desktop_domain', { args: {
      intentId: `search-history-rename-${randomUUID()}`, workspaceId, entity: 'conversation', action: 'update',
      objectId: conversationId, expectedRevision: created.revision, fields: { title },
    } });
    let searchRequestId = 990000;
    const query = async () => invoke(port, 'CHAT', 'query_desktop_local_index', { args: {
      query: title, category: 'all', sortMode: 'default', fileType: 'all', offset: 0,
      recordHistory: false, historyWorkspaceId: null,
    }, requestId: ++searchRequestId });
    const openSearch = async () => {
      await until(() => evaluate(port, 'Boolean(document.querySelector("#chat-search"))'), '搜索入口');
      assert.equal(await evaluate(port, `(()=>{const input=document.querySelector('#chat-search');input.value=${JSON.stringify(title)};input.dispatchEvent(new Event('input',{bubbles:true}));input.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',bubbles:true}));return true})()`), true);
      await until(() => evaluate(port, 'Boolean(document.querySelector("#full-search-input"))'), '全屏搜索');
    };
    const refreshSearch = async () => {
      assert.equal(await evaluate(port, `(()=>{const input=document.querySelector('#full-search-input');if(!input)return false;input.value=${JSON.stringify(title)};input.dispatchEvent(new Event('input',{bubbles:true}));return true})()`), true);
    };
    await openSearch();
    await until(() => evaluate(port, `Boolean(document.querySelector('.desktop-search-text-card[data-action="open-search-result"]')?.textContent.includes(${JSON.stringify(title)}))`), '改名后的搜索卡');
    assert.equal((await query()).hits.some(hit => hit.conversationId === conversationId && hit.title === title), true);
    if (keyboardUndoMode) {
      await evaluate(port, `window.__undoKeyEvents=[];window.addEventListener('keydown',event=>{if(event.ctrlKey&&event.key.toLowerCase()==='z')window.__undoKeyEvents.push({trusted:event.isTrusted,editing:['INPUT','TEXTAREA','SELECT'].includes(document.activeElement?.tagName),prevented:event.defaultPrevented,redo:event.shiftKey})});document.querySelector('#full-search-input').focus()`);
      await undoShortcut(port);
      const editingDidNotUndo = (await query()).hits.some(hit=>hit.conversationId===conversationId&&hit.title===title);
      if (!editingDidNotUndo) {
        const diagnostic=await evaluate(port,"({events:window.__undoKeyEvents,activeTag:document.activeElement?.tagName,activeId:document.activeElement?.id,searchPresent:Boolean(document.querySelector('#full-search-input'))})");
        await writeFile(join(root,'keyboard-undo-failure.json'),JSON.stringify(diagnostic,null,2));
        throw new Error(`文本输入焦点误撤销领域：${JSON.stringify(diagnostic)}`);
      }
      await evaluate(port,'document.activeElement?.blur();document.body.tabIndex=-1;document.body.focus()');
      await undoShortcut(port);
      await until(async()=>!(await query()).hits.some(hit=>hit.conversationId===conversationId&&hit.title===title),'快捷键撤销落盘');
    } else await invoke(port, 'CHAT', 'undo_desktop_domain', { args: { intentId: `search-history-undo-${randomUUID()}`, workspaceId } });
    assert.equal((await query()).hits.some(hit => hit.conversationId === conversationId && hit.title === title), false);
    await refreshSearch();
    await until(() => evaluate(port, 'document.querySelector(".desktop-search-state")?.textContent.includes("没有匹配的本机内容")'), '撤销后的搜索空态');
    if (keyboardUndoMode) {
      await evaluate(port,'document.activeElement?.blur();document.body.tabIndex=-1;document.body.focus()');
      await undoShortcut(port,true);
      await until(async()=>(await query()).hits.some(hit=>hit.conversationId===conversationId&&hit.title===title),'快捷键重做落盘');
    } else await invoke(port, 'CHAT', 'redo_desktop_domain', { args: { intentId: `search-history-redo-${randomUUID()}`, workspaceId } });
    assert.equal((await query()).hits.some(hit => hit.conversationId === conversationId && hit.title === title), true);
    await refreshSearch();
    await until(() => evaluate(port, `Boolean(document.querySelector('.desktop-search-text-card[data-action="open-search-result"]')?.textContent.includes(${JSON.stringify(title)}))`), '重做后的搜索卡');
    const keyboardEvidence = keyboardUndoMode ? await evaluate(port,'window.__undoKeyEvents') : null;
    if (keyboardUndoMode) {
      assert.equal(keyboardEvidence.length,3);
      assert.ok(keyboardEvidence.every(event=>event.trusted));
      assert.deepEqual(keyboardEvidence.map(event=>[event.editing,event.prevented,event.redo]),[[true,false,false],[false,true,false],[false,true,true]]);
    }
    stop();
    port = await start(true);
    await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'), '冷启 IPC');
    await openSearch();
    await until(() => evaluate(port, `Boolean(document.querySelector('.desktop-search-text-card[data-action="open-search-result"]')?.textContent.includes(${JSON.stringify(title)}))`), '冷启后的搜索卡');
    assert.equal((await query()).indexCurrent, true);
    result = { candidateSha256: receipt.sha256, root, workspaceId, conversationId,
      title, visibleSearchUndoRedo: true, undoEntry: keyboardUndoMode?'WebView2 Ctrl+Z/Ctrl+Shift+Z':'native IPC', keyboardEvidence, coldReadback: true, pids };
  } else {
  const fixtureDir = join(root, 'synthetic-search-inputs');
  await mkdir(fixtureDir, { recursive: true });
  const selectedName = videoCardMode ? 'stale-target.mp4' : audioCardMode ? 'stale-target.wav' : pdfCardMode ? 'stale-target.pdf' : archiveCardMode ? 'stale-target.zip' : 'stale-target.txt';
  const selectedPath = join(fixtureDir, selectedName);
  if (videoCardMode) execFileSync(join(receipt.mediaTools, 'bin', 'ffmpeg.exe'), [
    '-hide_banner', '-loglevel', 'error', '-f', 'lavfi', '-i', 'color=c=blue:s=128x96:d=1',
    '-c:v', 'libopenh264', '-pix_fmt', 'yuv420p', selectedPath,
  ], {timeout:30000});
  else if (audioCardMode) execFileSync(join(receipt.mediaTools,'bin','ffmpeg.exe'),[
    '-hide_banner','-loglevel','error','-f','lavfi','-i','anullsrc=r=48000:cl=mono','-t','1','-c:a','pcm_s16le',selectedPath,
  ],{timeout:30000});
  else if (pdfCardMode) {
    const streams=[1,2].map(page=>`BT /F1 24 Tf 40 150 Td (Synthetic page ${page}) Tj ET`);
    const objects=[
      '<< /Type /Catalog /Pages 2 0 R >>','<< /Type /Pages /Kids [3 0 R 4 0 R] /Count 2 >>',
      '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 200] /Resources << /Font << /F1 5 0 R >> >> /Contents 6 0 R >>',
      '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 200] /Resources << /Font << /F1 5 0 R >> >> /Contents 7 0 R >>',
      '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
      ...streams.map(text=>`<< /Length ${Buffer.byteLength(text)} >>\nstream\n${text}\nendstream`),
    ];
    let pdf='%PDF-1.4\n';const offsets=[0];
    objects.forEach((object,index)=>{offsets.push(Buffer.byteLength(pdf));pdf+=`${index+1} 0 obj\n${object}\nendobj\n`;});
    const xref=Buffer.byteLength(pdf);
    pdf+=`xref\n0 ${offsets.length}\n0000000000 65535 f \n`+offsets.slice(1).map(offset=>`${String(offset).padStart(10,'0')} 00000 n \n`).join('');
    pdf+=`trailer\n<< /Size ${offsets.length} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`;
    await writeFile(selectedPath,pdf);
  }
  else if (archiveCardMode) execFileSync('python',['-c',
    'import sys,zipfile; z=zipfile.ZipFile(sys.argv[1],"w",zipfile.ZIP_DEFLATED); z.writestr("folder/note.txt","Synthetic ZIP note"); z.writestr("folder/nested/other.txt","Synthetic nested note"); z.close()',selectedPath,
  ],{timeout:30000});
  else await writeFile(selectedPath, 'W13 synthetic attachment only\n');
  if (videoCardMode) {
    const metadata = JSON.parse(execFileSync(join(receipt.mediaTools,'bin','ffprobe.exe'),[
      '-v','error','-select_streams','v:0','-show_entries','stream=codec_name,width,height','-of','json',selectedPath,
    ],{encoding:'utf8',timeout:30000}));
    assert.equal(metadata.streams?.[0]?.codec_name,'h264','合成播放夹具必须明确使用系统支持的H.264');
  }
  const attachment = await invoke(port, 'CHAT', 'import_desktop_conversation_attachment', { args: { workspaceId, selectedPath } });
  const created = await invoke(port, 'CHAT', 'mutate_desktop_domain', { args: {
    intentId: `w13-create-${randomUUID()}`, workspaceId, entity: 'conversation', action: 'create',
    objectId: null, expectedRevision: null,
    fields: { title: 'W13 合成附件定位', projectId: null, firstMessage: '合成问题', attachmentIds: [attachment.id] },
  } });
  const conversationId = created.objectId;
  let projection = await invoke(port, 'CHAT', 'read_desktop_workspace', { workspaceId });
  let conversation = projection.exchange.conversations.find(item => item.id === conversationId);
  const message = conversation.messages.find(item => item.blocks?.some(block => block.asset?.id === attachment.id));
  assert.ok(message);
  await until(() => evaluate(port, 'Boolean(document.querySelector("#chat-search"))'), '搜索入口');
  await evaluate(port, `(()=>{const input=document.querySelector('#chat-search');input.value='stale-target';input.dispatchEvent(new Event('input',{bubbles:true}));input.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',bubbles:true}));return true})()`);
  const card = await until(() => evaluate(port, `Boolean(document.querySelector('[data-search-entry-id] [data-action="open-search-attachment"]'))`), '附件搜索卡');
  assert.equal(card, true);
  const cardBefore = await evaluate(port, `document.querySelector('.desktop-search-attachment-card')?.outerHTML.includes(${JSON.stringify(selectedName)})`);
  assert.equal(cardBefore, true);
  await until(() => evaluate(port, (videoCardMode || pdfCardMode)
    ? `(()=>{const img=document.querySelector('.desktop-search-attachment-card [data-video-thumbnail] img, .desktop-search-attachment-card img');return Boolean(img?.complete && img.naturalWidth>0)})()`
    : (audioCardMode || archiveCardMode) ? `Boolean(document.querySelector('.desktop-search-attachment-card'))` : `Boolean(document.querySelector('.desktop-search-attachment-card pre')?.textContent.includes('W13 synthetic attachment only'))`), '实际原生附件卡预览');
  let videoPlayback = null;
  if (videoCardMode || audioCardMode) {
    const mediaKind = videoCardMode ? 'video' : 'audio';
    await evaluate(port, `document.addEventListener('error',event=>{const v=event.target;if(v?.tagName==='${mediaKind.toUpperCase()}')globalThis.__acceptanceVideoError={code:v.error?.code,message:v.error?.message,readyState:v.readyState,networkState:v.networkState,h264:v.canPlayType('video/mp4; codecs="avc1.42E01E"'),vp9:v.canPlayType('video/mp4; codecs="vp09.00.10.08"')};},true)`);
    assert.equal(await click(port, '[data-search-entry-id] [data-action="open-search-attachment"]'), true);
    try {
      await until(() => evaluate(port, `Boolean(document.querySelector('[data-${mediaKind}-preview]')?.readyState>=2)`), `${mediaKind}真实解码`);
    } catch (error) {
      const diagnostic = await evaluate(port, `(()=>{const v=document.querySelector('[data-${mediaKind}-preview]');return {videoPresent:Boolean(v),readyState:v?.readyState,errorCode:v?.error?.code,sourceScheme:v?.currentSrc?.split(':')[0],captured:globalThis.__acceptanceVideoError,overlayText:document.querySelector('.attachment-preview-overlay')?.textContent?.slice(0,400)}})()`);
      const syntheticBytes = (await readFile(selectedPath)).toString('base64');
      diagnostic.syntheticBlob = await evaluate(port, `(async()=>{const bytes=Uint8Array.from(atob(${JSON.stringify(syntheticBytes)}),c=>c.charCodeAt(0));const url=URL.createObjectURL(new Blob([bytes],{type:'${videoCardMode ? 'video/mp4' : 'audio/wav'}'}));const v=document.createElement('${mediaKind}');v.preload='auto';document.body.append(v);const result=await new Promise(done=>{const finish=()=>done({readyState:v.readyState,width:v.videoWidth,error:v.error?.code||null});v.addEventListener('loadeddata',finish,{once:true});v.addEventListener('error',finish,{once:true});setTimeout(finish,3000);v.src=url;v.load()});v.remove();URL.revokeObjectURL(url);return result})()`);
      await writeFile(join(root,'video-playback-failure.json'),JSON.stringify(diagnostic,null,2));
      throw new Error(`${error.message}: ${JSON.stringify(diagnostic)}`);
    }
    const playback = await evaluate(port, `(async()=>{const v=document.querySelector('[data-${mediaKind}-preview]');await v.play();v.pause();await new Promise(done=>{v.addEventListener('seeked',done,{once:true});v.currentTime=0.4});v.dispatchEvent(new Event('timeupdate'));return {width:v.videoWidth,height:v.videoHeight,duration:v.duration,position:v.currentTime,error:v.error?.code||null}})()`);
    assert.equal(playback.error, null);
    if (videoCardMode) { assert.equal(playback.width, 128); assert.equal(playback.height, 96); }
    assert.ok(Math.abs(playback.position-0.4)<0.1);
    assert.equal(await click(port, `[data-action="close-${mediaKind}-preview"]`), true);
    const saved = await until(async () => {
      const preview = await invoke(port, 'CHAT', `read_desktop_${mediaKind}_preview`, {args:{workspaceId,attachmentId:attachment.id}});
      return preview.positionMillis>=350 && preview.positionMillis<=450 ? preview : false;
    }, '关闭后原生进度保存');
    stop(); port = await start(true);
    await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'), '视频进度冷启 IPC');
    const cold = await invoke(port, 'CHAT', `read_desktop_${mediaKind}_preview`, {args:{workspaceId,attachmentId:attachment.id}});
    assert.equal(cold.positionMillis, saved.positionMillis);
    await until(() => evaluate(port, 'Boolean(document.querySelector("#chat-search"))'), '冷启搜索入口');
    await evaluate(port, `(()=>{const input=document.querySelector('#chat-search');input.value='stale-target';input.dispatchEvent(new Event('input',{bubbles:true}));input.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',bubbles:true}));return true})()`);
    await until(() => evaluate(port, 'Boolean(document.querySelector("[data-search-entry-id] [data-action=open-search-attachment]"))'), '冷启视频卡');
    assert.equal(await click(port, '[data-search-entry-id] [data-action="open-search-attachment"]'), true);
    const reopened = await until(() => evaluate(port, `(()=>{const v=document.querySelector('[data-${mediaKind}-preview]');if(!v||v.readyState<2)return false;v.pause();return {position:v.currentTime,width:v.videoWidth,error:v.error?.code||null}})()`), '冷启重开视频');
    assert.equal(reopened.error,null); if (videoCardMode) assert.equal(reopened.width,128);
    assert.ok(reopened.position>=0.35 && reopened.position<=0.85, '重开应恢复已保存位置');
    assert.equal(await click(port, `[data-action="close-${mediaKind}-preview"]`),true);
    await until(() => evaluate(port, `!document.querySelector('[data-${mediaKind}-preview]')`), '关闭返回搜索');
    videoPlayback = {playback,savedPositionMillis:saved.positionMillis,coldPositionMillis:cold.positionMillis,reopened,closed:true};
  }
  let pdfReadback = null;
  if (pdfCardMode) {
    const pageReady = page => until(() => evaluate(port, `(()=>{const img=document.querySelector('.pdf-preview-frame');return Boolean(img?.complete&&img.naturalWidth>0&&document.querySelector('.pdf-preview-tools')?.textContent.includes('第 ${page} / 2 页'))})()`), `PDF第${page}页`);
    assert.equal(await click(port,'[data-search-entry-id] [data-action="open-search-attachment"]'),true);
    await pageReady(1); const first = await evaluate(port,"document.querySelector('.pdf-preview-frame').src");
    await click(port,'[data-action="pdf-page-next"]');await pageReady(2);
    const second = await evaluate(port,"document.querySelector('.pdf-preview-frame').src");assert.notEqual(first,second);
    await click(port,'[data-action="pdf-page-previous"]');await pageReady(1);
    await click(port,'[data-action="pdf-page-next"]');await pageReady(2);
    await click(port,'[data-action="close-pdf-preview"]');
    await until(()=>evaluate(port,"!document.querySelector('.pdf-preview-frame')"),'关闭PDF');
    stop(); port=await start(true);
    await until(()=>evaluate(port,'Boolean(document.querySelector("#chat-search"))'),'PDF冷启搜索');
    await evaluate(port, `(()=>{const input=document.querySelector('#chat-search');input.value='stale-target';input.dispatchEvent(new Event('input',{bubbles:true}));input.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',bubbles:true}));return true})()`);
    await until(()=>evaluate(port,'Boolean(document.querySelector("[data-search-entry-id] [data-action=open-search-attachment]"))'),'PDF冷启卡');
    await click(port,'[data-search-entry-id] [data-action="open-search-attachment"]');await pageReady(2);
    assert.equal(await evaluate(port,"document.querySelector('.pdf-preview-frame').src"),second);
    await click(port,'[data-action="close-pdf-preview"]');
    pdfReadback={pageCount:2,previousNext:true,distinctPagePixels:true,coldPageNumber:2,coldPixelsEqual:true,closed:true};
  }
  let archiveReadback = null;
  if (archiveCardMode) {
    const enter = async (action,path,attribute='archive-path') => {
      const selector=`[data-action="${action}"][data-${attribute}="${path}"]`;
      await until(()=>evaluate(port,`Boolean(document.querySelector(${JSON.stringify(selector)}))`),'ZIP导航项');
      assert.equal(await click(port,selector),true);
    };
    await click(port,'[data-search-entry-id] [data-action="open-search-attachment"]');
    await enter('open-archive-directory','folder/');
    await enter('open-archive-directory','folder/nested/');
    await enter('open-archive-entry-preview','folder/nested/other.txt','archive-entry-path');
    await until(()=>evaluate(port,"document.querySelector('.archive-text-preview pre')?.textContent==='Synthetic nested note'"),'ZIP内嵌套正文');
    assert.equal(await click(port,'[data-action="archive-preview-back"]'),true);
    await enter('open-archive-directory','folder/');
    await enter('open-archive-entry-preview','folder/note.txt','archive-entry-path');
    await until(()=>evaluate(port,"document.querySelector('.archive-text-preview pre')?.textContent==='Synthetic ZIP note'"),'ZIP内正文');
    await click(port,'[data-action="close-archive-preview"]');
    await until(()=>evaluate(port,"!document.querySelector('.archive-text-preview')"),'关闭ZIP');
    stop();port=await start(true);
    await until(()=>evaluate(port,'Boolean(document.querySelector("#chat-search"))'),'ZIP冷启搜索');
    await evaluate(port,`(()=>{const input=document.querySelector('#chat-search');input.value='stale-target';input.dispatchEvent(new Event('input',{bubbles:true}));input.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',bubbles:true}));return true})()`);
    await until(()=>evaluate(port,'Boolean(document.querySelector("[data-search-entry-id] [data-action=open-search-attachment]"))'),'ZIP冷启卡');
    await click(port,'[data-search-entry-id] [data-action="open-search-attachment"]');
    await enter('open-archive-directory','folder/');
    await enter('open-archive-entry-preview','folder/note.txt','archive-entry-path');
    await until(()=>evaluate(port,"document.querySelector('.archive-text-preview pre')?.textContent==='Synthetic ZIP note'"),'ZIP冷启正文');
    await click(port,'[data-action="close-archive-preview"]');
    archiveReadback={nestedDirectory:true,nestedText:true,returnToParent:true,text:true,coldTextEqual:true,closed:true};
  }
  if (visibleAnchorMode) {
    assert.equal(await evaluate(port, `(()=>{const card=document.querySelector('.desktop-search-attachment-card');if(!card)return false;card.dispatchEvent(new MouseEvent('contextmenu',{bubbles:true,cancelable:true}));return true})()`), true);
    await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=locate-search-attachment]"))'), '搜索附件定位菜单');
    assert.equal(await click(port, '[data-action="locate-search-attachment"]'), true);
    const located = await until(() => evaluate(port, `(()=>{const scope=document.querySelector('.chat-scroll[data-conversation-id=${JSON.stringify(conversationId)}]');const target=scope?.querySelector('.chat-message[data-message-id=${JSON.stringify(message.id)}] [data-attachment-id=${JSON.stringify(attachment.id)}]');return Boolean(target?.classList.contains('search-anchor-flash'))})()`), '准确附件锚点闪烁');
    assert.equal(located, true);
    assert.equal(await click(port, '[data-action="return-to-search"]'), true);
    await until(() => evaluate(port, 'Boolean(document.querySelector(".desktop-search-attachment-card"))'), '定位后返回搜索结果');
  }
  if (visibleDeleteMode) {
    const openCardMenu = async () => {
      assert.equal(await evaluate(port, `(()=>{const card=document.querySelector('.desktop-search-attachment-card');if(!card)return false;card.dispatchEvent(new MouseEvent('contextmenu',{bubbles:true,cancelable:true}));return true})()`), true);
      await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=ask-delete-search-attachment]"))'), '搜索附件菜单');
      assert.equal(await click(port, '[data-action="ask-delete-search-attachment"]'), true);
      await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=confirm-delete-search-attachment]"))'), '删除确认');
    };
    await openCardMenu();
    assert.equal(await click(port, '[data-action="close-dialog"]'), true);
    assert.equal(await evaluate(port, 'Boolean(document.querySelector(".desktop-search-attachment-card"))'), true);
    const afterCancel = await invoke(port, 'CHAT', 'read_desktop_workspace', { workspaceId });
    assert.ok(afterCancel.exchange.conversations.find(item => item.id === conversationId).messages.find(item => item.id === message.id).blocks.some(block => block.asset?.id === attachment.id));
    await openCardMenu();
    assert.equal(await click(port, '[data-action="confirm-delete-search-attachment"]'), true);
    await until(() => evaluate(port, '!document.querySelector(".desktop-search-attachment-card") && !document.querySelector("[data-action=confirm-delete-search-attachment]")'), '搜索结果删除并刷新');
    projection = await invoke(port, 'CHAT', 'read_desktop_workspace', { workspaceId });
    conversation = projection.exchange.conversations.find(item => item.id === conversationId);
    assert.ok(!conversation.messages.find(item => item.id === message.id).blocks.some(block => block.asset?.id === attachment.id));
    const page = await invoke(port, 'CHAT', 'query_desktop_local_index', { args: {
      query: 'stale-target', category: 'file', sortMode: 'default', fileType: 'all',
      offset: 0, recordHistory: false, historyWorkspaceId: null,
    }, requestId: 999002 });
    assert.equal(page.hits.some(hit => hit.attachmentId === attachment.id), false);
    stop();
    port = await start(true);
    await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'), '删除后冷启 IPC');
    projection = await invoke(port, 'CHAT', 'read_desktop_workspace', { workspaceId });
    conversation = projection.exchange.conversations.find(item => item.id === conversationId);
    assert.ok(!conversation.messages.find(item => item.id === message.id).blocks.some(block => block.asset?.id === attachment.id));
    result = { candidateSha256: receipt.sha256, root, workspaceId, conversationId,
      attachmentId: attachment.id, visibleDeleteCancelledSafely: true,
      visibleDeleteRefreshedSearch: true, visibleAnchor: visibleAnchorMode, coldReadback: true, pids };
  } else {
  await invoke(port, 'CHAT', 'mutate_desktop_domain', { args: {
    intentId: `w13-remove-${randomUUID()}`, workspaceId, entity: 'conversation', action: 'removeAttachment',
    objectId: conversationId, expectedRevision: conversation.revision,
    fields: { messageId: message.id, attachmentId: attachment.id },
  } });
  projection = await invoke(port, 'CHAT', 'read_desktop_workspace', { workspaceId });
  conversation = projection.exchange.conversations.find(item => item.id === conversationId);
  assert.ok(!conversation.messages.find(item => item.id === message.id).blocks.some(block => block.asset?.id === attachment.id));
  assert.equal(await evaluate(port, 'Boolean(document.querySelector(".desktop-search-attachment-card"))'), true);
  await evaluate(port, `(()=>{document.querySelector('.desktop-search-attachment-card').dispatchEvent(new MouseEvent('contextmenu',{bubbles:true,cancelable:true}));return true})()`);
  assert.equal(await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=locate-search-attachment]"))'), '附件菜单'), true);
  assert.equal(await click(port, '[data-action="locate-search-attachment"]'), true);
  await until(() => evaluate(port, 'document.querySelector(".desktop-search-state.error")?.textContent.includes("附件引用已删除")'), '过期命中提示');
  assert.equal(await evaluate(port, 'Boolean(document.querySelector(".desktop-search-attachment-card"))'), false);
  assert.equal(await evaluate(port, 'Boolean(document.querySelector(".chat-scroll"))'), false);
  const page = await invoke(port, 'CHAT', 'query_desktop_local_index', { args: {
    query: 'stale-target', category: 'file', sortMode: 'default', fileType: 'all',
    offset: 0, recordHistory: false, historyWorkspaceId: null,
  }, requestId: 999001 });
  assert.equal(page.hits.some(hit => hit.attachmentId === attachment.id), false);
  stop();
  port = await start(true);
  await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'), '冷启 IPC');
  projection = await invoke(port, 'CHAT', 'read_desktop_workspace', { workspaceId });
  conversation = projection.exchange.conversations.find(item => item.id === conversationId);
  assert.ok(conversation && !conversation.messages.find(item => item.id === message.id).blocks.some(block => block.asset?.id === attachment.id));
  await until(() => evaluate(port, `Boolean(document.querySelector('[data-action="select-chat"][data-id="${conversationId}"]'))`), '冷启会话列表');
  assert.equal(await click(port, `[data-action="select-chat"][data-id="${conversationId}"]`), true);
  await until(() => evaluate(port, 'Boolean(document.querySelector("#chat-composer"))'), '冷启聊天输入框');
  const composerConversationId = await evaluate(port, 'document.querySelector("[data-conversation-row].selected")?.dataset.id || null');
  assert.equal(composerConversationId, conversationId);
  const pasteHandled = await evaluate(port, `(()=>{const input=document.querySelector('#chat-composer');const transfer=new DataTransfer();transfer.items.add(new File(['W08 synthetic paste'], 'w08-paste.txt', {type:'text/plain'}));const event=new Event('paste',{bubbles:true,cancelable:true});Object.defineProperty(event,'clipboardData',{value:transfer});input.dispatchEvent(event);return event.defaultPrevented})()`);
  assert.equal(pasteHandled, true);
  await until(() => evaluate(port, 'document.querySelectorAll(".chat-composer-attachments [data-action=remove-composer-attachment]").length === 1'), '粘贴附件草稿卡');
  const dropped = await evaluate(port, `(()=>{const target=document.querySelector('.chat-composer');const transfer=new DataTransfer();transfer.items.add(new File(['W08 synthetic drop'], 'w08-drop.txt', {type:'text/plain'}));const event=new Event('drop',{bubbles:true,cancelable:true});Object.defineProperty(event,'dataTransfer',{value:transfer});target.dispatchEvent(event);return event.defaultPrevented})()`);
  assert.equal(dropped, true);
  await until(() => evaluate(port, 'document.querySelectorAll(".chat-composer-attachments [data-action=remove-composer-attachment]").length === 2'), '拖放附件草稿卡');
  const draft = await until(async () => {
    const value = await invoke(port, 'CHAT', 'read_desktop_conversation_draft', { workspaceId, conversationId: composerConversationId });
    return value?.attachments?.length === 2 ? value : null;
  }, '附件草稿持久化');
  assert.deepEqual(draft.attachments.map(item => item.displayName).sort(), ['w08-drop.txt', 'w08-paste.txt']);
  stop();
  port = await start(true);
  await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'), '附件草稿冷启 IPC');
  const coldDraft = await invoke(port, 'CHAT', 'read_desktop_conversation_draft', { workspaceId, conversationId: composerConversationId });
  assert.deepEqual(coldDraft.attachments.map(item => item.sha256).sort(), draft.attachments.map(item => item.sha256).sort());
  result = { candidateSha256: receipt.sha256, root, workspaceId, conversationId,
    attachmentId: attachment.id, messageId: message.id, cardPreviewKind: videoCardMode ? 'video' : audioCardMode ? 'audio' : pdfCardMode ? 'pdf' : archiveCardMode ? 'archive' : 'text', cardPreviewVisible: true, pdfReadback, archiveReadback, videoPlayback: videoCardMode ? videoPlayback : null, audioPlayback: audioCardMode ? videoPlayback : null, staleCardRejected: true,
    refreshedIndexHasAttachment: false, coldReadback: true,
    composerInputs: { paste: true, drop: true, attachmentIds: draft.attachments.map(item => item.id), coldReadback: true }, pids };
  }
  }
} finally { stop(); }
if (result && verifyInstanceGuard) result.singleInstanceGuard = {durableReceipt:true,rejectedDuplicate:true,originalStillResponsive:true};
console.log(JSON.stringify(result));
