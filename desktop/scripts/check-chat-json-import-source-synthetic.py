"""Read-only verification for actual Windows JSON imports into a selected chat source."""
import json,sqlite3,hashlib,sys
from pathlib import Path
root=Path(sys.argv[1]).resolve(strict=True);assert root.is_relative_to(Path('D:/CodexTemp').resolve());assert json.loads((root/'.nanfeng-windows-acceptance.json').read_text('utf-8-sig'))['identifier']=='com.nanzhufeng.ai.desktop.windowsacceptance'
r=json.loads((root/'chat-json-import-source-native.json').read_text('utf-8'));assert r['coldExact'] and r['providerRequests']==0 and r['actualPickerCancels']==2 and r['actualImports']==4;assert len(r['closes'])==2 and all(x['accepted'] and x['responding'] and x['exitCode']==0 for x in r['closes'])
def connect(p):return sqlite3.connect(p.resolve().as_uri()+'?mode=ro',uri=True)
count=0
for path in root.rglob('*'):
 if not path.is_file():continue
 with path.open('rb') as f:header=f.read(16)
 if header!=b'SQLite format 3\x00':continue
 with connect(path) as c:
  assert c.execute('PRAGMA integrity_check').fetchall()==[('ok',)]
  assert not c.execute('PRAGMA foreign_key_check').fetchall()
 count+=1
private=0
with connect(root/'workspace.sqlite3') as c:
 assert json.loads(c.execute('SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?',(r['ordinaryId'],)).fetchone()[0])==r['ordinaryBefore']['exchange']
 assert json.loads(c.execute('SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?',(r['sourceId'],)).fetchone()[0])==r['afterImport']['exchange']
 assert c.execute('SELECT count(*) FROM desktop_ordinary_chat_attempts').fetchone()[0]==0
 all_ids=[]
 for provider,first_key,replay_key in [('chatgpt','gpt','replayGpt'),('claude','cl','replayClaude')]:
  assert c.execute('SELECT status,count(*) FROM '+provider+'_import_tasks GROUP BY status').fetchall()==[('COMPLETED',2)]
  assert c.execute('SELECT status,count(*) FROM '+provider+'_import_items GROUP BY status').fetchall()==[('CONFIRMED',4)]
  receipts=c.execute('SELECT workspace_id,conversation_id FROM '+provider+'_import_receipts').fetchall();assert len(receipts)==2 and all(x[0]==r['sourceId'] for x in receipts)
  first=r[first_key];replay=r[replay_key];assert first['workspaceId']==replay['workspaceId']==r['sourceId'];assert first['status']==replay['status']=='COMPLETED';assert first['id']!=replay['id'];assert [x['conversationId'] for x in first['items']]==[x['conversationId'] for x in replay['items']];ids=[x['conversationId'] for x in first['items']];assert set(ids)=={x[1] for x in receipts};all_ids.extend(ids)
  for task in [first,replay]:
   row=c.execute('SELECT status,workspace_id,storage_key,byte_count,package_hash FROM '+provider+'_import_tasks WHERE id=?',(task['id'],)).fetchone();assert row[:2]==('COMPLETED',r['sourceId']);path=(root/row[2]).resolve(strict=True);assert path.is_relative_to(root);raw=path.read_bytes();assert len(raw)==row[3] and hashlib.sha256(raw).hexdigest()==row[4];assert raw==Path(r['chatgptPath' if provider=='chatgpt' else 'claudePath']).read_bytes();private+=1
 assert len(set(all_ids))==4 and set(all_ids)==set(r['addedConversationIds'])
 original_ids={x['id'] for x in r['sourceBefore']['exchange']['conversations']};assert [x for x in r['afterImport']['exchange']['conversations'] if x['id'] in original_ids]==r['sourceBefore']['exchange']['conversations']
with connect(root/'work-area/workspace.sqlite3') as c:
 assert json.loads(c.execute('SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?',(r['workId'],)).fetchone()[0])==r['workBefore']['exchange']
 assert c.execute('SELECT count(*) FROM desktop_ordinary_chat_attempts').fetchone()[0]==0
 for provider in ['chatgpt','claude']:assert c.execute('SELECT count(*) FROM '+provider+'_import_tasks').fetchone()[0]==0
print(json.dumps({'pass':True,'candidateSha256':r['candidateSha256'],'sqliteCount':count,'privateJsonCopiesExact':private,'completedTasks':4,'confirmedItems':8,'distinctImportedConversations':4,'replayIdsAndReceiptsExact':True,'ordinaryAndHiddenWorkPreserved':True,'selectedSourceWholeExchangeColdExact':True,'providerRequests':0}))
