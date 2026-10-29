"""Read-only synthetic paste/drop bytes, owner references and recovery audit."""
import hashlib
import json
import sqlite3
import struct
import sys
from pathlib import Path

root = Path(sys.argv[1]).resolve(strict=True)
assert root.is_relative_to(Path('D:/CodexTemp').resolve())
assert json.loads((root / '.nanfeng-windows-acceptance.json').read_text('utf-8-sig'))['identifier'] == 'com.nanzhufeng.ai.desktop.windowsacceptance'
r = json.loads((root / 'chat-transfer-current-native.json').read_text('utf-8'))
assert r['eventFilesSynthetic'] and not r['physicalClipboard'] and r['coldExact']
assert not r['empty']['prevented'] and all(r[k]['prevented'] for k in ['paste','drop','temporaryPaste'])
assert r['providerRequests'] == 0
assert len(r['closes']) == 2 and all(x['exitCode'] == 0 and x['accepted'] and x['responding'] for x in r['closes'])
assert r['ordinaryBefore']['attachments'] == []
assert r['temporaryBefore']['attachments'] == []

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

ordinary = r['ordinaryAfter']['attachments'][0]
text = r['ordinaryAfter']['attachments'][1]
temporary = r['temporaryAfter']['attachments'][0]
assert len(r['ordinaryAfter']['attachments']) == 2
for asset in [ordinary, text, temporary]:
    raw = (root / 'assets' / asset['sha256']).read_bytes()
    assert len(raw) == asset['byteCount'] and hashlib.sha256(raw).hexdigest() == asset['sha256']
    if asset is text:
        assert raw.decode('utf-8') == 'SYNTHETIC_DROP_TEXT 中文保留'
    else:
        assert raw.startswith(b'\x89PNG\r\n\x1a\n')
        assert struct.unpack('>II', raw[16:24]) == (7,5)
assert ordinary['sha256'] != temporary['sha256']
with connect(root / 'workspace.sqlite3') as connection:
    assert json.loads(connection.execute('SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?', (r['workspaceId'],)).fetchone()[0]) == r['before']['exchange']
    assert connection.execute('SELECT count(*) FROM desktop_ordinary_chat_attempts').fetchone()[0] == 0
    assert connection.execute('SELECT attachment_id FROM desktop_conversation_draft_attachments_v1 WHERE workspace_id=? ORDER BY position', (r['workspaceId'],)).fetchall() == [(ordinary['id'],), (text['id'],)]
    assert connection.execute('SELECT text FROM desktop_conversation_drafts_v1 WHERE workspace_id=?', (r['workspaceId'],)).fetchall() == [(r['ordinaryBefore']['text'],)]
    assert connection.execute('SELECT attachment_id,sha256 FROM desktop_temporary_attachments WHERE temporary_id=?', (r['temporaryAfter']['temporaryId'],)).fetchall() == [(temporary['id'], temporary['sha256'])]
    assert json.loads(connection.execute('SELECT recovery_json FROM desktop_temporary_recovery WHERE temporary_id=?', (r['temporaryAfter']['temporaryId'],)).fetchone()[0]) == r['temporaryAfter']
    assert r['temporaryAfter']['draftAttachmentIds'] == [temporary['id']]
with connect(root / 'work-area/workspace.sqlite3') as connection:
    assert json.loads(connection.execute('SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?', (r['workId'],)).fetchone()[0]) == r['workBefore']['exchange']
    assert connection.execute('SELECT count(*) FROM desktop_temporary_attachments').fetchone()[0] == 0
    assert connection.execute('SELECT count(*) FROM desktop_conversation_draft_attachments_v1').fetchone()[0] == 0
print(json.dumps({'pass':True,'candidateSha256':r['candidateSha256'],'sqliteCount':count,'privateAssetsHashSizeExact':3,'pngDimensionsExact':2,'textUtf8Exact':True,'ordinaryTemporaryAndHiddenWorkReferencesExact':True,'coldExact':True,'providerRequests':0,'physicalClipboard':False}))
