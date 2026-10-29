"""Independently audit real referenced assets and every backup manifest entry."""
import hashlib,json,sqlite3,sys,uuid,zipfile
from pathlib import Path
root=Path(sys.argv[1]).resolve(strict=True)
assert root.name.startswith('nanfeng-ai-windows-acceptance.')
assert str(root).lower().startswith('d:\\codextemp\\')
assert json.loads((root/'.nanfeng-windows-acceptance.json').read_text(encoding='utf-8-sig'))['identifier']=='com.nanzhufeng.ai.desktop.windowsacceptance'
e=json.loads((root/'referenced-backup-evidence.json').read_text(encoding='utf-8'))
assert e.get('preparationPass') is True and not e.get('forcedCleanup')
out={'root':str(root),'allOk':True,'areas':[]}
digest=lambda b:hashlib.sha256(b).hexdigest()
def connection(path):
 c=sqlite3.connect(path.as_uri()+'?mode=ro',uri=True)
 assert c.execute('pragma integrity_check').fetchall()==[('ok',)]
 assert c.execute('pragma foreign_key_check').fetchall()==[]
 return c
for area,lib in e['areas'].items():
 base=root if area=='CHAT' else root/'work-area'
 c=connection(base/'workspace.sqlite3')
 refs=[];assets={};conversations=messages=0
 for item in lib['libraries']:
  wid=item['id']
  actual=json.loads(c.execute('select exchange_json from workspace_exchange where workspace_id=?',(wid,)).fetchone()[0])
  with zipfile.ZipFile(item['input']) as z:
   expected=json.loads(z.read('payload/conversations.json'))
   assert actual['conversations']==expected
   for conv in expected:
    conversations+=1;messages+=len(conv['messages'])
    for msg in conv['messages']:
     for block in msg['blocks']:
      if block['kind']=='ASSET_REF':
       a=block['asset'];refs.append((wid,conv['id'],msg['id'],a['id']));assets[a['sha256']]=a
       b=(base/'assets'/a['sha256']).read_bytes()
       assert len(b)==a['byteCount']>0 and digest(b)==a['sha256']
       assert b==z.read(a['entry'])
   with zipfile.ZipFile(item['exportPath']) as exported:
    assert json.loads(exported.read('payload/conversations.json'))==expected
    for a in item['assets']:assert exported.read(a['entry'])==z.read(a['entry'])
  draft=c.execute('select text from desktop_conversation_drafts_v1 where workspace_id=? and conversation_key=?',(wid,item['draftId'])).fetchone()
  assert draft[0]==item['draft']['text']
  draft_refs=c.execute('select attachment_id from desktop_conversation_draft_attachments_v1 where workspace_id=? and conversation_key=? order by position',(wid,item['draftId'])).fetchall()
  assert draft_refs==[(item['assets'][1]['id'],)]
  rows=c.execute('select attachment_id,mime_type,display_name,byte_count,sha256 from desktop_conversation_attachments where workspace_id=?',(wid,)).fetchall()
  assert sorted(rows)==sorted((a['id'],a['mimeType'],a['displayName'],a['byteCount'],a['sha256']) for a in item['assets'])
 assert conversations==1200 and messages==2400 and len(refs)==1200 and len(set(refs))==1200 and len(assets)==1200
 c.close()
 with zipfile.ZipFile(lib['backup']) as z:
  assert z.testzip() is None
  m=json.loads(z.read('manifest.json'));paths=[x['path'] for x in m['entries']]
  assert len(paths)==len(set(paths)) and set(z.namelist())==set(paths)|{'manifest.json'}
  for entry in m['entries']:
   b=z.read(entry['path']);assert len(b)==entry['bytes'] and digest(b)==entry['sha256']
  for sha,a in assets.items():
   b=z.read('assets/attachments/v1/'+sha);assert len(b)==a['byteCount'] and digest(b)==sha
  temp=root/('referenced-backup-inspection-'+area+'-'+uuid.uuid4().hex);temp.mkdir()
  db=temp/'snapshot.sqlite3';db.write_bytes(z.read('database/nanfeng-ai.snapshot'));snapshot=connection(db)
  counts={t:snapshot.execute('select count(*) from "'+t.replace('"','""')+'"').fetchone()[0] for t in m['tableCounts']}
  assert counts==m['tableCounts']
  tables=[x[0] for x in snapshot.execute("select name from sqlite_master where type='table'")]
  lineage={}
  current=connection(base/'workspace.sqlite3')
  for table in tables:
   if any(x in table.lower() for x in ('provenance','receipt','attempt','import_task')):
    sql='select * from "'+table.replace('"','""')+'"'
    before=snapshot.execute(sql).fetchall();after=current.execute(sql).fetchall()
    assert sorted(before,key=repr)==sorted(after,key=repr),(area,table)
    lineage[table]=len(before)
  for item in lib['libraries']:
   sql='select exchange_json from workspace_exchange where workspace_id=?'
   assert json.loads(snapshot.execute(sql,(item['id'],)).fetchone()[0])==json.loads(current.execute(sql,(item['id'],)).fetchone()[0])
  current.close();snapshot.close()
 assert m['scope'] and m['excluded'] and counts
 out['areas'].append({'area':area,'conversations':conversations,'messages':messages,'attachmentOccurrences':len(refs),'independentNonemptyAssets':len(assets),'physicalAssetBytes':sum(a['byteCount'] for a in assets.values()),'draftsWithReferences':3,'manifestEntries':len(paths),'allManifestHashesVerified':True,'archiveBytes':Path(lib['backup']).stat().st_size,'supportedDataKeys':{'scope':m['scope'],'excluded':m['excluded'],'tables':sorted(counts),'controlledRoots':sorted(set(p.split('/')[1] for p in paths if p.startswith('assets/')))},'snapshotCounts':counts,'lineageCountsConserved':lineage,'integrity':'ok','foreignKeyViolations':0})
print(json.dumps(out,ensure_ascii=False))
