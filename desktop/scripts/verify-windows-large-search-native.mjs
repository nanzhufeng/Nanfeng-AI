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
const mode='large-search';
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
const areas=['CHAT','WORK'];
const sha=bytes=>createHash('sha256').update(bytes).digest('hex');
async function selectArea(port,area){
 await until(()=>click(port,'[data-action="show-'+(area==='CHAT'?'chat':'work')+'"]'),'area entry');
 await until(()=>evaluate(port,'globalThis.__nanfengDesktopWorkState?.dataArea==='+JSON.stringify(area)),'area selected');
}
async function installProjectionAccess(port){
 const tabs=await fetch(`http://127.0.0.1:${port}/json/list`,{signal:AbortSignal.timeout(10000)}).then(r=>r.json());
 const tab=tabs.find(t=>t.url==='http://tauri.localhost/');assert.ok(tab);
 const socket=new WebSocket(tab.webSocketDebuggerUrl);await new Promise((ok,no)=>{socket.addEventListener('open',ok,{once:true});socket.addEventListener('error',no,{once:true});});
 let sequence=0;const pending=new Map();
 const request=(method,params={})=>new Promise((ok,no)=>{const id=++sequence;const timer=setTimeout(()=>{pending.delete(id);no(new Error('projection CDP timeout '+method));},30000);pending.set(id,{ok,no,timer});socket.send(JSON.stringify({id,method,params}));});
 socket.addEventListener('message',async event=>{const m=JSON.parse(event.data);if(m.id&&pending.has(m.id)){const p=pending.get(m.id);pending.delete(m.id);clearTimeout(p.timer);m.error?p.no(new Error(JSON.stringify(m.error))):p.ok(m.result);}if(m.method==='Debugger.paused'){try{await request('Debugger.evaluateOnCallFrame',{callFrameId:m.params.callFrames[0].callFrameId,expression:'window.__w20Refresh=refresh;'});}finally{await request('Debugger.resume');}}});
 try{await request('Debugger.enable');const source=await readFile(join(desktopRoot,'src/app.mjs'),'utf8');const bp=await request('Debugger.setBreakpointByUrl',{urlRegex:'/app\\.mjs$',lineNumber:source.slice(0,source.indexOf('const tauriBridge =')).split('\n').length-1});await request('Page.reload');await until(()=>evaluate(port,'Boolean(window.__w20Refresh&&document.querySelector("#chat-composer")&&globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'),'projection access ready');await request('Debugger.removeBreakpoint',{breakpointId:bp.breakpointId});}finally{socket.close();}
}
async function selectLibraryProjection(port,area,id){
 await selectArea(port,area);
 // Diagnostic selection only: use the complete Rust read as the current
 // projection and the existing private refresh. No injected UI or fake rows.
 await evaluate(port,`(async()=>{globalThis.__nanfengDesktopWorkState.current=await window.__TAURI__.core.invoke('read_desktop_workspace',{workspaceId:${JSON.stringify(id)}},{headers:{'x-nanfeng-data-area':${JSON.stringify(area)}}});await window.__w20Refresh();return true})()`);
}

