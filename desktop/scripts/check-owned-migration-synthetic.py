"""Verify owned synthetic data survives a migration or same-schema upgrade."""
import hashlib
import json
import sqlite3
import sys
from pathlib import Path

evidence = Path(sys.argv[1]).resolve(strict=True)
copy = json.loads((evidence / 'copy.json').read_text('utf-8'))
root, source = Path(copy['root']), Path(copy['source'])
plan = json.loads(Path(sys.argv[2]).read_text('utf-8-sig'))
assert str(source) == plan['root']
assert root.is_relative_to(Path('D:/CodexTemp')) and source.is_relative_to(Path('D:/CodexTemp'))
before = json.loads((evidence / 'database-before.json').read_text('utf-8'))
after = json.loads((evidence / 'database-after.json').read_text('utf-8'))
for item in plan['rootFiles']:
    path = source / item['path']
    assert path.is_file() and hashlib.sha256(path.read_bytes()).hexdigest().upper() == item['sha256'], item['path']
source_schema = plan['sourceSchema']
assert source_schema in (52, 56) and plan['targetSchema'] == 56
business_tables = 0
new_tables = 0
for database in ['workspace.sqlite3', 'work-area/workspace.sqlite3']:
    a, b = before[database], after[database]
    assert a['schemaVersion'] == source_schema and b['schemaVersion'] == 56
    assert len(a['tables']) == (90 if source_schema == 52 else 104) and len(b['tables']) == 104
    if source_schema == 56:
        assert set(a['tables']) == set(b['tables'])
    for name, identity in a['tables'].items():
        if name == 'desktop_background_runtime_v1':
            continue
        assert b['tables'][name] == identity, name
        business_tables += 1
    for name in set(b['tables']) - set(a['tables']):
        assert b['tables'][name]['rows'] == 0
        new_tables += 1
    def status(directory):
        with sqlite3.connect((directory / database).as_uri() + '?mode=ro', uri=True) as c:
            c.row_factory = sqlite3.Row
            row = dict(c.execute('SELECT * FROM desktop_background_runtime_v1').fetchone())
            timestamp = row.pop('updated_at_ms')
            return row, timestamp
    old, old_time = status(source)
    new, new_time = status(root)
    assert old == new and new_time >= old_time
assert before['app-configuration.sqlite3'] == after['app-configuration.sqlite3']
preserved_files = 0
for item in plan['rootFiles']:
    normalized = item['path'].replace('\\', '/')
    if '/assets/' in '/' + normalized or 'credentials' in normalized.lower() and not normalized.endswith('.lock'):
        copied = root / item['path']
        assert copied.is_file() and hashlib.sha256(copied.read_bytes()).hexdigest().upper() == item['sha256']
        preserved_files += 1
count = 0
for path in root.rglob('*'):
    if not path.is_file():
        continue
    with path.open('rb') as stream:
        header = stream.read(16)
    if header != b'SQLite format 3\x00':
        continue
    with sqlite3.connect(path.as_uri() + '?mode=ro', uri=True) as c:
        assert c.execute('PRAGMA integrity_check').fetchall() == [('ok',)]
        assert not c.execute('PRAGMA foreign_key_check').fetchall()
    count += 1
native = json.loads((root / 'owned-migration-native.json').read_text('utf-8'))
assert len(native['closes']) == 2 and all(x['accepted'] and x['responding'] and x['exitCode'] == 0 for x in native['closes'])
assert native['providerRequests'] == 0 and native['hiddenNavigationCount'] == 0 and native['coldWorkspaceSettingsAndProviderPresenceExact']
assert bool(native.get('installedExecution')) == bool(copy.get('installedExecution'))
print(json.dumps({'pass': True, 'sourceFilesUnchanged': len(plan['rootFiles']), 'sourceSchema': source_schema, 'targetSchema': 56, 'schema52To56Areas': 2 if source_schema == 52 else 0, 'sameSchema56Areas': 2 if source_schema == 56 else 0, 'existingTableIdentitiesExact': business_tables, 'backgroundOnlyTimestampChanged': True, 'newEmptyTables': new_tables, 'settingsDatabaseExact': True, 'assetAndCredentialFilesExact': preserved_files, 'sqliteCount': count, 'providerRequests': 0, 'installationExecuted': bool(copy.get('installedExecution'))}))
