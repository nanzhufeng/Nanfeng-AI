import hashlib,json,sqlite3,sys,zipfile
from pathlib import Path
log=Path(sys.argv[1]);receipt=json.loads(log.read_text('utf-8-sig').splitlines()[-1]);root=Path(receipt['root'])
assert root.is_relative_to(Path('D:/CodexTemp'))
assert json.loads((root/'.nanfeng-windows-acceptance.json').read_text('utf-8-sig'))['identifier']=='com.nanzhufeng.ai.desktop.windowsacceptance'
native=json.loads((root/'temporary-backup-native.json').read_text('utf-8-sig'))
inspect=root/'synthetic-backup-inspection';inspect.mkdir(exist_ok=True)
with zipfile.ZipFile(receipt['selectedPath']) as package:
 manifest=json.loads(package.read('manifest.json'));names=package.namelist()
 for entry in manifest['entries']:
  raw=package.read(entry['path']);assert len(raw)==entry['bytes'] and hashlib.sha256(raw).hexdigest()==entry['sha256']
 raw=package.read('database/nanfeng-ai.snapshot');snapshot=inspect/'export.snapshot';snapshot.write_bytes(raw)
 connection=sqlite3.connect(snapshot.as_uri()+'?mode=ro',uri=True)
 counts={table:connection.execute('SELECT COUNT(*) FROM '+table).fetchone()[0] for table in ('desktop_temporary_recovery','desktop_temporary_attachments')}
 private=receipt['privateSha256'];shared=receipt['sharedSha256']
 asset=lambda h:'assets/attachments/v1/'+h
 checks={'temporaryTablesEmpty':all(n==0 for n in counts.values()),'privateBytesExcluded':asset(private) not in names,'sharedBytesExact':asset(shared) in names and hashlib.sha256(package.read(asset(shared))).hexdigest()==shared,'privateMetadataExcluded':connection.execute('SELECT COUNT(*) FROM desktop_attachment_assets WHERE sha256=?',[private]).fetchone()[0]==0,'ordinaryMetadataPreserved':connection.execute('SELECT COUNT(*) FROM desktop_conversation_attachments WHERE sha256=?',[shared]).fetchone()[0]==1,'temporaryDraftMessageAbsent':all(v.encode() not in raw for v in ('synthetic temporary private draft','synthetic temporary private message','temporary-private.txt'))}
 connection.close()
 databases=[]
 roots=[root]+([Path(receipt['restoreRoot'])] if receipt.get('restoreRoot') else [])
 for item in roots:
  assert item.is_relative_to(Path('D:/CodexTemp'))
  assert json.loads((item/'.nanfeng-windows-acceptance.json').read_text('utf-8-sig'))['identifier']=='com.nanzhufeng.ai.desktop.windowsacceptance'
 for path in (path for item in roots for path in item.rglob('*')):
  if not path.is_file():continue
  try:
   with path.open('rb') as file:header=file.read(16)
  except OSError:continue
  if header!=b'SQLite format 3\x00':continue
  con=sqlite3.connect(path.as_uri()+'?mode=ro',uri=True)
  integrity=con.execute('PRAGMA integrity_check').fetchall();foreign=con.execute('PRAGMA foreign_key_check').fetchall()
  assert integrity==[('ok',)] and not foreign,(str(path),integrity,len(foreign))
  tables={r[0] for r in con.execute("SELECT name FROM sqlite_master WHERE type='table'")}
  if path.parent==root and 'desktop_temporary_recovery' in tables:
   saved=con.execute('SELECT recovery_json FROM desktop_temporary_recovery').fetchone();assert saved and json.loads(saved[0])==native['before']
  for table in ('desktop_ordinary_chat_attempts','desktop_ocr_attempts_v1','desktop_history_knowledge_candidates_v1','desktop_transcription_tasks','domain_intents','usage_ledger_entries'):
   if table in tables:assert con.execute('SELECT COUNT(*) FROM '+table).fetchone()[0]==0,(str(path),table)
  databases.append(str(path));con.close()
 checks['sourceRecordsAndFilesPreserved']=native['afterExportPreserved'] and native['coldPreserved'] and all(hashlib.sha256((root/'assets'/h).read_bytes()).hexdigest()==h for h in (private,shared))
 if receipt.get('restoreRoot'):
  restoredRoot=Path(receipt['restoreRoot'])
  source=sqlite3.connect((root/'workspace.sqlite3').as_uri()+'?mode=ro',uri=True)
  restored=sqlite3.connect((restoredRoot/'workspace.sqlite3').as_uri()+'?mode=ro',uri=True)
  for table in ('workspaces','workspace_exchange','desktop_conversation_attachments'):
   assert restored.execute('SELECT * FROM '+table+' ORDER BY 1,2').fetchall()==source.execute('SELECT * FROM '+table+' ORDER BY 1,2').fetchall(),table
  for table in ('desktop_temporary_recovery','desktop_temporary_attachments'):
   assert restored.execute('SELECT COUNT(*) FROM '+table).fetchone()[0]==0
  assert restored.execute('SELECT COUNT(*) FROM desktop_attachment_assets WHERE sha256=?',[private]).fetchone()[0]==0
  checks['restoredBusinessAndPrivateIsolation']=native['restoredIsolation'] and not (restoredRoot/'assets'/private).exists() and hashlib.sha256((restoredRoot/'assets'/shared).read_bytes()).hexdigest()==shared
  if 'legacyRejected' in native: checks['legacyExplicitlyRejected']=native['legacyRejected']
  source.close();restored.close()
 result={'candidateSha256':receipt['candidateSha256'],'root':str(root),'temporaryTableCounts':counts,'checks':checks,'sqliteCount':len(databases),'integrity':'ok','foreignKeyFailures':0,'sqliteFiles':databases,'pass':all(checks.values()),'legacyUnsafePackageVerification':'PASSED' if native.get('legacyRejected') else 'NOT_RUN','scope':'Synthetic current package/source/cold/restore isolation only; legacy rejection is a separate unexecuted gate when no legacy path is supplied'}
 output=Path(sys.argv[2]);output.write_text(json.dumps(result,ensure_ascii=False,indent=2),'utf-8')
 print(json.dumps({k:v for k,v in result.items() if k!='sqliteFiles'},ensure_ascii=False))
 sys.exit(0 if result['pass'] else 1)
