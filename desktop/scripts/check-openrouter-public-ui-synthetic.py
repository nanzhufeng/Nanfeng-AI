"""Read-only public catalog cache and isolated UI persistence audit."""
import hashlib
import json
import sqlite3
import sys
from pathlib import Path

root = Path(sys.argv[1]).resolve(strict=True)
assert root.is_relative_to(Path('D:/CodexTemp').resolve())
assert json.loads((root / '.nanfeng-windows-acceptance.json').read_text('utf-8-sig'))['identifier'] == 'com.nanzhufeng.ai.desktop.windowsacceptance'
r = json.loads((root / 'openrouter-public-ui-native.json').read_text('utf-8'))
assert r['coldExact'] and r['modelConfigAndSettingsUnchanged'] and r['publicCatalogRealGet']
assert r['ordinaryProviderRequests'] == 0 and r['initial']['status'] == 'NOT_VERIFIED'
assert len(r['closes']) == 2 and all(x['accepted'] and x['responding'] and x['exitCode'] == 0 for x in r['closes'])
stored = json.loads((root / 'model-registry-openrouter-current-v1.json').read_text('utf-8'))
catalog = stored['catalog']
canonical = json.dumps([catalog['models'], catalog['presetMappings']], ensure_ascii=False, separators=(',', ':')).encode('utf-8')
assert hashlib.sha256(canonical).hexdigest() == catalog['catalogSha256']
assert catalog['catalogVersion'] == catalog['catalogSha256'][:16] == r['verified']['catalogVersion']
assert len(catalog['presetMappings']) == r['verified']['mappedPresetCount']
assert stored['verifiedAtMs'] == r['verified']['verifiedAtMs']
assert [x['presetId'] for x in catalog['presetMappings']] == r['verified']['mappedPresetIds']
assert r['verified']['sourceUrl'] == 'https://openrouter.ai/api/v1/models'
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
with sqlite3.connect((root / 'workspace.sqlite3').as_uri() + '?mode=ro', uri=True) as c:
    assert c.execute('SELECT count(*) FROM desktop_ordinary_chat_attempts').fetchone()[0] == 0
    assert json.loads(c.execute('SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?', (r['workspaceId'],)).fetchone()[0]) == r['before']['exchange']
print(json.dumps({'pass': True, 'modelsAtVerification': len(catalog['models']), 'presetMappingsAtVerification': len(catalog['presetMappings']), 'catalogHashAndVersionExact': True, 'sqliteCount': count, 'ordinaryProviderRequests': 0, 'realPublicCatalogGet': True}))
