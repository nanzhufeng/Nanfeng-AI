import { packageExchange, preflightPackage, semanticHash, sha256 } from '../../protocol/scripts/exchange-lib.mjs';
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
assert.ok(process.execArgv.includes('--max-old-space-size=2048'));
const receipt = JSON.parse(await readFile(join(desktopRoot, 'output/windows-acceptance/current.json'), 'utf8'));
assert.equal(receipt.identifier, 'com.nanzhufeng.ai.desktop.windowsacceptance');
const source = resolve(desktopRoot, 'output/windows-acceptance', receipt.sha256, 'NanfengAI-Windows-Acceptance.exe');
assert.equal(resolve(receipt.executable), source);
assert.equal(createHash('sha256').update(await readFile(source)).digest('hex').toUpperCase(), receipt.sha256);
const runExecutable = resolve(desktopRoot, 'output/windows-acceptance-binaries', receipt.sha256, 'nanfeng-ai-desktop-spike.exe');
const ps = (args) => execFileSync('pwsh', ['-NoProfile', ...args], { encoding: 'utf8', timeout: 60000 }).trim();
const launchPowerShell = (args) => new Promise((resolveLaunch, reject) => {
  const child = spawn('pwsh', ['-NoProfile', ...args], { stdio: ['ignore', 'pipe', 'pipe'] });
  let stdout = '';
  let stderr = '';
  const timer = setTimeout(() => { child.kill(); reject(new Error(`隔离候选启动超时：${stderr}`)); }, 30000);
  child.stdout.on('data', data => {
    stdout += data.toString();
    const line = stdout.split(/\r?\n/).find(item => item.trim().startsWith('{') && item.includes('"pid"'));
    if (!line) return;
    clearTimeout(timer);
    try { const emitted=JSON.parse(line); readFile(emitted.launchReceipt, 'utf8').then(data=>resolveLaunch(JSON.parse(data)),reject); } catch (error) { reject(error); }
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
const requests=[];
const provider=http.createServer(async(request,response)=>{
 let body='';for await(const chunk of request)body+=chunk.toString('utf8');
 const payload=JSON.parse(body);requests.push({payload,path:request.url});
 response.writeHead(200,{'content-type':'text/event-stream; charset=utf-8'});
 response.end('data: '+JSON.stringify({model:'openai/gpt-5.6-terra',choices:[{delta:{content:'合成预算完整回答'},finish_reason:'stop'}],usage:{prompt_tokens:32,completion_tokens:8,cost:0.000002}})+'\n\ndata: [DONE]\n\n');
});
await new Promise(done=>provider.listen(0,'127.0.0.1',done));const providerPort=provider.address().port;
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
  const args = ['-File', join(desktopRoot, 'scripts/start-windows-acceptance.ps1'), '-ExecutablePath', source, '-RootParent', rootParent,
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
  const tabs = await fetch(`http://127.0.0.1:${port}/json/list`,{signal:AbortSignal.timeout(10000)}).then(response => response.json());
  const tab = tabs.find(item => item.type === 'page' && item.url === 'http://tauri.localhost/');
  assert.ok(tab, '验收 WebView2 页面未就绪');
  const socket = new WebSocket(tab.webSocketDebuggerUrl);
  try {
    await new Promise((resolveReady, reject) => {
      socket.addEventListener('open', resolveReady, { once: true });
      socket.addEventListener('error', reject, { once: true });
    });
    return await new Promise((resolveResult, reject) => {
      const timer = setTimeout(() => reject(new Error('CDP evaluation timed out')), 20000);
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
  const pid=Number(launch.pid);
  const quote=value=>`'${String(value).replaceAll("'", "''")}'`;
  const info=JSON.parse(ps(['-Command',`$p=Get-CimInstance Win32_Process -Filter "ProcessId=${pid}";if(!$p -or [IO.Path]::GetFullPath([string]$p.ExecutablePath) -ne ${quote(runExecutable)} -or (Get-FileHash -LiteralPath ${quote(runExecutable)}).Hash -ne ${quote(receipt.sha256)}){throw 'PID identity mismatch'};$n=Get-Process -Id ${pid};@{title=$n.MainWindowTitle;responding=$n.Responding;accepted=$n.CloseMainWindow()}|ConvertTo-Json -Compress`]));
  assert.ok(info.title && info.responding && info.accepted);
  await until(()=>ps(['-Command',`@(Get-CimInstance Win32_Process -Filter "ProcessId=${pid}").Count`])==='0','自然关闭退出');
  await until(()=>ps(['-Command',`@(Get-CimInstance Win32_Process -Filter "Name='msedgewebview2.exe'" | Where-Object {$_.CommandLine -and $_.CommandLine.Contains(${quote(launch.webviewProfile)})}).Count`])==='0','本根WebView退出');
  pids.push(pid);launch=null;return info;
}



const result={candidateSha256:receipt.sha256,startupMode:'normal',realService:false,normalCloses:[],states:[]};let stage='start';let activePort=null;
const titleA='合成生命周期甲',titleB='合成生命周期乙',renamed='合成已重命名 α（正文不变）';
const bodyA='合成原始正文甲 BODY_A_PRESERVED',bodyB='合成原始正文乙 BODY_B_PRESERVED';
async function reload(port){await evaluate(port,'globalThis.__lifecycleOldPage=true;true');await cdp(port,'Page.reload',{ignoreCache:true});await until(()=>evaluate(port,'globalThis.__lifecycleOldPage!==true && globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady===true'),'实际新页面');}
async function settingsList(port,page){await until(()=>evaluate(port,'globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady===true && Boolean(document.querySelector("[data-action=show-settings]"))'),'设置真实入口就绪');await clickUi(port,'[data-action="show-settings"]');await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=open-settings-page][data-page=conversations]"))'),'对话设置');await clickUi(port,'[data-action="open-settings-page"][data-page="conversations"]');await until(()=>evaluate(port,`Boolean(document.querySelector('[data-action="open-settings-page"][data-page="${page}"]'))`),'列表入口');await clickUi(port,`[data-action="open-settings-page"][data-page="${page}"]`);await until(()=>evaluate(port,`globalThis.__nanfengDesktopWorkState?.settingsSection===${JSON.stringify(page)}`),'列表页面');}
try{
 let port=await start();activePort=port;result.root=root;await ready(port);const workspaceId=(await invoke(port,'CHAT','ensure_desktop_local_workspace')).summary.id;result.workspaceId=workspaceId;
 const work=(await invoke(port,'WORK','ensure_desktop_local_workspace')).summary.id;result.workWorkspaceId=work;result.workBefore=await invoke(port,'WORK','read_desktop_workspace',{workspaceId:work});
 const creates=[];for(const [title,firstMessage] of [[titleA,bodyA],[titleB,bodyB]])creates.push(await invoke(port,'CHAT','mutate_desktop_domain',{args:{intentId:'synthetic-management-'+randomUUID(),workspaceId,entity:'conversation',action:'create',fields:{title,projectId:null,firstMessage}}}));
 const [a,b]=creates.map(x=>x.objectId);result.a=a;result.b=b;
 const read=async()=>invoke(port,'CHAT','read_desktop_workspace',{workspaceId});const find=async id=>(await read()).exchange.conversations.find(c=>c.id===id);const row=id=>`[data-conversation-row][data-id="${id}"]`;
 const baseA=await find(a),baseB=await find(b);result.originalMessages={a:baseA.messages,b:baseB.messages};result.initialExchange=(await read()).exchange;
 const expect=async(id,predicate,label)=>until(async()=>{const c=await find(id);return predicate(c)&&c;},label);
 await reload(port);await until(()=>evaluate(port,`globalThis.__nanfengDesktopWorkState?.current?.summary?.id===${JSON.stringify(workspaceId)}`),'本区投影');await until(()=>evaluate(port,`Boolean(document.querySelector(${JSON.stringify(row(a))}))`),'甲会话');
 stage='rename-cancel-and-save';await clickUi(port,`${row(a)} [data-action="open-conversation-rename"]`);await until(()=>evaluate(port,'Boolean(document.querySelector("#conversation-rename"))'),'标题编辑');await evaluate(port,'(()=>{document.querySelector("#conversation-rename").value="取消不得保存";return true})()');await clickUi(port,'[data-action="close-dialog"]');assert.equal((await find(a)).title,titleA);
 await clickUi(port,`${row(a)} [data-action="open-conversation-rename"]`);await evaluate(port,`(()=>{const n=document.querySelector('#conversation-rename');n.value=${JSON.stringify(renamed)};n.dispatchEvent(new Event('input',{bubbles:true}));return true})()`);await clickUi(port,'[data-action="save-conversation-rename"]');await expect(a,c=>c.title===renamed,'标题持久');await until(()=>evaluate(port,`document.querySelector(${JSON.stringify(row(a))})?.dataset.revision==='2' && globalThis.__nanfengDesktopWorkState?.current?.exchange?.conversations?.find(c=>c.id===${JSON.stringify(a)})?.title===${JSON.stringify(renamed)} && !globalThis.__nanfengDesktopWorkState?.dialog`),'标题新投影菜单');assert.deepEqual((await find(a)).messages,baseA.messages);
 stage='pin-favorite-open-return';await until(()=>evaluate(port,`Boolean(document.querySelector(${JSON.stringify(`${row(a)} [data-action="open-conversation-row-menu"]`)}))`),'菜单');await clickUi(port,`${row(a)} [data-action="open-conversation-row-menu"]`);await clickUi(port,'[data-action="context-menu-pin"]');await expect(a,c=>c.pinned,'置顶');
 await until(()=>evaluate(port,'!document.querySelector("[data-action=context-menu-pin]")'),'菜单关闭');await clickUi(port,`${row(a)} [data-action="open-conversation-row-menu"]`);await clickUi(port,'[data-action="context-menu-favorite"]');await until(async()=>(await invoke(port,'CHAT','read_desktop_favorite_conversation_ids',{workspaceId})).includes(a),'收藏落盘');
 await settingsList(port,'favorites');await until(()=>evaluate(port,`Boolean(document.querySelector('.conversation-lifecycle-row [data-action="select-chat"][data-id="${a}"]'))`),'收藏行');await clickUi(port,`.conversation-lifecycle-row [data-action="select-chat"][data-id="${a}"]`);await until(()=>evaluate(port,`document.querySelector('.chat-scroll')?.textContent.includes(${JSON.stringify(bodyA)})`),'收藏原正文');await clickUi(port,'[data-action="return-to-settings-conversation-list"]');await until(()=>evaluate(port,'globalThis.__nanfengDesktopWorkState?.settingsSection==="favorites"'),'返回收藏列表');
 const disclosure=id=>`.conversation-lifecycle-actions[data-lifecycle-action-disclosure="${id}"]`;await clickUi(port,`${disclosure(a)} summary`);await clickUi(port,`${disclosure(a)} [data-action="toggle-conversation-favorite"]`);await until(async()=>!(await invoke(port,'CHAT','read_desktop_favorite_conversation_ids',{workspaceId})).includes(a),'取消收藏');assert.deepEqual((await find(a)).messages,baseA.messages);
 stage='archive-and-restore';await clickUi(port,'[data-action="show-chat"]');await until(()=>evaluate(port,`Boolean(document.querySelector(${JSON.stringify(row(a))}))`),'聊天返回');await clickUi(port,`${row(a)} [data-action="open-conversation-row-menu"]`);await clickUi(port,'[data-action="context-menu-archive"]');await expect(a,c=>c.archived,'归档');await until(()=>evaluate(port,`!document.querySelector(${JSON.stringify(row(a))})`),'日常列表隐藏');await settingsList(port,'archived');await until(()=>evaluate(port,`Boolean(document.querySelector(${JSON.stringify(disclosure(a))}))`),'归档行');await clickUi(port,`${disclosure(a)} summary`);await clickUi(port,`${disclosure(a)} [data-action="restore-conversation"]`);await expect(a,c=>!c.archived&&!c.deleted,'归档恢复');await until(()=>evaluate(port,`!document.querySelector(${JSON.stringify(disclosure(a))}) && globalThis.__nanfengDesktopWorkState?.dialog==null && globalThis.__nanfengDesktopWorkState?.current?.exchange?.conversations?.some(c=>c.id===${JSON.stringify(a)}&&!c.archived&&!c.deleted)`),'归档恢复实际投影');assert.deepEqual((await find(a)).messages,baseA.messages);
 stage='batch-select-cancel-confirm';await clickUi(port,'[data-action="show-chat"]');await until(()=>evaluate(port,`Boolean(document.querySelector(${JSON.stringify(row(b))}))`),'乙会话');await clickUi(port,'[data-action="toggle-conversation-batch-edit"]');for(const id of [a,b]){await clickUi(port,`[data-action="toggle-batch-conversation"][data-id="${id}"]`);await until(()=>evaluate(port,`document.querySelector('[data-action="toggle-batch-conversation"][data-id="${id}"]')?.getAttribute('aria-checked')==='true'`),'已选');}
 const beforeBatch=await read();await clickUi(port,'[data-action="request-batch-conversation-delete"]');await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=confirm-conversation-batch-delete]"))'),'批删确认');assert.ok(await evaluate(port,`document.querySelector('.dialog')?.textContent.includes('2 个会话将移入回收站，可恢复。')`));assert.deepEqual((await evaluate(port,'globalThis.__nanfengDesktopWorkState.dialog.items.map(i=>i.id)')).sort(),[a,b].sort());await clickUi(port,'[data-action="close-dialog"]');assert.deepEqual(await read(),beforeBatch);
 await clickUi(port,'[data-action="request-batch-conversation-delete"]');await clickUi(port,'[data-action="confirm-conversation-batch-delete"]');await expect(a,c=>c.deleted,'批删甲');await expect(b,c=>c.deleted,'批删乙');const deleted=await read();for(const old of beforeBatch.exchange.conversations.filter(c=>![a,b].includes(c.id)))assert.deepEqual(deleted.exchange.conversations.find(c=>c.id===old.id),old);result.states.push({phase:'both-recycled',exchange:deleted.exchange});assert.deepEqual((await find(a)).messages,baseA.messages);assert.deepEqual((await find(b)).messages,baseB.messages);
 stage='restore-one-and-cold';await settingsList(port,'recycle');await until(()=>evaluate(port,`Boolean(document.querySelector(${JSON.stringify(disclosure(a))}))`),'回收站甲');await clickUi(port,`${disclosure(a)} summary`);await clickUi(port,`${disclosure(a)} [data-action="restore-deleted-conversation"]`);await expect(a,c=>!c.deleted&&!c.archived,'甲恢复');assert.ok((await find(b)).deleted);const oneRestored=await read();result.states.push({phase:'one-restored',exchange:oneRestored.exchange});result.normalCloses.push(await normalClose());port=await start(true);activePort=port;await ready(port);assert.deepEqual(await read(),oneRestored);assert.equal(requests.length,0);
 stage='permanent-dialog-cancel-restore-other';await settingsList(port,'recycle');await until(()=>evaluate(port,`Boolean(document.querySelector(${JSON.stringify(disclosure(b))}))`),'冷启回收站乙');await clickUi(port,`${disclosure(b)} summary`);await clickUi(port,`${disclosure(b)} [data-action="open-conversation-permanent-delete"]`);await until(()=>evaluate(port,'Boolean(document.querySelector(".dialog[role=dialog]"))'),'永久确认');assert.ok(await evaluate(port,`document.querySelector('.dialog')?.textContent.includes(${JSON.stringify(titleB)})`));const beforeCancel=await read();await clickUi(port,'[data-action="close-dialog"]');assert.deepEqual(await read(),beforeCancel);await clickUi(port,`${disclosure(b)} [data-action="restore-deleted-conversation"]`);await expect(b,c=>!c.deleted&&!c.archived,'乙恢复');
 const final=await read();assert.deepEqual(final.exchange.conversations.find(c=>c.id===a).messages,baseA.messages);assert.deepEqual(final.exchange.conversations.find(c=>c.id===b).messages,baseB.messages);assert.equal(final.exchange.conversations.find(c=>c.id===a).title,renamed);assert.equal(final.exchange.conversations.find(c=>c.id===a).pinned,false);assert.ok(!(await invoke(port,'CHAT','read_desktop_favorite_conversation_ids',{workspaceId})).includes(a));result.final=final;assert.deepEqual(await invoke(port,'WORK','read_desktop_workspace',{workspaceId:work}),result.workBefore);
 result.normalCloses.push(await normalClose());port=await start(true);activePort=port;await ready(port);stage='final-cold';assert.deepEqual(await read(),final);assert.deepEqual(await invoke(port,'WORK','read_desktop_workspace',{workspaceId:work}),result.workBefore);assert.equal(requests.length,0);result.normalCloses.push(await normalClose());result.pass=true;
}catch(error){result.error=String(error.stack||error);result.stage=stage;if(launch&&activePort){result.failureUi=await evaluate(activePort,`({error:globalThis.__nanfengDesktopWorkState?.error,status:globalThis.__nanfengDesktopWorkState?.status,area:globalThis.__nanfengDesktopWorkState?.dataArea,pane:globalThis.__nanfengDesktopWorkState?.pane,context:globalThis.__nanfengDesktopWorkState?.contextMenu,rows:[...document.querySelectorAll('[data-conversation-row]')].map(n=>({id:n.dataset.id,revision:n.dataset.revision,pinned:n.dataset.pinned}))})`).catch(e=>String(e));}process.exitCode=1;}
finally{if(launch){result.forcedCleanup=true;stop();}provider.closeAllConnections();await new Promise(done=>provider.close(done));result.pids=pids;if(root)await writeFile(join(root,'conversation-management-chain.json'),JSON.stringify(result,null,2),{flag:'wx'});}
console.log(JSON.stringify({candidateSha256:receipt.sha256,root,pass:result.pass,error:result.error,stage:result.stage,pids}));
