"""Read-only ZIP, private bytes, source and attachment-occurrence checks."""
import json,sqlite3,hashlib,zipfile,sys
from pathlib import Path
root=Path(sys.argv[1]).resolve(strict=True);assert root.is_relative_to(Path('D:/CodexTemp').resolve());assert json.loads((root/'.nanfeng-windows-acceptance.json').read_text('utf-8-sig'))['identifier']=='com.nanzhufeng.ai.desktop.windowsacceptance'
r=json.loads((root/'chat-zip-import-source-native.json').read_text('utf-8'));assert r['coldExact'] and r['providerRequests']==0 and r['actualPickerCancels']==2 and r['actualImports']==3;assert len(r['closes'])==2 and all(x['accepted'] and x['responding'] and x['exitCode']==0 for x in r['closes'])
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
private=0;assets=0
with connect(root/'workspace.sqlite3') as c:
 for wid,expected in [(r['ordinaryId'],r['ordinaryBefore']),(r['sourceId'],r['afterImport'])]:assert json.loads(c.execute('SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?',(wid,)).fetchone()[0])==expected['exchange']
 assert c.execute('SELECT count(*) FROM desktop_ordinary_chat_attempts').fetchone()[0]==0
 assert c.execute('SELECT status,count(*) FROM p6k_zip_import_tasks GROUP BY status').fetchall()==[('COMPLETED',3)]
 assert c.execute('SELECT status,count(*) FROM p6k_zip_import_items GROUP BY status ORDER BY status').fetchall()==[('COMMITTED',2),('SKIPPED',1)]
 receipts=c.execute('SELECT workspace_id,provider,conversation_id FROM p6k_zip_import_receipts').fetchall();assert len(receipts)==2 and all(x[0]==r['sourceId'] for x in receipts);assert {x[2] for x in receipts}=={r['gpt']['items'][0]['conversationId'],r['cl']['items'][0]['conversationId']};assert r['replay']['items'][0]['conversationId']==r['gpt']['items'][0]['conversationId']
 for task,zip_path in [(r['gpt'],r['gptZip']),(r['cl'],r['clZip']),(r['replay'],r['gptZip'])]:
  row=c.execute('SELECT status,workspace_id,provider,storage_key,byte_count,package_hash FROM p6k_zip_import_tasks WHERE id=?',(task['id'],)).fetchone();assert row[:3]==('COMPLETED',r['sourceId'],task['provider']);path=(root/row[3]).resolve(strict=True);assert path.is_relative_to(root);raw=path.read_bytes();assert len(raw)==row[4] and hashlib.sha256(raw).hexdigest()==row[5];assert raw==Path(zip_path).read_bytes();private+=1
  with zipfile.ZipFile(path) as z:assert z.testzip() is None and 'conversations.json' in z.namelist() and len(z.namelist())==2
 summary=c.execute('SELECT referenced_occurrences,recoverable_unique_assets,missing_unique_assets,fallback_name_unique_assets,restored_unique_assets,restored_occurrences FROM p6k_zip_import_attachment_summary WHERE task_id=?',(r['gpt']['id'],)).fetchone();assert summary==(3,1,1,0,1,2)
 ledger=c.execute("SELECT parent_source_object_id,source_object_id,state,local_object_id,revision FROM p6k_zip_official_identity_ledger WHERE workspace_id=? AND provider='CHATGPT' AND entity_kind='ATTACHMENT' ORDER BY source_object_id",(r['sourceId'],)).fetchall();assert ledger==[('chat-zip-gpt','a::synthetic-media.dat','IMPORTED',r['gptRefs'][0]['asset']['id'],1),('chat-zip-gpt','u::synthetic-media.dat','IMPORTED',r['gptRefs'][0]['asset']['id'],1)]
 assert c.execute("SELECT count(*) FROM p6k_zip_official_identity_ledger WHERE provider='CLAUDE' AND entity_kind='ATTACHMENT'").fetchone()[0]==0
 for conv in r['afterImport']['exchange']['conversations']:
  for m in conv['messages']:
   for b in m['blocks']:
    if b['kind']=='ASSET_REF':
     a=b['asset'];raw=(root/'assets'/a['sha256']).read_bytes();assert len(raw)==a['byteCount'] and hashlib.sha256(raw).hexdigest()==a['sha256'];assets+=1
 assert len(r['gptRefs'])==2 and r['gptRefs'][0]['asset']['id']==r['gptRefs'][1]['asset']['id'] and all(x['asset']['sha256']==r['pngSha256'] for x in r['gptRefs'])
with connect(root/'work-area/workspace.sqlite3') as c:
 assert json.loads(c.execute('SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?',(r['workId'],)).fetchone()[0])==r['workBefore']['exchange'];assert c.execute('SELECT count(*) FROM p6k_zip_import_tasks').fetchone()[0]==0;assert c.execute('SELECT count(*) FROM desktop_ordinary_chat_attempts').fetchone()[0]==0
print(json.dumps({'pass':True,'candidateSha256':r['candidateSha256'],'sqliteCount':count,'privateZipCopiesHashAndCrcExact':private,'completedTasks':3,'distinctImportedConversations':2,'officialOccurrenceLedgerExact':True,'sameAssetTwoMessageOccurrences':True,'replayDidNotDuplicateLedger':True,'unmappedClaudeNotAttached':True,'referencedAssetBytesExact':assets,'ordinaryAndHiddenWorkPreserved':True,'selectedSourceWholeExchangeColdExact':True,'providerRequests':0}))
