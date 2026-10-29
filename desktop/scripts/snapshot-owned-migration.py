"""Read-only table identity snapshots; synthetic acceptance data only."""
import base64
import hashlib
import json
import sqlite3
import sys
from pathlib import Path

root = Path(sys.argv[1]).resolve(strict=True)
assert root.is_relative_to(Path('D:/CodexTemp').resolve())
assert json.loads((root / '.nanfeng-windows-acceptance.json').read_text('utf-8-sig'))['identifier'] == 'com.nanzhufeng.ai.desktop.windowsacceptance'
def safe(value):
    return {'blob': base64.b64encode(value).decode()} if isinstance(value, bytes) else value
result = {}
for relative in ['workspace.sqlite3', 'work-area/workspace.sqlite3', 'app-configuration.sqlite3']:
    path = root / relative
    if not path.exists():
        continue
    with sqlite3.connect(path.as_uri() + '?mode=ro', uri=True) as c:
        assert c.execute('PRAGMA integrity_check').fetchall() == [('ok',)]
        assert not c.execute('PRAGMA foreign_key_check').fetchall()
        tables = {}
        for (name,) in c.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name").fetchall():
            quoted = '"' + name.replace('"', '""') + '"'
            columns = [x[1] for x in c.execute('PRAGMA table_info(' + quoted + ')')]
            rows = sorted(json.dumps([safe(x) for x in row], ensure_ascii=False, separators=(',', ':')) for row in c.execute('SELECT * FROM ' + quoted))
            tables[name] = {'columns': columns, 'rows': len(rows), 'sha256': hashlib.sha256('\n'.join(rows).encode('utf-8')).hexdigest()}
        result[relative] = {'schemaVersion': c.execute('PRAGMA user_version').fetchone()[0], 'tables': tables}
Path(sys.argv[2]).write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', 'utf-8')
print(json.dumps({name: {'schemaVersion': x['schemaVersion'], 'tableCount': len(x['tables'])} for name, x in result.items()}))
