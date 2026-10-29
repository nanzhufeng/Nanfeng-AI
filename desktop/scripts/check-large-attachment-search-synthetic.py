"""Independent, read-only nonempty attachment references, bytes and index audit."""
import json,sys,sqlite3,hashlib,zipfile
from pathlib import Path
root=Path(sys.argv[1]).resolve(strict=True)
marker=json.loads((root/'.nanfeng-windows-acceptance.json').read_text(encoding='utf-8-sig'))
assert marker['identifier']=='com.nanzhufeng.ai.desktop.windowsacceptance'
evidence=json.loads((root/'large-attachment-search-evidence.json').read_text(encoding='utf-8'))
assert evidence.get('pass') is True and not evidence.get('forcedCleanup')
out={'root':str(root),'areas':[],'allOk':True}
for area,lib in evidence['areas'].items():
 base=root if area=='CHAT' else root/'work-area'
 c=sqlite3.connect((base/'workspace.sqlite3').as_uri()+'?mode=ro',uri=True)
 try:
  assert list(c.execute('PRAGMA integrity_check'))==[('ok',)]
  assert not list(c.execute('PRAGMA foreign_key_check'))
  workspace=lib['workspaceId']; actual=json.loads(c.execute('SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?',(workspace,)).fetchone()[0])
  with zipfile.ZipFile(lib['input']) as z: expected=json.loads(z.read('payload/conversations.json'))
  assert actual['conversations']==expected
  assert len(expected)==1152 and sum(len(x['messages']) for x in expected)==2304
  refs=[]; identities={}
  for conv in expected:
   for m in conv['messages']:
    for b in m['blocks']:
     if b['kind']=='ASSET_REF':
      a=b['asset']; refs.append((workspace,conv['id'],m['id'],a['id'])); identities[a['id']]=a
  assert len(refs)==1152 and len(set(refs))==1152 and len(identities)==9
  rows=list(c.execute('SELECT attachment_id,mime_type,display_name,byte_count,sha256 FROM desktop_conversation_attachments WHERE workspace_id=?',(workspace,)))
  assert len(rows)==9
  total=0
  for aid,mime,name,size,digest in rows:
   a=identities[aid]; assert (mime,name,size,digest)==(a['mimeType'],a['displayName'],a['byteCount'],a['sha256'])
   path=(base/'assets'/digest).resolve(strict=True); assert path.is_relative_to(base.resolve())
   b=path.read_bytes(); assert len(b)==size>0 and hashlib.sha256(b).hexdigest()==digest
   reference_count=c.execute('SELECT reference_count FROM desktop_attachment_assets WHERE sha256=?',(digest,)).fetchone()[0]
   assert reference_count==128,(aid,reference_count)
   total+=size
  indexed=list(c.execute('SELECT workspace_id,conversation_id,message_id,attachment_id FROM desktop_local_search_index WHERE workspace_id=? AND attachment_id IS NOT NULL',(workspace,)))
  assert sorted(indexed)==sorted(refs)
  kinds=dict(c.execute('SELECT content_kind,count(*) FROM desktop_local_search_index WHERE workspace_id=? AND attachment_id IS NOT NULL GROUP BY content_kind',(workspace,)))
  assert kinds=={'FILE':768,'IMAGE':128,'VIDEO':128,'AUDIO':128}
  for t in ('desktop_video_preview_positions','desktop_audio_preview_positions'):
   positions=list(c.execute('SELECT attachment_id,position_millis FROM '+t+' WHERE workspace_id=?',(workspace,)))
   assert len(positions)==1 and 350<=positions[0][1]<=450
  out['areas'].append({'area':area,'conversations':1152,'messages':2304,'attachmentOccurrences':len(refs),'nonemptyUniqueAssets':9,'physicalBytes':total,'perAssetReferences':128,'exactInputConversations':True,'exactIndexedOccurrences':True,'kinds':kinds,'integrity':'ok','foreignKeyViolations':0})
 finally:c.close()
print(json.dumps(out,ensure_ascii=False))