import { completeLaunchReceipt } from './windows-launch-receipt.mjs';
import assert from 'node:assert/strict';
import { createHash, randomUUID } from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import { readFile, writeFile, stat } from 'node:fs/promises';
import http from 'node:http';
import { dirname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

if (process.platform !== 'win32') throw new Error('仅在 Windows 隔离候选上验收。');
const desktopRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
assert.ok(process.argv[2], 'Explicit owned synthetic parent required');
const rootParent = resolve(process.argv[2]);
assert.ok(process.execArgv.includes('--max-old-space-size=2048'));
if (process.argv.includes('--screenshot')) throw new Error('后台验收不支持前台截图');
const receipt = JSON.parse(await readFile(join(desktopRoot, 'output/windows-acceptance/current.json'), 'utf8'));
assert.equal(receipt.identifier, 'com.nanzhufeng.ai.desktop.windowsacceptance');
const source = resolve(desktopRoot, 'output/windows-acceptance', receipt.sha256, 'NanfengAI-Windows-Acceptance.exe');
assert.equal(resolve(receipt.executable), source);
assert.equal(createHash('sha256').update(await readFile(source)).digest('hex').toUpperCase(), receipt.sha256);
const runExecutable = resolve(desktopRoot, 'output/windows-acceptance-binaries', receipt.sha256, 'nanfeng-ai-desktop-spike.exe');
const ps = (args) => execFileSync('pwsh', ['-NoProfile', ...args], { encoding: 'utf8', timeout: 60000, windowsHide: true }).trim();
const launchPowerShell = (args) => new Promise((resolveLaunch, reject) => {
  const child = spawn('pwsh', ['-NoProfile', ...args], { windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
  let stdout = '';
  let stderr = '';
  const timer = setTimeout(() => { child.kill(); reject(new Error(`隔离候选启动超时：${stderr}`)); }, 30000);
  child.stdout.on('data', data => {
    stdout += data.toString();
    let emitted;
    try { emitted=completeLaunchReceipt(stdout); } catch(error) { clearTimeout(timer); reject(error); return; }
    if (!emitted) return;
    clearTimeout(timer);
    readFile(emitted.launchReceipt, 'utf8').then(data=>resolveLaunch(JSON.parse(data)),reject);
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



let launch = null;
let root = '';
const pids = [];
const stop = () => {
  if (!launch) return;
  const pid = Number(launch.pid);
  const literal = value => `'${String(value).replaceAll("'", "''")}'`;
  const script = '$started=[datetime]' + literal(launch.processStartedAt) + '; $expected=' + literal(runExecutable) + '; $hash=' + literal(receipt.sha256) + '; '
    + '$p=Get-CimInstance Win32_Process -Filter "ProcessId=' + pid + '"; '
    + 'if ($p) { if ([IO.Path]::GetFullPath([string]$p.ExecutablePath) -ne $expected '
    + '-or (Get-FileHash -LiteralPath $expected -Algorithm SHA256).Hash -ne $hash) { throw "PID identity mismatch" }; '
    + '$owned=Get-Process -Id ' + pid + ' -ErrorAction Stop; if ([Math]::Abs(($owned.StartTime.ToUniversalTime()-$started.ToUniversalTime()).TotalMilliseconds) -gt 20) { throw "PID start time mismatch" }; ' 
    + 'Stop-Process -Id ' + pid + ' -Force -ErrorAction Stop }';
  ps(['-Command', script]);
  pids.push(pid);
  launch = null;
};
async function start(reopen = false) {
  const port = await reservePort();
  const args = ['-File', join(desktopRoot, 'scripts/start-windows-acceptance.ps1'), '-RootParent', rootParent,
    '-StartupMode', 'normal', '-DebugPort', String(port), '-OrdinaryChatTestEndpoint', `http://127.0.0.1:${providerPort}`];
  if (reopen) args.push('-Root', root);
  launch = await launchPowerShell(args);
  assert.equal(launch.visible, false);
  assert.equal(launch.sha256, receipt.sha256);
  assert.equal(resolve(launch.executable), runExecutable);
  root = resolve(launch.root);
  assert.ok(root.startsWith(rootParent + sep));
  const marker = JSON.parse(await readFile(join(root, '.nanfeng-windows-acceptance.json'), 'utf8'));
  assert.equal(marker.identifier, receipt.identifier);
  return port;
}
async function cdp(port, method, params = {}, which = "main") {
  const tabs = await fetch(`http://127.0.0.1:${port}/json/list`,{signal:AbortSignal.timeout(10000)}).then(response => response.json());
  const tab = tabs.find(item => item.type === 'page' && item.url === (which==='island'?'http://tauri.localhost/island.html':'http://tauri.localhost/'));
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
  let lastError="IPC not initialized";
  while (Date.now() < deadline) {
    try { if (await evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)')) {
      const hidden=JSON.parse(ps(['-File',join(desktopRoot,'scripts/close-windows-hidden-acceptance.ps1'),
        '-ProcessId',String(launch.pid),'-Executable',runExecutable,'-Sha256',receipt.sha256,
        '-StartedAt',launch.processStartedAt,'-InspectOnly']));
      assert.equal(hidden.visibleWindows,0);result.startupHidden=hidden;return;
    } } catch(error) { lastError=String(error); }
    await new Promise(resolveWait => setTimeout(resolveWait, 250));
  }
  throw new Error(`原生 IPC 未就绪: ${lastError}`);
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
  const clicked = await evaluate(port, `(()=>{const nodes=[...document.querySelectorAll(${JSON.stringify(selector)})].filter(n=>n.getClientRects().length && !n.disabled);if(nodes.length!==1)return {count:nodes.length};nodes[0].click();return {count:1}})()`);
  assert.equal(clicked?.count, 1, `控件必须可见、唯一且可用: ${selector}`);
}
async function normalClose() {
  const pid=Number(launch.pid);
  const quote=value=>`'${String(value).replaceAll("'", "''")}'`;
  const info=JSON.parse(ps(['-File', join(desktopRoot,'scripts/close-windows-hidden-acceptance.ps1'),
    '-ProcessId',String(pid),'-Executable',runExecutable,'-Sha256',receipt.sha256,
    '-StartedAt',launch.processStartedAt]));
  assert.equal(info.visibleWindows,0);assert.equal(info.closePosted,true);
  await until(()=>ps(['-Command',`@(Get-CimInstance Win32_Process -Filter "ProcessId=${pid}").Count`])==='0','自然关闭退出');
  await until(()=>ps(['-Command',`@(Get-CimInstance Win32_Process -Filter "Name='msedgewebview2.exe'" | Where-Object {$_.CommandLine -and $_.CommandLine.Contains(${quote(launch.webviewProfile)})}).Count`])==='0','本根WebView退出');
  pids.push(pid);launch=null;return info;
}





const requests=[],serverErrors=[];
const provider=http.createServer(async(req,res)=>{try{
 let raw='';for await(const chunk of req){raw+=chunk.toString('utf8');assert.ok(raw.length<4*1024*1024);}
 const body=JSON.parse(raw);assert.equal(req.headers.authorization,'Bearer local-acceptance-mock-only');assert.equal(body.model,'google/gemini-4-argon');
 const web=!!body.plugins;const image=JSON.stringify(body.messages||[]).includes('data:image/png;base64,');
 requests.push({model:body.model,web,image,idempotencyKey:req.headers['idempotency-key']});assert.ok(requests.length<=3);
 if(web){assert.equal(body.plugins[0].engine,'native');assert.equal(body.tools,undefined);assert.ok(body.messages.some(m=>m.role==='system'&&m.content.includes('不搜索私人信息')));}
 else assert.equal(body.tools,undefined);
 res.writeHead(200,{'Content-Type':'text/event-stream; charset=utf-8'});
 res.write(`data: ${JSON.stringify({model:body.model,choices:[{delta:{content:`Argon synthetic reply ${requests.length}`}}]})}\n\n`);
 const delta=web?{annotations:[{type:'url_citation',url_citation:{url:'https://example.test/argon-public-proof',title:'Synthetic public source'}}]}:{};
 const usage={prompt_tokens:1000,completion_tokens:100,prompt_tokens_details:{cached_tokens:500},...(web?{cost:.0073}:{})};
 res.end(`data: ${JSON.stringify({model:body.model,choices:[{delta,finish_reason:'stop'}],usage})}\n\ndata: [DONE]\n\n`);
}catch(error){serverErrors.push(String(error));res.destroy(error);}});
await new Promise(done=>provider.listen(0,'127.0.0.1',done));const providerPort=provider.address().port;
const result={candidateSha256:receipt.sha256,backgroundOnly:true,realService:false};let port,ws,conv;
async function conversation(){return (await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:ws})).exchange.conversations.find(c=>c.id===conv.id);}
async function preference(enabled){const s=await invoke(port,'CHAT','read_desktop_p6g_selection',{workspaceId:ws,conversationId:conv.id});await invoke(port,'CHAT','set_desktop_conversation_preferences',{args:{workspaceId:ws,conversationId:conv.id,expectedRevision:s.conversationOverride.revision,toneOverride:null,webSearchOverride:enabled}});}
async function selectChat(){await until(()=>evaluate(port,`!!document.querySelector('[data-action="select-chat"][data-id="${conv.id}"]')`),'seed conversation');await clickUi(port,`[data-action="select-chat"][data-id="${conv.id}"]`);await until(()=>evaluate(port,`__nanfengDesktopWorkState.selectedConversationId===${JSON.stringify(conv.id)}`),'selected conversation');}
async function send(text){const oldLeaf=(await conversation()).currentLeafId;await evaluate(port,`(()=>{const i=document.querySelector('#chat-composer');i.value=${JSON.stringify(text)};i.dispatchEvent(new InputEvent('input',{bubbles:true}));return true})()`);await until(()=>evaluate(port,'!!document.querySelector("[data-action=save-local-message]:not(:disabled)")'),'send enabled');await clickUi(port,'[data-action=save-local-message]');const n=requests.length;await until(async()=>{const c=await conversation();const m=c.messages.find(m=>m.id===c.currentLeafId);return m?.id!==oldLeaf&&m?.role==='assistant'&&m.delivery==='COMPLETE'&&requests.length>=n&&m.blocks.some(b=>(b.text||'').includes('Argon synthetic reply'));},'Argon reply complete');}
async function ledger(){return (await invoke(port,'CHAT','read_desktop_usage_ledger')).records.filter(r=>r.conversationId===conv.id);}
async function openModels(){await clickUi(port,'[data-action=show-settings]');await clickUi(port,'[data-action=open-settings-page][data-page=model]');await clickUi(port,'[data-action=open-settings-page][data-page=model-configuration]');await until(()=>evaluate(port,'!!document.querySelector("[data-preset-id=GEMINI_4_ARGON]")'),'Argon settings');}
async function writeCatalog(includeArgon){
 const model=(id,argon=false)=>({id,displayName:id,contextWindowTokens:argon?1048576:8192,maxOutputTokens:argon?65536:1024,inputModalities:argon?['audio','file','image','text','video']:['text'],outputModalities:['text'],supportedParameters:['tools'],inputMicrosPerToken:argon?2:null,outputMicrosPerToken:argon?10:null,cachedInputMicrosPerToken:null});
 const ids=[['CLAUDE_OPUS_5','anthropic/claude-opus-5'],['GPT_5_6_SOL','openai/gpt-5.6-sol'],['GPT_5_6_TERRA','openai/gpt-5.6-terra'],['GEMINI_3_8_FLASH','google/gemini-3.8-flash'],...(includeArgon?[['GEMINI_4_ARGON','google/gemini-4-argon']]:[])];
 const models=ids.map(([,id])=>model(id,id==='google/gemini-4-argon')).sort((a,b)=>a.id.localeCompare(b.id));
 const mappings=ids.map(([presetId,modelId])=>({presetId,modelId}));const hash=createHash('sha256').update(JSON.stringify([models,mappings])).digest('hex');
 const stored={format:'nfai.openrouter-registry',version:1,verifiedAtMs:Date.now(),catalog:{catalogVersion:hash.slice(0,16),catalogSha256:hash,sourceEtag:'synthetic-argon-cache-only',models,presetMappings:mappings}};
 await writeFile(join(root,'model-registry-openrouter-current-v1.json'),JSON.stringify(stored));
}
try{
 port=await start();await ready(port);ws=(await invoke(port,'CHAT','ensure_desktop_local_workspace')).summary.id;
 const settings=await invoke(port,'CHAT','read_desktop_model_service_settings');result.chatPresetCount=settings.flatMap(s=>s.presets||[]).length;assert.equal(result.chatPresetCount,21);
 const setting=settings.find(s=>s.providerId==='OPENROUTER');assert.ok(setting.presets.some(p=>p.id==='GEMINI_4_ARGON'));
 await invoke(port,'CHAT','save_desktop_model_service_settings',{args:{providerId:'OPENROUTER',enabled:true,presetId:'GEMINI_4_ARGON',expectedRevision:setting.revision,apiKey:'nfai-synthetic-argon-only'}});
 conv=(await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:ws})).exchange.conversations[0];const selected=await invoke(port,'CHAT','read_desktop_p6g_selection',{workspaceId:ws,conversationId:conv.id});await invoke(port,'CHAT','set_desktop_p6g_conversation_override',{args:{workspaceId:ws,conversationId:conv.id,expectedRevision:selected.conversationOverride.revision,modelId:'GEMINI_4_ARGON'}});await preference(false);
 result.seedClose=await normalClose();port=await start(true);await ready(port);await selectChat();
 await openModels();result.settingsArgon=await evaluate(port,'(()=>{const n=document.querySelector("[data-preset-id=GEMINI_4_ARGON]");return {disabled:n.disabled,checked:n.getAttribute("aria-checked"),text:n.textContent}})()');assert.equal(result.settingsArgon.checked,'true');assert.equal(result.settingsArgon.disabled,false);
 await clickUi(port,'[data-action=open-settings-page][data-page=feature-review]');result.featureReview=await until(()=>evaluate(port,'__nanfengDesktopWorkState.settingsSection==="feature-review"&&document.querySelector(".android-settings-secondary")?.textContent.includes("建议保留：Gemini 4 Argon")'),'feature review navigation complete');assert.equal(result.featureReview,true);
 await clickUi(port,'[data-action=show-chat][aria-label="返回应用"]');await clickUi(port,'[data-action=toggle-p6g-model-picker]');await clickUi(port,'.composer-model-sheet [data-tier=DEEP]');await clickUi(port,'.composer-model-sheet [data-model-id=GEMINI_4_ARGON]');
 await send('Argon local mock plain');await until(async()=> (await ledger()).length===1,'estimated usage');let rows=await ledger();assert.equal(rows[0].chargeMicros,2050);assert.equal(rows[0].currencyCode,'USD');assert.equal(rows[0].costSource,'LOCAL_ESTIMATE');
 await preference(true);await clickUi(port,`[data-action="select-chat"][data-id="${conv.id}"]`);await send('Argon local mock public search');await until(async()=> (await ledger()).length===2,'reported usage');rows=await ledger();assert.ok(rows.some(r=>r.chargeMicros===7300&&r.currencyCode==='USD'&&r.costSource==='PROVIDER_RESPONSE'));result.firstLedger=rows;
 result.close=await normalClose();await writeCatalog(false);port=await start(true);await ready(port);await selectChat();await openModels();result.missingCacheDisabled=await evaluate(port,'document.querySelector("[data-preset-id=GEMINI_4_ARGON]").disabled');assert.equal(result.missingCacheDisabled,true);assert.equal(requests.length,2);assert.deepEqual(await ledger(),rows);
 result.oldCacheClose=await normalClose();await writeCatalog(true);port=await start(true);await ready(port);await selectChat();await openModels();result.currentCacheEnabled=!(await evaluate(port,'document.querySelector("[data-preset-id=GEMINI_4_ARGON]").disabled'));assert.equal(result.currentCacheEnabled,true);assert.deepEqual(await ledger(),rows);
 await preference(false);
 const png=await readFile(join(desktopRoot,'src/assets/model-brands/gemini.png'));const attachment=await invoke(port,'CHAT','import_desktop_conversation_clipboard_attachment',{args:{workspaceId:ws,displayName:'synthetic-argon-brand.png',bytesBase64:png.toString('base64')}});
 await invoke(port,'CHAT','save_desktop_conversation_draft',{args:{workspaceId:ws,conversationId:conv.id,text:'Argon synthetic PNG',attachmentIds:[attachment.id]}});
 result.imageDraftClose=await normalClose();port=await start(true);await ready(port);await selectChat();await until(()=>evaluate(port,'document.querySelector("#chat-composer")?.value==="Argon synthetic PNG"'),'image draft restored');await send('Argon synthetic PNG');await until(async()=> (await ledger()).length===3,'image usage');result.finalLedger=await ledger();assert.ok(requests[2].image);assert.equal(requests[2].web,false);assert.equal(result.finalLedger.filter(r=>r.chargeMicros===2050).length,2);
 const final=await conversation();const searched=final.messages.find(m=>m.role==='assistant'&&JSON.stringify(m.blocks).includes('Argon synthetic reply 2'));assert.ok(searched&&JSON.stringify(searched.blocks).includes('https://example.test/argon-public-proof'),'actual returned source must persist with the web answer');result.webSourcePersisted=true;result.sourceModels=final.messages.filter(m=>m.role==='assistant'&&m.blocks.some(b=>(b.text||'').includes('Argon synthetic reply'))).map(m=>({model:m.modelSnapshot?.modelId,name:m.modelSnapshot?.displayName,actual:m.actualModelId,delivery:m.delivery}));assert.equal(result.sourceModels.length,3);
 const messageIds=final.messages.filter(m=>m.role==='assistant'&&m.blocks.some(b=>(b.text||'').includes('Argon synthetic reply'))).map(m=>m.id);
 result.footers=await until(async()=>{const values=await evaluate(port,`(${JSON.stringify(messageIds)}).map(id=>document.querySelector('[data-message-id="'+id+'"] .chat-message-metadata')?.textContent||'')`);return values.length===3&&values.every(text=>text.includes('Gemini 4 Argon'))&&values.filter(text=>text.includes('0.0138')).length===2&&values.some(text=>text.includes('0.0491'))&&values;},'frozen model and distinct native footer costs');
 result.finalClose=await normalClose();port=await start(true);await ready(port);assert.deepEqual(await ledger(),result.finalLedger);result.coldClose=await normalClose();assert.equal(requests.length,3);assert.deepEqual(serverErrors,[]);result.pass=true;
}catch(error){result.error=String(error);if(port)try{result.failureState=await evaluate(port,'({pane:__nanfengDesktopWorkState?.pane,error:__nanfengDesktopWorkState?.error,pending:!!__nanfengDesktopWorkState?.pendingChatSubmission})');result.failureLedger=ws&&conv?await ledger():null;}catch{}throw error;}
finally{if(launch)try{result.failureClose=await normalClose();}catch(error){result.closeError=String(error);stop();}await new Promise(done=>provider.close(done));result.root=root;result.requests=requests;result.serverErrors=serverErrors;result.pids=pids;if(root)await writeFile(join(root,'argon-native-evidence.json'),JSON.stringify(result,null,2)+'\n');}
console.log(JSON.stringify({root,pass:result.pass,pids,requests:requests.length}));
