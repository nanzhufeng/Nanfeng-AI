"""Read-only private archive and native preview persistence audit."""
import hashlib
import json
import sqlite3
import sys
from pathlib import Path
from zipfile import ZipFile

root = Path(sys.argv[1]).resolve(strict=True)
assert root.is_relative_to(Path('D:/CodexTemp').resolve())
assert json.loads((root / '.nanfeng-windows-acceptance.json').read_text('utf-8-sig'))['identifier'] == 'com.nanzhufeng.ai.desktop.windowsacceptance'
r = json.loads((root / 'chat-archive-current-native.json').read_text('utf-8'))
assert r['coldExact'] and r['nestedArchiveUnsupported'] and r['providerRequests'] == 0
assert len(r['closes']) == 2 and all(x['accepted'] and x['responding'] and x['exitCode'] == 0 for x in r['closes'])
assert [(x['width'], x['height']) for x in r['pages']] == [(1229, 2048), (2048, 1229), (2048, 1536), (2048, 512)]
assert len({x['dataUrlSha256'] for x in r['pages']}) == 4
assert [x['path'] for x in r['errors']] == ['docs/broken.pdf', 'docs/locked.pdf']
asset = r['asset']
private = root / 'assets' / asset['sha256']
raw = private.read_bytes()
assert len(raw) == asset['byteCount'] and hashlib.sha256(raw).hexdigest() == asset['sha256']
assert raw == (root / 'synthetic-archive.zip').read_bytes()
with ZipFile(private) as archive:
    assert archive.testzip() is None
    assert len(archive.infolist()) == 6
    assert '合成目录' in archive.read('docs/notes.md').decode('utf-8')
    assert archive.read('docs/report.pdf').startswith(b'%PDF-')
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
    assert json.loads(c.execute('SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?', (r['workspaceId'],)).fetchone()[0]) == r['before']['exchange']
    assert c.execute('SELECT count(*) FROM desktop_ordinary_chat_attempts').fetchone()[0] == 0
    assert c.execute('SELECT text FROM desktop_conversation_drafts_v1 WHERE workspace_id=?', (r['workspaceId'],)).fetchall() == [(r['draft']['text'],)]
    assert c.execute('SELECT attachment_id FROM desktop_conversation_draft_attachments_v1 WHERE workspace_id=? ORDER BY position', (r['workspaceId'],)).fetchall() == [(asset['id'],)]
print(json.dumps({'pass': True, 'candidateSha256': r['candidateSha256'], 'sqliteCount': count, 'privateZipBytesAndCrcExact': True, 'pdfPages': 4, 'draftReferencesAndTextExact': True, 'exchangeExact': True, 'providerRequests': 0}))
