"""Read-only native CHAT restore and hidden WORK data preservation checks."""
import hashlib,json,sqlite3,sys,zipfile
from pathlib import Path
base=Path(sys.argv[1]).resolve(strict=True)
facts=json.loads((base/'native-current.json').read_text('utf-8'))
root=Path(facts['root']).resolve(strict=True)
assert root.is_relative_to(Path('D:/CodexTemp').resolve())
assert json.loads((root/'.nanfeng-windows-acceptance.json').read_text('utf-8-sig'))['identifier']=='com.nanzhufeng.ai.desktop.windowsacceptance'
assert facts['pass'] and len(facts['normalCloses'])==3
assert all(v['accepted'] and v['responding'] and v['title'] for v in facts['normalCloses'])
assert facts['areas']['CHAT']['cancelReadOnly'] and facts['areas']['CHAT']['otherAreaPreserved']
assert facts['areas']['CHAT']['coldStatus']['operation']=='COMPLETED'
assert not facts['areas']['CHAT']['coldStatus']['restartRequired'] and not facts['areas']['CHAT']['coldStatus']['rollbackPending']
assert len(facts['refusals'])==1 and facts['refusals'][0]=={'area':'CHAT','crossArea':True}
def connect(path):return sqlite3.connect(path.as_uri()+'?mode=ro',uri=True)
areas={}
for area in ['CHAT','WORK']:
 item=facts['areas'][area];area_root=root if area=='CHAT' else root/'work-area'
 with connect(area_root/'workspace.sqlite3') as c:
  for projection in facts['before'][area]:
   value=json.loads(c.execute('SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?',(projection['summary']['id'],)).fetchone()[0]);assert value==projection['exchange']
  d=item['draft'];row=c.execute('SELECT text,model_id,tone_override,web_search_override FROM desktop_conversation_drafts_v1 WHERE workspace_id=? AND conversation_key=?',(item['workspaceId'],item['conversationId'])).fetchone();assert row==(d['text'],d['modelId'],d['toneOverride'],d['webSearchOverride'])
  assert c.execute('SELECT COUNT(*) FROM desktop_ordinary_chat_attempts').fetchone()[0]==0
 data=Path(item['privateAsset']).read_bytes();assert hashlib.sha256(data).hexdigest()==item['assetHash'] and len(data)==46
 with zipfile.ZipFile(item['backup']) as z:
  assert z.testzip() is None and len(z.namelist())==len(set(z.namelist()))
  manifest=json.loads(z.read('manifest.json'))
  for entry in manifest['entries']:
   raw=z.read(entry['path']);assert len(raw)==entry['bytes'];assert hashlib.sha256(raw).hexdigest()==entry['sha256']
  areas[area]={'exchangeAndDraftExact':True,'assetBytes':len(data),'fullPackageHashesAndCRC':True,'packageEntries':len(z.namelist()),'ordinaryAttempts':0,'uiRestorePerformed':area=='CHAT'}
checked=[]
for path in root.rglob('*'):
 if not path.is_file():continue
 with path.open('rb') as f:header=f.read(16)
 if header!=b'SQLite format 3\x00':continue
 with connect(path) as c:
  assert c.execute('PRAGMA integrity_check').fetchall()==[('ok',)],str(path)
  assert c.execute('PRAGMA foreign_key_check').fetchall()==[],str(path)
 checked.append(str(path.relative_to(root)))
print(json.dumps({'pass':True,'candidateSha256':facts['sha256'],'sqliteCount':len(checked),'normalCloses':len(facts['normalCloses']),'areas':areas}))
