import assert from 'node:assert/strict';
import { createHash, randomUUID } from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import { readFile, writeFile } from 'node:fs/promises';
import http from 'node:http';
import { dirname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

if (process.platform !== 'win32') throw new Error('仅在 Windows 隔离候选上验收。');
const desktopRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const rootParent = resolve(process.argv[2] || 'D:\\CodexTemp');
assert.ok(process.execArgv.includes('--max-old-space-size=2048'));
const screenshotMode = process.argv.includes('--screenshot');
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
const requests=[];const serverErrors=[];
const provider=http.createServer(async(req,res)=>{
 try {
  let raw='';for await(const chunk of req)raw+=chunk.toString('utf8');const body=JSON.parse(raw);
  assert.equal(req.headers.authorization==='Bearer local-acceptance-mock-only',true);
  const retrieval=body.model==='openai/gpt-6-luna' && Array.isArray(body.tools);
  requests.push({model:body.model,role:retrieval?'RETRIEVAL':'ANSWER',idempotencyKey:req.headers['idempotency-key'],grounded:JSON.stringify(body.messages||[]).includes('<retrieved-evidence>')});
  res.writeHead(200,{'Content-Type':'text/event-stream; charset=utf-8'});const event=value=>res.write(`data: ${JSON.stringify(value)}\n\n`);
  if(retrieval) {
   assert.equal(body.tool_choice,'required');assert.equal(body.provider.allow_fallbacks,false);
   event({model:body.model,choices:[{delta:{content:'合成检索资料'}}]});
   res.end(`data: ${JSON.stringify({model:body.model,choices:[{delta:{annotations:[{type:'url_citation',url_citation:{url:'https://example.test/companion-proof',title:'Synthetic evidence'}}]},finish_reason:'stop'}],usage:{prompt_tokens:5,completion_tokens:2,cost:0.000004}})}`);
  }else if(body.output_config) {
   assert.equal(req.headers['x-api-key']==='local-acceptance-mock-only',true);assert.equal(req.headers['anthropic-version'],'2023-06-01');
   event({type:'message_start',message:{model:body.model,usage:{input_tokens:5,cache_read_input_tokens:1}}});
   event({type:'content_block_delta',delta:{type:'text_delta',text:'合成原模型回答'}});
   event({type:'message_delta',delta:{stop_reason:'end_turn'},usage:{output_tokens:2}});res.end('data: {"type":"message_stop"}');
  }else {
   if(body.model.startsWith('gemini-'))assert.equal(body.max_tokens,undefined);
   event({model:body.model,choices:[{delta:{content:'合成原模型回答'}}]});
   res.end(`data: ${JSON.stringify({model:body.model,choices:[{delta:{},finish_reason:'stop'}],usage:{prompt_tokens:5,completion_tokens:2,cost:0.000004}})}`);
  }
 }catch(error){serverErrors.push(String(error));res.destroy(error);}
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
  const clicked = await evaluate(port, `(()=>{const nodes=[...document.querySelectorAll(${JSON.stringify(selector)})].filter(n=>n.getClientRects().length && !n.disabled);if(nodes.length!==1)return {count:nodes.length};nodes[0].click();return {count:1}})()`);
  assert.equal(clicked?.count, 1, `控件必须可见、唯一且可用: ${selector}`);
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





const result={candidateSha256:receipt.sha256,realService:false,requests,serverErrors};let port=0;let stage='start';
const sql=code=>execFileSync('python',['-c',code,root],{encoding:'utf8',timeout:10000});
async function newChat() {
 const back=await evaluate(port,`Boolean(document.querySelector('[data-action="show-chat"]'))`);if(back)await clickUi(port,'[data-action="show-chat"]');
 await until(()=>evaluate(port,`(()=>{const b=document.querySelector('.chat-header-content-actions [data-action="new-chat"]');return b&&!b.disabled&&b.getClientRects().length})()`),'新对话');
 await clickUi(port,'.chat-header-content-actions [data-action="new-chat"]');
}
try {
 port=await start();result.root=root;await ready(port);const ws=(await invoke(port,'CHAT','ensure_desktop_local_workspace')).summary.id;
 const settings=await invoke(port,'CHAT','read_desktop_model_service_settings');for(const setting of settings)await invoke(port,'CHAT','save_desktop_model_service_settings',{args:{providerId:setting.providerId,enabled:true,presetId:setting.presetId,expectedRevision:setting.revision,apiKey:`nfai-synthetic-last-model-${setting.providerId}`}});
 result.closeBeforeUpgrade=await normalClose();
 result.upgradeBefore=JSON.parse(sql(`import sqlite3,sys,json,hashlib
from pathlib import Path
root=Path(sys.argv[1]); old=Path('D:/CodexTemp/nanfeng-ai-windows-acceptance.04da2d88-ca9b-42a0-9d76-716ba5b65aa9'); result=[]
assert root.parent==Path('D:/CodexTemp') and (root/'.nanfeng-windows-acceptance.json').is_file()
for name in ['workspace.sqlite3','work-area/workspace.sqlite3']:
 source=sqlite3.connect('file:'+str(old/name)+'?mode=ro',uri=True);assert source.execute('PRAGMA user_version').fetchone()[0]==56
 current=sqlite3.connect(root/name);saved=sqlite3.connect(root/('before-model-upgrade-'+name.replace('/','-')));current.backup(saved);saved.close();source.backup(current);current.close()
 rows={t:source.execute('SELECT * FROM '+t+' ORDER BY rowid').fetchall() for t in ['workspace_exchange','p6g_conversation_override','desktop_conversation_drafts_v1']}
 result.append({'file':name,'version':56,'digest':hashlib.sha256(json.dumps(rows,sort_keys=True).encode()).hexdigest()})
print(json.dumps(result))`));
 port=await start(true);await ready(port);await invoke(port,'CHAT','read_desktop_p6g_global_default');
 result.upgradeAfter=JSON.parse(sql(`import sqlite3,sys,json,hashlib
from pathlib import Path
root=Path(sys.argv[1]);result=[]
for name in ['workspace.sqlite3','work-area/workspace.sqlite3']:
 c=sqlite3.connect('file:'+str(root/name)+'?mode=ro',uri=True)
 rows={t:c.execute('SELECT * FROM '+t+' ORDER BY rowid').fetchall() for t in ['workspace_exchange','p6g_conversation_override','desktop_conversation_drafts_v1']}
 result.append({'file':name,'version':c.execute('PRAGMA user_version').fetchone()[0],'digest':hashlib.sha256(json.dumps(rows,sort_keys=True).encode()).hexdigest()})
print(json.dumps(result))`));
 for(let i=0;i<2;i++){assert.equal(result.upgradeAfter[i].version,57);assert.equal(result.upgradeAfter[i].digest,result.upgradeBefore[i].digest);}
 let pref=await invoke(port,'CHAT','read_desktop_p6g_global_default');assert.equal(pref.composerRevision,0);
 await invoke(port,'CHAT','set_desktop_new_conversation_model',{modelId:'RELAYCORE_GPT_6_ASTRA',expectedRevision:0});
 pref=await invoke(port,'CHAT','read_desktop_p6g_global_default');assert.equal(pref.modelId,'RELAYCORE_GPT_6_ASTRA');assert.equal(pref.composerRevision,1);
 const work=await invoke(port,'WORK','read_desktop_p6g_global_default');assert.equal(work.modelId,null);assert.equal(work.composerRevision,0);result.areaIsolation=true;
 result.closeToRemember=await normalClose();port=await start(true);await ready(port);await newChat();
 result.rememberedLabel=await until(()=>evaluate(port,`(()=>{const t=document.querySelector('[data-action="toggle-p6g-model-picker"] span')?.textContent;return t?.includes('Astra')?t:null})()`),'记住的Astra');
 sql(`import sqlite3,sys;from pathlib import Path;c=sqlite3.connect(Path(sys.argv[1])/'workspace.sqlite3');c.execute("CREATE TRIGGER reject_ui_choice BEFORE UPDATE ON p6g_composer_default BEGIN SELECT RAISE(ABORT,'synthetic ui choice'); END;");c.commit()`);
 await clickUi(port,'[data-action="toggle-p6g-model-picker"]');
 await until(()=>evaluate(port,`Boolean(document.querySelector('[data-action="select-p6g-auto"]'))`),'失败测试Auto入口');
 await clickUi(port,'[data-action="select-p6g-auto"]');
 await until(()=>evaluate(port,`document.body.innerText.includes('型号选择未保存')`),'型号保存错误');
 const failedLabel=await evaluate(port,`document.querySelector('[data-action="toggle-p6g-model-picker"] span')?.textContent`);
 assert.ok(failedLabel.includes('Astra'));assert.equal((await invoke(port,'CHAT','read_desktop_p6g_global_default')).modelId,'RELAYCORE_GPT_6_ASTRA');result.uiFailurePreservedSelection=true;
 sql(`import sqlite3,sys;from pathlib import Path;c=sqlite3.connect(Path(sys.argv[1])/'workspace.sqlite3');c.execute('DROP TRIGGER reject_ui_choice');c.commit()`);
 await clickUi(port,'[data-action="select-p6g-auto"]');
 await until(async()=> (await invoke(port,'CHAT','read_desktop_p6g_global_default')).modelId===null,'故障解除后的同入口重试');
 assert.equal((await invoke(port,'CHAT','read_desktop_p6g_global_default')).modelId,null);result.uiRetrySaved=true;

 const before=await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:ws});const id=before.exchange.conversations[0].id;
 const selection=await invoke(port,'CHAT','read_desktop_p6g_selection',{workspaceId:ws,conversationId:id});
 await invoke(port,'CHAT','set_desktop_p6g_conversation_override',{args:{workspaceId:ws,conversationId:id,expectedRevision:selection.conversationOverride.revision,modelId:'RELAYCORE_GPT_6_1_SOL'}});
 const selected=await invoke(port,'CHAT','read_desktop_p6g_selection',{workspaceId:ws,conversationId:id});const last=await invoke(port,'CHAT','read_desktop_p6g_global_default');assert.equal(last.modelId,'RELAYCORE_GPT_6_1_SOL');
 sql(`import sqlite3,sys;from pathlib import Path;c=sqlite3.connect(Path(sys.argv[1])/'workspace.sqlite3');c.execute("CREATE TRIGGER reject_composer BEFORE UPDATE ON p6g_composer_default BEGIN SELECT RAISE(ABORT,'synthetic'); END;");c.commit()`);
 await assert.rejects(invoke(port,'CHAT','clear_desktop_p6g_conversation_override',{workspaceId:ws,conversationId:id,expectedRevision:selected.conversationOverride.revision}));
 assert.equal((await invoke(port,'CHAT','read_desktop_p6g_selection',{workspaceId:ws,conversationId:id})).conversationOverride.modelId,'RELAYCORE_GPT_6_1_SOL');result.atomicRollback=true;
 sql(`import sqlite3,sys;from pathlib import Path;c=sqlite3.connect(Path(sys.argv[1])/'workspace.sqlite3');c.execute('DROP TRIGGER reject_composer');c.commit()`);
 await assert.rejects(invoke(port,'CHAT','set_desktop_new_conversation_model',{modelId:null,expectedRevision:0}),/REVISION_CONFLICT/);
 await invoke(port,'CHAT','set_desktop_new_conversation_model',{modelId:null,expectedRevision:last.composerRevision});
 result.closeForAuto=await normalClose();port=await start(true);await ready(port);await newChat();
 result.autoLabel=await until(()=>evaluate(port,`(()=>{const t=document.querySelector('[data-action="toggle-p6g-model-picker"] span')?.textContent;return t?.includes('RC')?t:null})()`),'明确Auto冷读回');
 assert.equal((await invoke(port,'CHAT','read_desktop_p6g_global_default')).modelId,null);assert.equal(requests.length,0);
 result.closeFinal=await normalClose();result.pass=true;
}catch(error){if(port&&launch){try{result.uiText=await evaluate(port,'document.body.innerText.slice(0,1400)');}catch{}}result.pass=false;result.stage=stage;result.error=String(error);process.exitCode=1;}
finally{if(launch){result.forcedCleanup=true;stop();}provider.closeAllConnections();await new Promise(done=>provider.close(done));result.pids=pids;if(root)await writeFile(join(root,'last-model-native-evidence.json'),JSON.stringify(result,null,2),{flag:'wx'});}
console.log(JSON.stringify({root,pass:result.pass,stage:result.stage,error:result.error,requests:requests.length,pids}));
