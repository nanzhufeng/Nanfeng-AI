"""Independently check a real checkpoint against every pre-switch table/file."""
import json
from pathlib import Path
import sys
from backup_real_switch_baseline import CONTROLLED, connect, digest, tables, tree

root = Path(sys.argv[1]).resolve(strict=True)
assert root.name.startswith('nanfeng-ai-windows-acceptance.')
assert json.loads((root/'.nanfeng-windows-acceptance.json').read_text(encoding='utf-8-sig'))['identifier'] == 'com.nanzhufeng.ai.desktop.windowsacceptance'
evidence = sys.argv[2] if len(sys.argv) == 3 else 'real-switch-backup-evidence.json'
e = json.loads((root/evidence).read_text(encoding='utf-8'))
assert e['interruptionHit'] and e['preparationPass'] and not e.get('forcedCleanup')
area = e['interruptedArea']
base = root if area == 'CHAT' else root/'work-area'
checkpoint = base/'local-backup/v1/recovery'/e['checkpointId']
manifest = json.loads((checkpoint/'checkpoint-manifest-v4.json').read_text(encoding='utf-8'))
baseline = json.loads(Path(e['observer']['fullBaseline']).read_text(encoding='utf-8'))
assert manifest['version'] == 4
assert digest(checkpoint/'workspace.snapshot') == manifest['workspaceSha256']
assert tables(checkpoint/'workspace.snapshot') == baseline['databases'][area+'/workspace.sqlite3']['tables']
ledger = checkpoint/'databases/desktop-usage-ledger-v1.snapshot'
if manifest['usageLedgerSha256'] is not None:
    assert digest(ledger) == manifest['usageLedgerSha256']
    assert tables(ledger) == baseline['databases'][area+'/usage-ledger/usage-ledger-v1.sqlite3']['tables']
inventory = {}
for package in CONTROLLED.values():
    inventory.update(tree(checkpoint/'assets'/package,package))
assert inventory == manifest['assets'] == baseline['controlledFiles'][area]
assert digest(checkpoint/'assets/attachments/v1'/Path(e['modifiedAsset']['path']).name) == e['modifiedAsset']['sha256']
other = 'WORK' if area == 'CHAT' else 'CHAT'
other_base = root if other == 'CHAT' else root/'work-area'
other_inventory = {}
for local,package in CONTROLLED.items():
    other_inventory.update(tree(other_base/local,package))
assert other_inventory == baseline['controlledFiles'][other]
other_table_differences = []
for key,db in baseline['databases'].items():
    selected,local = key.split('/',1)
    if selected == area:
        continue
    path = root/local if selected in ('SHARED','CHAT') else root/'work-area'/local
    actual = tables(path)
    differences = [t for t in set(actual)|set(db['tables']) if actual.get(t) != db['tables'].get(t)]
    if differences:
        # Record the complete exception; never call full raw tables identical.
        assert selected == other and differences == ['desktop_attachment_assets'], (key,differences)
        old_connection = connect(db['snapshot']); new_connection = connect(path)
        columns = [r[1] for r in old_connection.execute('pragma table_info(desktop_attachment_assets)')]
        old_rows = old_connection.execute('select * from desktop_attachment_assets order by sha256').fetchall()
        new_rows = new_connection.execute('select * from desktop_attachment_assets order by sha256').fetchall()
        assert len(old_rows) == len(new_rows) == 1200
        stable = [i for i,c in enumerate(columns) if c not in ('last_referenced_at_ms','last_unreferenced_at_ms')]
        assert [tuple(r[i] for i in stable) for r in old_rows] == [tuple(r[i] for i in stable) for r in new_rows]
        changed_columns = [c for i,c in enumerate(columns) if any(a[i] != b[i] for a,b in zip(old_rows,new_rows))]
        assert set(changed_columns) == {'last_referenced_at_ms','last_unreferenced_at_ms'}
        referenced=columns.index('last_referenced_at_ms');unreferenced=columns.index('last_unreferenced_at_ms')
        assert all(b[referenced] == b[unreferenced] and b[referenced] >= a[referenced] for a,b in zip(old_rows,new_rows))
        assert len({r[referenced] for r in new_rows}) == 1
        other_table_differences.append(dict(database=key,table='desktop_attachment_assets',rows=1200,
            changedColumns=changed_columns,identityBytesCreatedAtReferenceCountExact=True,
            before=db['tables']['desktop_attachment_assets'],after=actual['desktop_attachment_assets'],
            newMaintenanceTimeMs=new_rows[0][referenced]))
        old_connection.close();new_connection.close()
state = json.loads((base/'local-backup/v1/state.json').read_text(encoding='utf-8'))
assert state == e['observer']['afterTerminateState']
assert state['operation'] == 'INTERRUPTED' and state['phase'] == 'SWITCHING'
old = base/'local-backup/v1'/('switch-old-'+e['checkpointId'])/'workspace.sqlite3'
assert old.is_file()
current = tables(base/'workspace.sqlite3')
before = baseline['databases'][area+'/workspace.sqlite3']['tables']
changed = [t for t in set(current)|set(before) if current.get(t) != before.get(t)]
result = dict(allOk=True,root=str(root),area=area,checkpointId=e['checkpointId'],
              actualPersistedSwitchCaptured=True,hookUsed=False,checkpointManifestHashesExact=True,
              allPreSwitchTablesExact=True,preSwitchTableCount=len(before),
              checkpointInventoryEntries=len(inventory),checkpointFileCount=sum(v is not None for v in inventory.values()),
              checkpointFileBytes=sum(p.stat().st_size for p in (checkpoint/'assets').rglob('*') if p.is_file()),
              modifiedDraftAndAssetRetainedInCheckpoint=True,
              otherAreaAndSharedAllTablesFilesExact=not other_table_differences,
              otherAreaBusinessFilesAndSharedTablesExact=True,otherAreaTableDifferences=other_table_differences,
              postColdStateExact=True,currentChangedTables=sorted(changed),normalRecoveryAccepted=False,
              checkpointRetained=True,integrity='ok',foreignKeyViolations=0)
output=root/'real-switch-independent.json'
with output.open('x',encoding='utf-8') as stream:
    json.dump(result,stream,ensure_ascii=False,indent=2)
print(json.dumps(result,ensure_ascii=False))
