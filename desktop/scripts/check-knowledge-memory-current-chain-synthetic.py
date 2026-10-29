"""Read-only independent owner, revision journal and synthetic CRUD checks."""
import json
import sqlite3
import subprocess
import sys
from pathlib import Path

receipt = json.loads(Path(sys.argv[1]).read_text(encoding='utf-8-sig'))
assert receipt['pass'] and receipt['startupMode']=='NORMAL' and not receipt['forcedCleanup']
assert len(receipt['closes'])==3 and all(x['responding'] and x['accepted'] and x['title'] for x in receipt['closes'])
root = Path(receipt['root']).resolve(strict=True)
assert json.loads((root / '.nanfeng-windows-acceptance.json').read_text(encoding='utf-8-sig'))['identifier'] == 'com.nanzhufeng.ai.desktop.windowsacceptance'
integrity = json.loads(subprocess.check_output([sys.executable,
    str(Path(__file__).with_name('check-stability-synthetic-sqlite.py')), str(root), '--closed-browser'], encoding='utf-8'))
assert integrity['allOk']
facts = []
for area, relative in [('CHAT', Path('.')), ('WORK', Path('work-area'))]:
    with sqlite3.connect((root / relative / 'workspace.sqlite3').as_uri() + '?mode=ro', uri=True) as c:
        c.row_factory = sqlite3.Row
        attempts = c.execute('SELECT count(*) FROM desktop_ordinary_chat_attempts').fetchone()[0]
        titles = c.execute('SELECT count(*) FROM desktop_conversation_title_attempts').fetchone()[0]
        assert attempts == titles == 0
        journals = [dict(r) for r in c.execute('SELECT * FROM domain_intents ORDER BY result_revision')]
        if area == 'CHAT':
            assert not journals
            for row in c.execute('SELECT exchange_json FROM workspace_exchange'):
                exchange = json.loads(row[0])
                synthetic_ids = {v['id'] for v in receipt['records'].values()}
                assert not synthetic_ids.intersection(x['id'] for group in ['knowledge', 'memory', 'relations'] for x in exchange[group])
            assert exchange == receipt['beforeChat']['exchange']
            continue
        exchange = json.loads(c.execute('SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?', (receipt['workspaceId'],)).fetchone()[0])
        assert exchange == receipt['coldSnapshots'][-1]['exchange']
        duplicate=receipt['duplicates'];assert any('EXACT_CONTENT' in x['reasons'] for x in duplicate['candidates'])
        anchor=next(x for x in exchange['knowledge'] if x['id']==duplicate['anchorId'])
        assert anchor['revision']==duplicate['anchorRevision'] and 'UNSAVED_DUPLICATE_QUERY_DRAFT' not in anchor['body']
        for entity, expected in receipt['records'].items():
            collection = {'knowledge': 'knowledge', 'memory': 'memory', 'relation': 'relations'}[entity]
            item = next(i for i in exchange[collection] if i['id'] == expected['id'])
            assert item['status'] == 'ACTIVE' and item['revision'] == expected['restoredRevision']
            if 'body' in expected:
                assert item['body'] == expected['body']
                intents = [j for j in journals if j['entity'] == entity and j['object_id'] == expected['id']]
                assert [j['action'] for j in intents] == ['create', 'update', 'update', 'softDelete', 'restore']
                assert [j['result_revision'] for j in intents] == [1, 2, 3, 4, 5]
                assert [j['expected_revision'] for j in intents] == [None, 1, 2, 3, 4]
                for j in intents:
                    after = json.loads(j['after_exchange_json'])
                    after_item = next(x for x in after[collection] if x['id'] == expected['id'])
                    assert 'UNSAVED_STALE_EDIT' not in after_item['body']
                before_restore = json.loads(intents[-1]['before_exchange_json'])
                old = next(x for x in before_restore[collection] if x['id'] == expected['id'])
                assert old['status'] == 'DELETED' and old['body'] == item['body'] and old['revision'] == 4
            else:
                assert [item['fromId'], item['toId'], item['kind']] == [expected['fromId'], expected['toId'], expected['kind']]
                intents = [j for j in journals if j['entity'] == entity and j['object_id'] == expected['id']]
                assert [j['action'] for j in intents] == ['create', 'softDelete', 'restore']
                assert [j['result_revision'] for j in intents] == [1, 2, 3]
            facts.append({'entity': entity, 'id': item['id'], 'revision': item['revision'], 'status': item['status'],
                          'committedIntents': len(intents), 'staleEditNeverCommitted': 'body' in expected})
assert receipt['providerRequests'] == 0 and all(x['exitCode'] == 0 for x in receipt['closes'])
print(json.dumps({'pass': True, 'root': str(root), 'candidateSha256': receipt['candidateSha256'],
                  'integrity': integrity, 'records': facts, 'chatDomainIntents': 0, 'providerRequests': 0}, ensure_ascii=False, indent=2))
