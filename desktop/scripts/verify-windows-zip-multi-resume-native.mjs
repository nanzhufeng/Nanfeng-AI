import assert from 'node:assert/strict';
import { createHash, randomUUID } from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import { mkdir, readFile, writeFile, appendFile } from 'node:fs/promises';
import { dirname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

if (process.platform !== 'win32') throw new Error('仅在 Windows 隔离候选上验收。');
const desktopRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const rootParent = resolve(process.argv[2] || 'D:\\CodexTemp');
const taskArea='CHAT';
const otherArea='WORK';
const mode='large-history-json';
assert.equal(process.argv.length,3);
assert.ok(process.execArgv.includes('--max-old-space-size=2048'));
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
  child.stdout.on('data', async chunk => {
    out += chunk.toString();
    const line = out.split(/\r?\n/).find(item => item.trim().startsWith('{') && item.includes('"pid"'));
    if (!line) return;
    clearTimeout(timer);
    // Hidden/redirected PowerShell may encode Chinese paths with a legacy code
    // page. Its ASCII receipt path locates the authoritative UTF-8 JSON file.
    try { const emitted=JSON.parse(line); done(JSON.parse(await readFile(emitted.launchReceipt,'utf8'))); } catch (error) { fail(error); }
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
  root = resolve(launch.root);
  assert.equal(launch.sha256, receipt.sha256);
  assert.equal(resolve(launch.executable), runningExecutable);
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
  const tabs = await fetch(`http://127.0.0.1:${port}/json/list`,{signal:AbortSignal.timeout(10000)}).then(response => response.json());
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

let result={checks:[],candidateSha256:receipt.sha256,startupMode:'diagnostic',realService:false,performanceBudget:null,measurements:[],areas:{},fullNormalRecoveryAccepted:false};
const check=(name,value)=>{assert.ok(value,name);result.checks.push({name,pass:true});};
async function normalClose(port){
 const pid=Number(launch.pid);
 const info=JSON.parse(ps(`$p=Get-Process -Id ${pid} -ErrorAction Stop; @{pid=$p.Id;responding=$p.Responding;title=$p.MainWindowTitle;accepted=$p.CloseMainWindow()}|ConvertTo-Json -Compress`));
 check('native window responding',info.responding);check('real native title present',Boolean(info.title));check('CloseMainWindow accepted',info.accepted);
 try { await until(()=>ps(`@(Get-CimInstance Win32_Process -Filter "ProcessId=${pid}").Count`)==='0','正常Close进程退出'); }
 catch(error) { result.closeFailure={info,process:ps(`Get-CimInstance Win32_Process -Filter "ProcessId=${pid}" | Select-Object ProcessId,ExecutablePath | ConvertTo-Json -Compress`),ui:await evaluate(port,'document.body.innerText.slice(-2200)')}; throw error; }
 await until(()=>ps(`@(Get-CimInstance Win32_Process -Filter "Name='msedgewebview2.exe'" | Where-Object { $_.CommandLine -and $_.CommandLine.Contains(${quote(launch.webviewProfile)}) }).Count`)==='0','本根WebView正常退出');result.checks.push({name:'normal Close exited and profile browser exited',pass:true,detail:info});pids.push(pid);launch=null;
}

const hash=value=>createHash('sha256').update(JSON.stringify(value)).digest('hex');
const delay=ms=>new Promise(ok=>setTimeout(ok,ms));
const timed=async(label,fn)=>{const t=performance.now();const value=await fn();result.measurements.push({label,milliseconds:performance.now()-t});return value;};
const formats=[{name:'chatgpt',prefix:'chatgpt'},{name:'claude',prefix:'claude'},{name:'knowledge',prefix:'nanfeng_knowledge'}];
const libraries=[];
const progress=async stage=>{result.stage=stage;result.updatedUtc=new Date().toISOString();await writeFile(join(root,'history-progress.json'),JSON.stringify(result,null,2));console.log(JSON.stringify({stage,root,checks:result.checks.length}));};
function fixture(format,area,count,start=0){
 return Array.from({length:count},(_,offset)=>{
  const i=start+offset,id=`w14-${area}-${format}-${i}`,title=`历史 ${area} ${format} ${i} 中文 😀`;
  const user=`${id} 用户\n括号 ([]) 与代码 \`const n = ${i};\``,answer=`${id} 回答\nUnicode Ω 😀\n表格 |甲|乙|\n末行 ${i}`;
  const date='2026-08-15T08:00:00Z';
  if(format==='chatgpt')return {id,title,create_time:1,update_time:2,mapping:{u:{parent:null,children:['a'],message:{id:'u',author:{role:'user'},content:{parts:[user]}}},a:{parent:'u',children:[],message:{id:'a',author:{role:'assistant'},content:{parts:[answer]}}}}};
  const messages=[{uuid:'u',sender:'human',created_at:date,text:user,parent_message_uuid:null},{uuid:'a',sender:'assistant',created_at:date,text:answer,parent_message_uuid:'u'}];
  if(format==='claude')return {uuid:id,name:title,created_at:date,updated_at:date,chat_messages:messages};
  return {id:i+1,title,createdAt:date,updatedAt:date,sourceText:JSON.stringify({chat_messages:messages})};
 });
}
async function confirmBatch(port,area,format,workspaceId,task,items){
 const ids=await evaluate(port,`(async()=>{const ids=[];for(const itemId of ${JSON.stringify(items.map(item=>item.id))}){ids.push(await window.__TAURI__.core.invoke(${JSON.stringify('confirm_'+format.prefix+'_import_item')},{args:{workspaceId:${JSON.stringify(workspaceId)},taskId:${JSON.stringify(task.id)},itemId}},{headers:{'x-nanfeng-data-area':${JSON.stringify(area)}}}));}return ids})()`);
 assert.equal(ids.length,items.length);return ids;
}
async function stage(port,lib,path){const staged=await invoke(port,lib.area,'stage_'+lib.format.prefix+'_export_selected',{selectedPath:path,workspaceId:lib.workspaceId});const task=await readTask(port,lib);assert.equal(task.id,staged.id);return task;}
async function readTask(port,lib){return invoke(port,lib.area,'read_latest_'+lib.format.prefix+'_import_task',{workspaceId:lib.workspaceId});}
async function validate(port,lib,expected){
 const projection=await invoke(port,lib.area,'read_desktop_workspace',{workspaceId:lib.workspaceId});
 const rows=projection.exchange.conversations.filter(row=>row.id!==lib.seedConversationId);
 assert.equal(rows.length,expected.length,`${lib.area}/${lib.format.name} complete conversation count`);
 assert.equal(new Set(rows.map(row=>row.id)).size,rows.length);
 for(const input of expected){
  const title=input.title??input.name;
  const row=rows.find(row=>row.title===title);assert.ok(row,`missing exact title ${title}`);
  const messages=input.mapping?Object.values(input.mapping).map(n=>n.message.content.parts[0]):(input.chat_messages??JSON.parse(input.sourceText).chat_messages).map(m=>m.text);
  assert.equal(row.messages.length,2,title);
  assert.deepEqual(row.messages.map(m=>m.blocks.filter(b=>b.kind==='TEXT').map(b=>b.text).join('\n')),messages,`${title} exact message bytes`);
 }
 check(`${lib.area}/${lib.format.name} full ${expected.length} conversations and message bytes`,true);
 return hash(projection.exchange);
}
function zipFixture(format,area,count,start=0){
 const rows=fixture(format,area,count,start);
 if(format==='chatgpt')for(const row of rows){const u=row.id+'-u',a=row.id+'-a';row.mapping.u.children=[a];row.mapping.u.message.id=u;row.mapping.a.parent=u;row.mapping.a.message.id=a;row.mapping={[u]:row.mapping.u,[a]:row.mapping.a};}
 if(format==='claude')for(const row of rows){row.chat_messages[0].uuid=row.uuid+'-u';row.chat_messages[1].uuid=row.uuid+'-a';row.chat_messages[1].parent_message_uuid=row.uuid+'-u';}
 return rows;
}
async function makeZip(path,input,provider){
 const jsonPath=path+'.json';await writeFile(jsonPath,JSON.stringify(input),{flag:'wx'});
 execFileSync('python',['-X','utf8','-c',"import zipfile,sys,json; rows=json.load(open(sys.argv[1],encoding='utf-8')); z=zipfile.ZipFile(sys.argv[2],'x',compression=zipfile.ZIP_STORED); chunks=[rows[i:i+100] for i in range(0,len(rows),100)] if sys.argv[3]=='CHATGPT' else [rows]; [z.writestr(('conversations-'+str(i+1)+'.json') if sys.argv[3]=='CHATGPT' else 'conversations.json',json.dumps(chunk,ensure_ascii=False)) for i,chunk in enumerate(chunks)]; z.close()",jsonPath,path,provider],{timeout:10000});return path;
}
async function zipStage(port,lib,path){
 const args=lib.resumeTaskId?{taskId:lib.resumeTaskId}:{args:{workspaceId:lib.workspaceId,provider:lib.provider,selectedPath:path}};const command=lib.resumeTaskId?'retry_p6k_zip_import_task':'stage_p6k_zip_import_selected';
 await evaluate(port,`(()=>{globalThis.__w14zip={done:false};window.__TAURI__.core.invoke(${JSON.stringify(command)},${JSON.stringify(args)},${JSON.stringify({headers:{'x-nanfeng-data-area':lib.area}})}).then(value=>{globalThis.__w14zip={done:true,value}}).catch(error=>{globalThis.__w14zip={done:true,error:String(error)}});return true})()`);
 let task=null,lastProgress='',lastChange=performance.now();const started=performance.now();
 while(!task){
  const state=await evaluate(port,'globalThis.__w14zip');
  if(state.done){if(state.error)throw new Error(state.error);task=state.value;break;}
  const db=join(root,lib.area==='CHAT'?'':'work-area','workspace.sqlite3');
  const progress=execFileSync('python',['-X','utf8','-c',"import sqlite3,sys,json; c=sqlite3.connect('file:'+sys.argv[1].replace('\\\\','/')+'?mode=ro',uri=True,timeout=3); print(json.dumps(c.execute('select status,count(*) from p6k_zip_import_items group by status').fetchall())); c.close()",db],{encoding:'utf8',timeout:10000}).trim();
  if(progress!==lastProgress){lastProgress=progress;lastChange=performance.now();result.zipProgress=JSON.parse(progress);await writeFile(join(root,'zip-live-progress.json'),JSON.stringify({area:lib.area,provider:lib.provider,elapsedMilliseconds:performance.now()-started,progress:result.zipProgress}));}
  assert.ok(performance.now()-lastChange<30000,'ZIP committed item progress stalled for 30 seconds');
  assert.ok(performance.now()-started<900000,'ZIP bounded observation exceeded 15 minutes');
  await delay(1000);
 }
 assert.equal(task.status,'COMPLETED');assert.equal(task.items.length,lib.expectedCount);assert.equal(task.items.filter(x=>x.status==='COMMITTED').length+task.items.filter(x=>x.status==='SKIPPED').length,lib.expectedCount);
 return task;
}
try {
 let port=await start();result.root=root;await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke)'),'native IPC');
 const dir=join(root,'synthetic-history-zip');await mkdir(dir,{recursive:true});
 for(const [index,format] of formats.slice(0,2).entries()){
  const area=index===0?'CHAT':'WORK',otherArea=index===0?'WORK':'CHAT';
  const ws=await invoke(port,area,'ensure_desktop_local_workspace');const other=await invoke(port,otherArea,'ensure_desktop_local_workspace');
  const otherHash=hash((await invoke(port,otherArea,'read_desktop_workspace',{workspaceId:other.summary.id})).exchange);
  const lib={area,format,provider:format.name.toUpperCase(),count:300,expectedCount:300,workspaceId:ws.summary.id,seedConversationId:null,input:zipFixture(format.name,area,300),paths:[],taskIds:[]};libraries.push(lib);
  const path=await makeZip(join(dir,`${area}-${format.name}-300.zip`),lib.input,lib.provider);lib.paths=[path];
  await evaluate(port,`(()=>{globalThis.__w14resume={done:false};window.__TAURI__.core.invoke('stage_p6k_zip_import_selected',{args:${JSON.stringify({workspaceId:lib.workspaceId,provider:lib.provider,selectedPath:path})}},${JSON.stringify({headers:{'x-nanfeng-data-area':area}})}).then(v=>{globalThis.__w14resume={done:true}}).catch(e=>{globalThis.__w14resume={done:true,error:String(e)}});return true})()`);
  const db=join(root,area==='CHAT'?'':'work-area','workspace.sqlite3');
  await until(()=>{
   const n=Number(execFileSync('python',['-X','utf8','-c',"import sqlite3,sys; c=sqlite3.connect('file:'+sys.argv[1].replace('\\\\','/')+'?mode=ro',uri=True); print(c.execute(\"select count(*) from p6k_zip_import_items where status='COMMITTED'\").fetchone()[0]);c.close()",db],{encoding:'utf8',timeout:10000}).trim());
   if(n>=50&&n<300){result.observedBeforeKill={area,committed:n,total:300};return true;}return false;
  },'actual partial ZIP commits');
  stop();
  const snapshot=JSON.parse(execFileSync('python',['-X','utf8','-c',"import sqlite3,sys,json; c=sqlite3.connect('file:'+sys.argv[1].replace('\\\\','/')+'?mode=ro',uri=True);print(c.execute('select exchange_json from workspace_exchange where workspace_id=?',(sys.argv[2],)).fetchone()[0]);c.close()",db,lib.workspaceId],{encoding:'utf8',timeout:10000}));
  await writeFile(join(dir,`${area}-after-kill-exchange.json`),JSON.stringify(snapshot));
  port=await start(true);await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke)'),'cold native IPC');
  const task=await invoke(port,area,'read_latest_p6k_zip_import_task',{workspaceId:lib.workspaceId});lib.resumeTaskId=task.id;lib.taskIds=[task.id];
  const committed=task.items.filter(x=>x.status==='COMMITTED');assert.ok(committed.length>=50&&committed.length<300);assert.equal(task.items.filter(x=>x.status==='PENDING').length,300-committed.length);
  assert.deepEqual((await invoke(port,area,'read_desktop_workspace',{workspaceId:lib.workspaceId})).exchange,snapshot);
  const partialInput=lib.input.filter(x=>snapshot.conversations.some(i=>i.title===(x.title??x.name)));await validate(port,lib,partialInput);
  await delay(1000);assert.deepEqual(await invoke(port,area,'read_latest_p6k_zip_import_task',{workspaceId:lib.workspaceId}),task);
  assert.equal(hash((await invoke(port,otherArea,'read_desktop_workspace',{workspaceId:other.summary.id})).exchange),otherHash);check(`${area}/${format.name} actual partial commits cold exact snapshot and no automatic retry`,true);
  lib.interruption={committed:committed.length,pending:300-committed.length,coldTaskStatus:task.status};
  const complete=await zipStage(port,lib,path);lib.latestTask=complete;lib.ids=complete.items.map(x=>x.conversationId);assert.equal(new Set(lib.ids).size,300);
  for(const item of committed)assert.equal(complete.items.find(x=>x.id===item.id).conversationId,item.conversationId);
  lib.hash=await validate(port,lib,lib.input);check(`${area}/${format.name} explicit retry complete original committed IDs conserved`,true);
  lib.exported=await invoke(port,area,'export_desktop_workspace_to_selected_path',{workspaceId:lib.workspaceId,selectedPath:join(dir,`${area}-${format.name}-complete.nfai-exchange`)});
  await progress(`${area}/${format.name} actual ZIP interruption resumed`);
 }
 await normalClose(port);port=await start(true);await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke)'),'final cold native IPC');
 for(const lib of libraries){assert.equal(await validate(port,lib,lib.input),lib.hash);assert.deepEqual(await invoke(port,lib.area,'read_latest_p6k_zip_import_task',{workspaceId:lib.workspaceId}),lib.latestTask);check(`${lib.area}/${lib.format.name} final cold complete exchange and task`,true);}
 await normalClose(port);result.pass=true;
} catch(error){result.pass=false;result.error=String(error);result.stack=error.stack;throw error;}
finally{
 if(launch){result.forcedCleanup=true;stop();}
 result.pids=pids;result.libraries=libraries.map(({input,latestTask,...lib})=>({...lib,latestTaskHash:latestTask?hash(latestTask):null}));
 if(root)await writeFile(join(root,'history-evidence.json'),JSON.stringify(result,null,2));
 console.log(JSON.stringify({root,pass:result.pass,error:result.error,checks:result.checks.length,pids}));
}
