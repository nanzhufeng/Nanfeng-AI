"""Read-only retention of hidden import tasks after Windows UI scope change."""
import hashlib
import json
import sqlite3
import sys
from pathlib import Path

root = Path(sys.argv[1]).resolve(strict=True)
assert root.is_relative_to(Path('D:/CodexTemp').resolve())
assert json.loads((root / '.nanfeng-windows-acceptance.json').read_text('utf-8-sig'))['identifier'] == 'com.nanzhufeng.ai.desktop.windowsacceptance'
r = json.loads((root / 'chat-json-result-scope-native.json').read_text('utf-8'))
assert r['coldExact'] and r['knowledgeTaskRetained'] and r['knowledgeResultHidden'] and r['providerRequests'] == 0
assert len(r['closes']) == 2 and all(x['accepted'] and x['responding'] and x['exitCode'] == 0 for x in r['closes'])
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
    assert c.execute('SELECT count(*) FROM desktop_ordinary_chat_attempts').fetchone()[0] == 0
    assert json.loads(c.execute('SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?', (r['workspaceId'],)).fetchone()[0]) == r['before']['exchange']
    task = c.execute('SELECT workspace_id,status,package_hash FROM nanfeng_knowledge_import_tasks WHERE id=?', (r['saved']['id'],)).fetchone()
    assert task[:2] == (r['workspaceId'], r['saved']['status'])
    assert task[2] == hashlib.sha256((root / 'synthetic-knowledge-history.json').read_bytes()).hexdigest()
    assert c.execute('SELECT count(*) FROM nanfeng_knowledge_import_items WHERE task_id=?', (r['saved']['id'],)).fetchone()[0] == len(r['saved']['items']) == 1
    assert c.execute('SELECT count(*) FROM nanfeng_knowledge_import_messages WHERE task_id=?', (r['saved']['id'],)).fetchone()[0] == 2
    assert c.execute('SELECT count(*) FROM nanfeng_knowledge_import_receipts').fetchone()[0] == 0
print(json.dumps({'pass': True, 'sqliteCount': count, 'savedKnowledgeTaskRetained': 1, 'savedKnowledgeMessagesRetained': 2, 'knowledgeNotCommittedOrDeleted': True, 'exchangeExact': True, 'providerRequests': 0}))
