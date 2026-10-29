"""Read-only settings queue result, unchanged business data and integrity checks."""
import hashlib,json,re,sqlite3,sys
from pathlib import Path
root=Path(sys.argv[1]).resolve(strict=True)
assert root.is_relative_to(Path('D:/CodexTemp').resolve())
assert json.loads((root/'.nanfeng-windows-acceptance.json').read_text('utf-8-sig'))['identifier']=='com.nanzhufeng.ai.desktop.windowsacceptance'
r=json.loads((root/'chat-settings-save-refusal-native.json').read_text('utf-8'))
assert r['coldExact'] and r['writeLockReleased'] and r['failedCommitSettingsUnchanged'] and r['providerRequests']==0
assert r['failureFields']==r['expectedFields'] and r['after']['revision']==r['before']['revision']+1
assert r['after']['appearance']==r['before']['appearance']
assert r['after']['product']=={**r['before']['product'],**r['expectedFields']}
assert len(r['closes'])==2 and all(x['accepted'] and x['responding'] and x['exitCode']==0 for x in r['closes'])
def connect(p):
 c=sqlite3.connect(p.as_uri()+'?mode=ro',uri=True);c.row_factory=sqlite3.Row;return c
with connect(root/'workspace.sqlite3') as c:
 rows=c.execute('SELECT * FROM desktop_app_settings').fetchall();assert len(rows)==1;row=dict(rows[0])
 mapping={'mode':'appearance_mode','fontSize':'font_size','themeColor':'theme_color'}
 for key,value in r['after']['appearance'].items():assert row[mapping[key]]==value
 for key,value in r['after']['product'].items():
  if key=='interests':
   assert c.execute('SELECT interests FROM desktop_portable_personalization_v1 WHERE id=1').fetchone()[0]==value
   continue
  column=re.sub(r'(?<!^)(?=[A-Z])','_',key).lower();assert row[column]==value,(key,column)
 assert row['revision']==r['after']['revision']
 exchange=json.loads(c.execute('SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?',(r['workspaceId'],)).fetchone()[0]);assert exchange==r['beforeExchange']['exchange']
 assert c.execute('SELECT count(*) FROM desktop_ordinary_chat_attempts').fetchone()[0]==0
count=0
for path in root.rglob('*'):
 if not path.is_file():continue
 with path.open('rb') as f:header=f.read(16)
 if header!=b'SQLite format 3\x00':continue
 with connect(path) as c:
  assert [tuple(row) for row in c.execute('PRAGMA integrity_check')]==[('ok',)]
  assert c.execute('PRAGMA foreign_key_check').fetchall()==[]
 count+=1
print(json.dumps({'pass':True,'candidateSha256':r['candidateSha256'],'sqliteCount':count,'fullStoredSettingsExact':True,'businessExchangeUnchanged':True,'revisionDelta':1,'coldExact':True,'editDraftPreservedOnRefusal':True,'providerRequests':0,'naturalExitCodes':[x['exitCode'] for x in r['closes']]}))