let requestId=800000;
async function query(port,area,category,offset=0,term='searchneedle'){
 return invoke(port,area,'query_desktop_local_index',{args:{query:term,category,sortMode:'default',fileType:'all',offset,recordHistory:false,historyWorkspaceId:null},requestId:++requestId});
}
async function complete(port,area,category,expected){
 let hits=[],pages=0;
 while(true){const p=await query(port,area,category,hits.length);pages++;assert.equal(p.totalCount,expected);hits.push(...p.hits);if(!p.hasMore)break;assert.ok(p.hits.length);assert.ok(pages<100);}
 assert.equal(hits.length,expected);assert.equal(new Set(hits.map(h=>h.entryId)).size,expected);
 for(const h of hits)assert.ok(h.conversationId.startsWith('w13-'+area.toLowerCase()+'-'));
 result.areas[area].queries??=[];result.areas[area].queries.push({category,pages,count:hits.length});return hits;
}
async function openSearch(port,term){
 await until(()=>evaluate(port,`(()=>{const i=document.querySelector('#chat-search');if(!i)return false;i.focus();return true})()`),'search entry');
 await until(()=>evaluate(port,'Boolean(document.querySelector("#full-search-input"))'),'fullscreen entry');
 await evaluate(port,`(()=>{const i=document.querySelector('#full-search-input');i.value=${JSON.stringify(term)};i.dispatchEvent(new Event('input',{bubbles:true}));return true})()`);
 await until(()=>evaluate(port,`!globalThis.__nanfengDesktopWorkState.searchLoading && globalThis.__nanfengDesktopWorkState.chatSearch===${JSON.stringify(term)}`),'query rendered');
}
async function fullUi(port,area,category,expected){
 assert.equal(await click(port,`[data-action="set-search-category"][data-category="${category}"]`),true);
 await evaluate(port,'new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve)))');
 await until(()=>evaluate(port,`globalThis.__nanfengDesktopWorkState.searchCategory===${JSON.stringify(category)}&&!globalThis.__nanfengDesktopWorkState.searchLoading&&globalThis.__nanfengDesktopWorkState.searchPage.totalCount===${expected}&&document.querySelectorAll('.desktop-search-text-card').length===globalThis.__nanfengDesktopWorkState.searchResults.length`),'category rendered');
 let pages=1;
 while(await evaluate(port,'Boolean(globalThis.__nanfengDesktopWorkState.searchPage.hasMore)')){
 const before=await evaluate(port,'globalThis.__nanfengDesktopWorkState.searchResults.length');
 await until(()=>evaluate(port,`Boolean(document.querySelector('[data-action="load-more-search-results"]:not([disabled])'))`),'next page mounted');assert.equal(await click(port,'[data-action="load-more-search-results"]'),true);
 await until(()=>evaluate(port,`!globalThis.__nanfengDesktopWorkState.searchLoadingMore&&globalThis.__nanfengDesktopWorkState.searchResults.length>${before}&&document.querySelectorAll('.desktop-search-text-card').length===globalThis.__nanfengDesktopWorkState.searchResults.length`),'UI next page');assert.ok(++pages<100);
 }
 const state=await evaluate(port,'({count:globalThis.__nanfengDesktopWorkState.searchResults.length,total:globalThis.__nanfengDesktopWorkState.searchPage.totalCount,ids:globalThis.__nanfengDesktopWorkState.searchResults.map(h=>h.entryId),cards:document.querySelectorAll(".desktop-search-text-card").length,marks:document.querySelectorAll(".desktop-search-text-card mark").length})');
 result.lastUi={area,category,expected,...state};assert.equal(state.count,expected);assert.equal(state.total,expected);assert.equal(new Set(state.ids).size,expected);assert.equal(state.cards,expected);if(expected)assert.ok(state.marks>0);
 result.areas[area].ui??=[];result.areas[area].ui.push({category,pages,...state,ids:undefined});check(area+' UI '+category+' complete',true);console.log(JSON.stringify({phase:'UI_COMPLETE',area,category,count:state.count,pages}));
}
async function conserve(port){for(const area of areas)for(const item of result.areas[area].libraries){const data=await invoke(port,area,'read_desktop_workspace',{workspaceId:item.id});assert.equal(hash(data.exchange),item.hash);}}
function sqlite(closed=false){const data=JSON.parse(execFileSync('python',[join(desktopRoot,'scripts/check-stability-synthetic-sqlite.py'),root,...(closed?['--closed-browser']:[])],{encoding:'utf8',timeout:60000}));assert.ok(data.allOk);return {databases:data.databases.length,allOk:data.allOk};}
try{
 let port=await start();result.root=root;
 await until(()=>evaluate(port,'Boolean(document.querySelector("#chat-composer")&&globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'),'ready');
 const {packageExchange,semanticHash,preflightPackage}=await import('../../protocol/scripts/exchange-lib.mjs');
 const original=JSON.parse(await readFile(join(desktopRoot,'../protocol/fixtures/nfai.exchange.v1.golden.json'),'utf8'));
 for(const area of areas){result.areas[area]={libraries:[]};await invoke(port,area,'ensure_desktop_local_workspace');
 for(const count of [100,300,600]){
 const ex=structuredClone(original);ex.export.id=`w13-search-${area.toLowerCase()}-${count}`;
 ex.conversations=Array.from({length:count},(_,i)=>{const c=structuredClone(original.conversations[0]);c.id=`w13-${area.toLowerCase()}-${count}-${i}`;c.title=`searchneedle ${area} ${count}/${i}`;c.currentLeafId=c.id+'-a';c.messages[0].id=c.id+'-u';c.messages[0].blocks=[{kind:'TEXT',ordinal:0,text:`searchneedle question ${area} ${count}/${i}`}];c.messages[1].id=c.id+'-a';c.messages[1].parentId=c.id+'-u';c.messages[1].blocks=[{kind:'TEXT',ordinal:0,text:`searchneedle answer ${area} ${count}/${i}`}];return c;});
 ex.export.semanticHash=semanticHash(ex);const bytes=packageExchange(ex,new Map());preflightPackage(bytes);const path=join(root,`synthetic-w13-${area}-${count}.nfai-exchange`);await writeFile(path,bytes,{flag:'wx'});
 const staged=await invoke(port,area,'stage_preflight_selected_exchange',{selectedPath:path});const imported=await invoke(port,area,'import_staged_exchange_as_new_workspace',{args:{stagingId:staged.stagingId,workspaceTitle:`合成W13 ${area} ${count}`}});
 result.areas[area].libraries.push({id:imported.summary.id,count,hash:hash(imported.exchange)});
 }
 const first=await query(port,area,'all');assert.equal(first.totalCount,3000);assert.equal(first.textCount,2000);result.areas[area].firstPage=first;assert.ok(first.totalCount>=1100);result.areas[area].expected=first.totalCount;result.areas[area].textExpected=first.totalCount;
 await complete(port,area,'all',first.totalCount);await complete(port,area,'text',first.totalCount);await complete(port,area,'file',0);check(area+' complete native pages and area isolation',true);
 }
 await installProjectionAccess(port);
 for(const area of areas){await selectLibraryProjection(port,area,result.areas[area].libraries[0].id);await openSearch(port,'searchneedle');await until(()=>evaluate(port,`!globalThis.__nanfengDesktopWorkState.searchLoading&&globalThis.__nanfengDesktopWorkState.searchPage.totalCount===${result.areas[area].expected}`),'typed query total');await delay(500);await fullUi(port,area,'all',result.areas[area].expected);await fullUi(port,area,'text',result.areas[area].textExpected);
 const hit=await evaluate(port,'globalThis.__nanfengDesktopWorkState.searchResults.find(h=>h.messageId)');assert.ok(hit);
 assert.equal(await click(port,`[data-action="open-search-result"][data-entry-id="${hit.entryId}"]`),true);
 await until(()=>evaluate(port,`globalThis.__nanfengDesktopWorkState.selectedConversationId===${JSON.stringify(hit.conversationId)}&&globalThis.__nanfengDesktopWorkState.searchAnchorMessageId===${JSON.stringify(hit.messageId)}&&Boolean(document.querySelector('.chat-message[data-message-id="${hit.messageId}"]'))`),'exact message navigation');check(area+' actual result message locator',true);
 const newTitle='renamedneedle '+area;
 assert.equal(await click(port,`[data-action="open-conversation-rename"][data-id="${hit.conversationId}"]`),true);
 await until(()=>evaluate(port,'Boolean(document.querySelector("#conversation-rename"))'),'rename dialog');
 await evaluate(port,`(()=>{const i=document.querySelector('#conversation-rename');i.value=${JSON.stringify(newTitle)};i.dispatchEvent(new Event('input',{bubbles:true}));return true})()`);
 assert.equal(await click(port,'[data-action="save-conversation-rename"]'),true);
 await until(()=>evaluate(port,`!globalThis.__nanfengDesktopWorkState.dialog&&globalThis.__nanfengDesktopWorkState.current.exchange.conversations.find(c=>c.id===${JSON.stringify(hit.conversationId)})?.title===${JSON.stringify(newTitle)}`),'saved title readback');
 for(const item of result.areas[area].libraries)if(item.id===hit.workspaceId){const updated=await invoke(port,area,'read_desktop_workspace',{workspaceId:item.id});item.hash=hash(updated.exchange);}
 assert.equal(await click(port,'[data-action="return-to-search"]'),true);
 await until(()=>evaluate(port,'Boolean(document.querySelector("#full-search-input"))'),'return search');
 await evaluate(port,`(()=>{const i=document.querySelector('#full-search-input');i.value='renamedneedle';i.dispatchEvent(new Event('input',{bubbles:true}));return true})()`);
 await until(()=>evaluate(port,`!globalThis.__nanfengDesktopWorkState.searchLoading&&globalThis.__nanfengDesktopWorkState.searchResults.length===1&&globalThis.__nanfengDesktopWorkState.searchResults[0].conversationId===${JSON.stringify(hit.conversationId)}&&document.querySelectorAll('.desktop-search-text-card').length===1`),'renamed result refresh');
 result.areas[area].rename={conversationId:hit.conversationId,workspaceId:hit.workspaceId,title:newTitle};result.areas[area].expected--;result.areas[area].textExpected--;check(area+' visible rename and refreshed unique result',true);
 await fullUi(port,area,'file',0);await click(port,'[data-action="close-search"]');}
 await conserve(port);result.beforeCloseSqlite=sqlite();await normalClose(port);
 port=await start(true);await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke)'),'cold bridge');
 await conserve(port);for(const area of areas){await complete(port,area,'all',result.areas[area].expected);await complete(port,area,'text',result.areas[area].textExpected);}for(const area of areas){const renamed=await query(port,area,'text',0,'renamedneedle');assert.equal(renamed.totalCount,1);assert.equal(renamed.hits[0].conversationId,result.areas[area].rename.conversationId);assert.equal(renamed.hits[0].title,result.areas[area].rename.title);}check('cold complete projections and search pages with renamed results',true);
 await normalClose(port);result.finalSqlite=sqlite(true);result.pass=true;
}catch(error){result.error=String(error.stack||error);process.exitCode=1;}
finally{if(launch){result.forcedCleanup=true;try{stop();}catch(error){result.cleanupError=String(error);}}result.pids=pids;if(root)await writeFile(join(root,'large-search-evidence.json'),JSON.stringify(result,null,2));}
console.log(JSON.stringify({root,pass:result.pass,error:result.error}));
