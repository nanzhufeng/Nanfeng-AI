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
assert.equal(parent, resolve(desktop, '../.runtime-qa/returned-model-tail-20261017'));
const scenario=process.argv[3];assert.ok(['blank-tail','null-only','blank-error'].includes(scenario));
const compare = false;assert.ok(process.execArgv.includes('--max-old-space-size=2048'));
const visible = false;
assert.ok(!(visible && compare));
const receipt = JSON.parse(await readFile(join(desktop, 'output/windows-acceptance-release/current.json'), 'utf8'));
assert.equal(receipt.identifier, 'com.nanzhufeng.ai.desktop.windowsacceptance');
assert.equal(createHash('sha256').update(await readFile(receipt.executable)).digest('hex').toUpperCase(), receipt.sha256);
assert.equal(receipt.buildProfile, 'release');
assert.equal(receipt.sha256, process.env.NANFENG_AI_EXPECTED_CANDIDATE_SHA256);
const executable = resolve(desktop, 'output/windows-acceptance-binaries', receipt.sha256, 'nanfeng-ai-desktop-spike.exe');
const expectedSourceInputSha256=createHash('sha256').update(await readFile(resolve(desktop,'../docs/review/20261017-windows-returned-model-tail/source-input-manifest-release-v1.json'))).digest('hex');
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
 const workspaceId=(await invoke('ensure_desktop_local_workspace')).summary.id;
 const setting=(await invoke('read_desktop_model_service_settings')).find(x=>x.providerId==='OPENROUTER');
 await invoke('save_desktop_model_service_settings',{args:{providerId:'OPENROUTER',enabled:true,presetId:'GPT_6_LUNA',expectedRevision:setting.revision,apiKey:'nfai-synthetic-returned-model-only'}});
 await invoke('import_desktop_conversation_clipboard_attachment',{args:{workspaceId,displayName:'unused-synthetic.txt',bytesBase64:Buffer.from('Python 3.14.0 unused attachment').toString('base64')}});
 const conv=(await invoke('read_desktop_workspace',{workspaceId})).exchange.conversations[0];assert.ok(conv);
 let selection=await invoke('read_desktop_p6g_selection',{workspaceId,conversationId:conv.id});
 await invoke('set_desktop_p6g_conversation_override',{args:{workspaceId,conversationId:conv.id,expectedRevision:selection.conversationOverride.revision,modelId:'GPT_6_LUNA'}});
 selection=await invoke('read_desktop_p6g_selection',{workspaceId,conversationId:conv.id});
 await invoke('set_desktop_conversation_preferences',{args:{workspaceId,conversationId:conv.id,expectedRevision:selection.conversationOverride.revision,toneOverride:null,webSearchOverride:false}});
 const text='returned model tail synthetic case';const preview=await invoke('preview_desktop_ordinary_chat_egress',{args:{workspaceId,conversationId:conv.id,text,attachmentIds:[],modelId:null,webSearchOverride:null}});
 assert.equal(preview.recipientModelId,'openai/gpt-6-luna');assert.equal(preview.retrievalRecipient,null);
 const auth={approvedAtMs:Date.now(),disclosureVersion:'normal-chat-egress-v2',clientSubmissionId:randomUUID(),draftFingerprint:preview.draftFingerprint,recipientChoiceId:preview.recipientChoiceId,recipientProviderId:preview.recipientProviderId,recipientModelId:preview.recipientModelId,retrievalRecipient:preview.retrievalRecipient};
 const attempt=await invoke('submit_desktop_ordinary_chat',{args:{workspaceId,conversationId:conv.id,expectedRevision:conv.revision,text,attachmentIds:[],modelId:null,egressAuthorization:auth}});
 result.attempt={state:attempt.state,safeErrorCode:attempt.safeErrorCode};assert.deepEqual(serverErrors,[]);assert.equal(requests,1);assert.ok(wire[0].materialExact&&wire[0].unusedAttachmentAbsent);
 const saved=await invoke('read_desktop_workspace',{workspaceId});const conversation=saved.exchange.conversations.find(x=>x.id===conv.id);const leaf=conversation.messages.find(x=>x.id===conversation.currentLeafId);
 result.actualModelId=leaf.actualModelId??null;result.delivery=leaf.delivery;
 assert.equal(result.actualModelId,expectedModel,'actual returned identity must survive blank tail or remain unknown without a real model');
 if(scenario==='blank-error'){assert.notEqual(attempt.state,'COMPLETED');assert.equal(leaf.delivery,'FAILED');}else{assert.equal(attempt.state,'COMPLETED');assert.equal(leaf.delivery,'COMPLETE');}
 const ledger=await invoke('read_desktop_usage_ledger');const rows=ledger.records.filter(x=>x.executionId===attempt.attemptId&&x.kind==='FINAL_MEASURED');
 result.rows=rows;assert.equal(rows.length,1);assert.equal(rows[0].actualModelId??null,expectedModel);assert.equal(rows[0].requestedModelId,'openai/gpt-6-luna');assert.equal(rows[0].inputTokens,5);assert.equal(rows[0].outputTokens,2);assert.equal(rows[0].chargeMicros,4);assert.equal(rows[0].currencyCode,'USD');result.quantitativeRows=1;
 for(const selector of ['[data-action=new-chat]','[data-action=select-chat][data-id="'+conv.id+'"]']){await until(()=>evaluate('Boolean(document.querySelector('+JSON.stringify(selector)+'))'),'navigation');await evaluate('document.querySelector('+JSON.stringify(selector)+').click();true');}
 await until(()=>evaluate('document.querySelector(".chat-scroll")?.textContent.includes('+JSON.stringify(answerText)+')'),'actual rendered answer');result.actualUiAnswerVisible=true;
 const beforeCold=await invoke('read_desktop_workspace',{workspaceId});const ledgerBeforeCold=await invoke('read_desktop_usage_ledger');const count=requests;
 await normalClose();await start(true);assert.deepEqual(await invoke('read_desktop_workspace',{workspaceId}),beforeCold);assert.deepEqual(await invoke('read_desktop_usage_ledger'),ledgerBeforeCold);assert.equal(requests,count);result.coldExact=true;await normalClose();result.pass=true;

}catch(error){result.error=String(error.stack||error);process.exitCode=1;}
finally{if(launch){try{await normalClose();}catch{stop();}}provider.closeAllConnections();await new Promise(done=>provider.close(done));result.root=root;result.pids=pids;result.closes=closes;result.providerRequests=requests;result.wire=wire;result.serverErrors=serverErrors;if(root)await writeFile(join(root,'returned-model-tail-native.json'),JSON.stringify(result,null,2));}
console.log(JSON.stringify({pass:result.pass,root,pids,scenario,providerRequests:requests,error:result.error}));
