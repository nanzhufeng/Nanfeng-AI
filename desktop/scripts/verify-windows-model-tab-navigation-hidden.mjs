import assert from 'node:assert/strict';
import { createHash, randomUUID } from 'node:crypto';
import { spawn, execFileSync } from 'node:child_process';
import { readFile, writeFile, stat } from 'node:fs/promises';
import { dirname, resolve, join, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import http from 'node:http';
import net from 'node:net';

assert.equal(process.platform, 'win32');
const desktop = resolve(dirname(fileURLToPath(import.meta.url)), '..');
assert.ok(process.argv[2], 'Explicit owned synthetic parent required');
const parent = resolve(process.argv[2]);
assert.equal(parent, resolve(desktop, '../.runtime-qa/model-tab-navigation-20261017'));
const scenario=process.argv[3];assert.ok(['tab'].includes(scenario));
const compare = false;assert.ok(process.execArgv.includes('--max-old-space-size=2048'));
const visible = false;
assert.ok(!(visible && compare));
const receipt = JSON.parse(await readFile(join(desktop, 'output/windows-acceptance-release/current.json'), 'utf8'));
assert.equal(receipt.identifier, 'com.nanzhufeng.ai.desktop.windowsacceptance');
assert.equal(createHash('sha256').update(await readFile(receipt.executable)).digest('hex').toUpperCase(), receipt.sha256);
assert.equal(receipt.buildProfile, 'release');
assert.equal(receipt.sha256, process.env.NANFENG_AI_EXPECTED_CANDIDATE_SHA256);
const executable = resolve(desktop, 'output/windows-acceptance-binaries', receipt.sha256, 'nanfeng-ai-desktop-spike.exe');
const expectedSourceInputSha256=createHash('sha256').update(await readFile(resolve(desktop,'../docs/review/20261017-windows-model-tab-navigation/source-input-manifest-release-v1.json'))).digest('hex');
const helper = join(desktop, 'scripts/windows-acceptance-cdp-eval.mjs');

let requests=0;const wire=[];const serverErrors=[];const closes=[];
const answerText=scenario==='blank-error'?'returned model synthetic partial answer':'returned model synthetic answer';
const expectedModel=scenario==='null-only'?null:'openai/gpt-6-luna';
const provider=http.createServer(async(request,response)=>{
 try {
  let raw='';for await(const chunk of request)raw+=chunk;const body=JSON.parse(raw);requests++;
  assert.equal(body.model,'openai/gpt-6-luna');assert.equal(body.stream,true);assert.ok(!body.tools);
  wire.push({model:body.model,stream:body.stream,materialExact:JSON.stringify(body.messages).includes('returned model tail synthetic case'),unusedAttachmentAbsent:!JSON.stringify(body.messages).includes('Python 3.14.0')});
  response.writeHead(200,{'Content-Type':'text/event-stream'});const event=value=>response.write('data: '+JSON.stringify(value)+'\n\n');
  event({model:scenario==='null-only'?'null':'openai/gpt-6-luna',choices:[{delta:{content:answerText}}]});
  if(scenario==='blank-error'){
   event({model:'',error:{code:'server_error'},usage:{prompt_tokens:5,completion_tokens:2,cost:0.000004}});
  }else{
   event({model:scenario==='null-only'?'null':'',choices:[{delta:{},finish_reason:'stop'}],usage:{prompt_tokens:5,completion_tokens:2,cost:0.000004}});
   response.write('data: [DONE]\n\n');
  }
  response.end();
 }catch(error){serverErrors.push(String(error));response.writeHead(500);response.end();}
});
await new Promise(done => provider.listen(0, '127.0.0.1', done));
const endpoint = `http://127.0.0.1:${provider.address().port}`;
let launch, root, port;
const pids = [];
const windows = [];
const quote = value => `'${String(value).replaceAll("'", "''")}'`;
const ps = code => execFileSync('pwsh', ['-NoProfile', '-Command', `[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false);${code}`], { encoding: 'utf8', windowsHide: true, timeout: 60000 }).trim();
const privateDesktopGuard=JSON.parse(ps(`& ${quote(join(desktop,'scripts/assert-windows-private-desktop.ps1'))}`));assert.equal(privateDesktopGuard.verified,true);
async function freePort() {
  const server = net.createServer();
  await new Promise(done => server.listen(0, '127.0.0.1', done));
  const value = server.address().port;
  await new Promise(done => server.close(done));
  return value;
}
async function until(read, label) {
  const deadline = Date.now() + 30000;
  while (Date.now() < deadline) {
    try { const value = await read(); if (value) return value; } catch { /* Startup only. */ }
    await new Promise(done => setTimeout(done, 100));
  }
  throw new Error(label);
}
async function evaluate(expression) {
  return await new Promise((done, fail) => {
    const child = spawn(process.execPath, ['--max-old-space-size=2048', helper, String(port)], { windowsHide: true });
    let output = '', errors = '';
    child.stdout.on('data', chunk => output += chunk);
    child.stderr.on('data', chunk => errors += chunk);
    child.on('error', fail);
    child.on('close', code => { try { code === 0 ? done(JSON.parse(output)) : fail(new Error(errors)); } catch (error) { fail(error); } });
    child.stdin.end(expression);
  });
}
async function invoke(command, args = {}) {
  const value = await evaluate(`window.__TAURI__.core.invoke(${JSON.stringify(command)},${JSON.stringify(args)},{headers:{'x-nanfeng-data-area':'CHAT'}}).catch(error=>({__error:String(error)}))`);
  assert.ok(!value?.__error, `${command}: ${value?.__error}`);
  return value;
}
async function start(reopen = false) {
  port = await freePort();
  const code = `[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false); & ${quote(join(desktop, 'scripts/start-windows-acceptance.ps1'))} -RootParent ${quote(parent)} -ExecutablePath ${quote(receipt.executable)} -StartupMode normal -DebugPort ${port} -OrdinaryChatTestEndpoint ${quote(endpoint)}${reopen ? ` -Root ${quote(root)}` : ''}`;
  const args = ['-NoProfile', '-Command', code];
  launch = await new Promise((done, fail) => {
    const child = spawn('pwsh', args, { windowsHide: true });
    let output = '', errors = '';
    child.stdout.setEncoding('utf8'); child.stderr.setEncoding('utf8');
    const timer = setTimeout(() => { child.kill(); fail(new Error(`launcher timeout: ${errors}`)); }, 30000);
    child.stdout.on('data', chunk => {
      output += chunk;
      const line = output.split(/\r?\n/).find(item => item.trim().startsWith('{') && item.includes('"pid"'));
      if (!line) return;
      try { const value = JSON.parse(line); clearTimeout(timer); child.stdout.destroy(); child.stderr.destroy(); child.unref(); done(value); } catch {}
    });
    child.stderr.on('data', chunk => errors += chunk);
    child.on('error', error => { clearTimeout(timer); fail(error); });
    child.on('close', code => { if (!output.includes('"pid"')) { clearTimeout(timer); fail(new Error(`launcher ${code}: ${errors}`)); } });
  });
  assert.equal(launch.visible,false);assert.equal(launch.sha256, receipt.sha256);
  assert.equal(resolve(launch.executable), executable);
  root = resolve(launch.root);
  assert.ok(root.startsWith(parent + sep));
  assert.equal(JSON.parse(await readFile(join(root, '.nanfeng-windows-acceptance.json'), 'utf8')).identifier, receipt.identifier);
  await until(() => evaluate('Boolean(window.__TAURI__?.core?.invoke)'), 'native IPC unavailable');
  await until(() => evaluate('globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady===true'), 'native data areas unavailable');

}
function stop() {
  if (!launch) return;
  const pid = Number(launch.pid);
  ps(`$p=Get-CimInstance Win32_Process -Filter 'ProcessId=${pid}';if($p){if([IO.Path]::GetFullPath([string]$p.ExecutablePath) -ne ${quote(executable)} -or (Get-FileHash -LiteralPath ${quote(executable)} -Algorithm SHA256).Hash -ne ${quote(receipt.sha256)}){throw 'PID mismatch'};$owned=Get-Process -Id ${pid} -ErrorAction Stop;$started=[DateTime]::Parse(${quote(launch.processStartedAt)}).ToUniversalTime();if([Math]::Abs(($owned.StartTime.ToUniversalTime()-$started).TotalMilliseconds) -gt 20){throw 'PID start time mismatch'};Stop-Process -Id ${pid} -Force}`);
  pids.push(pid); launch = null;
}
async function normalClose(){
 const pid=Number(launch.pid);
 const value=JSON.parse(ps(`& ${quote(join(desktop,'scripts/close-windows-hidden-acceptance.ps1'))} -ProcessId ${pid} -Executable ${quote(executable)} -Sha256 ${quote(receipt.sha256)} -StartedAt ${quote(launch.processStartedAt)}`));
 assert.equal(value.visibleWindows,0);assert.equal(value.closePosted,true);
 await until(()=>ps(`@(Get-CimInstance Win32_Process -Filter "ProcessId=${pid}").Count`)==='0','natural app exit');
 await until(()=>ps(`@(Get-CimInstance Win32_Process -Filter "Name='msedgewebview2.exe'" | Where-Object {$_.CommandLine -and $_.CommandLine.Contains(${quote(launch.webviewProfile)})}).Count`)==='0','owned profile exit');
 pids.push(pid);launch=null;closes.push(value);
}

const result={pass:false,candidateSha256:receipt.sha256,scenario,backgroundOnly:true,realService:false,privateDesktopGuard};
async function pressTab(shift=false){
 assert.ok(Number.isInteger(port)&&port>=1024&&port<=65535);
 const tabs=await fetch('http://127.0.0.1:'+port+'/json/list',{signal:AbortSignal.timeout(5000)}).then(r=>r.json());
 const tab=tabs.find(x=>x.type==='page'&&x.url==='http://tauri.localhost/');assert.ok(tab);
 const url=new URL(tab.webSocketDebuggerUrl);assert.ok(['127.0.0.1','localhost'].includes(url.hostname));assert.equal(Number(url.port),port);
 const ws=new WebSocket(tab.webSocketDebuggerUrl);
 try {
  await new Promise((done,fail)=>{const timer=setTimeout(()=>fail(new Error('CDP input connect timeout')),5000);ws.addEventListener('open',()=>{clearTimeout(timer);done();},{once:true});ws.addEventListener('error',error=>{clearTimeout(timer);fail(error);},{once:true});});
  let id=0;
  const command=params=>new Promise((done,fail)=>{const current=++id;const timer=setTimeout(()=>{ws.removeEventListener('message',listener);fail(new Error('CDP key event timeout'));},5000);const listener=event=>{const value=JSON.parse(event.data);if(value.id!==current)return;clearTimeout(timer);ws.removeEventListener('message',listener);value.error?fail(new Error('CDP key dispatch rejected')):done();};ws.addEventListener('message',listener);ws.send(JSON.stringify({id:current,method:'Input.dispatchKeyEvent',params}));});
  const key={key:'Tab',code:'Tab',windowsVirtualKeyCode:9,nativeVirtualKeyCode:9,modifiers:shift?8:0};
  await command({type:'rawKeyDown',...key});await command({type:'keyUp',...key});
 }finally{ws.close();}
 await evaluate('new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve)))');
}
async function focusSnapshot(){
 return evaluate("(()=>{const x=document.activeElement;return {tag:x?.tagName,id:x?.id??null,action:x?.dataset.action??null,preset:x?.dataset.presetId??null,provider:x?.dataset.providerId??null,platform:x?.getAttribute('data-provider-platform')??null,disabled:x?.disabled===true}})()");
}
try {
 await start();result.runtimeInfo=await invoke('read_desktop_runtime_info');assert.equal(result.runtimeInfo.sourceInputSha256,expectedSourceInputSha256);assert.equal(result.runtimeInfo.buildProfile,'release');
 await invoke('ensure_desktop_local_workspace');
 for(const selector of ['[data-action=show-settings]','[data-action=open-settings-page][data-page=model]','[data-action=open-settings-page][data-page=model-configuration]']){
  await until(()=>evaluate('Boolean(document.querySelector('+JSON.stringify(selector)+'))'),'settings entry');await evaluate('document.querySelector('+JSON.stringify(selector)+').click();true');
 }
 await until(()=>evaluate("Boolean(document.querySelector('.model-settings-browser [role=radiogroup] button.selected'))"),'model options');
 const before=await invoke('read_desktop_model_service_settings');result.initialProvider=await evaluate('__nanfengDesktopWorkState.modelProviderId');
 assert.equal(result.initialProvider,'OPENROUTER');
 await evaluate("(()=>{window.__tabKeys=[];document.addEventListener('keydown',event=>{if(event.key==='Tab')(()=>{const record={trusted:event.isTrusted,shift:event.shiftKey,targetId:event.target?.id??null,targetAction:event.target?.dataset?.action??null,preventedAtCapture:event.defaultPrevented};window.__tabKeys.push(record);queueMicrotask(()=>{record.preventedAfterDispatch=event.defaultPrevented;});})();},true);window.__tabCommands=[];const original=window.fetch.bind(window);window.fetch=(input,init)=>{const url=typeof input==='string'?input:input.url;window.__tabCommands.push(decodeURIComponent(new URL(url,location.href).pathname).split('/').filter(Boolean).join('/'));return original(input,init);};return true})()");
 const modelIds=await evaluate("[...document.querySelectorAll('.model-settings-model-list button')].filter(x=>!x.disabled).map(x=>x.dataset.presetId)");assert.ok(modelIds.length>1);
 const platformIds=await evaluate("[...document.querySelectorAll('.model-settings-platform-rail button')].filter(x=>!x.disabled).map(x=>x.dataset.providerId)");assert.equal(platformIds.length,5);
 await evaluate("document.querySelector('.model-settings-model-list button:not(:disabled)').focus();true");result.forward=[];
 for(let i=1;i<modelIds.length;i++){await pressTab();const focus=await focusSnapshot();assert.equal(focus.preset,modelIds[i]);assert.equal(focus.disabled,false);result.forward.push(focus);}
 await pressTab();result.afterModels=await focusSnapshot();assert.equal(result.afterModels.action,'toggle-model-service-enabled');
 result.backward=[];
 for(let i=modelIds.length-1;i>=0;i--){await pressTab(true);const focus=await focusSnapshot();assert.equal(focus.preset,modelIds[i]);result.backward.push(focus);}
 result.platformBackward=[];
 for(let i=platformIds.length-1;i>=0;i--){await pressTab(true);const focus=await focusSnapshot();assert.equal(focus.provider,platformIds[i]);assert.equal(focus.platform,'true');result.platformBackward.push(focus);}
 result.platformForward=[];
 for(let i=1;i<platformIds.length;i++){await pressTab();const focus=await focusSnapshot();assert.equal(focus.provider,platformIds[i]);result.platformForward.push(focus);}
 await pressTab();assert.equal((await focusSnapshot()).preset,modelIds[0]);
 await evaluate("document.querySelector('[data-action=toggle-model-service-enabled]').focus();true");
 await pressTab();result.registryRefreshFocus=await focusSnapshot();assert.equal(result.registryRefreshFocus.action,'refresh-openrouter-registry');
 await pressTab();result.credentialInput=await focusSnapshot();assert.equal(result.credentialInput.id,'model-service-api-key');
 const disabled=await evaluate("[...document.querySelectorAll('[data-action=reveal-model-service-credential],[data-action=test-model-service-connection],[data-action=save-model-service-settings]')].map(x=>({action:x.dataset.action,disabled:x.disabled}))");
 assert.ok(disabled.length===3&&disabled.every(x=>x.disabled));result.disabledActions=disabled;
 await pressTab(true);result.credentialReverse=await focusSnapshot();assert.equal(result.credentialReverse.action,'refresh-openrouter-registry');
 await pressTab(true);assert.equal((await focusSnapshot()).action,'toggle-model-service-enabled');
 await pressTab();assert.equal((await focusSnapshot()).action,'refresh-openrouter-registry');
 await pressTab();assert.equal((await focusSnapshot()).id,'model-service-api-key');
 await pressTab();result.afterDisabled=await focusSnapshot();assert.equal(result.afterDisabled.disabled,false);assert.ok(!disabled.some(x=>x.action===result.afterDisabled.action));
 result.webViewEndBoundary={lastDomFocus:result.afterDisabled,documentHasFocus:await evaluate('document.hasFocus()'),nativeHostFocusCycleProven:false};
 result.applicationTabBoundary=await evaluate("(async()=>{const owner=await import('./modal-layer-owner.mjs');const layers=[...document.querySelectorAll('.scrim, .android-settings-picker-scrim')];const top=layers.at(-1);const controls=top?owner.layerTabStops(top):[];return {tabEvent:window.__tabKeys.at(-1),layers:layers.length,topClasses:top?.className??null,activeIndex:controls.indexOf(document.activeElement),tabStops:controls.map(x=>({tag:x.tagName,id:x.id,action:x.dataset.action??null,preset:x.dataset.presetId??null})),activeId:document.activeElement?.id??null}})()");

 result.trustedKeyEvents=await evaluate('window.__tabKeys');assert.ok(result.trustedKeyEvents.length>10&&result.trustedKeyEvents.every(x=>x.trusted));
 result.nativeMutations=await evaluate("window.__tabCommands.filter(x=>/^(?:set|save|delete)_desktop_model_service_|^test_desktop_model_service_connection|^reveal_desktop_model_service|^refresh_desktop_openrouter_registry/.test(x))");assert.deepEqual(result.nativeMutations,[]);
 assert.deepEqual(await invoke('read_desktop_model_service_settings'),before);result.settingsUnchanged=true;assert.equal(requests,0);
 await normalClose();await start(true);assert.deepEqual(await invoke('read_desktop_model_service_settings'),before);assert.equal(requests,0);result.coldExact=true;await normalClose();result.pass=true;

}catch(error){result.error=String(error.stack||error);process.exitCode=1;}
finally{if(launch){try{await normalClose();}catch{stop();}}provider.closeAllConnections();await new Promise(done=>provider.close(done));result.root=root;result.pids=pids;result.closes=closes;result.providerRequests=requests;result.wire=wire;result.serverErrors=serverErrors;if(root)await writeFile(join(root,'model-tab-navigation-native.json'),JSON.stringify(result,null,2));}
console.log(JSON.stringify({pass:result.pass,root,pids,scenario,providerRequests:requests,error:result.error}));
