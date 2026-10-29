"""Independent closed-root checks of scoped cloud/local pin and receipt isolation."""
import hashlib,importlib.util,json,sys
from pathlib import Path
spec=importlib.util.spec_from_file_location('fault',Path(__file__).with_name('fault-cloud-restore-synthetic.py'));fault=importlib.util.module_from_spec(spec);spec.loader.exec_module(fault)
def check(value):
 root=fault.marked(value);n=json.loads((root/'cloud-pin-cancel-current-native.json').read_text(encoding='utf-8'));assert n['pass'] and not n['forcedCleanup'] and len(n['closes'])==3
 assert all(c['responding'] and c['accepted'] for c in n['closes'])
 commits=[r for r in n['requests'] if r['path'].endswith('commit_document')]
 assert [(r['documentId'],r['expectedRevision']) for r in commits]==[('cloud-conversation-list-v1',0),('cloud-work-conversation-list-v1',0),('cloud-work-conversation-list-v1',1),('cloud-conversation-list-v1',1)]
 deletes=[r for r in n['requests'] if r['path'].endswith('delete_document')];assert len(deletes)==1 and deletes[0]['expectedRevision']==1
 assert sum(r['path'].endswith('nanfeng_sync_inventory') for r in n['requests'])==4
 refusals=[c for c in n['cases'] if c.get('kind')=='continuation-refusal'];assert len(refusals)==2 and all(not c['response']['accepted'] and c['httpDelta']==0 for c in refusals)
 documents=json.loads((root/'synthetic-work-cloud-documents.json').read_text(encoding='utf-8'));areas={}
 for area,rel in [('CHAT','workspace.sqlite3'),('WORK','work-area/workspace.sqlite3')]:
  c=fault.connect(root/rel);assert c.execute('PRAGMA integrity_check').fetchone()[0]=='ok';assert c.execute('PRAGMA foreign_key_check').fetchall()==[]
  assert c.execute('SELECT COUNT(*) FROM desktop_selected_conversation_sync').fetchone()[0]==0
  assert fault.digest(fault.snapshot(c))==n['cold'][area]['businessSha256']==n['beforeClose'][area]['businessSha256']
  expected=next(d['payload']['records'][0]['content'] for d in documents if d['payload']['records'][0]['content']['surface']==area)
  actual=[]
  for wid,raw in c.execute('SELECT workspace_id,exchange_json FROM workspace_exchange'):
   actual += [(wid,conv) for conv in json.loads(raw)['conversations'] if conv['id']=='same-area-conversation']
  assert len(actual)==1;wid,conv=actual[0];assert wid==n['workspaceIds'][area]
  assert conv['messages']==expected['messages'] and conv['title']==expected['title'] and conv['currentLeafId']=='m5' and conv['pinned'] is True
  core={t:c.execute(f'SELECT * FROM {t} ORDER BY 1').fetchall() for t in ['workspaces','workspace_exchange','desktop_conversation_favorites']}
  assert fault.digest(core)==n['localPinFixture'][area]['localCoreSha256']==n['cold'][area]['localCoreSha256']
  attempts={t:c.execute(f'SELECT COUNT(*) FROM {t}').fetchone()[0] for t in ['desktop_ordinary_chat_attempts','desktop_ocr_attempts_v1','desktop_transcription_tasks','desktop_conversation_title_attempts']};assert all(v==0 for v in attempts.values())
  assert c.execute("SELECT COUNT(*) FROM desktop_sync_jobs WHERE stage IN ('COMMITTING','UNKNOWN')").fetchone()[0]==0
  areas[area]={'messageCount':len(conv['messages']),'localPinned':True,'completeTreeAndBlocksExact':True,'localCoreExactAcrossCloudOperationsAndCold':True,'selectedCount':0,'coldWholeSnapshotExact':True,'attempts':attempts};c.close()
 return {'root':str(root),'candidateSha256':n['candidateSha256'],'areas':areas,'listOnlyCASCommits':4,'casDeleteCount':1,'completeInventoryReads':4,'continuationRefusalsHttpZero':2,'naturalCloseCount':3,'integrityOk':True,'foreignKeys':0}
if __name__=='__main__':print(json.dumps(check(sys.argv[1]),ensure_ascii=False,indent=2))
