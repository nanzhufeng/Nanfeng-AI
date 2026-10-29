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
assert.equal(parent, resolve(desktop, '../.runtime-qa/model-nav-owner-20261017'));
const scenario=process.argv[3];assert.ok(['input','provider'].includes(scenario));
const compare = false;assert.ok(process.execArgv.includes('--max-old-space-size=2048'));
const visible = false;
assert.ok(!(visible && compare));
const receipt = JSON.parse(await readFile(join(desktop, 'output/windows-acceptance-release/current.json'), 'utf8'));
assert.equal(receipt.identifier, 'com.nanzhufeng.ai.desktop.windowsacceptance');
assert.equal(createHash('sha256').update(await readFile(receipt.executable)).digest('hex').toUpperCase(), receipt.sha256);
assert.equal(receipt.buildProfile, 'release');
assert.equal(receipt.sha256, process.env.NANFENG_AI_EXPECTED_CANDIDATE_SHA256);
const executable = resolve(desktop, 'output/windows-acceptance-binaries', receipt.sha256, 'nanfeng-ai-desktop-spike.exe');
const expectedSourceInputSha256=createHash('sha256').update(await readFile(resolve(desktop,'../docs/review/20261017-windows-model-navigation-owner/source-input-manifest-release-v1.json'))).digest('hex');
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
const focusIdentity=()=>evaluate("(()=>{const x=document.activeElement;return {id:x?.id??null,action:x?.dataset.action??null,preset:x?.dataset.presetId??null,provider:x?.dataset.providerId??null,platform:x?.getAttribute('data-provider-platform')??null}})()");
try {
 await start();result.runtimeInfo=await invoke('read_desktop_runtime_info');assert.equal(result.runtimeInfo.sourceInputSha256,expectedSourceInputSha256);assert.equal(result.runtimeInfo.buildProfile,'release');
 await invoke('ensure_desktop_local_workspace');
 for(const selector of ['[data-action=show-settings]','[data-action=open-settings-page][data-page=model]','[data-action=open-settings-page][data-page=model-configuration]']){
  await until(()=>evaluate('Boolean(document.querySelector('+JSON.stringify(selector)+'))'),'settings entry');await evaluate('document.querySelector('+JSON.stringify(selector)+').click();true');
 }
 await until(()=>evaluate("Boolean(document.querySelector('.model-settings-model-list button.selected'))"),'model settings');
 await evaluate("(()=>{const original=window.requestAnimationFrame.bind(window);let held;let hold=true;const wrapped=callback=>{if(hold&&String(callback).includes('.model-settings-browser button')){hold=false;held=callback;window.__modelNavigationFrameHeld=true;return 10001;}return original(callback);};window.requestAnimationFrame=wrapped;if(window.requestAnimationFrame!==wrapped)throw new Error('Frame observer unavailable');window.__releaseModelNavigationFrame=()=>{if(!held)throw new Error('No held navigation frame');original(held);};window.__nativeChoiceWrites=0;const fetchOriginal=window.fetch.bind(window);window.fetch=(input,init)=>{const url=typeof input==='string'?input:input.url;const command=decodeURIComponent(new URL(url,location.href).pathname).split('/').filter(Boolean).join('/');if(command==='set_desktop_model_service_preset')window.__nativeChoiceWrites++;return fetchOriginal(input,init);};return true})()");
 const selector=scenario==='input'?'.model-settings-model-list button.selected':'.model-settings-platform-rail button[data-provider-id=OPENROUTER]';
 const selected=await evaluate("(()=>{const selected=document.querySelector("+JSON.stringify(selector)+");selected.focus();const event=new KeyboardEvent('keydown',{key:'ArrowDown',bubbles:true,cancelable:true});selected.dispatchEvent(event);return event.defaultPrevented})()");assert.equal(selected,true);
 await until(()=>evaluate('window.__modelNavigationFrameHeld===true'),'held real navigation frame');
 if(scenario==='input'){
  await until(()=>evaluate("__nanfengDesktopWorkState.modelSettingsNotice==='型号已保存。'"),'real native choice receipt');
  await evaluate("document.querySelector('#model-service-api-key').focus();true");
 }else{
  await until(()=>evaluate("__nanfengDesktopWorkState.modelProviderId==='RELAYCORE_CLAUDE'"),'keyboard provider selection');
  await evaluate("(()=>{const target=document.querySelector('.model-settings-platform-rail button[data-provider-id=QWEN]');target.focus();target.click();return true})()");
  await until(()=>evaluate("__nanfengDesktopWorkState.modelProviderId==='QWEN'"),'new provider navigation');
 }
 await evaluate('new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve)))');
 result.beforeReleaseFocus=await focusIdentity();result.beforeReleaseProvider=await evaluate('__nanfengDesktopWorkState.modelProviderId');
 if(scenario==='input')assert.equal(result.beforeReleaseFocus.id,'model-service-api-key');else assert.equal(result.beforeReleaseFocus.provider,'QWEN');
 await evaluate('window.__releaseModelNavigationFrame();true');await evaluate('new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve)))');
 result.afterReleaseFocus=await focusIdentity();result.afterReleaseProvider=await evaluate('__nanfengDesktopWorkState.modelProviderId');
 assert.deepEqual(result.afterReleaseFocus,result.beforeReleaseFocus,'stale navigation frame must not reclaim focus after newer input or provider choice');
 assert.equal(result.afterReleaseProvider,result.beforeReleaseProvider);
 result.nativeChoiceWrites=await evaluate('window.__nativeChoiceWrites');assert.equal(result.nativeChoiceWrites,scenario==='input'?1:0);assert.equal(requests,0);
 const before=await invoke('read_desktop_model_service_settings');result.savedChoice=before.find(x=>x.providerId==='OPENROUTER');
 await normalClose();await start(true);assert.deepEqual(await invoke('read_desktop_model_service_settings'),before);assert.equal(requests,0);result.coldExact=true;await normalClose();result.pass=true;

}catch(error){result.error=String(error.stack||error);process.exitCode=1;}
finally{if(launch){try{await normalClose();}catch{stop();}}provider.closeAllConnections();await new Promise(done=>provider.close(done));result.root=root;result.pids=pids;result.closes=closes;result.providerRequests=requests;result.wire=wire;result.serverErrors=serverErrors;if(root)await writeFile(join(root,'model-navigation-owner-native.json'),JSON.stringify(result,null,2));}
console.log(JSON.stringify({pass:result.pass,root,pids,scenario,providerRequests:requests,error:result.error}));
