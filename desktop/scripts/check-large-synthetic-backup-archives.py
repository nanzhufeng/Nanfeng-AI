import hashlib,json,sqlite3,sys,uuid,zipfile
from pathlib import Path
root=Path(sys.argv[1]).resolve()
assert root.name.startswith('nanfeng-ai-windows-acceptance.')
assert str(root).lower().startswith('d:\\codextemp\\'.lower())
marker=json.loads((root/'.nanfeng-windows-acceptance.json').read_text(encoding='utf-8-sig'))
assert marker['identifier']=='com.nanzhufeng.ai.desktop.windowsacceptance'
results=[]
for area in ['CHAT','WORK']:
 p=root/f'synthetic-w15-{area}.nfai-backup'
 with zipfile.ZipFile(p) as z:
  assert z.testzip() is None
  m=json.loads(z.read('manifest.json'))
  paths=[e['path'] for e in m['entries']]
  assert len(paths)==len(set(paths))
  assert set(z.namelist())==set(paths)|{'manifest.json'}
  for e in m['entries']:
   b=z.read(e['path']);assert len(b)==e['bytes'];assert hashlib.sha256(b).hexdigest()==e['sha256']
  controlled={k:sum(x.startswith('assets/'+k+'/w15-large-synthetic/') for x in paths) for k in ['attachments/v1','workspace-packages/v1','zip-import-assets/v1','workspace-exchange-archives/v2']}
  assert all(n==300 for n in controlled.values()),controlled
  temp=root/('large-backup-inspect-'+area+'-'+uuid.uuid4().hex)
  temp.mkdir()
  db=Path(temp)/'snapshot.sqlite3';db.write_bytes(z.read('database/nanfeng-ai.snapshot'))
  c=sqlite3.connect(f'file:{db.as_posix()}?mode=ro',uri=True)
  assert c.execute('pragma integrity_check').fetchall()==[('ok',)]
  assert c.execute('pragma foreign_key_check').fetchall()==[]
  counts={name:c.execute('select count(*) from "'+name.replace('"','""')+'"').fetchone()[0] for name in m['tableCounts']}
  assert counts==m['tableCounts'];c.close()
 results.append({'area':area,'archiveSha256':hashlib.sha256(p.read_bytes()).hexdigest(),'archiveBytes':p.stat().st_size,'manifestEntries':len(paths),'controlledFileCounts':controlled,'schemaVersion':m['schemaVersion'],'tableCounts':counts,'allEntryHashesVerified':True,'snapshotIntegrity':'ok','foreignKeyViolations':0})
out={'root':str(root),'syntheticOnly':True,'archives':results}
(root/'large-backup-archive-inspection.json').write_text(json.dumps(out,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'archives':len(results),'entries':[r['manifestEntries'] for r in results],'allVerified':True}))
