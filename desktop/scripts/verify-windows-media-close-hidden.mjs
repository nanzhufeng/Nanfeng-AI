import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
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
assert.equal(parent, resolve(desktop, '../.runtime-qa/media-reader-close-20261017'));
const compare = false;assert.ok(process.execArgv.includes('--max-old-space-size=2048'));
const visible = false;
assert.ok(!(visible && compare));
const receipt = JSON.parse(await readFile(join(desktop, 'output/windows-acceptance-release/current.json'), 'utf8'));
assert.equal(receipt.identifier, 'com.nanzhufeng.ai.desktop.windowsacceptance');
assert.equal(createHash('sha256').update(await readFile(receipt.executable)).digest('hex').toUpperCase(), receipt.sha256);
assert.equal(receipt.buildProfile, 'release');
assert.equal(receipt.sha256, process.env.NANFENG_AI_EXPECTED_CANDIDATE_SHA256);
const executable = resolve(desktop, 'output/windows-acceptance-binaries', receipt.sha256, 'nanfeng-ai-desktop-spike.exe');
const helper = join(desktop, 'scripts/windows-acceptance-cdp-eval.mjs');
let requests = 0;let closedConnections=0;const responses=[];const closes=[];const partialText='SYNTHETIC_STREAM_STOP_PARTIAL 完整保留片段';
const provider = http.createServer(async (request, response) => {
 for await(const _ of request){} requests++;
 response.writeHead(200,{'Content-Type':'text/event-stream'});
 response.end(`data: ${JSON.stringify({model:'fixture-gallery',choices:[{delta:{content:'Synthetic gallery saved'},finish_reason:'stop'}],usage:{prompt_tokens:6,completion_tokens:2}})}\n\ndata: [DONE]\n\n`);
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

const result={pass:false,candidateSha256:receipt.sha256,backgroundOnly:true,realService:false,privateDesktopGuard};
const python='C:/Users/Administrator/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe';
async function click(selector){assert.equal(await evaluate(`(()=>{const n=document.querySelector(${JSON.stringify(selector)});if(!n||n.disabled)return false;n.click();return true})()`),true,selector);}
const stateKey={pdf:'pdfPreview',text:'textPreview',archive:'archivePreview',audio:'audioPreview',boundary:'previewBoundary'};
const actionFor=kind=>kind==='boundary'?'open-preview-boundary':`open-${kind}-preview`;
const closeFor=kind=>kind==='boundary'?'close-preview-boundary':`close-${kind}-preview`;
async function close(kind){await click(`[data-action=${closeFor(kind)}]`);await until(()=>evaluate(`globalThis.__nanfengDesktopWorkState.${stateKey[kind]}===null`),`${kind} closed`);}
async function loadingClose(kind,id){
 const open=`[data-action=${actionFor(kind)}][data-attachment-id="${id}"]`;
 const observed=await evaluate(`new Promise((done,fail)=>{const s=globalThis.__nanfengDesktopWorkState;const timer=setTimeout(()=>{o.disconnect();fail(new Error('loading close not observed'))},5000);const o=new MutationObserver(()=>{const p=s.${stateKey[kind]},b=document.querySelector('[data-action=${closeFor(kind)}]');if(!p?.loading||!b)return;o.disconnect();clearTimeout(timer);window.__closedReaderRequest=p.request;b.click();done({loading:true,closed:s.${stateKey[kind]}===null});});o.observe(document.querySelector('#app'),{childList:true,subtree:true});document.querySelector(${JSON.stringify(open)}).click();})`);
 assert.deepEqual(observed,{loading:true,closed:true});
 const requestId=await until(()=>evaluate('window.__closedReaderRequest?.id'),'closed reader native request allocated');
 const command=kind==='pdf'?'read_desktop_pdf_preview':'read_desktop_archive_preview';
 const rejected=await evaluate(`window.__TAURI__.core.invoke(${JSON.stringify(command)},${JSON.stringify({requestId,args:{workspaceId:result.workspaceId,attachmentId:id}})},{headers:{'x-nanfeng-data-area':'CHAT'}}).then(()=>({accepted:true}),error=>({error:String(error)}))`);
 assert.match(rejected.error||'',/预览请求已取消或已被读取/);assert.equal(await evaluate(`globalThis.__nanfengDesktopWorkState.${stateKey[kind]}===null`),true);
 return {closedWhileLoading:true,requestId,cancelledRequestRejected:rejected.error};
}
try {
 await start();result.runtimeInfo=await invoke('read_desktop_runtime_info');assert.equal(result.runtimeInfo.buildProfile,'release');
 result.workspaceId=(await invoke('ensure_desktop_local_workspace')).summary.id;const workspaceId=result.workspaceId;
 const fixtureCode=String.raw`from pathlib import Path
import sys,wave,zipfile
root=Path(sys.argv[1]);assert (root/'.nanfeng-windows-acceptance.json').is_file()
objects=[b'<< /Type /Catalog /Pages 2 0 R >>',b'<< /Type /Pages /Kids [3 0 R 5 0 R] /Count 2 >>',b'<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 200] /Resources << /Font << /F1 7 0 R >> >> /Contents 4 0 R >>',None,b'<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 200] /Resources << /Font << /F1 7 0 R >> >> /Contents 6 0 R >>',None,b'<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>']
for index,text in [(3,b'FIRST SYNTHETIC PAGE'),(5,b'SECOND SYNTHETIC PAGE')]:
 stream=b'BT /F1 14 Tf 20 100 Td ('+text+b') Tj ET';objects[index]=b'<< /Length '+str(len(stream)).encode()+b' >>\nstream\n'+stream+b'\nendstream'
data=bytearray(b'%PDF-1.4\n');offsets=[0]
for n,obj in enumerate(objects,1):offsets.append(len(data));data+=str(n).encode()+b' 0 obj\n'+obj+b'\nendobj\n'
xref=len(data);data+=b'xref\n0 8\n0000000000 65535 f \n'
for offset in offsets[1:]:data+=f'{offset:010d} 00000 n \n'.encode()
data+=b'trailer\n<< /Size 8 /Root 1 0 R >>\nstartxref\n'+str(xref).encode()+b'\n%%EOF\n';(root/'synthetic-two-pages.pdf').write_bytes(bytes(data))
(root/'synthetic-reader.txt').write_text('Synthetic reader close text. No user data.',encoding='utf8')
(root/'synthetic-boundary.bin').write_bytes(b'NF_SYNTHETIC_UNSUPPORTED_BINARY\x00\x01')
with zipfile.ZipFile(root/'synthetic-reader.zip','w',zipfile.ZIP_DEFLATED) as z:z.writestr('notes.txt','Synthetic archive close text.')
with wave.open(str(root/'synthetic-silence.wav'),'wb') as w:w.setnchannels(1);w.setsampwidth(2);w.setframerate(16000);w.writeframes(b'\x00\x00'*(16000*6))
`;
 execFileSync(python,['-X','utf8','-c',fixtureCode,root],{windowsHide:true,timeout:15000});
 const assets={};for(const [kind,name] of Object.entries({pdf:'synthetic-two-pages.pdf',text:'synthetic-reader.txt',archive:'synthetic-reader.zip',audio:'synthetic-silence.wav'}))assets[kind]=await invoke('import_desktop_conversation_attachment',{args:{workspaceId,selectedPath:join(root,name)}});
 const unsupported=await evaluate(`window.__TAURI__.core.invoke('import_desktop_conversation_attachment',${JSON.stringify({args:{workspaceId,selectedPath:join(root,'synthetic-boundary.bin')}})},{headers:{'x-nanfeng-data-area':'CHAT'}}).then(()=>({accepted:true}),error=>({error:String(error)}))`);assert.match(unsupported.error||'',/MIME 或内容标识不受支持/);result.unsupportedImportRejected=true;
 await invoke('save_desktop_conversation_draft',{args:{workspaceId,conversationId:null,text:'Synthetic media close draft',attachmentIds:Object.values(assets).map(a=>a.id)}});
 await normalClose();await start(true);await until(()=>evaluate('Boolean(document.querySelector("[data-action=new-chat]"))'),'new chat');await click('[data-action=new-chat]');
 await until(()=>evaluate(`Boolean(document.querySelector('[data-action=open-text-preview][data-attachment-id="${assets.text.id}"]'))`),'cold draft attachments');
 const draft=await invoke('read_desktop_conversation_draft',{workspaceId});result.assets=Object.fromEntries(Object.entries(assets).map(([k,a])=>[k,{id:a.id,mimeType:a.mimeType}]));
 const open=async kind=>{await click(`[data-action=${actionFor(kind)}][data-attachment-id="${assets[kind].id}"]`);};
 await open('text');await until(()=>evaluate('globalThis.__nanfengDesktopWorkState.textPreview?.text?.includes("Synthetic reader close text")'),'actual text read');await close('text');result.textClosed=true;
 await open('pdf');const pdf=()=>evaluate('(()=>{const p=globalThis.__nanfengDesktopWorkState.pdfPreview;return p&&{id:p.attachmentId,page:p.pageNumber,count:p.pageCount,error:p.error||"",loading:!!p.loading}})()');
 result.pdfFirst=await until(async()=>{const v=await pdf();return v?.page===1&&!v.loading?v:null},'first actual PDF page');assert.equal(result.pdfFirst.count,2);assert.equal(result.pdfFirst.error,'');assert.equal(await evaluate('document.querySelector("[data-action=pdf-page-previous]").disabled'),true);
 await click('[data-action=pdf-page-next]');result.pdfNext=await until(async()=>{const v=await pdf();return v?.page===2&&!v.loading?v:null},'PDF next page');assert.equal(await evaluate('document.querySelector("[data-action=pdf-page-next]").disabled'),true);
 await click('[data-action=pdf-page-previous]');result.pdfPrevious=await until(async()=>{const v=await pdf();return v?.page===1&&!v.loading?v:null},'PDF previous page');await close('pdf');result.pdfLoadingClose=await loadingClose('pdf',assets.pdf.id);
 await open('archive');result.archiveEntries=await until(()=>evaluate('globalThis.__nanfengDesktopWorkState.archivePreview?.entries?.map(e=>e.path)'),'actual ZIP entries');assert.deepEqual(result.archiveEntries,['notes.txt']);await close('archive');result.archiveLoadingClose=await loadingClose('archive',assets.archive.id);
 result.boundaryNativeUnproven='Unsupported binary is rejected by the real import owner; no persisted boundary fixture fabricated.';
 await open('audio');await until(()=>evaluate('(()=>{const a=document.querySelector("[data-audio-preview]");return a?.readyState>=1&&a.duration>=5.9&&!a.error})()'),'silent audio decoded');
 await evaluate('(()=>{const a=document.querySelector("[data-audio-preview]");a.pause();a.currentTime=2.5;return true})()');await until(()=>evaluate('Math.abs(document.querySelector("[data-audio-preview]").currentTime-2.5)<0.02'),'audio seek 2500');await evaluate('document.querySelector("[data-audio-preview]").dispatchEvent(new Event("timeupdate"));true');await close('audio');
 result.audioPosition=await until(async()=>{const v=await invoke('read_desktop_audio_preview',{args:{workspaceId,attachmentId:assets.audio.id}});return v.positionMillis===2500?v.positionMillis:null},'audio close position persisted');
 await open('audio');await until(()=>evaluate('document.querySelector("[data-audio-preview]")?.readyState>=1'),'audio reopened');await evaluate('(()=>{const a=document.querySelector("[data-audio-preview]");a.pause();a.currentTime=0;return true})()');await until(()=>evaluate('document.querySelector("[data-audio-preview]").currentTime===0'),'audio seek zero');await evaluate('document.querySelector("[data-audio-preview]").dispatchEvent(new Event("timeupdate"));true');await close('audio');
 result.audioZeroPosition=(await invoke('read_desktop_audio_preview',{args:{workspaceId,attachmentId:assets.audio.id}})).positionMillis;assert.equal(result.audioZeroPosition,0,'exact audio zero position must persist on close');

 await invoke('save_desktop_audio_preview_position',{args:{workspaceId,attachmentId:assets.audio.id,positionMillis:4500}});
 result.audioUnreadyClose=await evaluate(`new Promise((done,fail)=>{const timer=setTimeout(()=>{o.disconnect();fail(new Error('unready audio not observed'))},5000);const o=new MutationObserver(()=>{const a=document.querySelector('[data-audio-preview]');if(!a)return;o.disconnect();clearTimeout(timer);const p=globalThis.__nanfengDesktopWorkState.audioPreview;window.__closedSyntheticAudio=a;const observed={readyState:a.readyState,resumeReady:p.resumeReady===true};document.querySelector('[data-action=close-audio-preview]').click();done(observed);});o.observe(document.querySelector('#app'),{childList:true,subtree:true});document.querySelector('[data-action=open-audio-preview][data-attachment-id="${assets.audio.id}"]').click();})`);
 assert.deepEqual(result.audioUnreadyClose,{readyState:0,resumeReady:false});assert.equal((await invoke('read_desktop_audio_preview',{args:{workspaceId,attachmentId:assets.audio.id}})).positionMillis,4500);result.audioUnreadyPreserved=true;
 await evaluate('window.__closedSyntheticAudio.dispatchEvent(new Event("loadedmetadata"));true');assert.equal(await evaluate('globalThis.__nanfengDesktopWorkState.audioPreview===null'),true);result.audioLateMetadataStayedClosed=true;
 await open('audio');await until(()=>evaluate('globalThis.__nanfengDesktopWorkState.audioPreview?.resumeReady===true'),'audio restored owner');await evaluate('(()=>{const a=document.querySelector("[data-audio-preview]");a.pause();a.currentTime=0;return true})()');await until(()=>evaluate('document.querySelector("[data-audio-preview]").currentTime===0'),'final audio zero');await close('audio');assert.equal((await invoke('read_desktop_audio_preview',{args:{workspaceId,attachmentId:assets.audio.id}})).positionMillis,0);
 assert.deepEqual(await invoke('read_desktop_conversation_draft',{workspaceId}),draft);assert.equal(requests,0);await normalClose();await start(true);assert.deepEqual(await invoke('read_desktop_conversation_draft',{workspaceId}),draft);assert.equal((await invoke('read_desktop_audio_preview',{args:{workspaceId,attachmentId:assets.audio.id}})).positionMillis,0);result.coldExact=true;await normalClose();result.pass=true;
} catch(error){result.error=String(error.stack||error);process.exitCode=1;}
finally{if(launch){try{await normalClose();}catch{stop();}}provider.closeAllConnections();await new Promise(done=>provider.close(done));result.root=root;result.pids=pids;result.closes=closes;result.providerRequests=requests;if(root)await writeFile(join(root,'media-close-native.json'),JSON.stringify(result,null,2));}
console.log(JSON.stringify({pass:result.pass,root,pids,providerRequests:requests,error:result.error}));
