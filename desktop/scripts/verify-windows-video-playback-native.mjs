import assert from 'node:assert/strict';
import { createHash, randomUUID } from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

if (process.platform !== 'win32') throw new Error('仅在 Windows 隔离候选上验收。');
const desktopRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const rootParent = resolve(process.argv[2] || 'D:\\CodexTemp');
const videoCardMode = process.argv.includes('--video-card');
const audioCardMode = process.argv.includes('--audio-card');
const pdfCardMode = process.argv.includes('--pdf-card');
const archiveCardMode = process.argv.includes('--archive-card');
assert.ok([videoCardMode,audioCardMode,pdfCardMode,archiveCardMode].filter(Boolean).length<=1, '附件格式主链串行验收');
const visibleDeleteMode = process.argv.includes('--visible-delete');
const visibleAnchorMode = process.argv.includes('--visible-anchor');
const verifyInstanceGuard = process.argv.includes('--verify-single-instance-guard');
const keyboardUndoMode = process.argv.includes('--keyboard-undo');
const historyTitleMode = process.argv.includes('--history-title');
const imeSearchMode = process.argv.includes('--ime-search');
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
  return port;
};
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
  launch = await startProcess(args);
  assert.equal(launch.sha256, receipt.sha256);
  assert.equal(resolve(launch.executable), runningExecutable);
  root = resolve(launch.root);
  assert.ok(root.startsWith(rootParent + sep));
  const marker = JSON.parse(await readFile(join(root, '.nanfeng-windows-acceptance.json'), 'utf8'));
  assert.equal(marker.identifier, receipt.identifier);
  return port;
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

