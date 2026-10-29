"""Read-only integrity/count evidence for an explicitly marked acceptance root."""
import json
import os
import sqlite3
import sys
from pathlib import Path

root = Path(sys.argv[1]).resolve(strict=True)
include_browser = len(sys.argv) == 3 and sys.argv[2] == '--closed-browser'
if len(sys.argv) != 2 and not include_browser:
    raise ValueError('Only --closed-browser is supported')
marker = json.loads((root / '.nanfeng-windows-acceptance.json').read_text(encoding='utf-8-sig'))
if marker.get('identifier') != 'com.nanzhufeng.ai.desktop.windowsacceptance':
    raise ValueError('Unmarked acceptance root')
rows = []
paths = []
for directory, directories, files in os.walk(root):
    # Browser profile is not application SQLite; its live locks are expected.
    if not include_browser:
        directories[:] = [name for name in directories if name != 'webview2']
    paths.extend(Path(directory) / name for name in files)
for path in sorted(paths):
    browser_file = 'webview2' in path.relative_to(root).parts
    if not path.is_file() or (not browser_file and path.suffix.lower() not in ('.sqlite3', '.sqlite', '.db')):
        continue
    with path.open('rb') as stream:
        if stream.read(16) != b'SQLite format 3\x00':
            continue
    connection = sqlite3.connect(path.as_uri() + '?mode=ro', uri=True, timeout=3)
    try:
        integrity = [row[0] for row in connection.execute('PRAGMA integrity_check')]
        foreign = list(connection.execute('PRAGMA foreign_key_check'))
        counts = {}
        for (name,) in connection.execute("SELECT name FROM sqlite_master WHERE type='table'"):
            if any(word in name.lower() for word in ('attempt', 'attachment', 'task')):
                escaped = name.replace('"', '""')
                counts[name] = connection.execute(f'SELECT count(*) FROM "{escaped}"').fetchone()[0]
        rows.append({'path': str(path.relative_to(root)), 'integrity': integrity,
                     'foreignKeyViolations': len(foreign), 'counts': counts})
    finally:
        connection.close()
print(json.dumps({'root': str(root), 'count': len(rows), 'allOk': bool(rows) and
                  all(row['integrity'] == ['ok'] and row['foreignKeyViolations'] == 0 for row in rows),
                  'databases': rows}, ensure_ascii=False))
