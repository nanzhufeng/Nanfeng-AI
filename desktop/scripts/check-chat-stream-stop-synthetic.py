"""Read-only checks for marked Windows actual-stream cancellation receipts."""
import hashlib,json,sqlite3,sys
from pathlib import Path
root=Path(sys.argv[1]).resolve(strict=True)
assert root.is_relative_to(Path('D:/CodexTemp').resolve())
assert json.loads((root/'.nanfeng-windows-acceptance.json').read_text('utf-8-sig'))['identifier']=='com.nanzhufeng.ai.desktop.windowsacceptance'
r=json.loads((root/'ordinary-stream-stop-native.json').read_text('utf-8'))
assert r['providerRequests']==1 and r['coldExact']
assert len(r['closes'])==2 and all(v['accepted'] and v['responding'] and v['exitCode']==0 for v in r['closes'])
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
a=r['cancelled']
with connect(root/'workspace.sqlite3') as c:
 row=c.execute('SELECT attempt_id,workspace_id,conversation_id,user_message_id,assistant_message_id,state,safe_error_code,retry_count,terminal_at_ms FROM desktop_ordinary_chat_attempts').fetchall()
 assert len(row)==1
 assert row[0][:8]==(a['attemptId'],r['workspaceId'],r['conversationId'],a['userMessageId'],a['assistantMessageId'],'CANCELLED','CANCELLED',0)
 assert row[0][8] is not None
 exchange=json.loads(c.execute('SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?',(r['workspaceId'],)).fetchone()[0]);assert exchange==r['exchangeAfter']
 conversation=next(v for v in exchange['conversations'] if v['id']==r['conversationId'])
 assistant=next(m for m in conversation['messages'] if m['id']==a['assistantMessageId'])
 assert assistant['role']=='assistant' and assistant['delivery']=='CANCELLED'
 assert ''.join(b['text'] for b in assistant['blocks'] if b['kind']=='TEXT')==r['partialText']
 user=next(m for m in conversation['messages'] if m['id']==a['userMessageId']);assert user['role']=='user'
 assert ''.join(b['text'] for b in user['blocks'] if b['kind']=='TEXT')=='SYNTHETIC_STREAM_STOP_QUESTION'
 for conversation in exchange['conversations']:
  for message in conversation['messages']:
   for block in message['blocks']:
    if block['kind']=='ASSET_REF':
     asset=block['asset'];data=(root/'assets'/asset['sha256']).read_bytes();assert len(data)==asset['byteCount'];assert hashlib.sha256(data).hexdigest()==asset['sha256']
print(json.dumps({'pass':True,'sqliteCount':len(checked),'attemptIdentityExact':True,'cancelledPartialTextExact':True,'coldExchangeExact':True,'providerRequests':1,'candidateSha256':r['candidateSha256']}))
