"""Read-only interrupted WAL snapshot and full resumed import validation."""
import hashlib
import json
import sqlite3
import struct
import sys
from pathlib import Path

root = Path(sys.argv[1]).resolve(strict=True)
assert json.loads((root / '.nanfeng-windows-acceptance.json').read_text(encoding='utf-8-sig'))['identifier'] == 'com.nanzhufeng.ai.desktop.windowsacceptance'
evidence = json.loads((root / 'single-transaction-evidence.json').read_text(encoding='utf-8'))
assert evidence['pass'] is True and not evidence.get('forcedCleanup')
observation = evidence['interruption']
assert observation['terminationAccepted'] and observation['state'] == 'INTERRUPTED_UNCOMMITTED_WAL'
snapshot = (root / 'wal-uncommitted-snapshot.bin').read_bytes()
assert hashlib.sha256(snapshot).hexdigest() == observation['observedWalSha256']
magic, version, page_size = struct.unpack('>III', snapshot[:12])
assert magic in (0x377f0682, 0x377f0683) and version == 3007000
page_size = 65536 if page_size == 1 else page_size
assert page_size == observation['pageSize']
frame_size = page_size + 24
new_start = max(0, (observation['baselineBytes'] - 32) // frame_size)
frame_count = (len(snapshot) - 32) // frame_size
assert frame_count > new_start
for index in range(new_start, frame_count):
    offset = 32 + index * frame_size
    page, commit_size = struct.unpack('>II', snapshot[offset:offset + 8])
    assert page > 0 and commit_size == 0
    assert snapshot[offset + 8:offset + 16] == snapshot[16:24]
input_data = json.loads((root / 'single-transaction-synthetic.json').read_text(encoding='utf-8'))
connection = sqlite3.connect((root / 'workspace.sqlite3').as_uri() + '?mode=ro', uri=True)
try:
    assert [r[0] for r in connection.execute('PRAGMA integrity_check')] == ['ok']
    assert not list(connection.execute('PRAGMA foreign_key_check'))
    exchange = json.loads(connection.execute('SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?', (evidence['workspaceId'],)).fetchone()[0])
    matches = [c for c in exchange['conversations'] if c['id'] == evidence['conversationId']]
    assert len(matches) == 1
    assert matches[0]['title'] == input_data[0]['name']
    assert ['\n'.join(b['text'] for b in m['blocks'] if b['kind'] == 'TEXT') for m in matches[0]['messages']] == [m['text'] for m in input_data[0]['chat_messages']]
    receipts = connection.execute('SELECT conversation_id FROM claude_import_receipts WHERE workspace_id=?', (evidence['workspaceId'],)).fetchall()
    assert receipts == [(evidence['conversationId'],)]
    item = connection.execute('SELECT status,conversation_id FROM claude_import_items WHERE task_id=?', (evidence['taskId'],)).fetchall()
    assert item == [('CONFIRMED', evidence['conversationId'])]
    key, expected_hash = connection.execute('SELECT storage_key,package_hash FROM claude_import_tasks WHERE id=?', (evidence['taskId'],)).fetchone()
    path = (root / key).resolve(strict=True)
    assert path.is_relative_to(root)
    assert hashlib.sha256(path.read_bytes()).hexdigest() == expected_hash
finally:
    connection.close()
print(json.dumps({'root': str(root), 'allOk': True, 'uncommittedNewFrames': frame_count - new_start, 'snapshotHashMatches': True, 'exactMessages': len(input_data[0]['chat_messages']), 'receiptCount': 1, 'privateCopyHashMatches': True, 'integrity': 'ok', 'foreignKeyViolations': 0}, ensure_ascii=False))
