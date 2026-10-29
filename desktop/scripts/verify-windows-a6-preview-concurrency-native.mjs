import assert from 'node:assert/strict';
import { createHash, randomUUID } from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import { readFile, writeFile, stat } from 'node:fs/promises';
import http from 'node:http';
import { dirname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

if (process.platform !== 'win32') throw new Error('仅在 Windows 隔离候选上验收。');
const desktopRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const rootParent = resolve(process.argv[2] || 'D:\\CodexTemp');
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



const requests=[],serverErrors=[];
const firstText='预览并发测试已经收到的首段正文。';const nextText='预览期间继续收到并保存的正文。';
let releaseStream;const streamGate=new Promise(done=>releaseStream=done);
const provider=http.createServer(async(req,res)=>{try{
 let raw='';for await(const chunk of req)raw+=chunk.toString('utf8');const body=JSON.parse(raw);
 assert.equal(req.headers.authorization,'Bearer local-acceptance-mock-only');assert.equal(body.model,'openai/gpt-6.1-sol');requests.push({model:body.model});assert.equal(requests.length,1);
 res.writeHead(200,{'Content-Type':'text/event-stream; charset=utf-8'});const event=value=>res.write(`data: ${JSON.stringify(value)}\n\n`);
 event({model:body.model,choices:[{delta:{content:firstText}}]});res.flushHeaders();await streamGate;
 for(let i=0;i<24;i++){event({model:body.model,choices:[{delta:{content:nextText}}]});await new Promise(done=>setTimeout(done,120));}
 res.end(`data: ${JSON.stringify({model:body.model,choices:[{delta:{},finish_reason:'stop'}],usage:{prompt_tokens:8,completion_tokens:32,cost:0.000004}})}`);
}catch(error){serverErrors.push(String(error));res.destroy(error);}});
await new Promise(done=>provider.listen(0,'127.0.0.1',done));const providerPort=provider.address().port;
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








const authorization=p=>({approvedAtMs:Date.now(),disclosureVersion:'normal-chat-egress-v2',clientSubmissionId:randomUUID(),draftFingerprint:p.draftFingerprint,recipientChoiceId:p.recipientChoiceId,recipientProviderId:p.recipientProviderId,recipientModelId:p.recipientModelId,retrievalRecipient:p.retrievalRecipient});
const textOf=m=>(m.blocks||[]).filter(b=>b.kind==='TEXT').map(b=>b.text||'').join('');
function largePdf(){
 const stream=Buffer.from('q 0.5 w 0 0 0 RG 40 40 520 700 re S Q\n'.repeat(180000),'ascii');
 const objects=[Buffer.from('<< /Type /Catalog /Pages 2 0 R >>'),Buffer.from('<< /Type /Pages /Kids [3 0 R] /Count 1 >>'),Buffer.from('<< /Type /Page /Parent 2 0 R /MediaBox [0 0 600 800] /Resources << >> /Contents 4 0 R >>'),Buffer.concat([Buffer.from(`<< /Length ${stream.length} >>\nstream\n`),stream,Buffer.from('\nendstream')])];
 const parts=[Buffer.from('%PDF-1.4\n')],offsets=[0];let size=parts[0].length;
 for(let i=0;i<objects.length;i++){offsets.push(size);const item=Buffer.concat([Buffer.from(`${i+1} 0 obj\n`),objects[i],Buffer.from('\nendobj\n')]);parts.push(item);size+=item.length;}
 parts.push(Buffer.from(`xref\n0 5\n0000000000 65535 f \n${offsets.slice(1).map(n=>`${String(n).padStart(10,'0')} 00000 n \n`).join('')}trailer\n<< /Size 5 /Root 1 0 R >>\nstartxref\n${size}\n%%EOF\n`));return Buffer.concat(parts);
}

const result={candidateSha256:receipt.sha256,realService:false,backgroundOnly:true};let port=0;
try{
 port=await start();result.root=root;await ready(port);
 const ws=(await invoke(port,'CHAT','ensure_desktop_local_workspace')).summary.id;
 const setting=(await invoke(port,'CHAT','read_desktop_model_service_settings')).find(p=>p.providerId==='OPENROUTER');
 await invoke(port,'CHAT','save_desktop_model_service_settings',{args:{providerId:'OPENROUTER',enabled:true,presetId:'GPT_6_1_SOL',expectedRevision:setting.revision,apiKey:'nfai-synthetic-preview-concurrency'}});
 const conv=(await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:ws})).exchange.conversations[0];
 const selection=await invoke(port,'CHAT','read_desktop_p6g_selection',{workspaceId:ws,conversationId:conv.id});
 await invoke(port,'CHAT','set_desktop_p6g_conversation_override',{args:{workspaceId:ws,conversationId:conv.id,expectedRevision:selection.conversationOverride.revision,modelId:'GPT_6_1_SOL'}});
 const current=await invoke(port,'CHAT','read_desktop_p6g_selection',{workspaceId:ws,conversationId:conv.id});
 await invoke(port,'CHAT','set_desktop_conversation_preferences',{args:{workspaceId:ws,conversationId:conv.id,expectedRevision:current.conversationOverride.revision,toneOverride:null,webSearchOverride:false}});
 const pdf=largePdf();result.pdfBytes=pdf.length;assert.ok(pdf.length>5*1024*1024&&pdf.length<=20*1024*1024);
 const attachment=await invoke(port,'CHAT','import_desktop_conversation_clipboard_attachment',{args:{workspaceId:ws,displayName:'预览并发合成.pdf',bytesBase64:pdf.toString('base64')}});

 const wav=Buffer.alloc(4*1024*1024+44);wav.write('RIFF',0);wav.writeUInt32LE(wav.length-8,4);wav.write('WAVEfmt ',8);wav.writeUInt32LE(16,16);wav.writeUInt16LE(1,20);wav.writeUInt16LE(1,22);wav.writeUInt32LE(8000,24);wav.writeUInt32LE(16000,28);wav.writeUInt16LE(2,32);wav.writeUInt16LE(16,34);wav.write('data',36);wav.writeUInt32LE(wav.length-44,40);
 const extraAttachments=[];
 for(const [kind,name,bytes] of [['text','并发合成.txt',Buffer.from('预览并发完整文字。'.repeat(40000))],['audio','并发静音合成.wav',wav],['image','并发合成.png',await readFile(join(desktopRoot,'src-tauri/icons/icon.png'))]]){
  const value=await invoke(port,'CHAT','import_desktop_conversation_clipboard_attachment',{args:{workspaceId:ws,displayName:name,bytesBase64:bytes.toString('base64')}});extraAttachments.push({kind,id:value.id,bytes:bytes.length});
 }
 result.queuedAttachments=extraAttachments.map(a=>({kind:a.kind,bytes:a.bytes}));
 const text='请继续输出预览并发合成回答';const preview=await invoke(port,'CHAT','preview_desktop_ordinary_chat_egress',{args:{workspaceId:ws,conversationId:conv.id,text,attachmentIds:[],modelId:null,webSearchOverride:null}});
 const args={args:{workspaceId:ws,conversationId:conv.id,expectedRevision:conv.revision,text,attachmentIds:[],modelId:null,egressAuthorization:authorization(preview)}};
 await evaluate(port,`(()=>{window.__nfaiPreviewChatResult=null;window.__TAURI__.core.invoke('submit_desktop_ordinary_chat',${JSON.stringify(args)},{headers:{'x-nanfeng-data-area':'CHAT'}}).then(value=>window.__nfaiPreviewChatResult=value,error=>window.__nfaiPreviewChatError=String(error));return true;})()`);
 await until(async()=>{const data=await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:ws});return data.exchange.conversations.find(c=>c.id===conv.id).messages.some(m=>textOf(m)===firstText);},'生成首段检查点');
 const requestId=await invoke(port,'CHAT','begin_desktop_pdf_preview');
 await evaluate(port,`(()=>{window.__nfaiPdfPreview={status:'PENDING',started:performance.now()};window.__TAURI__.core.invoke('read_desktop_pdf_preview',${JSON.stringify({requestId,args:{workspaceId:ws,attachmentId:attachment.id,pageNumber:1}})},{headers:{'x-nanfeng-data-area':'CHAT'}}).then(value=>window.__nfaiPdfPreview={status:'COMPLETED',durationMs:performance.now()-window.__nfaiPdfPreview.started,pageNumber:value.pageNumber,pageCount:value.pageCount,pngBytes:value.dataUrl.length},error=>window.__nfaiPdfPreview={status:'FAILED',error:String(error)});return true;})()`);

 await evaluate(port,`(()=>{window.__nfaiQueuedPreviews=[];for(const attachment of ${JSON.stringify(extraAttachments)}){const item={kind:attachment.kind,status:'PENDING',started:performance.now()};window.__nfaiQueuedPreviews.push(item);window.__TAURI__.core.invoke('read_desktop_'+attachment.kind+'_preview',{args:{workspaceId:${JSON.stringify(ws)},attachmentId:attachment.id,...(attachment.kind==='image'?{fullSize:false}:{})}},{headers:{'x-nanfeng-data-area':'CHAT'}}).then(value=>{item.status='COMPLETED';item.durationMs=performance.now()-item.started;item.truncated=value.truncated??null;item.mimeType=value.mimeType??null;item.payloadLength=value.text?.length??value.dataUrl?.length??value.mediaUrl?.length;},error=>{item.status='FAILED';item.error=String(error);});}return true;})()`);
 await new Promise(done=>setTimeout(done,150));
 assert.equal(await evaluate(port,"window.__nfaiPdfPreview.status==='PENDING'"),true);
 let replacementRejected=false;try{await invoke(port,'CHAT','restore_desktop_local_backup',{fingerprint:'nfai-synthetic-no-package',replaceLocal:false});}catch(error){replacementRejected=String(error).includes('预览处理中');}assert.equal(replacementRejected,true,'backup replacement must not overlap preview decoding');result.replacementRejectedWhileRendering=true;
 releaseStream();
 result.persistedWhileRendering=false;const deadline=Date.now()+12000;
 while(Date.now()<deadline){
   const status=await evaluate(port,'window.__nfaiPdfPreview.status');
   if(status!=='PENDING')break;
   const data=await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:ws});
   if(data.exchange.conversations.find(c=>c.id===conv.id).messages.some(m=>textOf(m).startsWith(firstText+nextText))){
     if(await evaluate(port,"window.__nfaiPdfPreview.status==='PENDING'")){result.persistedWhileRendering=true;break;}
   }
   await new Promise(done=>setTimeout(done,100));
 }
 await until(()=>evaluate(port,"window.__nfaiPdfPreview.status!=='PENDING'"),'PDF本地渲染结束');result.pdfPreview=await evaluate(port,'window.__nfaiPdfPreview');

 await until(()=>evaluate(port,"window.__nfaiQueuedPreviews.every(p=>p.status!=='PENDING')"),'排队图片音频文字预览');result.queuedPreviews=await evaluate(port,'window.__nfaiQueuedPreviews');assert.ok(result.queuedPreviews.every(p=>p.status==='COMPLETED'),JSON.stringify(result.queuedPreviews));assert.equal(result.queuedPreviews.find(p=>p.kind==='text').truncated,true);assert.equal(result.queuedPreviews.find(p=>p.kind==='audio').mimeType,'audio/wav');
 let afterPreviewError='';try{await invoke(port,'CHAT','restore_desktop_local_backup',{fingerprint:'nfai-synthetic-no-package',replaceLocal:false});}catch(error){afterPreviewError=String(error);}assert.ok(afterPreviewError&&!afterPreviewError.includes('预览处理中'));result.replacementGateReleased=true;
 let foreignRejected=false;try{await invoke(port,'WORK','read_desktop_pdf_preview',{args:{workspaceId:ws,attachmentId:attachment.id,pageNumber:1}});}catch{foreignRejected=true;}assert.equal(foreignRejected,true);result.foreignAreaRejected=true;
 assert.equal(result.pdfPreview.status,'COMPLETED');assert.equal(result.pdfPreview.pageNumber,1);assert.ok(result.pdfPreview.durationMs>=300,'fixture did not create a meaningful concurrency window');
 await until(()=>evaluate(port,"window.__nfaiPreviewChatResult?.state==='COMPLETED'"),'持续生成完成');
 const data=await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:ws});assert.ok(data.exchange.conversations.find(c=>c.id===conv.id).messages.some(m=>textOf(m)===firstText+nextText.repeat(24)));
 assert.equal(requests.length,1);assert.equal(serverErrors.length,0);result.close=await normalClose();
 port=await start(true);await ready(port);const cold=(await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:ws})).exchange.conversations.find(c=>c.id===conv.id);assert.ok(cold.messages.some(m=>textOf(m)===firstText+nextText.repeat(24)));result.coldClose=await normalClose();
 assert.equal(result.persistedWhileRendering,true,'preview held back generation checkpoints until rendering finished');result.pass=true;
}catch(error){result.pass=false;result.error=String(error);process.exitCode=1;}
finally{releaseStream();if(launch){result.forcedCleanup=true;try{stop();}catch(error){result.cleanupError=String(error);}}provider.closeAllConnections();await new Promise(done=>provider.close(done));result.requests=requests;result.serverErrors=serverErrors;result.pids=pids;if(root)await writeFile(join(root,'a6-preview-concurrency-native-evidence.json'),JSON.stringify(result,null,2),{flag:'wx'});}
console.log(JSON.stringify({root,pass:result.pass,error:result.error,pdfPreview:result.pdfPreview,persistedWhileRendering:result.persistedWhileRendering,pids}));
