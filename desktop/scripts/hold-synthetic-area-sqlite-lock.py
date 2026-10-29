"""Hold a bounded write lock on a verified synthetic acceptance area's existing DB."""
import json
import sqlite3
import sys
import time
from pathlib import Path

def main():
    root = Path(sys.argv[1]).resolve(strict=True)
    area = sys.argv[2]
    if area not in ('CHAT', 'WORK'):
        raise SystemExit('invalid area')
    marker = json.loads((root / '.nanfeng-windows-acceptance.json').read_text(encoding='utf-8-sig'))
    if marker.get('identifier') != 'com.nanzhufeng.ai.desktop.windowsacceptance':
        raise SystemExit('invalid acceptance identity')
    ready, release = [Path(v).resolve() for v in sys.argv[3:5]]
    if ready.parent != root or release.parent != root or ready.exists() or release.exists():
        raise SystemExit('invalid or reused signals')
    database = (root / ('work-area' if area == 'WORK' else '') / 'workspace.sqlite3').resolve(strict=True)
    if not database.is_relative_to(root):
        raise SystemExit('database escapes root')
    connection = sqlite3.connect(database.as_uri() + '?mode=rw', uri=True, timeout=1, isolation_level=None)
    try:
        actual = connection.execute('SELECT area FROM desktop_data_area_v1 WHERE id=1').fetchone()
        if actual != (area,):
            raise SystemExit('database area identity mismatch')
        connection.execute('BEGIN IMMEDIATE')
        ready.write_text(json.dumps({'area': area, 'database': str(database), 'lock': 'BEGIN IMMEDIATE', 'watchdogSeconds': 20}), encoding='utf-8')
        deadline = time.monotonic() + 20
        while not release.is_file() and time.monotonic() < deadline:
            time.sleep(.02)
        if not release.is_file():
            raise SystemExit('synthetic lock release timed out')
        connection.execute('ROLLBACK')
    finally:
        connection.close()
    return 0

if __name__ == '__main__':
    raise SystemExit(main())
