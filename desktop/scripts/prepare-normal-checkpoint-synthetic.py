import hashlib,json,pathlib,shutil,sqlite3,sys,uuid
from backup_real_switch_baseline import tables,tree,CONTROLLED
area,fault=sys.argv[1:];assert area in ('CHAT','WORK') and fault in ('valid','missing','corrupt')
source=pathlib.Path('D:/CodexTemp/nfa-w15-real-switch-'+area.lower()+'-01a0f79a')/('nanfeng-ai-windows-acceptance.'+{'CHAT':'836f1089-d603-4892-9e17-4f60cb311dcf','WORK':'c75a4188-123f-4bf8-ade0-dbac092f635b'}[area])
marker=json.loads((source/'.nanfeng-windows-acceptance.json').read_text(encoding='utf-8-sig'));assert marker['identifier']=='com.nanzhufeng.ai.desktop.windowsacceptance'
files=[]
for p in source.rglob('*'):
 assert not p.is_symlink() and not p.is_junction()
 if p.is_file() and 'webview2' not in p.relative_to(source).parts:files.append(p)
size=sum(p.stat().st_size for p in files);assert shutil.disk_usage('D:/').free>size+1024**3
parent=pathlib.Path('D:/CodexTemp')/('nfa-normal-checkpoint-'+str(uuid.uuid4()));run=str(uuid.uuid4());root=parent/('nanfeng-ai-windows-acceptance.'+run)
shutil.copytree(source,root,ignore=shutil.ignore_patterns('webview2'))
marker['runId']=run;(root/'.nanfeng-windows-acceptance.json').write_text(json.dumps(marker),encoding='utf-8')
e=json.loads((source/'real-switch-backup-evidence.json').read_text(encoding='utf-8'));baseline=json.loads(pathlib.Path(e['observer']['fullBaseline']).read_text(encoding='utf-8'))
drafts={}
for selected,libs in e['areas'].items():
 c=sqlite3.connect(pathlib.Path(baseline['databases'][selected+'/workspace.sqlite3']['snapshot']).as_uri()+'?mode=ro',uri=True)
 drafts[selected]={item['id']:c.execute('select text from desktop_conversation_drafts_v1 where workspace_id=? and conversation_key=?',(item['id'],item['draftId'])).fetchone()[0] for item in libs['libraries']};c.close()
base=root if area=='CHAT' else root/'work-area';state=json.loads((base/'local-backup/v1/state.json').read_text(encoding='utf-8'));assert state['phase']=='SWITCHING'
checkpoint=base/'local-backup/v1/recovery'/state['checkpointId'];snapshot=checkpoint/'workspace.snapshot'
assert tables(snapshot)==baseline['databases'][area+'/workspace.sqlite3']['tables']
if fault!='valid':
 saved=snapshot.with_name('workspace.snapshot-preserved-before-fault');saved.write_bytes(snapshot.read_bytes())
 if fault=='missing':snapshot.rename(checkpoint/'workspace.snapshot-missing-preserved')
 else:snapshot.write_bytes(b'SYNTHETIC DAMAGED CHECKPOINT')
config={'area':area,'fault':fault,'sourceRoot':str(source),'root':str(root),'copiedBytes':size,'baseline':e['observer']['fullBaseline'],'drafts':drafts,'checkpointId':state['checkpointId']}
config['beforeDatabases']={key:tables((root if key.split('/',1)[0] in ('CHAT','SHARED') else root/'work-area')/key.split('/',1)[1]) for key in baseline['databases']}
config['beforeControlledFiles']={}
for selected in ('CHAT','WORK'):
 current={}
 for local,prefix in CONTROLLED.items():current.update(tree((root if selected=='CHAT' else root/'work-area')/local,prefix))
 config['beforeControlledFiles'][selected]=current
(root/'normal-checkpoint-config.json').write_text(json.dumps(config,ensure_ascii=False,indent=2),encoding='utf-8');print(str(root))
