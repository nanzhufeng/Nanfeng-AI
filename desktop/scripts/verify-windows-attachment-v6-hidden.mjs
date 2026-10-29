import { completeLaunchReceipt } from './windows-launch-receipt.mjs';
import assert from 'node:assert/strict';
import { createHash, randomUUID } from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import { readFile, writeFile } from 'node:fs/promises';
import http from 'node:http';
import { dirname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

if (process.platform !== 'win32') throw new Error('仅在 Windows 隔离候选上验收。');
const desktopRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
assert.ok(process.argv[2], 'Explicit owned synthetic parent required');
const rootParent = resolve(process.argv[2]);
assert.ok(process.execArgv.includes('--max-old-space-size=2048'));
if (process.argv.includes('--screenshot')) throw new Error('后台验收不支持前台截图');
const namespace=process.argv.includes('--release')?'windows-acceptance-release':'windows-acceptance';
const receipt = JSON.parse(await readFile(join(desktopRoot, 'output',namespace,'current.json'), 'utf8'));
assert.equal(receipt.buildProfile,process.argv.includes('--release')?'release':'debug');
assert.equal(receipt.identifier, 'com.nanzhufeng.ai.desktop.windowsacceptance');
const source = resolve(desktopRoot,'output',namespace,receipt.sha256, 'NanfengAI-Windows-Acceptance.exe');
assert.equal(resolve(receipt.executable), source);
assert.equal(createHash('sha256').update(await readFile(source)).digest('hex').toUpperCase(), receipt.sha256);
const runExecutable = resolve(desktopRoot, 'output/windows-acceptance-binaries', receipt.sha256, 'nanfeng-ai-desktop-spike.exe');
const ps = (args) => execFileSync('pwsh', ['-NoProfile', ...args], { encoding: 'utf8', timeout: 60000, windowsHide:true }).trim();
const privateDesktopGuard=JSON.parse(ps(['-File',join(desktopRoot,'scripts/assert-windows-private-desktop.ps1')]));assert.equal(privateDesktopGuard.verified,true);
const launchPowerShell = (args) => new Promise((resolveLaunch, reject) => {
  const child = spawn('pwsh', ['-NoProfile', ...args], { windowsHide:true,stdio: ['ignore', 'pipe', 'pipe'] });
  let stdout = '';
  let stderr = '';
  const timer = setTimeout(() => { child.kill(); reject(new Error(`隔离候选启动超时：${stderr}`)); }, 30000);
  child.stdout.on('data', data => {
    stdout += data.toString();
    let emitted;
    try { emitted=completeLaunchReceipt(stdout); } catch(error) { clearTimeout(timer);reject(error);return; }
    if (!emitted) return;
    clearTimeout(timer);
    readFile(emitted.launchReceipt,'utf8').then(data=>resolveLaunch(JSON.parse(data)),reject);
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
const v6Topics=process.argv.includes('--v6-topics');
const topics=['印象派','Photosynthesis','古典诗词','禅','C','R','光合作用','乡愁'];
const qwen=process.argv.includes('--qwen'),deepseek=process.argv.includes('--deepseek'),zhipu=process.argv.includes('--zhipu');const providerId=qwen?'QWEN':deepseek?'DEEPSEEK':zhipu?'ZHIPU':'OPENROUTER';const choiceId=qwen?'QWEN_3_7_PLUS':deepseek?'DEEPSEEK_V4_PRO':zhipu?'GLM_5_3_FLASH':'GPT_6_1_SOL';const wireModel=qwen?'qwen3.7-plus':deepseek?'deepseek-v4-pro':zhipu?'glm-5.3-flash':'openai/gpt-6.1-sol';const currency=providerId==='OPENROUTER'?'USD':'CNY';const expectedRetrievals=zhipu?8:process.argv.includes("--mixed-media")?3:2;
const unknownSecond=process.argv.includes('--unknown-second');
const sourceRemove=process.argv.includes('--source-remove');
const readablePdf=process.argv.includes('--readable-pdf');
const mixedMedia=process.argv.includes('--mixed-media');
if(mixedMedia)assert.ok(!qwen&&!deepseek&&!zhipu&&!unknownSecond&&!sourceRemove&&!readablePdf);
const mixedImage=mixedMedia?await readFile(join(desktopRoot,'src-tauri/icons/icon.png')):null;
if(readablePdf)assert.ok(!qwen&&!deepseek&&!zhipu&&!unknownSecond&&!sourceRemove);
if(sourceRemove)assert.ok(!qwen&&!deepseek&&!zhipu&&!unknownSecond);
let onSecondRetrieval=null;
const partialJson=process.argv.includes('--partial-json');
if(partialJson) assert.ok(zhipu && !unknownSecond,'部分JSON验收使用单项智谱检索');
const requests=[];const serverErrors=[];const wireContents=[];
const provider=http.createServer(async(req,res)=>{
 try {
  let raw='';for await(const chunk of req)raw+=chunk.toString('utf8');const body=JSON.parse(raw);wireContents.push(JSON.stringify(body));
  const qwenResponses=qwen&&Array.isArray(body.input);
  if(qwenResponses){assert.equal(body.messages,undefined);assert.equal(body.enable_search,undefined);body.messages=body.input;}
  assert.equal(req.headers.authorization==='Bearer local-acceptance-mock-only',true);
  const retrieval=body.model===wireModel && (Array.isArray(body.tools)||body.enable_search===true);
  const inventory=mixedMedia&&JSON.stringify(body.messages||[]).includes('忠实读取本次每一份材料');
  requests.push({model:body.model,role:inventory?'INVENTORY':retrieval?'RETRIEVAL':'ANSWER',idempotencyKey:req.headers['idempotency-key'],grounded:JSON.stringify(body.messages||[]).includes('<retrieved-evidence>')});
  if(deepseek){
   assert.equal(req.headers['x-api-key'],'local-acceptance-mock-only');assert.equal(req.headers['anthropic-version'],'2023-06-01');
   const ids=retrieval?[...new Set([...JSON.stringify(body.messages).matchAll(/\[(A[1-9][0-9]*)\] /g)].map(m=>m[1]))]:[];
   if(retrieval){assert.ok(ids.length>0&&ids.length<=4);requests.at(-1).facts=ids;if(unknownSecond&&requests.filter(r=>r.role==='RETRIEVAL').length===2){res.destroy();return;}}
   const content=[{type:'text',text:retrieval?ids.map(id=>`[${id}] ${v6Topics?'已检索':'已核实'}\n${v6Topics?'合成主题的相关网页资料。':'公开机构在2026年发布该项数据。'}\n来源：https://example.test/${id}`).join('\n'):'合成原模型回答'}];
   if(retrieval)content.unshift({type:'web_search_tool_result',content:ids.map(id=>({type:'web_search_result',url:`https://example.test/${id}`,title:'Synthetic evidence'}))});
   res.writeHead(200,{'Content-Type':'application/json'});res.end(JSON.stringify({type:'message',model:body.model,stop_reason:'end_turn',content,usage:{input_tokens:5,output_tokens:2,cost:0.000004}}));return;
  }
  if(qwenResponses){
   const ids=retrieval?[...new Set([...JSON.stringify(body.input).matchAll(/\[(A[1-9][0-9]*)\] /g)].map(m=>m[1]))]:[];
   if(retrieval){assert.equal(body.tools[0].type,'web_search');assert.ok(ids.length>0&&ids.length<=4);assert.ok(!JSON.stringify(body.input).includes('USER_CONTROL_SENTINEL'));assert.ok(!JSON.stringify(body.input).includes('PRIVATE_FILENAME_SENTINEL'));requests.at(-1).facts=ids;}
   else{assert.equal(body.tools,undefined);assert.ok(requests.at(-1).grounded);}
   if(retrieval&&unknownSecond&&requests.filter(r=>r.role==='RETRIEVAL').length===2){res.destroy();return;}
   const text=retrieval?(partialJson?JSON.stringify({items:ids.map(id=>({id,status:'verified',summary:id==='A1'?'公开事项1仅部分已核实，发布日期仍未确认。':'公开机构在2026年发布该项数据。',urls:[`https://example.test/${id}`]}))}):ids.map(id=>`[${id}] ${v6Topics?'已检索':'已核实'}\n${v6Topics?'合成主题的相关网页资料。':'公开机构在2026年发布该项数据。'}\n来源：https://example.test/${id}`).join('\n')):'合成原模型回答';
   const response={status:'completed',model:body.model,output:[...(retrieval?[{type:'web_search_call',status:'completed'}]:[]),{type:'message',role:'assistant',content:[{type:'output_text',text,annotations:ids.map(id=>({type:'url_citation',url:`https://example.test/${id}`,title:'Synthetic evidence'}))}]}],usage:{input_tokens:5,output_tokens:2}};
   if(body.stream===false){res.writeHead(200,{'Content-Type':'application/json'});res.end(JSON.stringify(response));return;}
   res.writeHead(200,{'Content-Type':'text/event-stream; charset=utf-8'});
   res.write(`data: ${JSON.stringify({type:'response.created',response:{model:body.model}})}\n\n`);
   res.write(`data: ${JSON.stringify({type:'response.output_text.delta',delta:text})}\n\n`);
   res.end(`data: ${JSON.stringify({type:'response.completed',response})}\n\ndata: [DONE]\n\n`);return;
  }
  res.writeHead(200,{'Content-Type':'text/event-stream; charset=utf-8'});const event=value=>res.write(`data: ${JSON.stringify(value)}\n\n`);
  if(inventory){
   assert.ok(JSON.stringify(body.messages).includes(mixedImage.toString('base64')));
   assert.ok(!JSON.stringify(body.messages).includes('application/pdf'));
   event({model:body.model,choices:[{delta:{content:'- 图像事项1：公开机构在2026年发布第一份公告。\n- 图像事项2：公开机构在2026年发布第二份公告。'}}]});
   res.end(`data: ${JSON.stringify({model:body.model,choices:[{delta:{},finish_reason:'stop'}],usage:{prompt_tokens:5,completion_tokens:2,cost:0.000004}})}`);return;
  }
  if(retrieval) {
   assert.ok(!JSON.stringify(body.messages).includes('USER_CONTROL_SENTINEL'));assert.ok(!JSON.stringify(body.messages).includes('PRIVATE_FILENAME_SENTINEL'));
   if(qwen){assert.equal(body.enable_search,true);assert.equal(body.search_options.forced_search,true);}else if(zhipu){assert.ok(body.tools.some(t=>t.type==='web_search'));}else{assert.ok(body.tools.some(t=>t.type==='openrouter:web_search'));}
   const ids=[...new Set([...JSON.stringify(body.messages).matchAll(/\[(A[1-9][0-9]*)\] /g)].map(m=>m[1]))];
   assert.ok(ids.length>0 && ids.length<=4);requests[requests.length-1].facts=ids;
   if(sourceRemove && requests.filter(r=>r.role==='RETRIEVAL').length===2){assert.ok(onSecondRetrieval);await onSecondRetrieval();}
   if(unknownSecond && requests.filter(r=>r.role==='RETRIEVAL').length===2){res.destroy();return;}
   const rows=partialJson?JSON.stringify({items:ids.map(id=>({id,status:'verified',summary:id==='A1'?'公开事项1仅部分已核实，发布日期仍未确认。':'公开机构在2026年发布该项数据。',urls:[`https://example.test/${id}`]}))}):ids.map(id=>`[${id}] ${v6Topics?'已检索':'已核实'}\n${v6Topics?'合成主题的相关网页资料。':'公开机构在2026年发布该项数据。'}\n来源：https://example.test/${id}`).join('\n');
   event({model:body.model,choices:[{delta:{content:rows}}]});
   res.end(`data: ${JSON.stringify({model:body.model,choices:[{delta:{annotations:ids.map(id=>({type:'url_citation',url_citation:{url:`https://example.test/${id}`,title:'Synthetic evidence'}}))},finish_reason:'stop'}],usage:{prompt_tokens:5,completion_tokens:2,cost:0.000004}})}`);
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
    '-ExecutablePath',source,'-StartupMode', 'normal', '-DebugPort', String(port), '-OrdinaryChatTestEndpoint', `http://127.0.0.1:${providerPort}`];
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
    try { const value = await check(); if (value) return value; } catch { /* Hidden page may be refreshing. */ }
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





const result={privateDesktopGuard,candidateSha256:receipt.sha256,providerId,wireModel,currency,realService:false,backgroundOnly:true};let port=0;
try {
 port=await start();result.root=root;await ready(port);result.runtimeInfo=await invoke(port,'CHAT','read_desktop_runtime_info');const ws=(await invoke(port,'CHAT','ensure_desktop_local_workspace')).summary.id;
 const settings=await invoke(port,'CHAT','read_desktop_model_service_settings');for(const configuredProvider of [providerId]){const providerId=configuredProvider;const setting=settings.find(s=>s.providerId===providerId);await invoke(port,'CHAT','save_desktop_model_service_settings',{args:{providerId,enabled:true,presetId:choiceId,expectedRevision:setting.revision,apiKey:`nfai-synthetic-fact-${providerId}`}});}
 if(mixedMedia){
   result.unsupportedMedia=[];
   const wav=Buffer.alloc(76);wav.write('RIFF');wav.writeUInt32LE(68,4);wav.write('WAVEfmt ',8);wav.writeUInt32LE(16,16);wav.writeUInt16LE(1,20);wav.writeUInt16LE(1,22);wav.writeUInt32LE(8000,24);wav.writeUInt32LE(16000,28);wav.writeUInt16LE(2,32);wav.writeUInt16LE(16,34);wav.write('data',36);wav.writeUInt32LE(32,40);
   for(const [name,bytes] of [['unsupported.wav',wav],['unsupported.mp4',Buffer.from('000000186674797069736f6d0000020069736f6d6d703432','hex')]]){
     const media=await invoke(port,'CHAT','import_desktop_conversation_clipboard_attachment',{args:{workspaceId:ws,displayName:name,bytesBase64:bytes.toString('base64')}});
     await assert.rejects(invoke(port,'CHAT','preview_desktop_ordinary_chat_egress',{args:{workspaceId:ws,conversationId:null,text:'分析完整材料',attachmentIds:[media.id],modelId:choiceId,webSearchOverride:true}}),/不能处理本次附件/);
     assert.equal(requests.length,0);result.unsupportedMedia.push({name,mimeType:media.mimeType,networkRequests:0});
   }
 }
 const pdfBytes=(readablePdf||mixedMedia)?await readFile(join(desktopRoot,"../protocol/fixtures/attachment-readable-media-v1.pdf")):null;
 const materials=mixedMedia?[["readable.pdf",pdfBytes],["fixture.png",mixedImage],["mixed.txt",'- 文本事项1：企业在2025年发布第一份报告。\n- 文本事项2：企业在2025年发布第二份报告。']]:readablePdf?[["readable.pdf",pdfBytes]]:v6Topics?[["PRIVATE_FILENAME_SENTINEL.txt",topics.map(t=>`- ${t}`).join('\n')]]:[['source-one.txt',Array.from({length:8},(_,i)=>`- 公开事项${i+1}：机构在2026年发布第${i+1}项数据。`).join('\n')]];
 const ids=[];for(const [name,text] of materials){const a=await invoke(port,'CHAT','import_desktop_conversation_clipboard_attachment',{args:{workspaceId:ws,displayName:name,bytesBase64:Buffer.from(text).toString('base64')}});ids.push(a.id);}
 const data=await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:ws});const conv=data.exchange.conversations[0];
 let selection=await invoke(port,'CHAT','read_desktop_p6g_selection',{workspaceId:ws,conversationId:conv.id});await invoke(port,'CHAT','set_desktop_p6g_conversation_override',{args:{workspaceId:ws,conversationId:conv.id,expectedRevision:selection.conversationOverride.revision,modelId:choiceId}});
 selection=await invoke(port,'CHAT','read_desktop_p6g_selection',{workspaceId:ws,conversationId:conv.id});await invoke(port,'CHAT','set_desktop_conversation_preferences',{args:{workspaceId:ws,conversationId:conv.id,expectedRevision:selection.conversationOverride.revision,toneOverride:null,webSearchOverride:true}});
 const text=v6Topics?'USER_CONTROL_SENTINEL 请结合网页解释全部材料':mixedMedia?'核验全部混合材料':'核验附件中的全部八个公开事项';const preview=await invoke(port,'CHAT','preview_desktop_ordinary_chat_egress',{args:{workspaceId:ws,conversationId:conv.id,text,attachmentIds:ids,modelId:null,webSearchOverride:null}});
 assert.equal(preview.retrievalRecipient,null);assert.equal(preview.recipientModelId,wireModel);
 const auth={approvedAtMs:Date.now(),disclosureVersion:'normal-chat-egress-v2',clientSubmissionId:randomUUID(),draftFingerprint:preview.draftFingerprint,recipientChoiceId:preview.recipientChoiceId,recipientProviderId:preview.recipientProviderId,recipientModelId:preview.recipientModelId,retrievalRecipient:preview.retrievalRecipient};
 const cacheCounts=()=>JSON.parse(execFileSync('python',['-c',"import sqlite3,json,sys;from pathlib import Path;c=sqlite3.connect((Path(sys.argv[1])/'workspace.sqlite3').as_uri()+'?mode=ro',uri=True);print(json.dumps([c.execute('select count(*) from desktop_attachment_search_checkpoints').fetchone()[0],c.execute('select count(*) from desktop_attachment_search_coverage').fetchone()[0]]))",root],{encoding:'utf8'}));
 if(sourceRemove)onSecondRetrieval=async()=>{
   result.beforeSourceRemove=cacheCounts();assert.equal(result.beforeSourceRemove[0],4);
   const live=(await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:ws})).exchange.conversations.find(c=>c.id===conv.id);
   const user=live.messages.find(m=>m.blocks?.some(b=>b.kind==='ASSET_REF'&&b.asset?.id===ids[0]));assert.ok(user);
   await invoke(port,'CHAT','mutate_desktop_domain',{args:{workspaceId:ws,intentId:randomUUID(),entity:'conversation',action:'removeAttachment',objectId:conv.id,expectedRevision:live.revision,fields:{messageId:user.id,attachmentId:ids[0]}}});
   result.afterSourceRemove=cacheCounts();assert.deepEqual(result.afterSourceRemove,[0,0]);
 };
 const attempt=await invoke(port,'CHAT','submit_desktop_ordinary_chat',{args:{workspaceId:ws,conversationId:conv.id,expectedRevision:conv.revision,text,attachmentIds:ids,modelId:null,egressAuthorization:auth}});if(sourceRemove){
 assert.equal(attempt.state,'CANCELLED');assert.equal(attempt.safeErrorCode,'SOURCE_MESSAGE_CHANGED');assert.equal(requests.length,2);assert.ok(requests.every(r=>r.role==='RETRIEVAL'));
 assert.deepEqual(cacheCounts(),[0,0]);
 const rows=(await invoke(port,'CHAT','read_desktop_usage_ledger')).records.filter(r=>r.executionId===attempt.attemptId);
 assert.equal(rows.filter(r=>r.callMetadata?.role==='RETRIEVAL'&&r.kind==='FINAL_MEASURED').length,2);
 assert.equal(rows.filter(r=>r.callMetadata?.role==='ANSWER').length,0);result.sourceRemovedStopped=true;
}else if(unknownSecond){
 assert.notEqual(attempt.state,'COMPLETED');assert.equal(requests.length,2);assert.ok(requests.every(r=>r.role==='RETRIEVAL'));
 const rows=(await invoke(port,'CHAT','read_desktop_usage_ledger')).records.filter(r=>r.executionId===attempt.attemptId);
 assert.equal(rows.filter(r=>r.callMetadata?.role==='RETRIEVAL' && r.kind==='FINAL_MEASURED').length,1);
 assert.equal(rows.filter(r=>r.callMetadata?.role==='RETRIEVAL' && r.kind==='STREAM_PENDING').length,1);
 assert.equal(rows.filter(r=>r.callMetadata?.role==='ANSWER').length,0);result.unknownStoppedBeforeAnswer=true;
}else{
assert.equal(attempt.state,'COMPLETED');assert.equal(serverErrors.length,0);
 const answer=requests.find(r=>r.role==='ANSWER');assert.ok(answer?.grounded);assert.equal(requests.filter(r=>r.role==='RETRIEVAL').length,expectedRetrievals);assert.equal(requests.filter(r=>r.role==='ANSWER').length,1);
 const finalBody=wireContents.find(body=>body.includes(wireModel)&&body.includes('<retrieved-evidence>'));assert.ok(finalBody);assert.ok(finalBody.includes(mixedMedia?'12 / 12':'8 / 8'));if(mixedMedia){assert.equal(requests.filter(r=>r.role==='INVENTORY').length,1);assert.ok(finalBody.includes(mixedImage.toString('base64')));assert.ok(finalBody.includes('文本事项2'));assert.ok(finalBody.includes('图像事项2'));const originalImageMessage=JSON.parse(wireContents.at(-1)).messages.find(m=>m.role==='user'&&Array.isArray(m.content)&&m.content.some(p=>p.type==='image_url'));assert.ok(originalImageMessage);assert.ok(!JSON.stringify(originalImageMessage).includes('图像事项'));result.originalImageDidNotAbsorbReadOutput=true;result.mixedMediaPreserved=true;}if(partialJson){assert.ok(finalBody.includes('证据不足，仍未核实：公开事项1仅部分已核实'));assert.ok(!finalBody.includes('已核实：公开事项1仅部分已核实'));result.partialJsonDemoted=true;}for(let i=1;i<=8;i++){assert.ok(finalBody.includes(v6Topics?topics[i-1]:(readablePdf||mixedMedia)?`Python 3.14.${i}`:`公开事项${i}`));assert.ok(finalBody.includes(`https://example.test/A${i}`));}if(v6Topics){assert.ok(finalBody.includes('已检索'));assert.ok(!finalBody.includes('已核实：'));result.retrievedNotVerified=true;}
 const ledger=(await invoke(port,'CHAT','read_desktop_usage_ledger')).records.filter(r=>r.executionId===attempt.attemptId && r.kind==='FINAL_MEASURED');const retrievals=ledger.filter(r=>r.callMetadata?.role==='RETRIEVAL');assert.equal(retrievals.length,expectedRetrievals+(mixedMedia?1:0));assert.equal(new Set(retrievals.map(r=>r.invocationId)).size,expectedRetrievals+(mixedMedia?1:0));assert.ok(retrievals.every(r=>r.currencyCode===currency&&r.requestedModelId===wireModel&&r.actualModelId===wireModel&&!r.callMetadata.modelDisplayName.includes('Luna')));assert.equal(ledger.filter(r=>r.callMetadata?.role==='ANSWER').length,1);const all=(await invoke(port,'CHAT','read_desktop_usage_ledger')).records.filter(r=>r.executionId===attempt.attemptId);const known=new Set(all.filter(r=>r.kind==='FINAL_MEASURED').map(r=>r.invocationId));assert.equal(all.filter(r=>r.kind==='STREAM_PENDING'&&!known.has(r.invocationId)).length,0,'成功调用不能残留虚假未知费用');result.batchFacts=requests.filter(r=>r.role==='RETRIEVAL').map(r=>r.facts);result.finalRecords=ledger.length;result.originalMaterialsAndEvidencePreserved=true;if(readablePdf||mixedMedia){assert.ok(finalBody.includes(pdfBytes.toString('base64')));assert.ok(finalBody.includes('本机 PDF 文本层'));result.pdfOriginalAndFullLocalText=true;}
}
 result.beforeColdLedger=await invoke(port,'CHAT','read_desktop_usage_ledger');
 result.close=await normalClose();
 if(sourceRemove){port=await start(true);await ready(port);assert.deepEqual(cacheCounts(),[0,0]);
   const restored=(await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:ws})).exchange.conversations.find(c=>c.id===conv.id);
   assert.ok(restored);assert.ok(!restored.messages.some(m=>m.attachmentSearchCoverage));
   assert.ok(!restored.messages.some(m=>m.blocks?.some(b=>b.kind==='ASSET_REF'&&b.asset?.id===ids[0])));
   assert.equal(requests.length,2);result.coldSourceRemovalPreserved=true;result.reopenClose=await normalClose();
 }
 if(!sourceRemove){const count=requests.length;port=await start(true);await ready(port);assert.deepEqual(await invoke(port,'CHAT','read_desktop_usage_ledger'),result.beforeColdLedger);assert.equal(requests.length,count);result.coldClose=await normalClose();}
 result.pass=true;
}catch(error){result.pass=false;result.error=String(error);process.exitCode=1;}
finally{if(launch){try{result.failureClose=await normalClose();}catch(error){result.closeError=String(error);result.forcedCleanup=true;stop();}}provider.closeAllConnections();await new Promise(done=>provider.close(done));result.requests=requests;result.serverErrors=serverErrors;result.pids=pids;if(root)await writeFile(join(root,`v6-${providerId}-${v6Topics?'topics':mixedMedia?'mixed':unknownSecond?'unknown':'success'}-native-evidence.json`),JSON.stringify(result,null,2),{flag:'wx'});}
console.log(JSON.stringify({root,pass:result.pass,error:result.error,requests:requests.length,serverErrors,pids}));
