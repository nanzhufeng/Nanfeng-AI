"""Read-only verification of marked native stream-switch synthetic receipts."""
import json,sqlite3,sys,hashlib
from pathlib import Path
root=Path(sys.argv[1]).resolve(strict=True)
assert root.is_relative_to(Path('D:/CodexTemp').resolve())
assert json.loads((root/'.nanfeng-windows-acceptance.json').read_text('utf-8-sig'))['identifier']=='com.nanzhufeng.ai.desktop.windowsacceptance'
r=json.loads((root/'chat-stream-switch-native.json').read_text('utf-8'))
assert r['requests']==2 and r['coldExact'] and r['backgroundDidNotOverwriteDraft']
assert len(r['closes'])==3 and all(x['accepted'] and x['responding'] and x['exitCode']==0 for x in r['closes'])
def connect(p):return sqlite3.connect(p.resolve().as_uri()+'?mode=ro',uri=True)
checked=[]
for p in root.rglob('*'):
 if not p.is_file():continue
 with p.open('rb') as f:header=f.read(16)
 if header!=b'SQLite format 3\x00':continue
 with connect(p) as c:
  assert c.execute('PRAGMA integrity_check').fetchall()==[('ok',)]
  assert not c.execute('PRAGMA foreign_key_check').fetchall()
 checked.append(str(p.relative_to(root)))
with connect(root/'workspace.sqlite3') as c:
 rows=c.execute('SELECT attempt_id,workspace_id,conversation_id,user_message_id,assistant_message_id,state,retry_count,terminal_at_ms FROM desktop_ordinary_chat_attempts').fetchall()
 assert len(rows)==2
 exchange=json.loads(c.execute('SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?',(r['workspaceId'],)).fetchone()[0]);assert exchange==r['after']['exchange']
 for label,question,answer in [('A','SYNTHETIC_SWITCH_A_QUESTION','SYNTHETIC_SWITCH_A_PARTIAL 后台完成A'),('B','SYNTHETIC_SWITCH_B_DRAFT','SYNTHETIC_SWITCH_B_FINAL')]:
  a=r['completed'+label];row=next(x for x in rows if x[0]==a['attemptId']);assert row[:7]==(a['attemptId'],r['workspaceId'],r['conversation'+label],a['userMessageId'],a['assistantMessageId'],'COMPLETED',0);assert row[7] is not None
  conv=next(x for x in exchange['conversations'] if x['id']==a['conversationId']);u=next(x for x in conv['messages'] if x['id']==a['userMessageId']);v=next(x for x in conv['messages'] if x['id']==a['assistantMessageId']);assert u['role']=='user' and v['role']=='assistant' and v['delivery']=='COMPLETE';assert ''.join(x['text'] for x in u['blocks'] if x['kind']=='TEXT')==question;assert ''.join(x['text'] for x in v['blocks'] if x['kind']=='TEXT')==answer
  assert v['parentId']==u['id'] and conv['currentLeafId']==v['id']
 a=r['completedA'];b=r['completedB'];sources=c.execute('SELECT source_kind,source_id FROM desktop_ordinary_chat_context_sources WHERE attempt_id=?',(b['attemptId'],)).fetchall();assert ('历史对话',a['userMessageId']) in sources and ('历史对话',a['assistantMessageId']) in sources
 for conv in exchange['conversations']:
  for m in conv['messages']:
   for block in m['blocks']:
    if block['kind']=='ASSET_REF':
     asset=block['asset'];raw=(root/'assets'/asset['sha256']).read_bytes();assert len(raw)==asset['byteCount'] and hashlib.sha256(raw).hexdigest()==asset['sha256']
with connect(root/'work-area/workspace.sqlite3') as c:assert c.execute('SELECT count(*) FROM desktop_ordinary_chat_attempts').fetchone()[0]==0
facts=r['requestChecks'];assert facts[0]['userHasA'] and not facts[0]['userHasB'];assert not facts[1]['userHasA'] and facts[1]['userHasB'];assert all(x['userMessageCount']==1 for x in facts)
print(json.dumps({'pass':True,'sqliteCount':len(checked),'twoAttemptIdentitiesExact':True,'coldWholeExchangeExact':True,'currentUserPathSeparated':True,'historicalContextSourceIdsExact':True,'providerRequests':2,'candidateSha256':r['candidateSha256']}))
