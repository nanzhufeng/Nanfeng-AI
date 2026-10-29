"""Create a small, independent marked-root native restart-gate regression."""
from pathlib import Path

base = Path(__file__).parent
source = (base / 'verify-windows-referenced-backup-native.mjs').read_text(encoding='utf-8')
source = source.replace("const mode='referenced-backup';", "const mode='process-instance-backup';")
source = source.replace('for(const count of [200,400,600])', 'for(const count of [4])')
source = source.replace('1200', '4')
needle = "assert.equal(result.restore.restartRequired,true);result.restores[area]=result.restore;"
assert source.count(needle) == 1
source = source.replace(needle, needle + r'''
 const persisted=JSON.parse(await readFile(join(areaRoot(area),'local-backup/v1/state.json'),'utf8'));
 assert.equal(persisted.processId,Number(launch.pid));assert.match(persisted.processInstance,/^[0-9a-f]{64}$/);
 result.instances??={};result.instances[area]={restorePid:Number(launch.pid),instance:persisted.processInstance};
 const blocked=await evaluate(port,`window.__TAURI__.core.invoke('save_desktop_conversation_draft',${JSON.stringify({args:{workspaceId:item.id,conversationId:item.draftId,text:'MUST NOT WRITE IN RESTORE PROCESS',attachmentIds:[],modelId:null,toneOverride:null,webSearchOverride:null}})},${JSON.stringify({headers:{'x-nanfeng-data-area':area}})}).then(()=>({unexpectedSuccess:true}),error=>({error:String(error)}))`);
 assert.ok(blocked.error?.includes('完全重启'));check(area+' same restoring process refuses business write',true);
''')
needle = "for(const selected of areas)await verifyArea(port,selected);check(area+' complete restored data/drafts and other area cold conserved',true);"
assert source.count(needle) == 1
source = source.replace(needle, needle + r'''
 result.instances[area].coldPid=Number(launch.pid);
 const saved=JSON.parse(await readFile(join(areaRoot(area),'local-backup/v1/state.json'),'utf8'));
 assert.equal(saved.processInstance,result.instances[area].instance);
 // Real new process; do not forge OS PID reuse or mutate a persisted identity.
 const draft=result.areas[area].libraries[0].draft;
 await invoke(port,area,'save_desktop_conversation_draft',{args:{workspaceId:item.id,conversationId:item.draftId,text:draft.text,attachmentIds:draft.attachmentIds,modelId:draft.modelId,toneOverride:draft.toneOverride,webSearchOverride:draft.webSearchOverride}});
 await verifyArea(port,area);check(area+' cold process accepts business write with restore state retained',true);
''')
target = base / 'verify-windows-process-instance-backup-native.mjs'
with target.open('x', encoding='utf-8', newline='\n') as f:
    f.write(source)

audit = (base / 'check-referenced-backup-synthetic.py').read_text(encoding='utf-8')
audit = audit.replace('conversations==1200 and messages==2400 and len(refs)==1200 and len(set(refs))==1200 and len(assets)==1200', 'conversations==4 and messages==8 and len(refs)==4 and len(set(refs))==4 and len(assets)==4')
with (base / 'check-process-instance-backup.py').open('x', encoding='utf-8', newline='\n') as f:
    f.write(audit)
