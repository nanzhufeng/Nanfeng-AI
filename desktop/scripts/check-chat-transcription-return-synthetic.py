import json,sqlite3,hashlib,subprocess,sys
from pathlib import Path
root=Path(sys.argv[1]).resolve(strict=True)
assert root.is_relative_to(Path('D:/CodexTemp/nfa-chat-transcription-return').resolve())
load=lambda p:json.loads(p.read_text(encoding='utf-8-sig'))
assert load(root/'.nanfeng-windows-acceptance.json')['identifier']=='com.nanzhufeng.ai.desktop.windowsacceptance'
d=load(root/'ocr-current-chain-native.json');assert d['pass'] and d['leaveWhileRunning'] and d['completedOffPageWithoutNavigation'];assert len(d['closes'])==2 and not d['forcedCleanup'];assert all(x['accepted'] and x['responding'] for x in d['closes'])
with sqlite3.connect((root/'workspace.sqlite3').as_uri()+'?mode=ro',uri=True) as c:
 c.row_factory=sqlite3.Row
 task=dict(c.execute('select * from desktop_transcription_tasks where id=?',(d['taskId'],)).fetchone());assert task['state']=='COMPLETED' and task['attempt_count']==task['provider_request_count']==1
 for kind in ['source','result']:
  b=(root/'assets'/task[kind+'_sha256']).read_bytes();assert len(b)==task[kind+'_byte_count'];assert hashlib.sha256(b).hexdigest()==task[kind+'_sha256']
 assert (root/'assets'/task['result_sha256']).read_text(encoding='utf-8')=='# 合成 OCR 正文\n\n第二行仅用于隔离验收。'
 ex=json.loads(c.execute('select exchange_json from workspace_exchange where workspace_id=?',(d['workspaceId'],)).fetchone()[0]);assert ex==d['sourceBefore']['exchange']
 draft=dict(c.execute('select * from desktop_conversation_drafts_v1 where workspace_id=? and conversation_key=?',(d['workspaceId'],d['originConversationId'])).fetchone());assert draft['text']=='运行态返回必须保留的合成草稿'
 assert c.execute('select count(*) from desktop_ordinary_chat_attempts').fetchone()[0]==0
 attempts=c.execute('select state,input_tokens,output_tokens,charge_micros from desktop_ocr_attempts_v1').fetchall();assert [tuple(x) for x in attempts]==[('COMPLETED',12,8,4)]
with sqlite3.connect((root/'work-area/workspace.sqlite3').as_uri()+'?mode=ro',uri=True) as c:
 assert c.execute('select count(*) from desktop_transcription_tasks').fetchone()[0]==0
 assert c.execute('select count(*) from desktop_ocr_attempts_v1').fetchone()[0]==0
integrity=json.loads(subprocess.check_output([sys.executable,str(Path(__file__).with_name('check-stability-synthetic-sqlite.py')),str(root),'--closed-browser'],encoding='utf-8'));assert integrity['allOk']
assert subprocess.check_output(['pwsh','-NoProfile','-Command','@(Get-Process -Id '+','.join(str(int(x)) for x in d['pids'])+' -ErrorAction SilentlyContinue).Count'],encoding='utf-8').strip()=='0'
print(json.dumps({'pass':True,'root':str(root),'taskAndAttempts':True,'sourceAndResultHashExact':True,'sourceExchangeExact':True,'draftTextExact':True,'ordinaryRequests':0,'ocrRequests':1,'naturalCloses':2,'ownedPidsRemaining':0,'integrity':integrity},ensure_ascii=False,indent=2))
