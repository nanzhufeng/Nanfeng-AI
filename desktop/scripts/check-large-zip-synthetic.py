"""Independent, read-only full JSON import body and receipt validation."""
import hashlib
import json
import sqlite3
import sys
import zipfile
from pathlib import Path

root = Path(sys.argv[1]).resolve(strict=True)
marker = json.loads((root / '.nanfeng-windows-acceptance.json').read_text(encoding='utf-8-sig'))
assert marker['identifier'] == 'com.nanzhufeng.ai.desktop.windowsacceptance'
evidence = json.loads((root / 'history-evidence.json').read_text(encoding='utf-8'))
assert evidence['pass'] is True and not evidence.get('forcedCleanup')
result = {'root': str(root), 'libraries': [], 'allOk': True}
for lib in evidence['libraries']:
    base = root if lib['area'] == 'CHAT' else root / 'work-area'
    connection = sqlite3.connect((base / 'workspace.sqlite3').as_uri() + '?mode=ro', uri=True)
    try:
        assert [r[0] for r in connection.execute('PRAGMA integrity_check')] == ['ok']
        assert not list(connection.execute('PRAGMA foreign_key_check'))
        exchange = json.loads(connection.execute('SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?', (lib['workspaceId'],)).fetchone()[0])
        rows = [r for r in exchange['conversations'] if r['id'] != lib['seedConversationId']]
        latest=Path(lib['paths'][-1]).resolve(strict=True)
        assert latest.is_relative_to(root)
        with zipfile.ZipFile(latest) as z:
            inputs=[]
            for name in z.namelist(): inputs.extend(json.loads(z.read(name)))
        assert len(rows)==len(inputs)==603
        assert len({row['id'] for row in rows}) == len(rows)
        for source in inputs:
            title = source.get('title', source.get('name'))
            matches = [row for row in rows if row['title'] == title]
            assert len(matches) == 1
            row = matches[0]
            if 'mapping' in source:
                texts = [node['message']['content']['parts'][0] for node in source['mapping'].values()]
            else:
                messages = source.get('chat_messages') or json.loads(source['sourceText'])['chat_messages']
                texts = [m['text'] for m in messages]
            assert len(row['messages']) == 2
            assert ['\n'.join(b['text'] for b in m['blocks'] if b['kind'] == 'TEXT') for m in row['messages']] == texts
        receipts=connection.execute('SELECT conversation_id FROM p6k_zip_import_receipts WHERE workspace_id=?',(lib['workspaceId'],)).fetchall()
        assert len(receipts)==1203
        assert {r[0] for r in receipts}=={r['id'] for r in rows}
        provenance=connection.execute('SELECT conversation_id FROM p6k_zip_import_provenance WHERE workspace_id=?',(lib['workspaceId'],)).fetchall()
        assert len(provenance)==603
        assert {r[0] for r in provenance}=={r['id'] for r in rows}
        tasks=connection.execute('SELECT storage_key,package_hash FROM p6k_zip_import_tasks WHERE workspace_id=?',(lib['workspaceId'],)).fetchall()
        assert len(tasks)==3
        for storage_key, package_hash in tasks:
            private = (base / storage_key).resolve(strict=True)
            assert private.is_relative_to(base.resolve())
            assert hashlib.sha256(private.read_bytes()).hexdigest() == package_hash
        result['libraries'].append({'area': lib['area'], 'format': lib['format']['name'], 'conversations': len(rows), 'messages': sum(len(r['messages']) for r in rows), 'receipts': len(receipts), 'privateCopies': len(tasks), 'exactBodies': True, 'integrity': 'ok', 'foreignKeyViolations': 0})
    finally:
        connection.close()
print(json.dumps(result, ensure_ascii=False))
