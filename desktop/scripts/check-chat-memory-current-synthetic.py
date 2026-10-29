import json,sqlite3,subprocess,sys
from pathlib import Path
root=Path(sys.argv[1]).resolve(strict=True);assert root.is_relative_to(Path('D:/CodexTemp/nfa-chat-memory-current').resolve())
load=lambda p:json.loads(p.read_text(encoding='utf-8-sig'))
assert load(root/'.nanfeng-windows-acceptance.json')['identifier']=='com.nanzhufeng.ai.desktop.windowsacceptance'
d=load(root/'chat-memory-current.json');assert d['pass'] and d['providerRequests']==0 and len(d['closes'])==2;assert all(x['responding'] and x['accepted'] and x['exitCode']==0 for x in d['closes'])
with sqlite3.connect((root/'workspace.sqlite3').as_uri()+'?mode=ro',uri=True) as c:
 ex=json.loads(c.execute('select exchange_json from workspace_exchange where workspace_id=?',(d['workspaceId'],)).fetchone()[0]);assert ex==d['after']['exchange']
 active=[x for x in ex['memory'] if x['status']=='ACTIVE' and x['scope']=='GLOBAL'];assert len(active)==1 and active[0]['title']=='概览'
 for text in ['【工作偏好】','合成记忆编辑验收 a','并发更新：保留当前版本。','【写作习惯】','合成记忆编辑验收 b','补充：使用完整主题正文。']:assert text in active[0]['body']
 assert len([x for x in ex['memory'] if x['status']=='DELETED' and x['scope']=='GLOBAL'])==1
 assert [x for x in ex['memory'] if x['scope']!='GLOBAL']==d['scopedBefore']
 assert c.execute('select count(*) from desktop_ordinary_chat_attempts').fetchone()[0]==0
with sqlite3.connect((root/'work-area/workspace.sqlite3').as_uri()+'?mode=ro',uri=True) as c:
 ex=json.loads(c.execute('select exchange_json from workspace_exchange where workspace_id=?',(d['workId'],)).fetchone()[0]);assert ex==d['workBefore']['exchange']
integrity=json.loads(subprocess.check_output([sys.executable,str(Path(__file__).with_name('check-stability-synthetic-sqlite.py')),str(root),'--closed-browser'],encoding='utf-8'));assert integrity['allOk']
pids=[int(x['pid']) for x in d['closes']];assert subprocess.check_output(['pwsh','-NoProfile','-Command','@(Get-Process -Id '+','.join(map(str,pids))+' -ErrorAction SilentlyContinue).Count'],encoding='utf-8').strip()=='0'
print(json.dumps({'pass':True,'root':str(root),'globalActive':1,'globalDeleted':1,'summaryWholeBodyExact':True,'nonGlobalMemoryExact':True,'workExchangeExact':True,'providerRequests':0,'ordinaryAttempts':0,'naturalCloses':2,'exitCodes':[0,0],'ownedPidsRemaining':0,'integrity':integrity},ensure_ascii=False,indent=2))
