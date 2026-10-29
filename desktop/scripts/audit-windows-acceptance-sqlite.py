"""Audit every SQLite file header in explicitly marked, closed synthetic roots."""
import json
import sqlite3
import sys
from pathlib import Path

reports = []
for argument in sys.argv[1:]:
    root = Path(argument).resolve()
    marker = json.loads((root / '.nanfeng-windows-acceptance.json').read_text(encoding='utf-8-sig'))
    if root.parent != Path('D:/CodexTemp').resolve() or not root.name.startswith('nanfeng-ai-windows-acceptance.'):
        raise SystemExit('Only isolated acceptance roots are allowed')
    if marker.get('identifier') != 'com.nanzhufeng.ai.desktop.windowsacceptance':
        raise SystemExit('Invalid acceptance identity')
    databases = []
    for path in root.rglob('*'):
        if not path.is_file():
            continue
        with path.open('rb') as source:
            if source.read(16) != b'SQLite format 3\0':
                continue
        wal = Path(str(path) + '-wal')
        mode = '?mode=ro' if wal.exists() and wal.stat().st_size else '?mode=ro&immutable=1'
        with sqlite3.connect(path.as_uri() + mode, uri=True) as connection:
            results = [row[0] for row in connection.execute('PRAGMA integrity_check')]
        databases.append({'path': path.relative_to(root).as_posix(), 'ok': results == ['ok']})
    reports.append({'root': str(root), 'count': len(databases), 'allOk': bool(databases) and all(item['ok'] for item in databases), 'databases': databases})
print(json.dumps({'roots': reports, 'total': sum(row['count'] for row in reports), 'allOk': bool(reports) and all(row['allOk'] for row in reports)}))
if not reports or not all(row['allOk'] for row in reports):
    raise SystemExit(1)
