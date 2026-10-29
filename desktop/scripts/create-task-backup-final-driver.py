"""Complete the preserved same-root retry receipts with exact v1 export projection and cold reads."""
from pathlib import Path
base=Path(__file__).parent
prefix=(base/'verify-windows-task-backup-resume-cold-native.mjs').read_text(encoding='utf-8').split('const originalEvidence=')[0]
body=r'''
const e=JSON.parse(await readFile(join(root,'task-assets-backup-evidence.json'),'utf8'));
const resumed=JSON.parse(await readFile(join(root,'task-assets-backup-resume-cold-third-evidence.json'),'utf8'));
assert.equal(e.candidateSha256,receipt.sha256);assert.equal(resumed.retries.length,6);assert.equal(resumed.checks.length,12);assert.equal(resumed.persistentRetries.length,6);
const states={};const areas=['CHAT','WORK'];
const {preflightPackage,semanticHash}=await import('../../protocol/scripts/exchange-lib.mjs');
const pick=(o,keys)=>Object.fromEntries(keys.filter(k=>Object.hasOwn(o,k)).map(k=>[k,o[k]]));
function portable(source){
 const ex=pick(structuredClone(source),['format','version','export','projects','conversations','knowledge','memory','relations','settings']);ex.export=pick(ex.export,['id','createdAt','origin','semanticHash','sensitivity']);ex.export.origin=pick(ex.export.origin,['platform','appVersion']);
 const groups={projects:['id','title','description','pinned','archived','revision','createdAt','updatedAt'],knowledge:['id','title','body','tags','scope','projectId','status','revision','contentHash','createdAt','updatedAt','classification'],memory:['id','body','scope','scopeId','status','revision','contentHash','createdAt','updatedAt','classification'],relations:['id','fromId','toId','kind','status','revision','createdAt']};for(const [group,keys] of Object.entries(groups))ex[group]=ex[group].map(o=>pick(o,keys));
 ex.conversations=ex.conversations.map(c=>({...pick(c,['id','projectId','title','currentLeafId','pinned','archived','revision','createdAt','updatedAt']),messages:c.messages.map(m=>({...pick(m,['id','parentId','ordinal','role','delivery','revision','createdAt']),role:m.role.toLowerCase(),blocks:m.blocks.map((b,i)=>{const block=pick(b,['kind','ordinal','text','asset']);block.ordinal??=i;if(block.asset){block.asset=pick(block.asset,['id','entry','mimeType','displayName','byteCount','sha256','classification']);block.asset.classification??=source.export.sensitivity;}return block;})}))}));ex.settings=pick(ex.settings,['uiLanguage','theme']);ex.export.semanticHash=semanticHash(ex);return ex;
}
async function verify(port,area,initial=false){
 for(const lib of e.areas[area].libraries){
  const ws=await invoke(port,area,'read_desktop_workspace',{workspaceId:lib.id});const task=await invoke(port,area,'read_latest_'+lib.prefix+'_import_task',{workspaceId:lib.id});const draft=await invoke(port,area,'read_desktop_conversation_draft',{workspaceId:lib.id,conversationId:lib.seedId});const key=area+'/'+lib.label;
  if(initial){
   assert.deepEqual(draft,lib.draft);
   if(lib.label.endsWith('-staged')){
    assert.equal(task.status,'COMPLETED');assert.equal(task.id,lib.task.id);assert.ok(task.items.every(i=>i.status==='CONFIRMED'));assert.deepEqual(task.items.map(i=>i.conversationId),resumed.retries.find(r=>r.area===area&&r.label===lib.label).ids);
    const rows=ws.exchange.conversations.filter(c=>c.id!==lib.seedId),input=JSON.parse(await readFile(lib.input,'utf8'));assert.equal(rows.length,12);for(const source of input){const row=rows.find(c=>c.title===(source.title??source.name));assert.ok(row);const messages=source.mapping?[source.mapping.u.message.content.parts[0],source.mapping.a.message.content.parts[0]]:(source.chat_messages??JSON.parse(source.sourceText).chat_messages).map(m=>m.text);assert.deepEqual(row.messages.map(m=>m.blocks.filter(b=>b.kind==='TEXT').map(b=>b.text).join('\n')),messages);}
   }else{assert.equal(hash(ws.exchange),lib.hash);assert.deepEqual(task,lib.task);}
   states[key]={hash:hash(ws.exchange),task,draft};
   const selectedPath=join(root,`final-restored-export-${area}-${lib.label}.nfai-exchange`);const artifact=await invoke(port,area,'export_desktop_workspace_to_selected_path',{workspaceId:lib.id,selectedPath});const exported=preflightPackage(await readFile(selectedPath));assert.deepEqual(exported.exchange,portable(ws.exchange));result.exports.push({area,label:lib.label,workspaceId:lib.id,selectedPath,artifact,expectedHash:hash(exported.exchange),conversations:exported.exchange.conversations.length});
  }else{assert.equal(hash(ws.exchange),states[key].hash);assert.deepEqual(task,states[key].task);assert.deepEqual(draft,states[key].draft);}
 }
 assert.deepEqual(await invoke(port,area,'list_desktop_workspace_exchange_v2_committed'),e.areas[area].v2List);
 const selectedPath=join(root,`v2-final-${area}-${randomUUID()}.nfai-exchange`);await invoke(port,area,'reexport_desktop_workspace_exchange_v2_selected',{workspaceId:e.areas[area].v2.workspaceId,selectedPath});assert.equal(createHash('sha256').update(await readFile(selectedPath)).digest('hex'),e.areas[area].v2ExportHash);
 check(area+' complete tasks/receipts/full text/drafts/v2 '+(initial?'after explicit retry':'cold'),true);
}
function sqlite(){const data=JSON.parse(execFileSync('python',[join(desktopRoot,'scripts/check-stability-synthetic-sqlite.py'),root,'--closed-browser'],{encoding:'utf8',timeout:60000}));assert.ok(data.allOk);return data;}
try{
 let port=await start(true);result.root=root;await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke)'),'final restored bridge');result.exports=[];
 for(const area of areas)await verify(port,area,true);check('all22 exact portable v1 projections exported',result.exports.length===22);await normalClose(port);result.beforeColdSqlite=sqlite();
 port=await start(true);await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke)'),'final cold bridge');for(const area of areas)await verify(port,area);result.status={};for(const area of areas){result.status[area]=await invoke(port,area,'read_desktop_local_backup_status');assert.equal(result.status[area].operation,'RESTORED_RESTART_REQUIRED');}
 await normalClose(port);result.finalSqlite=sqlite();result.finalStates=states;result.pass=true;
}catch(error){result.error=String(error.stack||error);process.exitCode=1;}
finally{if(launch){result.forcedCleanup=true;try{stop();}catch(error){result.cleanupError=String(error);}}result.pids=pids;await writeFile(join(root,'task-assets-backup-final-cold-evidence.json'),JSON.stringify(result,null,2),{flag:'wx'});}
console.log(JSON.stringify({root,pass:result.pass,checks:result.checks.length,error:result.error,fullNormalRecoveryAccepted:false}));
'''
out=base/'verify-windows-task-backup-final-cold-native.mjs'
with out.open('x',encoding='utf-8',newline='\n') as f:f.write(prefix+body)
print(str(out))
