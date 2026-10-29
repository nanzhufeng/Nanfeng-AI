"""Read-only body/receipt preservation checks in a marked synthetic acceptance root."""
import hashlib,importlib.util,json,sys
from pathlib import Path
spec=importlib.util.spec_from_file_location('fault',Path(__file__).with_name('fault-cloud-restore-synthetic.py'))
fault=importlib.util.module_from_spec(spec);spec.loader.exec_module(fault)
def digest(value):return hashlib.sha256(json.dumps(value,ensure_ascii=False,separators=(',',':')).encode()).hexdigest()
def check(value):
 root=fault.marked(value);result={}
 for area,relative in [('CHAT','workspace.sqlite3'),('WORK','work-area/workspace.sqlite3')]:
  c=fault.connect(root/relative)
  bodies={t:c.execute(f'SELECT * FROM {t} ORDER BY 1').fetchall() for t in ['workspaces','workspace_exchange']}
  selected=c.execute('SELECT * FROM desktop_selected_conversation_sync ORDER BY 1').fetchall()
  attempts={t:c.execute(f'SELECT COUNT(*) FROM {t}').fetchone()[0] for t in ['desktop_ordinary_chat_attempts','desktop_ocr_attempts_v1','desktop_transcription_tasks','desktop_conversation_title_attempts']}
  assert all(v==0 for v in attempts.values());assert c.execute('PRAGMA integrity_check').fetchone()[0]=='ok';assert c.execute('PRAGMA foreign_key_check').fetchall()==[]
  result[area]={'bodyHash':digest(bodies),'receiptHash':digest(selected),'selectedCount':len(selected),'workspaceCount':len(bodies['workspaces']),'attempts':attempts};c.close()
 return result
if __name__=='__main__':print(json.dumps(check(sys.argv[1]),ensure_ascii=False,indent=2))
