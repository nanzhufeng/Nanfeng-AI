"""Seed historical facts only in an explicitly marked, stopped acceptance root."""
import json,sqlite3,sys,copy,hashlib,subprocess
from pathlib import Path
launch=Path(sys.argv[1]); evidence=Path(sys.argv[2]); l=json.loads(launch.read_text(encoding='utf-8-sig'));root=Path(l['root']).resolve(strict=True)
assert root.parent==launch.parent.resolve() and root.name.startswith('nanfeng-ai-windows-acceptance.')
marker=json.loads((root/'.nanfeng-windows-acceptance.json').read_text(encoding='utf-8'));assert marker['identifier']==l['identifier'] and root.name=='nanfeng-ai-windows-acceptance.'+marker['runId']
probe=subprocess.run(['powershell','-NoProfile','-Command',f"if(Get-Process -Id {int(l['pid'])} -ErrorAction SilentlyContinue){{exit 1}}"],capture_output=True);assert probe.returncode==0,'process must be stopped'
record=json.loads(evidence.read_text(encoding='utf-8'))
old=record['areas']['WORK']['workspaceId'];new=record['areas']['CHAT']['workspaceId'];db=root/'work-area/workspace.sqlite3'
with sqlite3.connect(db) as c:
 c.execute('PRAGMA foreign_keys=OFF')
 assert c.execute('select count(*) from workspaces where id=?',(old,)).fetchone()[0]==1
 exchange=json.loads(c.execute('select exchange_json from workspace_exchange where workspace_id=?',(old,)).fetchone()[0])
 def rewrite(value):
  if isinstance(value,dict):return {k:rewrite(v) for k,v in value.items()}
  if isinstance(value,list):return [rewrite(v) for v in value]
  return new if value==old else value
 exchange=rewrite(exchange)
 c.execute('update workspaces set id=? where id=?',(new,old))
 for (table,) in c.execute("select name from sqlite_master where type='table'").fetchall():
  if 'workspace_id' in [r[1] for r in c.execute('pragma table_info("'+table+'")')]:c.execute('update "'+table+'" set workspace_id=? where workspace_id=?',(new,old))
 c.execute('update workspace_exchange set exchange_json=? where workspace_id=?',(json.dumps(exchange,ensure_ascii=False),new))
 assert not c.execute('pragma foreign_key_check').fetchall()
 assert c.execute('pragma integrity_check').fetchone()[0]=='ok'
record['areas']['WORK']['workspaceId']=new
record['areas']['WORK']['rawHash']=hashlib.sha256(json.dumps(exchange,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()).hexdigest()
record['identitySeed']={'oldWorkWorkspaceId':old,'sharedWorkspaceId':new,'scope':'stopped marked synthetic WORK database only','foreignKeys':0}
evidence.write_text(json.dumps(record,ensure_ascii=False,indent=2),encoding='utf-8')
print('SEEDED same workspace ID in synthetic WORK; private assets retained')