let result = {checks:[], candidateSha256:receipt.sha256, startupMode:'diagnostic', realService:false};
const check=(name,value)=>{assert.ok(value,name);result.checks.push({name,pass:true});};
async function normalClose(port){
 const pid=Number(launch.pid);
 const info=JSON.parse(ps(`$p=Get-Process -Id ${pid} -ErrorAction Stop; @{pid=$p.Id;responding=$p.Responding;title=$p.MainWindowTitle;accepted=$p.CloseMainWindow()}|ConvertTo-Json -Compress`));
 check('native window responding',info.responding);check('real native title present',Boolean(info.title));check('CloseMainWindow accepted',info.accepted);
 try { await until(()=>ps(`@(Get-CimInstance Win32_Process -Filter "ProcessId=${pid}").Count`)==='0','正常Close进程退出'); }
 catch(error) { result.closeFailure={info,process:ps(`Get-CimInstance Win32_Process -Filter "ProcessId=${pid}" | Select-Object ProcessId,ExecutablePath | ConvertTo-Json -Compress`),ui:await evaluate(port,'document.body.innerText.slice(-2200)')}; throw error; }
 result.checks.push({name:'normal Close exited',pass:true,detail:info});pids.push(pid);launch=null;
}
async function searchOpen(port){
 await until(()=>evaluate(port,'Boolean(document.querySelector("#chat-search"))'),'搜索入口');
 await evaluate(port,`(()=>{const i=document.querySelector('#chat-search');i.value='w10-playback';i.dispatchEvent(new Event('input',{bubbles:true}));i.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',bubbles:true}));return true})()`);
 await until(()=>evaluate(port,`(()=>{const i=document.querySelector('.desktop-search-attachment-card img');return Boolean(i?.complete&&i.naturalWidth>0)})()`),'真实缩略图');
 check('search video card click',await click(port,'[data-search-entry-id] [data-action="open-search-attachment"]'));
 await until(()=>evaluate(port,'document.querySelector("[data-video-preview]")?.readyState>=2'),'视频解码');
}
try {
 let port=await start();result.root=root;
 await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke)'),'原生IPC');
 const workspaceId=(await invoke(port,'CHAT','ensure_desktop_local_workspace')).summary.id;
 const workId=(await invoke(port,'WORK','ensure_desktop_local_workspace')).summary.id;
 const workBefore=await invoke(port,'WORK','read_desktop_workspace',{workspaceId:workId});
 const inputs=join(root,'synthetic-w10-inputs');await mkdir(inputs,{recursive:true});
 const selectedPath=join(inputs,'w10-playback.mp4');
 execFileSync(join(receipt.mediaTools,'bin','ffmpeg.exe'),['-hide_banner','-loglevel','error','-f','lavfi','-i','testsrc2=s=320x180:r=24:d=8','-c:v','libopenh264','-pix_fmt','yuv420p',selectedPath],{timeout:30000});
 const attachment=await invoke(port,'CHAT','import_desktop_conversation_attachment',{args:{workspaceId,selectedPath}});
 await invoke(port,'CHAT','mutate_desktop_domain',{args:{intentId:randomUUID(),workspaceId,entity:'conversation',action:'create',objectId:null,expectedRevision:null,fields:{title:'W10 合成视频播放',projectId:null,firstMessage:'合成视频',attachmentIds:[attachment.id]}}});
 const chatBefore=await invoke(port,'CHAT','read_desktop_workspace',{workspaceId});
 await searchOpen(port);
 const motion=await evaluate(port,`(async()=>{const v=document.querySelector('[data-video-preview]');await v.play();const before=v.currentTime;await new Promise(r=>setTimeout(r,700));v.pause();const quality=v.getVideoPlaybackQuality();return {before,after:v.currentTime,width:v.videoWidth,height:v.videoHeight,duration:v.duration,error:v.error?.code||null,decodedFrames:quality.totalVideoFrames,source:v.currentSrc.split('/').slice(0,3).join('/')}})()`);
 await writeFile(join(root,'w10-playing.png'),Buffer.from((await cdp(port,'Page.captureScreenshot',{format:'png'})).data,'base64'));result.motion=motion;check('actual playback time advanced',motion.after-motion.before>0.3);check('decoded dimensions',motion.width===320&&motion.height===180);check('actual decoded frames',motion.decodedFrames>5);check('decode has no error',motion.error===null);
 await evaluate(port,`(()=>{const r=document.querySelector('[data-video-preview-range]');r.value='3250';r.dispatchEvent(new Event('input',{bubbles:true}));return true})()`);
 const seek=await until(()=>evaluate(port,`(()=>{const v=document.querySelector('[data-video-preview]');return !v.seeking&&Math.abs(v.currentTime-3.25)<0.1?{position:v.currentTime,range:document.querySelector('[data-video-preview-range]').value}:false})()`),'进度控件seek');result.seek=seek;
 check('seek through existing control',Math.abs(seek.position-3.25)<0.1);
 await click(port,'[data-action="close-video-preview"]');
 await until(()=>evaluate(port,'!document.querySelector("[data-video-preview]")'),'关闭播放器');
 const saved=await until(async()=>{const p=await invoke(port,'CHAT','read_desktop_video_preview',{args:{workspaceId,attachmentId:attachment.id}});return Math.abs(p.positionMillis-3250)<100?p:false;},'原生保存位置');
 result.savedPositionMillis=saved.positionMillis;
 assert.deepEqual((await invoke(port,'CHAT','read_desktop_workspace',{workspaceId})).exchange,chatBefore.exchange);check('CHAT exchange unchanged',true);
 assert.deepEqual(await invoke(port,'WORK','read_desktop_workspace',{workspaceId:workId}),workBefore);check('WORK projection unchanged',true);
 await normalClose(port);port=await start(true);
 await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke)'),'cold IPC');
 const cold=await invoke(port,'CHAT','read_desktop_video_preview',{args:{workspaceId,attachmentId:attachment.id}});check('cold owner position exact',cold.positionMillis===saved.positionMillis);
 await searchOpen(port);
 const reopened=await evaluate(port,`(()=>{const v=document.querySelector('[data-video-preview]');v.pause();return {position:v.currentTime,width:v.videoWidth,error:v.error?.code||null}})()`);result.reopened=reopened;
 check('cold player resumes saved position',reopened.position>=3.15&&reopened.position<4.3);check('cold actual video decoded',reopened.width===320&&reopened.error===null);
 await evaluate(port,`(async()=>{const v=document.querySelector('[data-video-preview]');globalThis.__w10Start=v.currentTime;await v.play();})()`);
 await until(()=>evaluate(port,`document.querySelector('[data-video-preview]').currentTime-globalThis.__w10Start>0.3`),'cold实际播放推进');check('cold playback advanced',true);
 await click(port,'[data-action="close-video-preview"]');await until(()=>evaluate(port,'!document.querySelector("[data-video-preview]")'),'cold关闭');
 assert.deepEqual((await invoke(port,'CHAT','read_desktop_workspace',{workspaceId})).exchange,chatBefore.exchange);check('cold CHAT exchange unchanged',true);
 assert.deepEqual(await invoke(port,'WORK','read_desktop_workspace',{workspaceId:workId}),workBefore);check('cold WORK unchanged',true);
 await normalClose(port);result.pass=true;
} catch(error){result.error=String(error.stack||error);process.exitCode=1;}
finally{if(launch){result.forcedCleanup=true;stop();}result.pids=pids;if(root)await writeFile(join(root,'w10-playback-evidence.json'),JSON.stringify(result,null,2));}
console.log(JSON.stringify({root,pass:result.pass,checks:result.checks.length,error:result.error}));
