"""Compose same-root post-restore explicit retry/cold verification without changing old receipts."""
from pathlib import Path
base=Path(__file__).parent
source=(base/'verify-windows-referenced-backup-native.mjs').read_text(encoding='utf-8')
prefix=source.split('const areas=')[0].replace('assert.equal(process.argv.length,3);','assert.equal(process.argv.length,4);').replace("let root = '';","let root = resolve(process.argv[3]);")
body=r'''
const originalEvidence=JSON.parse(await readFile(join(root,'task-assets-backup-evidence.json'),'utf8'));
assert.equal(originalEvidence.candidateSha256,receipt.sha256);assert.equal(originalEvidence.checks.length,46);assert.equal(originalEvidence.pids.length,4);assert.equal(originalEvidence.finalSqlite.allOk,true);assert.ok(!originalEvidence.forcedCleanup);
const areas=['CHAT','WORK'];
const {preflightPackage}=await import('../../protocol/scripts/exchange-lib.mjs');
const states={};
const sha=b=>createHash('sha256').update(b).digest('hex');
async function readTask(port,area,lib){return invoke(port,area,'read_latest_'+lib.prefix+'_import_task',{workspaceId:lib.id});}
function persistentTask(area,lib){return JSON.parse(execFileSync('python',['-c',"import sqlite3,sys,json,pathlib; c=sqlite3.connect(pathlib.Path(sys.argv[1]).as_uri()+'?mode=ro',uri=True); print(json.dumps(c.execute('select status,failure,retry_count,package_hash,storage_key from '+sys.argv[2]+' where id=?',(sys.argv[3],)).fetchone())); c.close()",join(root,area==='CHAT'?'':'work-area','workspace.sqlite3'),lib.prefix+'_import_tasks',lib.task.id],{encoding:'utf8',timeout:10000}));}
async function verify(port,area,initial=false){
 for(const lib of originalEvidence.areas[area].libraries){
  const ws=await invoke(port,area,'read_desktop_workspace',{workspaceId:lib.id});const task=await readTask(port,area,lib);const draft=await invoke(port,area,'read_desktop_conversation_draft',{workspaceId:lib.id,conversationId:lib.seedId});
  const key=area+'/'+lib.label;
  if(initial){
   assert.equal(hash(ws.exchange),lib.hash);const expected=structuredClone(lib.task);if(expected.status==='AWAITING_CONFIRMATION'){expected.status='FAILED';if(Object.hasOwn(expected,'failure'))expected.failure='INTERRUPTED';}assert.deepEqual(task,expected);assert.deepEqual(draft,lib.draft);states[key]={hash:hash(ws.exchange),task,draft};
  }else{assert.equal(hash(ws.exchange),states[key].hash);assert.deepEqual(task,states[key].task);assert.deepEqual(draft,states[key].draft);}
 }
 assert.deepEqual(await invoke(port,area,'list_desktop_workspace_exchange_v2_committed'),originalEvidence.areas[area].v2List);
 const exportPath=join(root,`v2-resume-${area}-${randomUUID()}.nfai-exchange`);await invoke(port,area,'reexport_desktop_workspace_exchange_v2_selected',{workspaceId:originalEvidence.areas[area].v2.workspaceId,selectedPath:exportPath});assert.equal(sha(await readFile(exportPath)),originalEvidence.areas[area].v2ExportHash);
 check(area+' full restored tasks/exchange/drafts/v2 '+(initial?'before explicit retry':'after explicit retry'),true);
}
function sqlite(){const data=JSON.parse(execFileSync('python',[join(desktopRoot,'scripts/check-stability-synthetic-sqlite.py'),root,'--closed-browser'],{encoding:'utf8',timeout:60000}));assert.ok(data.allOk);return data;}
try{
 let port=await start(true);result.root=root;await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke)'),'same-root native bridge');
 const runtime=await invoke(port,'CHAT','read_desktop_runtime_info');assert.equal(runtime.startupMode,'UI_SCHEMA_DIAGNOSTIC_ACCEPTANCE');assert.equal(runtime.automaticWorkSuppressed,true);result.runtime=runtime;
 for(const area of areas)await verify(port,area,true);
 result.retries=[];result.exports=[];
 for(const area of areas){
  for(const lib of originalEvidence.areas[area].libraries.filter(l=>l.label.endsWith('-staged'))){
   const before=await readTask(port,area,lib);assert.equal(before.status,'FAILED');assert.ok(before.items.every(i=>i.status==='PENDING_CONFIRMATION'));
   const persistentBefore=persistentTask(area,lib);assert.deepEqual(persistentBefore.slice(0,2),['FAILED','INTERRUPTED']);await invoke(port,area,'retry_'+lib.prefix+'_import_task',{taskId:before.id,workspaceId:lib.id});const retried=await readTask(port,area,lib);assert.equal(retried.id,before.id);assert.equal(retried.status,'AWAITING_CONFIRMATION');const persistentAfter=persistentTask(area,lib);assert.equal(persistentAfter[2],persistentBefore[2]+1);assert.deepEqual(persistentAfter.slice(3),persistentBefore.slice(3));assert.equal(retried.items.length,12);result.persistentRetries??=[];result.persistentRetries.push({area,label:lib.label,before:persistentBefore,after:persistentAfter});
   const ids=[];for(const item of retried.items)ids.push(await invoke(port,area,'confirm_'+lib.prefix+'_import_item',{args:{workspaceId:lib.id,taskId:retried.id,itemId:item.id}}));assert.equal(new Set(ids).size,12);
   const after=await readTask(port,area,lib);assert.equal(after.status,'COMPLETED');assert.ok(after.items.every(i=>i.status==='CONFIRMED'));
   const ws=await invoke(port,area,'read_desktop_workspace',{workspaceId:lib.id}),rows=ws.exchange.conversations.filter(c=>c.id!==lib.seedId),input=JSON.parse(await readFile(lib.input,'utf8'));assert.equal(rows.length,12);
   for(const source of input){const row=rows.find(c=>c.title===(source.title??source.name));assert.ok(row);const expected=source.mapping?[source.mapping.u.message.content.parts[0],source.mapping.a.message.content.parts[0]]:(source.chat_messages??JSON.parse(source.sourceText).chat_messages).map(m=>m.text);assert.deepEqual(row.messages.map(m=>m.blocks.filter(b=>b.kind==='TEXT').map(b=>b.text).join('\n')),expected);}
   const key=area+'/'+lib.label;states[key].hash=hash(ws.exchange);states[key].task=after;result.retries.push({area,label:lib.label,workspaceId:lib.id,taskId:after.id,ids,beforeHash:hash(before),retriedHash:hash(retried),afterHash:hash(after)});check(area+'/'+lib.label+' restored private package explicit retry and all12 confirmations',true);
  }
  await verify(port,area);await verify(port,area==='CHAT'?'WORK':'CHAT');
 }
 for(const area of areas)for(const lib of originalEvidence.areas[area].libraries){
  const selectedPath=join(root,`resume-export-${area}-${lib.label}.nfai-exchange`);const artifact=await invoke(port,area,'export_desktop_workspace_to_selected_path',{workspaceId:lib.id,selectedPath});const exported=preflightPackage(await readFile(selectedPath));const ws=await invoke(port,area,'read_desktop_workspace',{workspaceId:lib.id});assert.deepEqual(exported.exchange.conversations,ws.exchange.conversations);result.exports.push({area,label:lib.label,workspaceId:lib.id,selectedPath,artifact,conversationHash:hash(ws.exchange.conversations),conversations:ws.exchange.conversations.length});
 }
 check('all22 restored/retried workspaces actual semantic exports complete',result.exports.length===22);
 await normalClose(port);result.preColdSqlite=sqlite();port=await start(true);await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke)'),'explicit retry cold bridge');for(const area of areas)await verify(port,area);
 result.finalTaskStates=states;result.status={};for(const area of areas){result.status[area]=await invoke(port,area,'read_desktop_local_backup_status');assert.equal(result.status[area].operation,'RESTORED_RESTART_REQUIRED');}
 await normalClose(port);result.finalSqlite=sqlite();result.pass=true;
}catch(error){result.error=String(error.stack||error);process.exitCode=1;}
finally{if(launch){result.forcedCleanup=true;try{stop();}catch(error){result.cleanupError=String(error);}}result.pids=pids;await writeFile(join(root,'task-assets-backup-resume-cold-third-evidence.json'),JSON.stringify(result,null,2),{flag:'wx'});}
console.log(JSON.stringify({root,pass:result.pass,checks:result.checks.length,retries:result.retries?.length,error:result.error,fullNormalRecoveryAccepted:false}));
'''
out=base/'verify-windows-task-backup-resume-cold-native.mjs'
with out.open('x',encoding='utf-8',newline='\n') as f:f.write(prefix+body)
print(str(out))
