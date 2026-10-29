"""Read-only official ZIP source/occurrence/private bytes/receipt audit."""
import json,sys,sqlite3,hashlib,zipfile
from pathlib import Path
root=Path(sys.argv[1]).resolve(strict=True)
marker=json.loads((root/'.nanfeng-windows-acceptance.json').read_text(encoding='utf-8-sig'))
assert marker['identifier']=='com.nanzhufeng.ai.desktop.windowsacceptance'
evidence=json.loads((root/'large-attachment-zip-evidence.json').read_text(encoding='utf-8'))
assert evidence.get('pass') is True and not evidence.get('forcedCleanup')
out={'root':str(root),'areas':[],'allOk':True}
for area,lib in evidence['areas'].items():
 base=root if area=='CHAT' else root/'work-area'
 c=sqlite3.connect((base/'workspace.sqlite3').as_uri()+'?mode=ro',uri=True)
 try:
  assert list(c.execute('PRAGMA integrity_check'))==[('ok',)]
  assert not list(c.execute('PRAGMA foreign_key_check'))
  ws=lib['workspaceId']; actual=json.loads(c.execute('SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?',(ws,)).fetchone()[0])
  snapshot=json.loads((root/('imported-'+area+'.json')).read_text(encoding='utf-8'))
  assert actual==snapshot
  with zipfile.ZipFile(root/(area+'-complete.nfai-exchange')) as z:
   exported=json.loads(z.read('payload/conversations.json'))
   schema=json.loads((Path(__file__).resolve().parents[2]/'protocol/nfai.exchange.v1.schema.json').read_text(encoding='utf-8'))['$defs']
   fields=lambda value,kind:{k:v for k,v in value.items() if k in schema[kind]['properties']}
   portable=[fields(conv,'conversation') for conv in json.loads(json.dumps(actual['conversations']))]
   for conv in portable:
    conv['messages']=[fields(m,'message') for m in conv['messages']]
    for m in conv['messages']:
     m['blocks']=[fields(b,'block') for b in m['blocks']]
     for ordinal,b in enumerate(m['blocks']):
      b.setdefault('ordinal',ordinal)
      if b['kind']=='ASSET_REF':
       b['asset'].setdefault('classification',actual['export']['sensitivity'])
       assert z.read(b['asset']['entry'])==(base/'assets'/b['asset']['sha256']).read_bytes()
   assert exported==portable
  with zipfile.ZipFile(lib['input']) as z:
   inputs=[row for name in z.namelist() if name.startswith('conversations-') for row in json.loads(z.read(name))]
   assets={name:z.read(name) for name in z.namelist() if name.endswith('.dat')}
  assert len(inputs)==576 and len(assets)==9
  rows=[r for r in actual['conversations'] if r['id']!=lib['seedId']]
  assert len(rows)==576 and sum(len(r['messages']) for r in rows)==1152
  refs=[]; identities={};sources=[];graph_sources=[]
  for source in inputs:
   conv=next(r for r in rows if r['title']==source['title'])
   sources.append((source['id'],conv['id']))
   graph='p6k-chatgpt-graph-'+hashlib.sha256(json.dumps({'sources':[source['id']]},ensure_ascii=False,separators=(',',':'),sort_keys=True).encode('utf-8')).hexdigest()[:24]
   graph_sources.append((graph,conv['id']))
   for m,node in zip(conv['messages'],source['mapping'].values()):
    sm=node['message'];assert m['role']==sm['author']['role']
    assert '\n'.join(b['text'] for b in m['blocks'] if b['kind']=='TEXT')==sm['content']['parts'][0]
    blocks=[b for b in m['blocks'] if b['kind']=='ASSET_REF'];assert len(blocks)==1
    a=blocks[0]['asset'];meta=sm['metadata']['attachments'][0]
    assert meta['id']==sm['content']['parts'][1]['asset_pointer'].removeprefix('file-service://')
    data=assets[meta['id']+'.dat'];assert a['displayName']==meta['name']
    assert a['byteCount']==len(data)>0 and a['sha256']==hashlib.sha256(data).hexdigest()
    assert (base/'assets'/a['sha256']).read_bytes()==data
    refs.append((ws,conv['id'],m['id'],a['id']));identities[a['id']]=a
    ledger=list(c.execute('SELECT state,local_object_id,content_hash FROM p6k_zip_official_identity_ledger WHERE workspace_id=? AND entity_kind=? AND parent_source_object_id=? AND source_object_id=?',(ws,'ATTACHMENT',source['id'],sm['id']+'::'+meta['id']+'.dat')))
    assert ledger==[('IMPORTED',a['id'],a['sha256'])]
  assert len(refs)==1152 and len(set(refs))==1152 and len(identities)==9
  attachments=list(c.execute('SELECT attachment_id,mime_type,display_name,byte_count,sha256 FROM desktop_conversation_attachments WHERE workspace_id=?',(ws,)))
  assert len(attachments)==9
  maintenance_cache=[]
  for aid,mime,name,size,digest in attachments:
   a=identities[aid];assert (mime,name,size,digest)==(a['mimeType'],a['displayName'],a['byteCount'],a['sha256'])
   live_count=sum(identities[r[3]]['sha256']==digest for r in refs)
   assert live_count==128
   cached=c.execute('SELECT reference_count FROM desktop_attachment_assets WHERE sha256=?',(digest,)).fetchone()[0]
   assert cached in (0,128)
   maintenance_cache.append({'sha256':digest,'liveOccurrences':live_count,'lastMaintenanceReferenceCount':cached})
  indexed=list(c.execute('SELECT workspace_id,conversation_id,message_id,attachment_id FROM desktop_local_search_index WHERE workspace_id=? AND attachment_id IS NOT NULL',(ws,)))
  assert sorted(indexed)==sorted(refs)
  provenance=list(c.execute('SELECT source_conversation_id,conversation_id FROM p6k_zip_import_provenance WHERE workspace_id=?',(ws,)))
  receipts=list(c.execute('SELECT source_conversation_id,conversation_id FROM p6k_zip_import_receipts WHERE workspace_id=?',(ws,)))
  assert sorted(provenance)==sorted(graph_sources) and sorted(receipts)==sorted(graph_sources)
  ledger=list(c.execute('SELECT entity_kind,state,local_object_id,parent_source_object_id,source_object_id FROM p6k_zip_official_identity_ledger WHERE workspace_id=?',(ws,)))
  kinds={k:sum(r[0]==k for r in ledger) for k in ('CONVERSATION','MESSAGE','ATTACHMENT')}
  assert kinds=={'CONVERSATION':576,'MESSAGE':1152,'ATTACHMENT':1152},kinds
  assert all(r[1]=='IMPORTED' for r in ledger)
  assert sorted(r[2] for r in ledger if r[0]=='CONVERSATION')==sorted(r[1] for r in sources)
  assert sorted(r[2] for r in ledger if r[0]=='MESSAGE')==sorted(r[2] for r in refs)
  assert sorted(r[2] for r in ledger if r[0]=='ATTACHMENT')==sorted(r[3] for r in refs)
  for task in lib['taskIds']:
   row=c.execute('SELECT status,storage_key,package_hash,byte_count FROM p6k_zip_import_tasks WHERE id=? AND workspace_id=?',(task,ws)).fetchone();assert row[0]=='COMPLETED'
   private=(base/row[1]).resolve(strict=True);assert private.is_relative_to(base.resolve())
   b=private.read_bytes();assert len(b)==row[3] and hashlib.sha256(b).hexdigest()==row[2] and b==Path(lib['input']).read_bytes()
   summary=c.execute('SELECT referenced_occurrences,recoverable_unique_assets,missing_unique_assets,fallback_name_unique_assets,restored_unique_assets,restored_occurrences FROM p6k_zip_import_attachment_summary WHERE task_id=?',(task,)).fetchone()
   assert summary==(1152,9,0,0,9,1152),summary
   items=list(c.execute('SELECT conversation_id,status FROM p6k_zip_import_items WHERE task_id=? ORDER BY ordinal',(task,)))
   assert [r[0] for r in items]==lib['ids'] and all(r[1] in ('COMMITTED','SKIPPED') for r in items)
  out['areas'].append({'area':area,'importedConversations':576,'messages':1152,'occurrences':1152,'uniqueNonemptyAssets':9,'physicalBytes':sum(len(b) for b in assets.values()),'liveOccurrencesPerAsset':128,'lastMaintenanceCache':maintenance_cache,'normalStartupMaintenanceVerified':False,'receiptCount':len(receipts),'provenanceCount':len(provenance),'identityLedger':kinds,'exactSourceTextAndOwnership':True,'exactPrivateZipAndAssets':True,'exactIndexTuples':True,'integrity':'ok','foreignKeyViolations':0})
 finally:c.close()
print(json.dumps(out,ensure_ascii=False))
