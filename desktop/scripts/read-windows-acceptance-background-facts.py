"""Read content-free reminder facts from closed, marked synthetic roots."""
import json
import sqlite3
import sys
from pathlib import Path

root = Path(sys.argv[1]).resolve()
marker = json.loads((root / '.nanfeng-windows-acceptance.json').read_text(encoding='utf-8-sig'))
assert root.parent == Path('D:/CodexTemp').resolve()
assert root.name.startswith('nanfeng-ai-windows-acceptance.')
assert marker['identifier'] == 'com.nanzhufeng.ai.desktop.windowsacceptance'
result = {}
for area, database in [('CHAT', root / 'workspace.sqlite3'),
                       ('WORK', root / 'work-area/workspace.sqlite3')]:
    wal = Path(str(database) + '-wal')
    mode = '?mode=ro' if wal.exists() and wal.stat().st_size else '?mode=ro&immutable=1'
    with sqlite3.connect(database.as_uri() + mode, uri=True) as connection:
        rows = connection.execute('SELECT plan_id,status,safe_error_code FROM desktop_reminder_runs_v1 ORDER BY run_id').fetchall()
        result[area] = {'runCount': len(rows), 'runs': [dict(zip(['planId', 'status', 'safeErrorCode'], row)) for row in rows],
                        'integrity': connection.execute('PRAGMA quick_check').fetchone()[0]}
print(json.dumps(result))
