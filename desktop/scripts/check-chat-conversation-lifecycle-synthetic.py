import json,sqlite3,subprocess,sys
from pathlib import Path
root=Path(sys.argv[1]).resolve(strict=True);assert root.is_relative_to(Path('D:/CodexTemp/nfa-chat-conversation-lifecycle').resolve())
load=lambda p:json.loads(p.read_text(encoding='utf-8-sig'))
assert load(root/'.nanfeng-windows-acceptance.json')['identifier']=='com.nanzhufeng.ai.desktop.windowsacceptance'
d=load(root/'chat-conversation-lifecycle.json');assert d['pass'] and d['providerRequests']==0;assert len(d['closes'])==2 and all(x['responding'] and x['accepted'] and x['exitCode']==0 for x in d['closes'])
with sqlite3.connect((root/'workspace.sqlite3').as_uri()+'?mode=ro',uri=True) as c:
 ex=json.loads(c.execute('select exchange_json from workspace_exchange where workspace_id=?',(d['workspaceId'],)).fetchone()[0]);assert ex==d['after']['exchange']
 recycled=next(x for x in ex['conversations'] if x['id']==d['actions']['conversationId']);assert recycled['deleted'] and len(recycled['messages'])==1
 restored=next(x for x in ex['conversations'] if x['id']==d['actions']['favoriteAndRestore']['conversationId']);assert not restored.get('deleted',False) and not restored['archived'] and restored['pinned'];assert len(restored['messages'])==1 and '收藏归档恢复后保留正文' in json.dumps(restored['messages'],ensure_ascii=False)
 favorites=[x[0] for x in c.execute('select conversation_id from desktop_conversation_favorites where workspace_id=?',(d['workspaceId'],))];assert set(favorites)==set(d['favoritesBefore']) and restored['id'] not in favorites
 assert [x for x in ex['conversations'] if x['id'] not in [recycled['id'],restored['id']]]==d['before']['exchange']['conversations']
 for k in ['projects','knowledge','memory','relations']:assert ex[k]==d['before']['exchange'][k]
 assert c.execute('select count(*) from desktop_ordinary_chat_attempts').fetchone()[0]==0
with sqlite3.connect((root/'work-area/workspace.sqlite3').as_uri()+'?mode=ro',uri=True) as c:
 ex=json.loads(c.execute('select exchange_json from workspace_exchange where workspace_id=?',(d['workId'],)).fetchone()[0]);assert ex==d['workBefore']['exchange']
integrity=json.loads(subprocess.check_output([sys.executable,str(Path(__file__).with_name('check-stability-synthetic-sqlite.py')),str(root),'--closed-browser'],encoding='utf-8'));assert integrity['allOk']
pids=[int(x['pid']) for x in d['closes']];assert subprocess.check_output(['pwsh','-NoProfile','-Command','@(Get-Process -Id '+','.join(map(str,pids))+' -ErrorAction SilentlyContinue).Count'],encoding='utf-8').strip()=='0'
print(json.dumps({'pass':True,'root':str(root),'recycledRetained':True,'restoredBodyAndPinExact':True,'favoritesRestoredToBaseline':True,'otherConversationsAndAreasExact':True,'ordinaryAttempts':0,'normalExitCodes':[0,0],'ownedPidsRemaining':0,'integrity':integrity},ensure_ascii=False,indent=2))
