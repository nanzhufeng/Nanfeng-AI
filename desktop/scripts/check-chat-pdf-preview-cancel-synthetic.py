"""Read-only synthetic PDF preview cancellation and cold-state checks."""
import hashlib
import json
import sqlite3
import sys
from pathlib import Path

root = Path(sys.argv[1]).resolve(strict=True)
assert root.is_relative_to(Path('D:/CodexTemp').resolve())
assert json.loads((root / '.nanfeng-windows-acceptance.json').read_text('utf-8-sig'))['identifier'] == 'com.nanzhufeng.ai.desktop.windowsacceptance'
r = json.loads((root / 'chat-pdf-preview-cancel-native.json').read_text('utf-8'))
assert r['coldExact'] and r['loadingClose'] and r['page2Retained'] and r['pages'] == 2
assert r['providerRequests'] == 0
assert len(r['closes']) == 2 and all(x['accepted'] and x['responding'] and x['exitCode'] == 0 for x in r['closes'])

def connect(path):
    return sqlite3.connect(path.resolve().as_uri() + '?mode=ro', uri=True)

count = 0
for path in root.rglob('*'):
    if not path.is_file():
        continue
    with path.open('rb') as stream:
        header = stream.read(16)
    if header != b'SQLite format 3\x00':
        continue
    with connect(path) as connection:
        assert connection.execute('PRAGMA integrity_check').fetchall() == [('ok',)]
        assert not connection.execute('PRAGMA foreign_key_check').fetchall()
    count += 1

asset = r['asset']
raw = (root / 'assets' / asset['sha256']).read_bytes()
assert raw == (root / 'synthetic-preview.pdf').read_bytes()
assert len(raw) == asset['byteCount'] and hashlib.sha256(raw).hexdigest() == asset['sha256']
assert raw.startswith(b'%PDF-1.4')
with connect(root / 'workspace.sqlite3') as connection:
    assert json.loads(connection.execute('SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?', (r['ordinaryId'],)).fetchone()[0]) == r['before']['exchange']
    assert connection.execute('SELECT count(*) FROM desktop_ordinary_chat_attempts').fetchone()[0] == 0
    assert connection.execute('SELECT page_number FROM desktop_pdf_preview_positions WHERE workspace_id=? AND attachment_id=?', (r['ordinaryId'], asset['id'])).fetchone() == (2,)
    assert connection.execute('SELECT text FROM desktop_conversation_drafts_v1 WHERE workspace_id=?', (r['ordinaryId'],)).fetchall() == [(r['draft']['text'],)]
    assert connection.execute('SELECT attachment_id FROM desktop_conversation_draft_attachments_v1 WHERE workspace_id=? ORDER BY position', (r['ordinaryId'],)).fetchall() == [(asset['id'],)]
with connect(root / 'work-area/workspace.sqlite3') as connection:
    assert json.loads(connection.execute('SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?', (r['workId'],)).fetchone()[0]) == r['workBefore']['exchange']
    assert connection.execute('SELECT count(*) FROM desktop_pdf_preview_positions').fetchone()[0] == 0
    assert connection.execute('SELECT count(*) FROM desktop_conversation_drafts_v1').fetchone()[0] == 0
print(json.dumps({'pass': True, 'candidateSha256': r['candidateSha256'], 'sqliteCount': count, 'page2Retained': True, 'sourceAndPrivatePdfExact': True, 'draftTextAndAttachmentExact': True, 'ordinaryAndHiddenWorkExchangeExact': True, 'providerRequests': 0}))
