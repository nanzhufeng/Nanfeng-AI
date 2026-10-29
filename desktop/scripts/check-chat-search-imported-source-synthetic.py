"""Read-only synthetic imported-chat search and preservation checks."""
import hashlib
import json
import sqlite3
import sys
from pathlib import Path

root = Path(sys.argv[1]).resolve(strict=True)
assert root.is_relative_to(Path('D:/CodexTemp').resolve())
assert json.loads((root / '.nanfeng-windows-acceptance.json').read_text('utf-8-sig'))['identifier'] == 'com.nanzhufeng.ai.desktop.windowsacceptance'
r = json.loads((root / 'chat-search-imported-source-native.json').read_text('utf-8'))
assert r['coldExact'] and r['crossSourceDraftPreserved'] and r['staleDeletedResultRefused']
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

with connect(root / 'workspace.sqlite3') as connection:
    for wid, expected in [(r['ordinaryId'], r['ordinaryBefore']), (r['sourceId'], r['after'])]:
        actual = json.loads(connection.execute('SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?', (wid,)).fetchone()[0])
        assert actual == expected['exchange']
    assert connection.execute('SELECT count(*) FROM desktop_ordinary_chat_attempts').fetchone()[0] == 0
    gpt = r['gpt']['items'][0]['conversationId']
    claude = r['cl']['items'][0]['conversationId']
    assert not connection.execute('SELECT entry_id FROM desktop_local_search_index WHERE conversation_id=?', (gpt,)).fetchall()
    rows = connection.execute("SELECT message_id,normalized_text FROM desktop_local_search_index WHERE conversation_id=? AND content_kind='TEXT'", (claude,)).fetchall()
    assert len(rows) == 2 and all(x[0] for x in rows)
    assert any('synthetic_chat_zip_claude_answer' in x[1] for x in rows)
    assert connection.execute('SELECT index_version FROM desktop_local_search_index_state WHERE workspace_id=?', (r['sourceId'],)).fetchone()[0] == 3
    for page in [r['livePage'], r['coldLive']]:
        assert any(h['workspaceId'] == r['sourceId'] and h['conversationId'] == claude and h['messageId'] in {x[0] for x in rows} for h in page['hits'])
    assert not r['deletedPage']['hits'] and not r['coldDeleted']['hits']
    assert r['imageHit']['workspaceId'] == r['sourceId'] and r['imageHit']['conversationId'] == gpt
    conv = next(c for c in r['beforeDelete']['exchange']['conversations'] if c['id'] == gpt)
    message = next(m for m in conv['messages'] if m['id'] == r['imageHit']['messageId'])
    asset = next(b['asset'] for b in message['blocks'] if b['kind'] == 'ASSET_REF' and b['asset']['id'] == r['imageHit']['attachmentId'])
    raw = (root / 'assets' / asset['sha256']).read_bytes()
    assert len(raw) == asset['byteCount'] and hashlib.sha256(raw).hexdigest() == asset['sha256']

with connect(root / 'work-area/workspace.sqlite3') as connection:
    assert json.loads(connection.execute('SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?', (r['workId'],)).fetchone()[0]) == r['workBefore']['exchange']
    assert connection.execute('SELECT count(*) FROM p6k_zip_import_tasks').fetchone()[0] == 0
    assert connection.execute('SELECT count(*) FROM desktop_ordinary_chat_attempts').fetchone()[0] == 0

print(json.dumps({'pass': True, 'candidateSha256': r['candidateSha256'], 'sqliteCount': count, 'claudeTextRows': len(rows), 'deletedConversationIndexRows': 0, 'indexVersion': 3, 'sourceAndOrdinaryExchangeExact': True, 'hiddenWorkPreserved': True, 'exactImageOccurrenceAndBytes': True, 'providerRequests': 0}))
