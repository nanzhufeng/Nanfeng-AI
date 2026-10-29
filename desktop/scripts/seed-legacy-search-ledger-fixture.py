"""Seed only an empty, marked, repository-owned synthetic ledger; never import into user data."""
import hashlib,json,re,sqlite3,sys
from pathlib import Path
repo=Path(__file__).resolve().parents[2]
root=Path(sys.argv[1]).resolve(strict=True)
qa=(repo/'.runtime-qa').resolve(strict=True)
assert root.is_relative_to(qa) and re.fullmatch(r'nanfeng-ai-windows-acceptance\.[a-fA-F0-9-]{8,64}',root.name)
marker_path=root/'.nanfeng-windows-acceptance.json'
assert not marker_path.is_symlink() and marker_path.stat().st_size<=4096
marker=json.loads(marker_path.read_text(encoding='utf-8-sig'))
assert marker=={'schemaVersion':1,'identifier':'com.nanzhufeng.ai.desktop.windowsacceptance','runId':root.name.removeprefix('nanfeng-ai-windows-acceptance.')}
fixture_path=repo/'desktop/tests/fixtures/legacy-independent-search-ledger-v1.json'
assert fixture_path.stat().st_size<65536
fixture=json.loads(fixture_path.read_text(encoding='utf-8'))
assert fixture['format']=='nfai.synthetic.legacy-search-ledger.v1' and fixture['schemaVersion']==3 and len(fixture['rows'])==4
db=root/'usage-ledger/usage-ledger-v1.sqlite3'
assert db.is_file() and not db.is_symlink() and not db.parent.is_symlink()
c=sqlite3.connect(db.as_uri()+'?mode=rw',uri=True,timeout=5)
try:
 assert c.execute('pragma user_version').fetchone()[0]==3
 assert all(c.execute('select count(*) from '+table).fetchone()[0]==0 for table in ['usage_ledger_entries','usage_ledger_call_metadata','usage_ledger_call_recovery'])
 columns='entry_id,replay_token,execution_id,conversation_id,branch_leaf_message_id,invocation_id,attempt_id,kind,fact_grade,requested_model_id,actual_model_id,input_tokens,output_tokens,cached_input_tokens,charge_micros,budget_micros,adjustment_micros,currency_code,cost_source,reconciliation_fingerprint,reconciles_entry_id,source,occurred_at_ms'.split(',')
 c.execute('begin immediate')
 for row in fixture['rows']:
  assert set(row)==set(columns)|{'callMetadata'} and row['entry_id'].startswith('usage-legacy-')
  c.execute('insert into usage_ledger_entries('+','.join(columns)+') values('+','.join('?' for _ in columns)+')',[row[name] for name in columns])
  c.execute('insert into usage_ledger_call_metadata(entry_id,metadata_json) values(?,?)',(row['entry_id'],json.dumps(row['callMetadata'],ensure_ascii=False,separators=(',',':'))))
 c.commit()
 assert c.execute('pragma quick_check').fetchall()==[('ok',)]
 print(json.dumps({'seededRows':4,'fixtureSha256':hashlib.sha256(fixture_path.read_bytes()).hexdigest(),'ledgerSchema':3}))
finally:c.close()
