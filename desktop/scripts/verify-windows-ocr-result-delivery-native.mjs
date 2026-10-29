import assert from 'node:assert/strict';
import { createHash, randomUUID } from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

if (process.platform !== 'win32') throw new Error('仅在 Windows 隔离候选上验收。');
const desktopRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const rootParent = resolve(process.argv[2] || 'D:\\CodexTemp');
const taskArea='CHAT';
const otherArea='WORK';
assert.equal(process.argv.length,3,'只接受隔离根参数；当前WORK没有转写入口');
const receipt = JSON.parse(await readFile(join(desktopRoot, 'output/windows-acceptance/current.json'), 'utf8'));
assert.equal(receipt.identifier, 'com.nanzhufeng.ai.desktop.windowsacceptance');
const source = resolve(desktopRoot, 'output/windows-acceptance', receipt.sha256, 'NanfengAI-Windows-Acceptance.exe');
assert.equal(resolve(receipt.executable), source);
assert.equal(createHash('sha256').update(await readFile(source)).digest('hex').toUpperCase(), receipt.sha256);
const runningExecutable = resolve(desktopRoot, 'output/windows-acceptance-binaries', receipt.sha256, 'nanfeng-ai-desktop-spike.exe');
const ps = script => execFileSync('pwsh', ['-NoProfile', '-Command', script], { encoding: 'utf8', timeout: 60000 }).trim();
const quote = value => `'${String(value).replaceAll("'", "''")}'`;
const reservePort = async () => {
  const server = (await import('node:http')).createServer();
  await new Promise(done => server.listen(0, '127.0.0.1', done));
  const port = server.address().port;
  await new Promise(done => server.close(done));
  activePort=port;return port;
};
let activePort=0;
let launch = null;
let root = '';
const pids = [];
const startProcess = args => new Promise((done, fail) => {
  const child = spawn('pwsh', ['-NoProfile', '-File', join(desktopRoot, 'scripts/start-windows-acceptance.ps1'), ...args], {
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  let out = '';
  let err = '';
  const timer = setTimeout(() => { child.kill(); fail(new Error(`启动超时：${err}`)); }, 30000);
  child.stdout.on('data', chunk => {
    out += chunk.toString();
    const line = out.split(/\r?\n/).find(item => item.trim().startsWith('{') && item.includes('"pid"'));
    if (!line) return;
    clearTimeout(timer);
    try { done(JSON.parse(line)); } catch (error) { fail(error); }
    child.stdout.destroy();
    child.stderr.destroy();
    child.unref();
  });
  child.stderr.on('data', chunk => { err += chunk.toString(); });
  child.on('error', error => { clearTimeout(timer); fail(error); });
  child.on('exit', code => { if (!out.includes('"pid"')) { clearTimeout(timer); fail(new Error(`启动失败 ${code}：${err}`)); } });
});
async function start(reopen = false) {
  const port = await reservePort();
  const args = ['-RootParent', rootParent, '-StartupMode', 'diagnostic', '-Visible', '-DebugPort', String(port)];
  if (reopen) args.push('-Root', root);
  launch = await startProcess(args);result.launches??=[];result.launches.push(launch);
  assert.equal(launch.sha256, receipt.sha256);
  assert.equal(resolve(launch.executable), runningExecutable);
  root = resolve(launch.root);
  assert.ok(root.startsWith(rootParent + sep));
  const marker = JSON.parse(await readFile(join(root, '.nanfeng-windows-acceptance.json'), 'utf8'));
  assert.equal(marker.identifier, receipt.identifier);
  activePort=port;return port;
}
function stop() {
  if (!launch) return;
  const pid = Number(launch.pid);
  ps(`$p=Get-CimInstance Win32_Process -Filter "ProcessId=${pid}"; if ($p) { if ([IO.Path]::GetFullPath([string]$p.ExecutablePath) -ne ${quote(runningExecutable)} -or (Get-FileHash -LiteralPath ${quote(runningExecutable)} -Algorithm SHA256).Hash -ne ${quote(receipt.sha256)}) { throw 'PID identity mismatch' }; Stop-Process -Id ${pid} -Force -ErrorAction Stop }`);
  pids.push(pid);
  launch = null;
}
async function cdp(port, method, params) {
  const tabs = await fetch(`http://127.0.0.1:${port}/json/list`).then(response => response.json());
  const tab = tabs.find(item => item.type === 'page' && item.url === 'http://tauri.localhost/');
  assert.ok(tab, 'WebView2 页面未就绪');
  const socket = new WebSocket(tab.webSocketDebuggerUrl);
  try {
    await new Promise((done, fail) => {
      socket.addEventListener('open', done, { once: true });
      socket.addEventListener('error', fail, { once: true });
    });
    return await new Promise((done, fail) => {
      const timer = setTimeout(() => fail(new Error('CDP evaluation timed out')), 30000);
      socket.addEventListener('message', event => {
        const message = JSON.parse(event.data);
        if (message.id !== 1) return;
        clearTimeout(timer);
        if (message.error || message.result?.exceptionDetails) fail(new Error(JSON.stringify(message.result?.exceptionDetails || message.error)));
        else done(message.result);
      });
      socket.send(JSON.stringify({id:1,method,params}));
    });
  } finally { socket.close(); }
}
const evaluate = async (port,expression) => (await cdp(port,'Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true}))?.result?.value;
const undoShortcut = async (port,redo=false) => {
  const params={key:redo?'Z':'z',code:'KeyZ',windowsVirtualKeyCode:90,modifiers:redo?10:2};
  await cdp(port,'Input.dispatchKeyEvent',{type:'rawKeyDown',...params});
  await cdp(port,'Input.dispatchKeyEvent',{type:'keyUp',...params});
};

async function invoke(port, area, command, args = {}) {
  const value = await evaluate(port, `window.__TAURI__.core.invoke(${JSON.stringify(command)},${JSON.stringify(args)},${JSON.stringify({ headers: { 'x-nanfeng-data-area': area } })}).catch(error=>({__error:String(error)}))`);
  if (value?.__error) throw new Error(`${command}: ${value.__error}`);
  return value;
}
async function until(read, label) {
  const deadline = Date.now() + 30000;
  let lastError = null;
  while (Date.now() < deadline) {
    try { const value = await read(); if (value) return value; } catch (error) { lastError = String(error); }
    await new Promise(done => setTimeout(done, 200));
  }
  throw new Error(`${label} 未在原生界面出现${lastError ? `；最后读取错误：${lastError}` : ''}`);
}
const click = (port, selector) => evaluate(port, `(()=>{const node=document.querySelector(${JSON.stringify(selector)});if(!node)return false;node.click();return true})()`);

let result = {checks:[], candidateSha256:receipt.sha256, startupMode:'diagnostic', realService:false, fixtureLayer:'persisted completed result only; no OCR execution', systemSavePicker:false};
const check=(name,value)=>{assert.ok(value,name);result.checks.push({name,pass:true});};
async function normalClose(port){
 const pid=Number(launch.pid);
 const info=JSON.parse(ps(`$p=Get-Process -Id ${pid} -ErrorAction Stop; @{pid=$p.Id;responding=$p.Responding;title=$p.MainWindowTitle;accepted=$p.CloseMainWindow()}|ConvertTo-Json -Compress`));
 check('native window responding',info.responding);check('real native title present',Boolean(info.title));check('CloseMainWindow accepted',info.accepted);
 try { await until(()=>ps(`@(Get-CimInstance Win32_Process -Filter "ProcessId=${pid}").Count`)==='0','正常Close进程退出'); }
 catch(error) { result.closeFailure={info,process:ps(`Get-CimInstance Win32_Process -Filter "ProcessId=${pid}" | Select-Object ProcessId,ExecutablePath | ConvertTo-Json -Compress`),ui:await evaluate(port,'document.body.innerText.slice(-2200)')}; throw error; }
 await until(()=>ps(`@(Get-CimInstance Win32_Process -Filter "Name='msedgewebview2.exe'" | Where-Object { $_.CommandLine -and $_.CommandLine.Contains(${quote(launch.webviewProfile)}) }).Count`)==='0','本根WebView正常退出');result.checks.push({name:'normal Close exited and profile browser exited',pass:true,detail:info});pids.push(pid);launch=null;
}


async function installBridge(port){
 const tabs=await fetch(`http://127.0.0.1:${port}/json/list`).then(r=>r.json());const tab=tabs.find(t=>t.url==='http://tauri.localhost/');assert.ok(tab);
 const socket=new WebSocket(tab.webSocketDebuggerUrl);await new Promise((ok,no)=>{socket.addEventListener('open',ok,{once:true});socket.addEventListener('error',no,{once:true});});
 let sequence=0;const pending=new Map();
 const request=(method,params={})=>new Promise((ok,no)=>{const id=++sequence;const timer=setTimeout(()=>{pending.delete(id);no(new Error('bridge CDP timeout '+method));},30000);pending.set(id,{ok,no,timer});socket.send(JSON.stringify({id,method,params}));});
 socket.addEventListener('message',async event=>{const m=JSON.parse(event.data);if(m.id&&pending.has(m.id)){const p=pending.get(m.id);pending.delete(m.id);clearTimeout(p.timer);m.error?p.no(new Error(JSON.stringify(m.error))):p.ok(m.result);}if(m.method==='Debugger.paused'){try{await request('Debugger.evaluateOnCallFrame',{callFrameId:m.params.callFrames[0].callFrameId,expression:'window.__TAURI__.core=Object.assign({},window.__TAURI__.core,{invoke:window.__TAURI__.core.invoke});window.__w11BridgeInstalled=true;'});}finally{await request('Debugger.resume');}}});
 try{await request('Debugger.enable');const source=await readFile(join(desktopRoot,'src/app.mjs'),'utf8');const bp=await request('Debugger.setBreakpointByUrl',{urlRegex:'/app\\.mjs$',lineNumber:source.slice(0,source.indexOf('const tauriBridge =')).split('\n').length-1});await request('Page.reload');await until(()=>evaluate(port,'window.__w11BridgeInstalled&&Boolean(document.querySelector("[data-action=show-transcription]"))'),'初始化前桥接');await request('Debugger.removeBreakpoint',{breakpointId:bp.breakpointId});check('mutable core bridge installed before app capture',await evaluate(port,'Object.getOwnPropertyDescriptor(window.__TAURI__.core,"invoke").writable'));}finally{socket.close();}
}
const markdown='# W11 合成 OCR 正文\n\n中文 & <标签> "引号" 😀\n第二行\n\n|项目|值|\n|---|---|\n|验证|42|';
const hash=b=>createHash('sha256').update(b).digest('hex');
const python=(code,...args)=>execFileSync('python',['-c',code,...args],{encoding:'utf8',timeout:30000}).trim();
const openTask=async(port,id)=>{await until(()=>click(port,'[data-action="show-transcription"]'),'转写入口就绪');await until(()=>evaluate(port,`Boolean(document.querySelector('[data-action="select-transcription-task"][data-task-id="${id}"]'))`),'任务列表');await click(port,`[data-action="select-transcription-task"][data-task-id="${id}"]`);await until(()=>evaluate(port,`Boolean(document.querySelector('[data-action="copy-transcription-result"][data-task-id="${id}"]'))`),'完成详情');};
try{
 let port=await start();result.root=root;
 await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke)'),'IPC');
 const workspaceId=(await invoke(port,taskArea,'ensure_desktop_local_workspace')).summary.id;
 const workId=(await invoke(port,otherArea,'ensure_desktop_local_workspace')).summary.id;
 const workBefore=await invoke(port,otherArea,'read_desktop_workspace',{workspaceId:workId});
 result.taskArea=taskArea;
 if(taskArea==='WORK'){await until(()=>click(port,'[data-action=show-work]'),'WORK入口');await until(()=>evaluate(port,'globalThis.__nanfengDesktopWorkState?.dataArea==="WORK"'),'WORK归属');}
 const input=join(root,'synthetic-w11-inputs');await mkdir(input,{recursive:true});
 const sourcePath=join(input,'W11 合成原件.png');
 await writeFile(sourcePath,Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aL1sAAAAASUVORK5CYII=','base64'));
 const task=await invoke(port,taskArea,'import_desktop_ocr_source',{args:{workspaceId,selectedPath:sourcePath}});result.taskId=task.id;
 const resultPath=join(input,'W11 合成结果.md');await writeFile(resultPath,markdown);
 const asset=await invoke(port,taskArea,'import_desktop_conversation_attachment',{args:{workspaceId,selectedPath:resultPath}});result.resultAttachmentId=asset.id;
 await normalClose(port);
 python("import sqlite3,sys;c=sqlite3.connect(sys.argv[1]);c.execute('PRAGMA foreign_keys=ON');c.execute(\"UPDATE desktop_transcription_tasks SET state='COMPLETED',result_attachment_id=?,result_sha256=?,result_byte_count=?,page_count=1 WHERE id=? AND model_id='glm-ocr'\",(sys.argv[3],sys.argv[4],len(sys.argv[5].encode('utf-8')),sys.argv[2]));assert c.execute('SELECT changes()').fetchone()[0]==1;c.execute('INSERT INTO desktop_transcription_segments VALUES(?,0,0,0,?)',(sys.argv[2],sys.argv[5]));c.commit();assert c.execute('PRAGMA integrity_check').fetchone()[0]=='ok';assert not c.execute('PRAGMA foreign_key_check').fetchall();c.close()",join(root,...(taskArea==='WORK'?['work-area']:[]),'workspace.sqlite3'),task.id,asset.id,hash(Buffer.from(markdown)),markdown);
 port=await start(true);await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke)'),'coldIPC');
 const completed=(await invoke(port,taskArea,'read_desktop_transcription_state',{workspaceId:null})).tasks.find(t=>t.id===task.id);
 check('persistent completed GLM result',completed.state==='COMPLETED'&&completed.segments[0].text===markdown&&completed.attemptCount===0&&completed.providerRequestCount===0);
 await installBridge(port);if(taskArea==='WORK'){await until(()=>click(port,'[data-action=show-work]'),'reload WORK入口');await until(()=>evaluate(port,'globalThis.__nanfengDesktopWorkState?.dataArea==="WORK"'),'reload WORK归属');}await openTask(port,task.id);
 check('three current export controls',await evaluate(port,`['txt','md','docx'].every(f=>document.querySelector('[data-action="export-transcription-task"][data-format="'+f+'"]'))`));
 check('no legacy SRT control',await evaluate(port,`!document.querySelector('[data-action="export-transcription-task"][data-format="srt"]')`));
 await evaluate(port,`(()=>{const core=window.__TAURI__.core;window.__w11Original=core.invoke;window.__w11Exports=[];window.__w11Calls=[];core.invoke=async function(command,args,options){window.__w11Calls.push(command);if(command==='plugin:dialog|save')return window.__w11ExportPath;const value=await window.__w11Original.call(this,command,args,options);if(command==='export_desktop_transcription_task')window.__w11Exports.push({args,receipt:value});return value;};return true})()`);
 result.exports=[];
 for(const format of ['txt','md','docx']){
  const path=join(input,'实际 UI 导出.'+format);await evaluate(port,`window.__w11ExportPath=${JSON.stringify(path)}`);
  check('UI export '+format,await click(port,`[data-action="export-transcription-task"][data-format="${format}"]`));
  let exported;try{exported=await until(()=>evaluate(port,`window.__w11Exports.find(x=>x.args.args.format==='${format}')`),'导出 '+format);}catch(error){result.bridgeDiagnostic=await evaluate(port,`({calls:window.__w11Calls,body:document.body.innerText.slice(-1700),internalDescriptor:Object.getOwnPropertyDescriptor(window.__TAURI_INTERNALS__,'invoke')?.writable})`);throw error;}
  const bytes=await readFile(path);assert.equal(bytes.length,exported.receipt.byteCount);assert.equal(hash(bytes),exported.receipt.sha256.toLowerCase());
  if(format!=='docx')assert.equal(bytes.toString('utf8'),markdown);
  else{const parsed=JSON.parse(python("import zipfile,xml.etree.ElementTree as E,json,sys;z=zipfile.ZipFile(sys.argv[1]);assert z.testzip() is None;assert {'[Content_Types].xml','_rels/.rels','word/document.xml'}<=set(z.namelist());[E.fromstring(z.read(n)) for n in z.namelist()];r=E.fromstring(z.read('word/document.xml'));print(json.dumps([t.text for t in r.iter('{http://schemas.openxmlformats.org/wordprocessingml/2006/main}t')],ensure_ascii=True))",path));assert.equal(parsed[1],markdown);assert.equal(parsed[0],task.sourceDisplayName);}
  result.exports.push({format,byteCount:bytes.length,sha256:hash(bytes)});check('export bytes verified '+format,true);
 }
 await evaluate(port,`(()=>{const original=navigator.clipboard.writeText.bind(navigator.clipboard);navigator.clipboard.writeText=async text=>{await original(text);window.__w11Copied=text;};return true})()`);
 check('copy existing control',await click(port,`[data-action="copy-transcription-result"][data-task-id="${task.id}"]`));
 await until(()=>evaluate(port,`window.__w11Copied===${JSON.stringify(markdown)}`),'实际clipboard写入完成');check('clipboard original promise resolved',true);
 check('result preview control',await click(port,`[data-action="preview-transcription-result"][data-task-id="${task.id}"]`));
 await until(()=>evaluate(port,`document.querySelector('.text-preview-surface .local-text-preview')?.textContent.includes('W11 合成 OCR 正文')`),'私有预览');await click(port,'[data-action="close-text-preview"]');
 await invoke(port,taskArea,'save_desktop_conversation_draft',{args:{workspaceId,conversationId:null,text:'W11 合成保留草稿',attachmentIds:[],modelId:null,toneOverride:null,webSearchOverride:false}});
 check('continue existing UI',await click(port,`[data-action="continue-chat-with-transcription"][data-task-id="${task.id}"]`));
 const draft=await until(async()=>{const d=await invoke(port,taskArea,'read_desktop_conversation_draft',{workspaceId,conversationId:null});return d?.attachments?.some(a=>a.id===asset.id)?d:false},'持久草稿');assert.equal(draft.text,'W11 合成保留草稿');assert.equal(draft.webSearchOverride,false);check('existing draft preserved',true);
 await openTask(port,task.id);check('delete UI ask',await click(port,`[data-action="ask-delete-transcription-task"][data-task-id="${task.id}"]`));await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=confirm-delete-transcription-task]"))'),'删除确认');check('delete UI confirm',await click(port,`[data-action="confirm-delete-transcription-task"][data-task-id="${task.id}"]`));
 await until(async()=>!(await invoke(port,taskArea,'read_desktop_transcription_state',{workspaceId:null})).tasks.some(t=>t.id===task.id),'删除owner回读');
 assert.equal((await invoke(port,taskArea,'read_desktop_text_preview',{args:{workspaceId,attachmentId:asset.id}})).text,markdown);check('shared draft result survives task deletion',true);
 assert.deepEqual(await invoke(port,otherArea,'read_desktop_workspace',{workspaceId:workId}),workBefore);check('WORK unchanged',true);
 await normalClose(port);port=await start(true);await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke)'),'second cold IPC');
 check('cold deletion persistent',!(await invoke(port,taskArea,'read_desktop_transcription_state',{workspaceId:null})).tasks.some(t=>t.id===task.id));
 assert.deepEqual(await invoke(port,taskArea,'read_desktop_conversation_draft',{workspaceId,conversationId:null}),draft);check('cold complete draft preserved',true);
 assert.equal((await invoke(port,taskArea,'read_desktop_text_preview',{args:{workspaceId,attachmentId:asset.id}})).text,markdown);check('cold shared bytes preserved',true);
 assert.deepEqual(await invoke(port,otherArea,'read_desktop_workspace',{workspaceId:workId}),workBefore);check('cold WORK unchanged',true);
 await normalClose(port);result.pass=true;
}catch(error){result.error=String(error.stack||error);try{result.failureUi=await evaluate(activePort,'({body:document.body.innerText.slice(-1800),calls:window.__w11Calls,bridge:window.__TAURI_INTERNALS__&&Object.getOwnPropertyDescriptor(window.__TAURI_INTERNALS__,"invoke")?.writable})');}catch{}process.exitCode=1;}
finally{if(launch){result.forcedCleanup=true;stop();}result.pids=pids;if(root)await writeFile(join(root,'w11-result-delivery-evidence.json'),JSON.stringify(result,null,2));}
console.log(JSON.stringify({root,pass:result.pass,checks:result.checks.length,error:result.error}));
