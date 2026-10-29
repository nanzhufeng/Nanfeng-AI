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
assert.equal(parent, resolve(desktop, '../.runtime-qa/model-nav-regression-20261017'));
const scenario=process.argv[3];assert.ok(['focus'].includes(scenario));
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
try {
 await start();result.runtimeInfo=await invoke('read_desktop_runtime_info');assert.equal(result.runtimeInfo.sourceInputSha256,expectedSourceInputSha256);assert.equal(result.runtimeInfo.buildProfile,'release');
 await invoke('ensure_desktop_local_workspace');
 for(const selector of ['[data-action=show-settings]','[data-action=open-settings-page][data-page=model]','[data-action=open-settings-page][data-page=model-configuration]']){
  await until(()=>evaluate('Boolean(document.querySelector('+JSON.stringify(selector)+'))'),'settings entry');
  await evaluate('document.querySelector('+JSON.stringify(selector)+').click();true');
 }
 await until(()=>evaluate("Boolean(document.querySelector('.model-settings-browser [role=radiogroup] button.selected'))"),'model options');
 await evaluate("(()=>{const original=window.fetch.bind(window);let release;const gate=new Promise(resolve=>release=resolve);let hold=true;window.__releaseKeyboardModelSave=()=>release();const wrapped=(input,init)=>{const url=typeof input==='string'?input:input.url;const command=decodeURIComponent(new URL(url,location.href).pathname).split('/').filter(Boolean).join('/');if(command==='set_desktop_model_service_preset')window.__keyboardModelSaveRequests=(window.__keyboardModelSaveRequests||0)+1;const response=original(input,init);if(command==='set_desktop_model_service_preset'&&hold){hold=false;return response.then(reply=>{if(reply.headers.get('Tauri-Response')!=='ok')throw new Error('Native setting write failed before focus check');window.__keyboardModelSaveCommitted=true;return gate.then(()=>reply);});}return response;};window.fetch=wrapped;if(window.fetch!==wrapped)throw new Error('Fetch observer not installed');return true})()");
 const target=await evaluate("(()=>{const selected=document.querySelector('.model-settings-browser [role=radiogroup] button.selected');const choices=[...selected.closest('[role=radiogroup]').querySelectorAll('button')].filter(x=>!x.disabled);const next=choices[(choices.indexOf(selected)+1)%choices.length];selected.focus();const event=new KeyboardEvent('keydown',{key:'ArrowDown',bubbles:true,cancelable:true});selected.dispatchEvent(event);return {expected:next.dataset.presetId,prevented:event.defaultPrevented}})()");
 assert.ok(target.prevented);result.expectedPreset=target.expected;
 await until(()=>evaluate('window.__keyboardModelSaveCommitted===true'),'real model choice committed with held receipt');
 await evaluate('new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve)))');
 result.beforeReceiptFocus=await evaluate("document.activeElement?.dataset.presetId??null");
 assert.equal(result.beforeReceiptFocus,target.expected,'initial keyboard selection must receive focus');
 await evaluate('window.__releaseKeyboardModelSave();true');
 await until(()=>evaluate("__nanfengDesktopWorkState.modelSettingsNotice==='型号已保存。'"),'model choice receipt rendered');
 await evaluate('new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve)))');
 result.afterReceiptFocus=await evaluate("document.activeElement?.dataset.presetId??null");
 assert.equal(result.afterReceiptFocus,target.expected,'late native model save receipt must preserve keyboard focus');
 result.keys=[];let finalPreset=target.expected;
 for(const key of ['Home','End','ArrowUp','ArrowDown','ArrowLeft','ArrowRight']){
  const step=await evaluate("(()=>{const selected=document.activeElement;const group=selected.closest('[role=radiogroup]');if(!group)throw new Error('Keyboard focus left model choices');const choices=[...group.querySelectorAll('button')].filter(x=>!x.disabled);const index=choices.indexOf(selected);const key="+JSON.stringify(key)+";const change=['ArrowLeft','ArrowUp'].includes(key)?-1:1;const next=choices[key==='Home'?0:key==='End'?choices.length-1:(index+change+choices.length)%choices.length];const event=new KeyboardEvent('keydown',{key,bubbles:true,cancelable:true});selected.dispatchEvent(event);return {expected:next.dataset.presetId,prevented:event.defaultPrevented}})()");
  assert.ok(step.prevented);finalPreset=step.expected;
  await until(()=>evaluate("__nanfengDesktopWorkState.modelServiceSettings.find(x=>x.providerId==='OPENROUTER')?.presetId==="+JSON.stringify(step.expected)+"&&__nanfengDesktopWorkState.modelSettingsNotice==='型号已保存。'"),'keyboard choice saved');
  await evaluate('new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve)))');
  const observed=await evaluate("(()=>{const active=document.activeElement;const list=active?.closest('.model-settings-model-list');const a=active?.getBoundingClientRect();const b=list?.getBoundingClientRect();return {focused:active?.dataset.presetId??null,visible:!!list&&a.top>=b.top-2&&a.bottom<=b.bottom+2&&a.top>=-2&&a.bottom<=innerHeight+2,overflow:!!list&&list.scrollHeight>list.clientHeight+1,scrollTop:list?.scrollTop}})()");
  assert.equal(observed.focused,step.expected);assert.equal(observed.visible,true,'focused model must stay visible after receipt repaint');
  if(key==='End'){assert.equal(observed.overflow,true);assert.ok(observed.scrollTop>0);}
  result.keys.push({key,expected:step.expected,...observed});
 }
 result.nativeChoiceWrites=await evaluate('window.__keyboardModelSaveRequests');
 assert.equal(result.nativeChoiceWrites,7,'focus restoration must not submit extra native writes');
 const before=await invoke('read_desktop_model_service_settings');result.savedChoice=before.find(x=>x.providerId==='OPENROUTER');assert.equal(result.savedChoice.presetId,finalPreset); 
 assert.equal(requests,0);
 await normalClose();await start(true);assert.deepEqual(await invoke('read_desktop_model_service_settings'),before);assert.equal(requests,0);result.coldExact=true;await normalClose();result.pass=true;

}catch(error){result.error=String(error.stack||error);process.exitCode=1;}
finally{if(launch){try{await normalClose();}catch{stop();}}provider.closeAllConnections();await new Promise(done=>provider.close(done));result.root=root;result.pids=pids;result.closes=closes;result.providerRequests=requests;result.wire=wire;result.serverErrors=serverErrors;if(root)await writeFile(join(root,'model-keyboard-focus-native.json'),JSON.stringify(result,null,2));}
console.log(JSON.stringify({pass:result.pass,root,pids,scenario,providerRequests:requests,error:result.error}));
