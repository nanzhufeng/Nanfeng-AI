"""Read-only checks for marked Windows explicit-retry receipts."""
import hashlib,json,sqlite3,sys
from pathlib import Path
root=Path(sys.argv[1]).resolve(strict=True)
assert root.is_relative_to(Path('D:/CodexTemp').resolve())
assert json.loads((root/'.nanfeng-windows-acceptance.json').read_text('utf-8-sig'))['identifier']=='com.nanzhufeng.ai.desktop.windowsacceptance'
r=json.loads((root/'ordinary-explicit-retry-native.json').read_text('utf-8'))
assert r['providerRequests']==2 and r['failureColdExact'] and r['completedColdExact']
assert len(r['requestFacts'])==2 and r['requestFacts'][0]['messagesSha256']==r['requestFacts'][1]['messagesSha256']
assert all(f['questionPresent'] and f['attachmentPresent'] for f in r['requestFacts'])
assert len(r['closes'])==3 and all(v['accepted'] and v['responding'] and v['exitCode']==0 for v in r['closes'])
def connect(p):return sqlite3.connect(p.as_uri()+'?mode=ro',uri=True)
checked=[]
for p in root.rglob('*'):
 if not p.is_file():continue
 with p.open('rb') as f:header=f.read(16)
 if header!=b'SQLite format 3\x00':continue
 with connect(p) as c:
  assert c.execute('PRAGMA integrity_check').fetchall()==[('ok',)],str(p)
  assert c.execute('PRAGMA foreign_key_check').fetchall()==[],str(p)
 checked.append(str(p.relative_to(root)))
a=r['completed']
with connect(root/'workspace.sqlite3') as c:
 row=c.execute('SELECT attempt_id,workspace_id,conversation_id,user_message_id,assistant_message_id,state,safe_error_code,retry_count,terminal_at_ms FROM desktop_ordinary_chat_attempts').fetchall()
 assert len(row)==1
 assert row[0][:8]==(a['attemptId'],r['workspaceId'],r['conversationId'],a['userMessageId'],a['assistantMessageId'],'COMPLETED',None,1)
 assert row[0][8] is not None
 exchange=json.loads(c.execute('SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?',(r['workspaceId'],)).fetchone()[0]);assert exchange==r['exchangeAfter']
 conversation=next(v for v in exchange['conversations'] if v['id']==r['conversationId'])
 assistant=next(m for m in conversation['messages'] if m['id']==a['assistantMessageId'])
 assert assistant['role']=='assistant' and assistant['delivery']=='COMPLETE'
 assert ''.join(b['text'] for b in assistant['blocks'] if b['kind']=='TEXT')==r['partialText']
 user=next(m for m in conversation['messages'] if m['id']==a['userMessageId']);assert user['role']=='user'
 assert ''.join(b['text'] for b in user['blocks'] if b['kind']=='TEXT')=='SYNTHETIC_EXPLICIT_RETRY_QUESTION'
 for conversation in exchange['conversations']:
  for message in conversation['messages']:
   for block in message['blocks']:
    if block['kind']=='ASSET_REF':
     asset=block['asset'];data=(root/'assets'/asset['sha256']).read_bytes();assert len(data)==asset['byteCount'];assert hashlib.sha256(data).hexdigest()==asset['sha256']
 failed=next(m for m in conversation['messages'] if m['id']==r['failedMessage']['id']);assert failed==r['failedMessage'] and failed['delivery']=='FAILED';assert failed['parentId']==assistant['parentId']==user['id'];assert failed['attemptId']==assistant['attemptId']==a['attemptId'];assert failed['id']!=assistant['id'];assert conversation['currentLeafId']==assistant['id']
 assert any(b['kind']=='ASSET_REF' and b['asset']=={k:v for k,v in r['asset'].items() if k!='classification'} for b in user['blocks']);assert r['asset']['classification']=='NORMAL'
 diagnostics=c.execute("SELECT state,safe_error_code,http_status FROM desktop_ordinary_chat_diagnostics WHERE attempt_id=?",(a['attemptId'],)).fetchall();assert ('FAILED','SERVICE_UNAVAILABLE',503) in diagnostics
with connect(root/'usage-ledger/usage-ledger-v1.sqlite3') as c:
 entries=c.execute('SELECT attempt_id,conversation_id,branch_leaf_message_id,kind,fact_grade,input_tokens,output_tokens,charge_micros,cost_source FROM usage_ledger_entries').fetchall()
 assert entries==[(a['attemptId'],r['conversationId'],a['assistantMessageId'],'FINAL_MEASURED','PROVIDER_REPORTED',5,7,9,'PROVIDER_RESPONSE')]
print(json.dumps({'pass':True,'sqliteCount':len(checked),'sameAttemptRetryCount':1,'retrySiblingIdentityExact':True,'originalFailureDiagnostic503':True,'orderedMaterialsWireHashExact':True,'measuredLedgerEntries':1,'chargeMicros':9,'providerRequests':2,'candidateSha256':r['candidateSha256']}))
