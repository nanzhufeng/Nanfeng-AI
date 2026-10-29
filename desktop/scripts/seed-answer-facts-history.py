"""Seed historical facts only in an explicitly marked, stopped acceptance root."""
import json,sqlite3,sys,copy,hashlib,subprocess
from pathlib import Path
launch=Path(sys.argv[1]); evidence=Path(sys.argv[2]); l=json.loads(launch.read_text(encoding='utf-8-sig'));root=Path(l['root']).resolve(strict=True)
assert root.parent==launch.parent.resolve() and root.name.startswith('nanfeng-ai-windows-acceptance.')
marker=json.loads((root/'.nanfeng-windows-acceptance.json').read_text(encoding='utf-8'));assert marker['identifier']==l['identifier'] and root.name=='nanfeng-ai-windows-acceptance.'+marker['runId']
probe=subprocess.run(['powershell','-NoProfile','-Command',f"if(Get-Process -Id {int(l['pid'])} -ErrorAction SilentlyContinue){{exit 1}}"],capture_output=True);assert probe.returncode==0,'process must be stopped'
record=json.loads(evidence.read_text(encoding='utf-8'))
for area,rel in [('CHAT','workspace.sqlite3'),('WORK','work-area/workspace.sqlite3')]:
 saved=record['areas'][area]; db=root/rel
 with sqlite3.connect(db) as c:
  c.execute('PRAGMA foreign_keys=ON');assert c.execute('select count(*) from desktop_ordinary_chat_attempts').fetchone()[0]==0
  exchange=json.loads(c.execute('select exchange_json from workspace_exchange where workspace_id=?',(saved['workspaceId'],)).fetchone()[0]);conv=next(x for x in exchange['conversations'] if x['id']==saved['conversationId']); user,assistant=conv['messages'];conv['messages']=[];saved['caseMessageIds']={};parent=None
  for idx,(name,route,verified) in enumerate([('missing','NONE',None),('legacy-false','NONE',None),('disabled','NONE',0),('requested','NATIVE',0),('verified','NATIVE',1)]):
   u=copy.deepcopy(user);u['id']=f'facts-user-{idx}';u['parentId']=parent;a=copy.deepcopy(assistant);a['id']=f'facts-assistant-{idx}';a['parentId']=u['id'];a['blocks']=[{'kind':'TEXT','ordinal':0,'text':f'Synthetic {area} {name} answer'}];conv['messages'] += [u,a];parent=a['id'];saved['caseMessageIds'][name]=a['id']
   if name=='missing':continue
   attempt=f'facts-attempt-{idx}'
   c.execute("INSERT INTO desktop_ordinary_chat_attempts(attempt_id,workspace_id,conversation_id,user_message_id,assistant_message_id,idempotency_key,request_fingerprint,state,created_at_ms,updated_at_ms,web_search_route,web_search_verified) VALUES(?,?,?,?,?,?,?,'COMPLETED',?,?,?,?)",(attempt,saved['workspaceId'],conv['id'],u['id'],a['id'],attempt,'synthetic-history',1790830000000+idx,1790830000000+idx,route,verified))
   for ordinal,(kind,title,sid) in enumerate([('对话风格','基础风格和语气','tone:direct'),('资料库','协议资料\n第二行😀','synthetic-knowledge'),('资料库','<img src=x onerror=synthetic>','synthetic-markup')]):c.execute('INSERT INTO desktop_ordinary_chat_context_sources VALUES(?,?,?,?,?)',(attempt,ordinal,kind,sid,title))
  conv['currentLeafId']=parent
  raw=json.dumps(exchange,ensure_ascii=False,separators=(',',':'));c.execute('update workspace_exchange set exchange_json=? where workspace_id=?',(raw,saved['workspaceId']));saved['seededExchangeHash']=hashlib.sha256(json.dumps(exchange,ensure_ascii=False,separators=(',',':'),sort_keys=True).encode()).hexdigest();assert c.execute('pragma integrity_check').fetchone()[0]=='ok'
evidence.write_text(json.dumps(record,ensure_ascii=False,indent=2),encoding='utf-8');print('SEEDED dual-area historical facts; 4 attempts each; no provider calls')
