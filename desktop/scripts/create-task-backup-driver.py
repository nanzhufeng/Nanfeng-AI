"""Compose an independent synthetic native backup chain from existing identity/close helpers."""
from pathlib import Path

base = Path(__file__).parent
source = (base/'verify-windows-referenced-backup-native.mjs').read_text(encoding='utf-8')
prefix = source.split('const areas=')[0].replace("const mode='referenced-backup';", "const mode='task-assets-backup';")
history = (base/'verify-windows-large-history-json-native.mjs').read_text(encoding='utf-8')
fixture = history[history.index('function fixture('):history.index('async function confirmBatch(')]
v2 = (base/'verify-windows-v2-private-owner-native.mjs').read_text(encoding='utf-8')
create = v2[v2.index('function createPackage('):v2.index('function inspectPackage(')].replace('canonicalJson(', 'v2lib.canonicalJson(').replace('sha256(', 'sha(').replace('"w",compression', '"x",compression')
body = r'''
const areas=['CHAT','WORK'];
const sha=bytes=>createHash('sha256').update(bytes).digest('hex');
const formats=[{name:'chatgpt',prefix:'chatgpt'},{name:'claude',prefix:'claude'},{name:'knowledge',prefix:'nanfeng_knowledge'}];
const v2lib=await import('../../protocol/scripts/exchange-v2-lib.mjs');
const {packageExchange,semanticHash,preflightPackage}=await import('../../protocol/scripts/exchange-lib.mjs');
const original=JSON.parse(await readFile(join(desktopRoot,'../protocol/fixtures/nfai.exchange.v1.golden.json'),'utf8'));
const areaRoot=area=>area==='CHAT'?root:join(root,'work-area');
const evidencePath=()=>join(root,'task-assets-backup-evidence.json');
async function progress(stage){result.stage=stage;await writeFile(join(root,'task-assets-backup-progress.json'),JSON.stringify(result,null,2));console.log(JSON.stringify({stage,root,checks:result.checks.length}));}
function baseline(label){return JSON.parse(execFileSync('python',[join(desktopRoot,'scripts/check-task-assets-backup.py'),'snapshot',root,label],{encoding:'utf8',timeout:60000}));}
function compare(label,area,backup){return JSON.parse(execFileSync('python',[join(desktopRoot,'scripts/check-task-assets-backup.py'),'compare',root,label,area,backup],{encoding:'utf8',timeout:60000}));}
async function seed(port,area,label){
 const ex=structuredClone(original);ex.export.id=`w15-task-${area.toLowerCase()}-${label}`;
 for(const c of ex.conversations){const old=c.id;c.id=ex.export.id+'-'+old;c.currentLeafId=c.id+'-a';c.messages[0].id=c.id+'-u';c.messages[1].id=c.id+'-a';c.messages[1].parentId=c.id+'-u';for(const m of c.messages)m.blocks=[{kind:'TEXT',ordinal:0,text:'W15 nonempty task seed '+label}];}
 ex.export.semanticHash=semanticHash(ex);const path=join(root,`seed-${area}-${label}.nfai-exchange`);await writeFile(path,packageExchange(ex,new Map()),{flag:'wx'});
 const pre=await invoke(port,area,'stage_preflight_selected_exchange',{selectedPath:path});const ws=await invoke(port,area,'import_staged_exchange_as_new_workspace',{args:{stagingId:pre.stagingId,workspaceTitle:`合成非空任务 ${area} ${label}`}});
 return {id:ws.summary.id,seedId:ws.exchange.conversations[0].id};
}
async function readTask(port,area,lib){return invoke(port,area,'read_latest_'+lib.prefix+'_import_task',{workspaceId:lib.id});}
async function verifyArea(port,area,restored=false){
 const state=result.areas[area];
 for(const lib of state.libraries){
  const current=await invoke(port,area,'read_desktop_workspace',{workspaceId:lib.id});assert.equal(hash(current.exchange),lib.hash);
  const task=await readTask(port,area,lib);const expected=structuredClone(lib.task);
  if(restored&&!['COMPLETED','PARTIALLY_COMPLETED','FAILED','CANCELLED','SKIPPED'].includes(expected.status)){expected.status='FAILED';if(Object.hasOwn(expected,'failure'))expected.failure='INTERRUPTED';}
  assert.deepEqual(task,expected,`${area}/${lib.label} full task projection`);
  assert.deepEqual(await invoke(port,area,'read_desktop_conversation_draft',{workspaceId:lib.id,conversationId:lib.seedId}),lib.draft);
 }
 assert.deepEqual(await invoke(port,area,'list_desktop_workspace_exchange_v2_committed'),state.v2List);
 const exportPath=join(root,`v2-${area}-${randomUUID()}.nfai-exchange`);
 await invoke(port,area,'reexport_desktop_workspace_exchange_v2_selected',{workspaceId:state.v2.workspaceId,selectedPath:exportPath});assert.equal(sha(await readFile(exportPath)),state.v2ExportHash);state.v2ReadExports??=[];state.v2ReadExports.push(exportPath);
 check(area+' complete exchange/tasks/items/drafts/v2 reexport '+(restored?'restored':'original'),true);
}
function sqlite(closed=false){const data=JSON.parse(execFileSync('python',[join(desktopRoot,'scripts/check-stability-synthetic-sqlite.py'),root,...(closed?['--closed-browser']:[])],{encoding:'utf8',timeout:60000}));assert.ok(data.allOk);return data;}
async function makeHistory(port,area,format,kind){
 const label=format.name+'-'+kind,ws=await seed(port,area,label);const input=fixture(format.name,area+'-'+kind,12);
 const path=join(root,`${area}-${label}.json`);await writeFile(path,JSON.stringify(input),{flag:'wx'});
 const lib={...ws,label,prefix:format.prefix,input:path,confirmedIds:[]};result.areas[area].libraries.push(lib);
 const stagedReceipt=await invoke(port,area,'stage_'+format.prefix+'_export_selected',{selectedPath:path,workspaceId:ws.id});const staged=await readTask(port,area,lib);assert.equal(staged.id,stagedReceipt.id);assert.equal(staged.items.length,12);
 const count=kind==='completed'?12:kind==='partial'?7:0;
 for(const item of staged.items.slice(0,count))lib.confirmedIds.push(await invoke(port,area,'confirm_'+format.prefix+'_import_item',{args:{workspaceId:ws.id,taskId:staged.id,itemId:item.id}}));
 lib.task=await readTask(port,area,lib);assert.equal(lib.task.items.filter(x=>x.status==='CONFIRMED').length,count);assert.equal(lib.task.items.filter(x=>x.status==='PENDING_CONFIRMATION').length,12-count);
 assert.equal(lib.task.status,kind==='completed'?'COMPLETED':kind==='partial'?'PARTIALLY_COMPLETED':'AWAITING_CONFIRMATION');
 const read=await invoke(port,area,'read_desktop_workspace',{workspaceId:ws.id});const rows=read.exchange.conversations.filter(c=>c.id!==ws.seedId);assert.equal(rows.length,count);
 for(const source of input.slice(0,count)){const row=rows.find(c=>c.title===(source.title??source.name));assert.ok(row);const expected=source.mapping?[source.mapping.u.message.content.parts[0],source.mapping.a.message.content.parts[0]]:(source.chat_messages??JSON.parse(source.sourceText).chat_messages).map(m=>m.text);assert.deepEqual(row.messages.map(m=>m.blocks.filter(b=>b.kind==='TEXT').map(b=>b.text).join('\n')),expected);}
 lib.hash=hash(read.exchange);await invoke(port,area,'save_desktop_conversation_draft',{args:{workspaceId:ws.id,conversationId:ws.seedId,text:'完整任务草稿 '+area+' '+label,attachmentIds:[],modelId:null,toneOverride:null,webSearchOverride:null}});lib.draft=await invoke(port,area,'read_desktop_conversation_draft',{workspaceId:ws.id,conversationId:ws.seedId});
 check(area+'/'+label+' owner tasks with precise confirmation status and full body',true);
}
async function makeZip(port,area,provider){
 const label=provider.toLowerCase()+'-zip',ws=await seed(port,area,label),format=provider.toLowerCase();
 const rows=fixture(format,area+'-zip',18),asset=Buffer.from('SYNTHETIC '+area+' '+provider+' ZIP nonempty text\nΩ 😀\n'+Array.from({length:64},(_,i)=>sha(Buffer.from(area+provider+i))).join('\n'));
 const assetId='synthetic-'+area+'-'+provider;
 for(const row of rows){if(provider==='CHATGPT'){
  const u=row.id+'-u',a=row.id+'-a';row.mapping.u.children=[a];row.mapping.u.message.id=u;row.mapping.a.parent=u;row.mapping.a.message.id=a;row.mapping={[u]:row.mapping.u,[a]:row.mapping.a};
  for(const node of Object.values(row.mapping)){node.message.content.parts.push({asset_pointer:'file-service://'+assetId});node.message.metadata={attachments:[{id:assetId,name:'synthetic-'+area+'-'+provider+'.txt'}]};}
 }else{for(const m of row.chat_messages){m.uuid=row.uuid+(m.sender==='human'?'-u':'-a');m.parent_message_uuid=m.sender==='human'?null:row.uuid+'-u';}}
 }
 const path=join(root,`${area}-${label}.zip`),jsonPath=path+'.json',assetPath=path+'.txt';await writeFile(jsonPath,JSON.stringify(rows),{flag:'wx'});await writeFile(assetPath,asset,{flag:'wx'});
 execFileSync('python',['-c',"import sys,zipfile; z=zipfile.ZipFile(sys.argv[1],'x',compression=zipfile.ZIP_STORED); z.write(sys.argv[2],'conversations.json'); z.write(sys.argv[3],sys.argv[4]+'.dat'); z.close()",path,jsonPath,assetPath,assetId],{timeout:30000});
 const task=await invoke(port,area,'stage_p6k_zip_import_selected',{args:{workspaceId:ws.id,provider,selectedPath:path}});assert.equal(task.status,'COMPLETED');assert.equal(task.items.length,18);assert.ok(task.items.every(i=>i.status==='COMMITTED'));
 const lib={...ws,label,prefix:'p6k_zip',input:path,task};result.areas[area].libraries.push(lib);lib.task=await readTask(port,area,lib);assert.equal(lib.task.id,task.id);
 const read=await invoke(port,area,'read_desktop_workspace',{workspaceId:ws.id});const imported=read.exchange.conversations.filter(c=>c.id!==ws.seedId);assert.equal(imported.length,18);
 for(const source of rows){const row=imported.find(c=>c.title===(source.title??source.name));assert.ok(row);const expected=source.mapping?Object.values(source.mapping).map(n=>n.message.content.parts[0]):source.chat_messages.map(m=>m.text);assert.deepEqual(row.messages.map(m=>m.blocks.filter(b=>b.kind==='TEXT').map(b=>b.text).join('\n')),expected);}
 if(provider==='CHATGPT'){assert.equal(task.officialAttachmentSummary.restoredOccurrences,36);assert.equal(task.officialAttachmentSummary.restoredUniqueAssets,1);const refs=imported.flatMap(c=>c.messages.flatMap(m=>m.blocks.filter(b=>b.kind==='ASSET_REF')));assert.equal(refs.length,36);assert.ok(refs.every(r=>r.asset.sha256===sha(asset)&&r.asset.byteCount===asset.length));lib.references=refs.map(r=>r.asset);}
 lib.hash=hash(read.exchange);await invoke(port,area,'save_desktop_conversation_draft',{args:{workspaceId:ws.id,conversationId:ws.seedId,text:'完整ZIP草稿 '+area+' '+provider,attachmentIds:[],modelId:null,toneOverride:null,webSearchOverride:null}});lib.draft=await invoke(port,area,'read_desktop_conversation_draft',{workspaceId:ws.id,conversationId:ws.seedId});check(area+'/'+label+' owner completed task receipt and full message/asset bytes',true);
}
async function makeV2(port,area){
 const exchange=JSON.parse(await readFile(join(desktopRoot,'../protocol/fixtures/nfai.exchange.v2.golden.json'),'utf8'));const assetHash=sha(Buffer.from('a'));for(const attachment of exchange.knowledge[0].attachments){attachment.entry=`assets/${assetHash}`;attachment.sha256=assetHash;}exchange.export.id+='-'+area.toLowerCase();exchange.export.semanticHash=v2lib.semanticHash(exchange);v2lib.validateExchangeV2(exchange);
 const path=join(root,'synthetic-v2-'+area+'.nfai-exchange');createPackage(path,exchange);const imported=await invoke(port,area,'import_desktop_workspace_exchange_v2_selected',{selectedPath:path});assert.equal(imported.semanticHash,exchange.export.semanticHash);assert.equal(imported.assetCount,1);
 const state=result.areas[area];state.v2=imported;state.v2List=await invoke(port,area,'list_desktop_workspace_exchange_v2_committed');state.v2Input=path;
 const output=join(root,'synthetic-v2-export-'+area+'.nfai-exchange');await invoke(port,area,'reexport_desktop_workspace_exchange_v2_selected',{workspaceId:imported.workspaceId,selectedPath:output});state.v2ExportHash=sha(await readFile(output));
}
try{
 let port=await start();result.root=root;await until(()=>evaluate(port,'Boolean(document.querySelector("#chat-composer")&&globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'),'native ready');
 const runtime=await invoke(port,'CHAT','read_desktop_runtime_info');assert.equal(runtime.startupMode,'UI_SCHEMA_DIAGNOSTIC_ACCEPTANCE');assert.equal(runtime.automaticWorkSuppressed,true);
 for(const area of areas){result.areas[area]={libraries:[]};await invoke(port,area,'ensure_desktop_local_workspace');for(const format of formats)for(const kind of ['completed','partial','staged']){await makeHistory(port,area,format,kind);await progress(area+'/'+format.name+'/'+kind);}for(const provider of ['CHATGPT','CLAUDE']){await makeZip(port,area,provider);await progress(area+'/'+provider+'/ZIP');}await makeV2(port,area);await verifyArea(port,area);}
 result.beforeExport=baseline('before-export');
 for(const area of areas){const state=result.areas[area];state.backup=join(root,'synthetic-task-'+area+'.nfai-backup');state.artifact=await timed(area+' backup export',()=>invoke(port,area,'export_desktop_local_backup',{selectedPath:state.backup}));assert.equal(sha(await readFile(state.backup)),state.artifact.sha256);state.preflight=await invoke(port,area,'preflight_desktop_local_backup',{selectedPath:state.backup});assert.equal(state.preflight.missing.length,0);assert.equal(state.preflight.unsupported.length,0);await progress(area+' backup exported');}
 result.beforeSqlite=sqlite();await normalClose(port);port=await start(true);await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke)'),'cold bridge');for(const area of areas)await verifyArea(port,area);
 result.restores={};const restored=new Set();
 for(const area of areas){
  const lib=result.areas[area].libraries[0];await invoke(port,area,'save_desktop_conversation_draft',{args:{workspaceId:lib.id,conversationId:lib.seedId,text:'备份后明确合成修改 '+area,attachmentIds:[],modelId:null,toneOverride:null,webSearchOverride:null}});
  result['beforeRestore'+area]=baseline('before-restore-'+area);
  const pre=await invoke(port,area,'preflight_desktop_local_backup',{selectedPath:result.areas[area].backup});result.restores[area]=await timed(area+' restore',()=>invoke(port,area,'restore_desktop_local_backup',{fingerprint:pre.fingerprint,replaceLocal:true}));assert.equal(result.restores[area].restartRequired,true);restored.add(area);
  await normalClose(port);result['afterRestoreClosed'+area]=baseline('after-restore-closed-'+area);
  result['independent'+area]=compare('before-restore-'+area,area,result.areas[area].backup);
  port=await start(true);await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke)'),'restored cold bridge');for(const selected of areas)await verifyArea(port,selected,restored.has(selected));await progress(area+' restored complete cold data');
 }
 result.coldStatus={};for(const area of areas){result.coldStatus[area]=await invoke(port,area,'read_desktop_local_backup_status');assert.equal(result.coldStatus[area].operation,'RESTORED_RESTART_REQUIRED');}
 await normalClose(port);result.finalSqlite=sqlite(true);result.finalBaseline=baseline('final-cold-closed');result.finalIndependent={};for(const area of areas)result.finalIndependent[area]=compare('after-restore-closed-WORK',area,result.areas[area].backup);
 result.preparationPass=true;result.normalRecoveryBoundary='Diagnostic only; NORMAL startup finalization remains reserved by 12h monitor. No Provider tasks manufactured.';
}catch(error){result.error=String(error.stack||error);process.exitCode=1;}
finally{if(launch){result.forcedCleanup=true;try{stop();}catch(error){result.cleanupError=String(error);}}result.pids=pids;if(root)await writeFile(evidencePath(),JSON.stringify(result,null,2),{flag:'wx'});}
console.log(JSON.stringify({root,preparationPass:result.preparationPass,error:result.error,fullNormalRecoveryAccepted:false}));
'''
out = base/'verify-windows-task-assets-backup-native.mjs'
with out.open('x', encoding='utf-8', newline='\n') as f:
    f.write(prefix+fixture+create+body)
print(str(out))
