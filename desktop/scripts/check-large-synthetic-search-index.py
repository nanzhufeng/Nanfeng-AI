"""Read-only exact synthetic tuple validation; never repairs SQLite or its index."""
import json,sqlite3,sys
from pathlib import Path
root=Path(sys.argv[1]).resolve(strict=True)
marker=json.loads((root/'.nanfeng-windows-acceptance.json').read_text(encoding='utf-8-sig'))
assert marker['identifier']=='com.nanzhufeng.ai.desktop.windowsacceptance'
evidence=json.loads((root/'large-search-evidence.json').read_text(encoding='utf-8'))
assert evidence['pass'] is True
result={'root':str(root),'areas':{}}
for area in ['CHAT','WORK']:
 base=root if area=='CHAT' else root/'work-area'
 candidates=[base/'workspace.sqlite3']
 assert candidates[0].is_file()
 con=sqlite3.connect(candidates[0].as_uri()+'?mode=ro',uri=True)
 try:
  rows=con.execute("SELECT entry_id,conversation_id,message_id,content_kind,title,snippet FROM desktop_local_search_index WHERE conversation_id LIKE ?",('w13-'+area.lower()+'-%',)).fetchall()
  expected=set()
  for count in [100,300,600]:
   for i in range(count):
    cid=f'w13-{area.lower()}-{count}-{i}'
    expected.add((cid+':title',cid,None,'TITLE'))
    for suffix in ['u','a']:expected.add((cid+':'+cid+'-'+suffix+':text',cid,cid+'-'+suffix,'TEXT'))
  assert {tuple(row[:4]) for row in rows}==expected
  assert len(rows)==3000
  rename=evidence['areas'][area]['rename']
  renamed=[row for row in rows if row[1]==rename['conversationId']]
  assert len(renamed)==3 and all(row[4]==rename['title'] for row in renamed)
  old=con.execute("SELECT count(*) FROM desktop_local_search_index WHERE instr(normalized_text,'searchneedle')>0").fetchone()[0]
  new=con.execute("SELECT count(*) FROM desktop_local_search_index WHERE instr(normalized_text,'renamedneedle')>0").fetchone()[0]
  assert old==2999 and new==1,(old,new)
  assert [r[0] for r in con.execute('PRAGMA integrity_check')]==['ok']
  assert not list(con.execute('PRAGMA foreign_key_check'))
  stale=con.execute("SELECT count(*) FROM workspace_exchange e LEFT JOIN workspaces w ON w.id=e.workspace_id LEFT JOIN desktop_local_search_index_state s ON s.workspace_id=e.workspace_id WHERE w.id IS NULL OR s.workspace_id IS NULL OR s.index_version<>2 OR s.semantic_hash<>w.semantic_hash").fetchone()[0]
  result['areas'][area]={'staleWorkspaceIndexes':stale,'exactIndexTuples':len(rows),'conversations':1000,'messages':2000,'oldQuery':old,'renamedQuery':new,'integrity':'ok','foreignKeyViolations':0}
 finally:con.close()
print(json.dumps(result,ensure_ascii=False))
