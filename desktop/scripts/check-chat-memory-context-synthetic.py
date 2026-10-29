import json,sqlite3,subprocess,sys
from pathlib import Path
root=Path(sys.argv[1]).resolve(strict=True);assert root.is_relative_to(Path('D:/CodexTemp/nfa-chat-memory-context').resolve())
load=lambda p:json.loads(p.read_text(encoding='utf-8-sig'))
assert load(root/'.nanfeng-windows-acceptance.json')['identifier']=='com.nanzhufeng.ai.desktop.windowsacceptance'
d=load(root/'chat-memory-context.json');assert d['pass'] and d['providerRequests']==len(d['requests'])==len(d['rounds'])==4;assert [x['enabled'] for x in d['rounds']]==[True,False,True,False]
with sqlite3.connect((root/'workspace.sqlite3').as_uri()+'?mode=ro',uri=True) as c:
 assert c.execute('pragma user_version').fetchone()[0]==56
 for ordinal,r in enumerate(d['rounds']):
  a=r['record']['attemptId'];sources=c.execute("select source_id from desktop_ordinary_chat_context_sources where attempt_id=? and source_kind='Memory'",(a,)).fetchall();assert sources==([(d['globalId'],)] if r['enabled'] else [])
  assert c.execute('select state from desktop_ordinary_chat_attempts where attempt_id=?',(a,)).fetchone()[0]=='COMPLETED'
  text=json.dumps(d['requests'][ordinal]['messages'],ensure_ascii=False);assert ('GLOBAL_MEMORY_CONTEXT_TOKEN' in text)==r['enabled'];assert 'PROJECT_MEMORY_CONTEXT_TOKEN' not in text and 'DELETED_MEMORY_CONTEXT_TOKEN' not in text
 ex=json.loads(c.execute('select exchange_json from workspace_exchange where workspace_id=?',(d['workspaceId'],)).fetchone()[0]);assert ex==d['after']['exchange'] and ex['memory']==d['seed']['exchange']['memory']
with sqlite3.connect((root/'work-area/workspace.sqlite3').as_uri()+'?mode=ro',uri=True) as c:
 ex=json.loads(c.execute('select exchange_json from workspace_exchange where workspace_id=?',(d['workId'],)).fetchone()[0]);assert ex==d['workBefore']['exchange'];assert c.execute('select count(*) from desktop_ordinary_chat_attempts').fetchone()[0]==0
with sqlite3.connect((root/'usage-ledger/usage-ledger-v1.sqlite3').as_uri()+'?mode=ro',uri=True) as c:
 ids={x[0] for x in c.execute('select attempt_id from usage_ledger_entries')};assert ids=={r['record']['attemptId'] for r in d['rounds']}
assert len(d['closes'])==2 and all(x['responding'] and x['accepted'] and x['exitCode']==0 for x in d['closes']);assert d['settings']['product']['memoryEnabled'] is False and d['settings']['product']['memoryRetrievalEnabled'] is False
integrity=json.loads(subprocess.check_output([sys.executable,str(Path(__file__).with_name('check-stability-synthetic-sqlite.py')),str(root),'--closed-browser'],encoding='utf-8'));assert integrity['allOk']
pids=[int(x['pid']) for x in d['closes']];assert subprocess.check_output(['pwsh','-NoProfile','-Command','@(Get-Process -Id '+','.join(map(str,pids))+' -ErrorAction SilentlyContinue).Count'],encoding='utf-8').strip()=='0'
print(json.dumps({'pass':True,'root':str(root),'onOffOnOffRequestExact':True,'persistentMemorySourceIdentityExact':True,'memoryUnchanged':True,'workExchangeExact':True,'ordinaryAttempts':4,'uniqueLedgerAttempts':4,'normalCloseExitCodes':[0,0],'integrity':integrity},ensure_ascii=False,indent=2))
