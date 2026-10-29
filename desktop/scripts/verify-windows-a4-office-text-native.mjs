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
const requests=[];const serverErrors=[];const wireContents=[];const requestTrace=[];
const provider=http.createServer(async(req,res)=>{
 try {
  let raw='';for await(const chunk of req)raw+=chunk.toString('utf8');const body=JSON.parse(raw);wireContents.push(JSON.stringify(body));requestTrace.push({path:req.url,model:body.model,mockAuthorization:req.headers.authorization==='Bearer local-acceptance-mock-only'});
  const titleCall=req.url==='/chat/completions' && body.model==='deepseek-flash';
  assert.equal(req.headers.authorization===(titleCall?'Bearer nfai-synthetic-docx':'Bearer local-acceptance-mock-only'),true);
  const retrieval=body.model==='openai/gpt-6-luna' && Array.isArray(body.tools);
  requests.push({model:body.model,role:titleCall?'TITLE':retrieval?'RETRIEVAL':'ANSWER',idempotencyKey:req.headers['idempotency-key'],grounded:JSON.stringify(body.messages||[]).includes('<retrieved-evidence>')});
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







const result={candidateSha256:receipt.sha256,realService:false};let port=0;
const fixtures=JSON.parse(execFileSync('python',['-c',`import io,zipfile,base64,json
def pack(parts):
 b=io.BytesIO()
 with zipfile.ZipFile(b,'w') as z:
  for name,body in parts:z.writestr(name,body)
 return b.getvalue()
def pdf():
 stream=b'BT /F1 12 Tf 72 720 Td (PDF_COMPLETE_TAIL) Tj ET'
 objects=[b'<< /Type /Catalog /Pages 2 0 R >>',b'<< /Type /Pages /Kids [3 0 R] /Count 1 >>',b'<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>',b'<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',b'<< /Length '+str(len(stream)).encode()+b' >>\\nstream\\n'+stream+b'\\nendstream']
 out=b'%PDF-1.4\\n';offsets=[0]
 for i,obj in enumerate(objects,1):offsets.append(len(out));out+=str(i).encode()+b' 0 obj\\n'+obj+b'\\nendobj\\n'
 pos=len(out);out+=b'xref\\n0 6\\n0000000000 65535 f \\n'+b''.join(('%010d 00000 n \\n'%o).encode() for o in offsets[1:])+b'trailer\\n<< /Size 6 /Root 1 0 R >>\\nstartxref\\n'+str(pos).encode()+b'\\n%%EOF\\n';return out
files=[('full.docx',pack([('word/document.xml',"<w:p xmlns:w='urn:w'><w:r><w:t><![CDATA[DOCX_COMPLETE_TAIL]]></w:t></w:r></w:p>")])),('full.xlsx',pack([('xl/worksheets/sheet1.xml',"<worksheet><sheetData><row><c r='A1' t='inlineStr'><is><t>前<![CDATA[XLSX_COMPLETE_TAIL &amp; RAW]]>后</t></is></c></row></sheetData></worksheet>")])),('full.pptx',pack([('ppt/slides/slide1.xml',"<p:sld xmlns:p='urn:p' xmlns:a='urn:a'><a:p><a:r><a:t>前<![CDATA[PPTX_COMPLETE_TAIL &amp; RAW]]>后</a:t></a:r></a:p></p:sld>")])),('full.zip',pack([('one.txt','ZIP_FIRST_COMPLETE'),('folder/two.yaml','ZIP_SECOND_COMPLETE_TAIL'),('full.xml','<record>XML_COMPLETE_TAIL</record>'),('nested.xlsx',pack([('xl/worksheets/sheet1.xml',"<worksheet><row><c t='inlineStr'><is><t><![CDATA[NESTED_XLSX_COMPLETE_TAIL]]></t></is></c></row></worksheet>")])),('full.yaml','value: YAML_COMPLETE_TAIL'),('full.txt','TXT_COMPLETE_TAIL'),('full.pdf',pdf())])),('full.xml',b'<record>XML_COMPLETE_TAIL</record>'),('full.yaml',b'value: YAML_COMPLETE_TAIL'),('full.pdf',pdf()),('full.txt',b'TXT_COMPLETE_TAIL')]
print(json.dumps([{'name':n,'data':base64.b64encode(b).decode()} for n,b in files[:4]]))`],{encoding:'utf8',timeout:10000}));
try {
 port=await start();result.root=root;await ready(port);const ws=(await invoke(port,'CHAT','ensure_desktop_local_workspace')).summary.id;const ids=[];result.imports=[];
 for(const f of fixtures){const a=await invoke(port,'CHAT','import_desktop_conversation_clipboard_attachment',{args:{workspaceId:ws,displayName:f.name,bytesBase64:f.data}});ids.push(a.id);result.imports.push({name:f.name,mime:a.mimeType});}

 const duplicate=execFileSync('python',['-c',`import io,zipfile,base64,warnings
warnings.simplefilter('ignore',UserWarning)
b=io.BytesIO()
with zipfile.ZipFile(b,'w') as z:
 z.writestr('same.txt','first')
 z.writestr('same.txt','second')
print(base64.b64encode(b.getvalue()).decode())`],{encoding:'utf8',timeout:10000}).trim();
 await assert.rejects(invoke(port,'CHAT','import_desktop_conversation_clipboard_attachment',{args:{workspaceId:ws,displayName:'synthetic-duplicate.zip',bytesBase64:duplicate}}));assert.equal(requests.length,0);result.duplicateImportRejectedBeforeRequest=true;
 const setting=(await invoke(port,'CHAT','read_desktop_model_service_settings')).find(x=>x.providerId==='DEEPSEEK');await invoke(port,'CHAT','save_desktop_model_service_settings',{args:{providerId:setting.providerId,enabled:true,presetId:setting.presetId,expectedRevision:setting.revision,apiKey:'nfai-synthetic-docx'}});
 const preview=await invoke(port,'CHAT','preview_desktop_ordinary_chat_egress',{args:{workspaceId:ws,conversationId:null,text:'请完整综合四个附件及压缩包内全部原件',attachmentIds:ids,modelId:'DEEPSEEK_V4_PRO',webSearchOverride:false}});assert.equal(preview.recipientModelId,'deepseek-v4-pro');
 const attempt=await invoke(port,'CHAT','submit_desktop_ordinary_chat',{args:{workspaceId:ws,conversationId:null,expectedRevision:null,text:'请完整综合四个附件及压缩包内全部原件',attachmentIds:ids,modelId:'DEEPSEEK_V4_PRO',webSearchOverride:false,egressAuthorization:{approvedAtMs:Date.now(),disclosureVersion:'normal-chat-egress-v1',clientSubmissionId:randomUUID()}}});assert.equal(attempt.state,'COMPLETED');
 const primary=wireContents.find(body=>body.includes('deepseek-v4-pro'));result.materialChecks={};
 for(const marker of ['DOCX_COMPLETE_TAIL','XLSX_COMPLETE_TAIL','PPTX_COMPLETE_TAIL','ZIP_FIRST_COMPLETE','ZIP_SECOND_COMPLETE_TAIL','XML_COMPLETE_TAIL','YAML_COMPLETE_TAIL','PDF_COMPLETE_TAIL','TXT_COMPLETE_TAIL','NESTED_XLSX_COMPLETE_TAIL','XLSX_COMPLETE_TAIL &amp; RAW','PPTX_COMPLETE_TAIL &amp; RAW','请完整综合四个附件及压缩包内全部原件']){assert.ok(primary?.includes(marker),marker);result.materialChecks[marker]=true;}
 await until(()=>requests.some(r=>r.role==='TITLE'),'独立标题模拟请求');assert.equal(serverErrors.length,0);result.close=await normalClose();result.pass=true;
}catch(error){result.pass=false;result.error=String(error);process.exitCode=1;}
finally{if(launch){result.forcedCleanup=true;stop();}provider.closeAllConnections();await new Promise(done=>provider.close(done));result.requests=requests.length;result.serverErrors=serverErrors;result.pids=pids;if(root)await writeFile(join(root,'office-text-native-evidence.json'),JSON.stringify(result,null,2),{flag:'wx'});}
console.log(JSON.stringify({root,pass:result.pass,error:result.error,requests:requests.length,serverErrors,pids}));
