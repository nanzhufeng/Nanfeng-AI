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
const mode='large-attachment-search';
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

const term='attachmentneedle';
const formats=[['txt','text/plain','file'],['pdf','application/pdf','file'],['zip','application/zip','file'],['docx','application/vnd.openxmlformats-officedocument.wordprocessingml.document','file'],['xlsx','application/vnd.openxmlformats-officedocument.spreadsheetml.sheet','file'],['pptx','application/vnd.openxmlformats-officedocument.presentationml.presentation','file'],['png','image/png','image'],['mp4','video/mp4','video'],['wav','audio/wav','audio']];
let requestId=900000;
async function query(port,area,category='all',offset=0,fileType='all'){
 return invoke(port,area,'query_desktop_local_index',{args:{query:term,category,sortMode:'default',fileType,offset,recordHistory:false,historyWorkspaceId:null},requestId:++requestId});
}
const tuple=h=>[h.workspaceId,h.conversationId,h.messageId,h.attachmentId].join('|');
async function complete(port,area,category,expected,fileType='all'){
 const hits=[];let pages=0;
 do{const page=await query(port,area,category,hits.length,fileType);assert.equal(page.totalCount,expected);hits.push(...page.hits);assert.ok(++pages<100);if(!page.hasMore)break;assert.ok(page.hits.length);}while(true);
 assert.equal(hits.length,expected);assert.equal(new Set(hits.map(h=>h.entryId)).size,expected);
 const wanted=result.areas[area].references.filter(r=>(category==='all'||r.category===category)&&(fileType==='all'||r.fileType===fileType));
 assert.deepEqual(hits.map(tuple).sort(),wanted.map(tuple).sort());
 result.areas[area].queries.push({category,fileType,count:hits.length,pages});return hits;
}
async function openSearch(port){
 await until(()=>evaluate(port,"(()=>{const i=document.querySelector('#chat-search');if(!i)return false;i.focus();return true})()"),'search entry');
 await until(()=>evaluate(port,'Boolean(document.querySelector("#full-search-input"))'),'fullscreen search');
 await evaluate(port,`(()=>{const i=document.querySelector('#full-search-input');i.value=${JSON.stringify(term)};i.dispatchEvent(new Event('input',{bubbles:true}));return true})()`);
 await until(()=>evaluate(port,`!globalThis.__nanfengDesktopWorkState.searchLoading&&globalThis.__nanfengDesktopWorkState.chatSearch===${JSON.stringify(term)}`),'search ready');
}
async function fullUi(port,area,category,expected,fileType='all'){
 assert.equal(await click(port,`[data-action="set-search-category"][data-category="${category}"]`),true);
 await evaluate(port,'new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve)))');
 if(fileType!=='all'){
 await until(()=>click(port,'[data-action="toggle-search-file-types"]'),'file type menu');
 await until(()=>click(port,`[data-action="set-search-file-type"][data-file-type="${fileType}"]`),'file type select');
 }
 await until(()=>evaluate(port,`globalThis.__nanfengDesktopWorkState.searchCategory===${JSON.stringify(category)}&&!globalThis.__nanfengDesktopWorkState.searchLoading&&globalThis.__nanfengDesktopWorkState.searchPage.totalCount===${expected}&&( ${JSON.stringify(category)}==="all"||globalThis.__nanfengDesktopWorkState.searchResults.every(h=>h.contentKind===${JSON.stringify(category.toUpperCase())}))`),'category ready');
 let pages=1;
 while(await evaluate(port,'Boolean(globalThis.__nanfengDesktopWorkState.searchPage.hasMore)')){
 const before=await evaluate(port,'globalThis.__nanfengDesktopWorkState.searchResults.length');
 await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=load-more-search-results]:not([disabled])"))'),'page button');
 assert.equal(await click(port,'[data-action="load-more-search-results"]'),true);
 await until(()=>evaluate(port,`!globalThis.__nanfengDesktopWorkState.searchLoadingMore&&globalThis.__nanfengDesktopWorkState.searchResults.length>${before}`),'page appended');assert.ok(++pages<100);
 }
 const state=await evaluate(port,'({count:globalThis.__nanfengDesktopWorkState.searchResults.length,total:globalThis.__nanfengDesktopWorkState.searchPage.totalCount,hits:globalThis.__nanfengDesktopWorkState.searchResults,cards:document.querySelectorAll(".desktop-search-attachment-card").length,marks:document.querySelectorAll(".desktop-search-attachment-card mark").length})');
 assert.equal(state.count,expected);assert.equal(state.total,expected);assert.equal(state.cards,expected);assert.equal(new Set(state.hits.map(h=>h.entryId)).size,expected);assert.ok(state.marks>0);
 const wanted=result.areas[area].references.filter(r=>(category==='all'||r.category===category)&&(fileType==='all'||r.fileType===fileType));assert.equal(hash(state.hits.map(tuple).sort()),hash(wanted.map(tuple).sort()),'visible occurrence tuple hash');
 result.areas[area].ui.push({category,fileType,count:state.count,cards:state.cards,marks:state.marks,pages});check(`${area} ${category}/${fileType} actual complete UI`,true);
 console.log(JSON.stringify({phase:'UI_COMPLETE',area,category,fileType,count:expected,pages}));return state.hits;
}
async function preview(port,area,format,cold=false){
 // Read one precise reference from the actual search projection. Lazy native
 // previews are checked only for the visible card; no fabricated DOM/payload.
 const h=await evaluate(port,`globalThis.__nanfengDesktopWorkState.searchResults.find(h=>h.displayName?.endsWith('.${format}'))`);
 assert.ok(h,format+' hit');const card=`[data-search-entry-id="${h.entryId}"]`;
 const expectedText={txt:'W13 nonempty TXT body',docx:'W13 nonempty DOCX body',xlsx:'W13 nonempty XLSX body',pptx:'W13 nonempty PPTX body'}[format];
 await evaluate(port,`document.querySelector(${JSON.stringify(card)})?.scrollIntoView({block:'center'})`);
 if(expectedText)await until(()=>evaluate(port,`document.querySelector(${JSON.stringify(card+' pre')})?.textContent.includes(${JSON.stringify(expectedText)})`),'actual card body '+format);
 if(['png','pdf','mp4'].includes(format))await until(()=>evaluate(port,`(()=>{const img=document.querySelector(${JSON.stringify(card+' img')});return Boolean(img?.complete&&img.naturalWidth>0)})()`),'actual thumbnail '+format);
 assert.equal(await click(port,card+' [data-action="open-search-attachment"]'),true);
 let closeKind='text',detail={};
 if(format==='png'){
 closeKind='image';await until(()=>evaluate(port,'(()=>{const img=document.querySelector("[data-image-preview-content]");return Boolean(img?.complete&&img.naturalWidth===128&&img.naturalHeight===96)})()'),'real image pixels');detail={width:128,height:96};
 }else if(format==='pdf'){
 closeKind='pdf';await until(()=>evaluate(port,'(()=>{const img=document.querySelector(".pdf-preview-frame");return Boolean(img?.complete&&img.naturalWidth>0&&document.querySelector(".pdf-preview-tools")?.textContent.includes("1 / 1"))})()'),'native PDF raster');detail={pageCount:1};
 }else if(format==='mp4'||format==='wav'){
 closeKind=format==='mp4'?'video':'audio';await until(()=>evaluate(port,`Boolean(document.querySelector('[data-${closeKind}-preview]')?.readyState>=2)`),'real decode '+format);
 detail=await evaluate(port,`(async()=>{const v=document.querySelector('[data-${closeKind}-preview]');await v.play();v.pause();await new Promise(done=>{v.addEventListener('seeked',done,{once:true});v.currentTime=0.4});v.dispatchEvent(new Event('timeupdate'));return {width:v.videoWidth,height:v.videoHeight,duration:v.duration,position:v.currentTime,error:v.error?.code||null}})()`);assert.equal(detail.error,null);assert.ok(Math.abs(detail.position-0.4)<0.1);if(format==='mp4'){assert.equal(detail.width,128);assert.equal(detail.height,96);}
 }else if(format==='zip'){
 closeKind='archive';await until(()=>click(port,'[data-action="open-archive-directory"][data-archive-path="folder/"]'),'archive folder');
 await until(()=>click(port,'[data-action="open-archive-entry-preview"][data-archive-entry-path="folder/note.txt"]'),'archive entry');await until(()=>evaluate(port,'document.querySelector(".archive-text-preview pre")?.textContent==="W13 nonempty archive body"'),'archive nonempty body');detail={directory:true,text:true};
 }else{await until(()=>evaluate(port,`document.querySelector('.text-preview-surface .local-text-preview')?.textContent.includes(${JSON.stringify(expectedText)})`),'real preview body '+format);detail={body:true};}
 assert.equal(await click(port,`[data-action="close-${closeKind}-preview"]`),true);
 await until(()=>evaluate(port,'!document.querySelector(".attachment-preview-overlay")'),'preview closed');
 if(format==='mp4'||format==='wav'){const saved=await until(async()=>{const p=await invoke(port,area,`read_desktop_${closeKind}_preview`,{args:{workspaceId:h.workspaceId,attachmentId:h.attachmentId}});return p.positionMillis>=350&&p.positionMillis<=450?p:false;},'native saved position');detail.savedPositionMillis=saved.positionMillis;}
 // Locate the exact occurrence in its conversation and message.
 await evaluate(port,`document.querySelector(${JSON.stringify(card)})?.dispatchEvent(new MouseEvent('contextmenu',{bubbles:true,cancelable:true}))`);
 await until(()=>click(port,'[data-action="locate-search-attachment"]'),'locate menu');
 await until(()=>evaluate(port,`(()=>{const scope=document.querySelector('.chat-scroll[data-conversation-id="${h.conversationId}"]');const target=scope?.querySelector('.chat-message[data-message-id="${h.messageId}"] [data-attachment-id="${h.attachmentId}"]');return Boolean(target?.classList.contains('search-anchor-flash'))})()`),'precise attachment occurrence');
 assert.equal(await click(port,'[data-action="return-to-search"]'),true);
 await until(()=>evaluate(port,`Boolean(document.querySelector(${JSON.stringify(card)}))`),'return exact search card');
 result.areas[area].previews.push({format,cold,reference:tuple(h),detail});console.log(JSON.stringify({phase:'PREVIEW_COMPLETE',area,format,cold}));check(`${area} ${format} actual preview close and occurrence ${cold?'cold':''}`,true);
}
async function conserve(port){for(const area of areas){const item=result.areas[area];const data=await invoke(port,area,'read_desktop_workspace',{workspaceId:item.workspaceId});assert.equal(hash(data.exchange),item.exchangeHash);}}
function sqlite(closed=false){const data=JSON.parse(execFileSync('python',[join(desktopRoot,'scripts/check-stability-synthetic-sqlite.py'),root,...(closed?['--closed-browser']:[])],{encoding:'utf8',timeout:60000}));assert.ok(data.allOk);return {databases:data.databases.length,allOk:data.allOk};}
async function zipStage(port,lib,path){
 const args={args:{workspaceId:lib.workspaceId,provider:lib.provider,selectedPath:path}};
 await evaluate(port,`(()=>{globalThis.__w14zip={done:false};window.__TAURI__.core.invoke('stage_p6k_zip_import_selected',${JSON.stringify(args)},${JSON.stringify({headers:{'x-nanfeng-data-area':lib.area}})}).then(value=>{globalThis.__w14zip={done:true,value}}).catch(error=>{globalThis.__w14zip={done:true,error:String(error)}});return true})()`);
 let task=null,lastProgress='',lastChange=performance.now();const started=performance.now();
 while(!task){
  const state=await evaluate(port,'globalThis.__w14zip');
  if(state.done){if(state.error)throw new Error(state.error);task=state.value;break;}
  const db=join(root,lib.area==='CHAT'?'':'work-area','workspace.sqlite3');
  const progress=execFileSync('python',['-c',"import sqlite3,sys,json; c=sqlite3.connect('file:'+sys.argv[1].replace('\\\\','/')+'?mode=ro',uri=True,timeout=3); print(json.dumps(c.execute('select status,count(*) from p6k_zip_import_items group by status').fetchall())); c.close()",db],{encoding:'utf8',timeout:10000}).trim();
  if(progress!==lastProgress){lastProgress=progress;lastChange=performance.now();result.zipProgress=JSON.parse(progress);await writeFile(join(root,'zip-live-progress.json'),JSON.stringify({area:lib.area,provider:lib.provider,elapsedMilliseconds:performance.now()-started,progress:result.zipProgress}));}
  assert.ok(performance.now()-lastChange<30000,'ZIP committed item progress stalled for 30 seconds');
  assert.ok(performance.now()-started<900000,'ZIP bounded observation exceeded 15 minutes');
  await delay(1000);
 }
 assert.equal(task.status,'COMPLETED');assert.equal(task.items.length,lib.expectedCount);assert.equal(task.items.filter(x=>x.status==='COMMITTED').length+task.items.filter(x=>x.status==='SKIPPED').length,lib.expectedCount);
 return task;
}
async function validateImported(port,area,input,initial=false){
 const lib=result.areas[area],data=await invoke(port,area,'read_desktop_workspace',{workspaceId:lib.workspaceId});
 const rows=data.exchange.conversations.filter(c=>c.id!==lib.seedId);assert.equal(rows.length,576);
 const references=[];
 for(const source of input){const row=rows.find(c=>c.title===source.title);assert.ok(row,source.title);assert.equal(row.messages.length,2);
  for(const [index,message] of row.messages.entries()){
   const sourceMessage=Object.values(source.mapping)[index].message;
   assert.equal(message.blocks.filter(b=>b.kind==='TEXT').map(b=>b.text).join('\n'),sourceMessage.content.parts[0]);
   const blocks=message.blocks.filter(b=>b.kind==='ASSET_REF');assert.equal(blocks.length,1);
   const asset=blocks[0].asset,f=result.formats.find(f=>asset.displayName.endsWith('.'+f.ext));assert.ok(f);
   assert.equal(asset.sha256,f.sha256);assert.equal(asset.byteCount,f.byteCount);assert.equal(asset.mimeType,f.mime);
   assert.equal(asset.displayName,sourceMessage.metadata.attachments[0].name);
   references.push({workspaceId:lib.workspaceId,conversationId:row.id,messageId:message.id,attachmentId:asset.id,category:f.category,fileType:f.fileType});
  }
 }
 assert.equal(references.length,1152);assert.equal(new Set(references.map(tuple)).size,1152);
 if(initial){lib.references=references;lib.exchangeHash=hash(data.exchange);lib.conversationIds=rows.map(c=>c.id);await writeFile(join(root,'imported-'+area+'.json'),JSON.stringify(data.exchange),{flag:'wx'});}
 else{assert.equal(hash(data.exchange),lib.exchangeHash);assert.deepEqual(references,lib.references);assert.deepEqual(rows.map(c=>c.id),lib.conversationIds);}
 check(area+' full input text official message ownership and private asset identity',true);
 return data;
}
async function zipInput(path,rows){
 await writeFile(path+'.json',JSON.stringify(rows),{flag:'wx'});
 execFileSync('python',['-c',"import json,zipfile,sys,pathlib; rows=json.load(open(sys.argv[1],encoding='utf-8')); base=pathlib.Path(sys.argv[3]); z=zipfile.ZipFile(sys.argv[2],'x',compression=zipfile.ZIP_STORED); [z.writestr('conversations-'+str(i//100+1)+'.json',json.dumps(rows[i:i+100],ensure_ascii=False)) for i in range(0,len(rows),100)]; [z.write(base/('fixture.'+ext),'synthetic-'+ext+'.dat') for ext in ['txt','pdf','zip','docx','xlsx','pptx','png','mp4','wav']]; z.close()",path+'.json',path,join(root,'synthetic-attachments')],{timeout:30000});
}
const progress=async stage=>{result.stage=stage;await writeFile(join(root,'zip-attachment-progress.json'),JSON.stringify(result,null,2));console.log(JSON.stringify({stage,root,checks:result.checks.length}));};
try{
 let port=await start();result.root=root;
 await until(()=>evaluate(port,'Boolean(document.querySelector("#chat-composer")&&globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'),'ready');
 const fixtureDir=join(root,'synthetic-attachments');await mkdir(fixtureDir);
 execFileSync('python',[join(desktopRoot,'scripts/create-large-search-attachments-synthetic.py'),fixtureDir],{encoding:'utf8',timeout:30000});
 const ffmpeg=join(receipt.mediaTools,'bin/ffmpeg.exe');
 execFileSync(ffmpeg,['-hide_banner','-loglevel','error','-f','lavfi','-i','color=c=green:s=128x96:d=1','-frames:v','1',join(fixtureDir,'fixture.png')],{timeout:30000});
 execFileSync(ffmpeg,['-hide_banner','-loglevel','error','-f','lavfi','-i','color=c=blue:s=128x96:d=1','-c:v','libopenh264','-pix_fmt','yuv420p',join(fixtureDir,'fixture.mp4')],{timeout:30000});
 execFileSync(ffmpeg,['-hide_banner','-loglevel','error','-f','lavfi','-i','sine=frequency=440:sample_rate=48000','-t','1','-c:a','pcm_s16le',join(fixtureDir,'fixture.wav')],{timeout:30000});
 result.formats=[];
 for(const [ext,mime,category] of formats){const bytes=await readFile(join(fixtureDir,'fixture.'+ext));result.formats.push({ext,mime,category,byteCount:bytes.length,sha256:sha(bytes),fileType:['xlsx','pptx','png','mp4','wav'].includes(ext)?'other':ext});}
 const {packageExchange,semanticHash}=await import('../../protocol/scripts/exchange-lib.mjs');
 const original=JSON.parse(await readFile(join(desktopRoot,'../protocol/fixtures/nfai.exchange.v1.golden.json'),'utf8'));
 for(const area of areas){
  await invoke(port,area,'ensure_desktop_local_workspace');
  const ex=structuredClone(original);ex.export.id='w14-zip-attachment-seed-'+area.toLowerCase();
  for(const c of ex.conversations)for(const m of c.messages)m.blocks=[{kind:'TEXT',ordinal:0,text:'W14 ZIP seed'}];ex.export.semanticHash=semanticHash(ex);
  const seed=join(root,area+'-seed.nfai-exchange');await writeFile(seed,packageExchange(ex,new Map()),{flag:'wx'});
  const pre=await invoke(port,area,'stage_preflight_selected_exchange',{selectedPath:seed});
  const imported=await invoke(port,area,'import_staged_exchange_as_new_workspace',{args:{stagingId:pre.stagingId,workspaceTitle:'W14 非空附件ZIP '+area}});
  const input=[];
  for(const f of result.formats)for(let i=0;i<64;i++){
   const id=`w14-${area}-${f.ext}-${i}`,u=id+'-u',a=id+'-a',attachment={id:'synthetic-'+f.ext,name:`${term}-${area}.${f.ext}`};
   const message=(role,mid)=>({id:mid,author:{role},content:{parts:[`${id} ${role} 正文\nUnicode Ω 😀\n代码 \`const n = ${i};\`\n末行`,{asset_pointer:'file-service://synthetic-'+f.ext}]},metadata:{attachments:[attachment]}});
   input.push({id,title:`历史 ${area} ${f.ext} ${i} 中文 😀`,create_time:1,update_time:2,mapping:{[u]:{parent:null,children:[a],message:message('user',u)},[a]:{parent:u,children:[],message:message('assistant',a)}}});
  }
  const lib={area,provider:'CHATGPT',expectedCount:576,workspaceId:imported.summary.id,seedId:imported.exchange.conversations[0].id,input:join(root,area+'-576-attachments.zip'),queries:[],ui:[],previews:[],taskIds:[]};result.areas[area]=lib;
  await zipInput(lib.input,input);
  const task=await timed(area+' official ZIP576',()=>zipStage(port,lib,lib.input));lib.taskIds.push(task.id);lib.ids=task.items.map(i=>i.conversationId);
  assert.equal(new Set(lib.ids).size,576);
  assert.deepEqual(task.officialAttachmentSummary,{referencedOccurrences:1152,recoverableUniqueAssets:9,missingUniqueAssets:0,fallbackNameUniqueAssets:0,restoredUniqueAssets:9,restoredOccurrences:1152});
  assert.equal(task.manualAssets.length,9);assert.ok(task.manualAssets.every(a=>a.status==='AUTO_LINKED'));
  await validateImported(port,area,input,true);await progress(area+' official ZIP imported');
  const replay=await zipStage(port,lib,lib.input);lib.taskIds.push(replay.id);assert.deepEqual(replay.items.map(i=>i.conversationId),lib.ids);
  await validateImported(port,area,input);check(area+' official ZIP replay conserved IDs text and occurrences',true);
  lib.latestTask=await invoke(port,area,'read_latest_p6k_zip_import_task',{workspaceId:lib.workspaceId});
  await invoke(port,area,'export_desktop_workspace_to_selected_path',{workspaceId:lib.workspaceId,selectedPath:join(root,area+'-complete.nfai-exchange')});
  for(const [category,count] of [['all',1152],['file',768],['image',128],['video',128],['audio',128]])await complete(port,area,category,count);
  check(area+' full native classified ownership tuples',true);await progress(area+' replay and export complete');
 }
 await installProjectionAccess(port);
 for(const area of areas){await selectLibraryProjection(port,area,result.areas[area].workspaceId);await openSearch(port);await fullUi(port,area,'all',1152);
  for(const category of ['image','video','audio','file'])await fullUi(port,area,category,category==='file'?768:128);
  await fullUi(port,area,'all',1152);for(const [format] of formats)await preview(port,area,format);await click(port,'[data-action="close-search"]');}
 await conserve(port);result.beforeCloseSqlite=sqlite();await normalClose(port);
 port=await start(true);await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke)'),'cold bridge');await conserve(port);
 for(const area of areas){const lib=result.areas[area],input=JSON.parse(await readFile(lib.input+'.json','utf8'));await validateImported(port,area,input);assert.deepEqual(await invoke(port,area,'read_latest_p6k_zip_import_task',{workspaceId:lib.workspaceId}),lib.latestTask);delete lib.latestTask;check(area+' cold exact task receipt',true);}
 await installProjectionAccess(port);
 for(const area of areas){for(const [category,count] of [['all',1152],['file',768],['image',128],['video',128],['audio',128]])await complete(port,area,category,count);
  await selectLibraryProjection(port,area,result.areas[area].workspaceId);await openSearch(port);await fullUi(port,area,'all',1152);for(const [format] of formats)await preview(port,area,format,true);await click(port,'[data-action="close-search"]');}
 await conserve(port);await normalClose(port);result.finalSqlite=sqlite(true);result.pass=true;
}catch(error){result.error=String(error.stack||error);process.exitCode=1;}
finally{if(launch){result.forcedCleanup=true;try{stop();}catch(error){result.cleanupError=String(error);}}result.pids=pids;if(root)await writeFile(join(root,'large-attachment-zip-evidence.json'),JSON.stringify(result,null,2));}
console.log(JSON.stringify({root,pass:result.pass,error:result.error?.slice(0,1600)}));
