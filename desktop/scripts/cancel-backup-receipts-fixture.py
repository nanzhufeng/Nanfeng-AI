"""Owned synthetic business-only fixtures; never read app configuration or credentials."""
import hashlib
from contextlib import closing
import json
import os
from pathlib import Path
import shutil
import sqlite3
import sys
import uuid
import zipfile

REPO = Path(__file__).resolve().parents[2]
STAGE = REPO / 'docs/review/20261017-windows-cancel-backup-receipts'
SOURCES = json.loads((STAGE / 'sources.json').read_text(encoding='utf-8'))
FACT_KEYS = ('id', 'parentId', 'ordinal', 'role', 'delivery', 'attemptId', 'continuationOf',
             'blocks', 'modelSnapshot', 'usage', 'estimatedUsage', 'chargeMicros',
             'currencyCode', 'costPriceVersion', 'costSource')
DBS = ('workspace.sqlite3', 'work-area/workspace.sqlite3',
       'usage-ledger/usage-ledger-v1.sqlite3', 'work-area/usage-ledger/usage-ledger-v1.sqlite3')


def owned(root):
    root = Path(root).resolve()
    assert root.is_relative_to(REPO / '.runtime-qa'), 'Only repository synthetic roots allowed'
    marker = json.loads((root / '.nanfeng-windows-acceptance.json').read_text(encoding='utf-8-sig'))
    assert marker['identifier'] == 'com.nanzhufeng.ai.desktop.windowsacceptance'
    return root


def ro(path):
    return sqlite3.connect(Path(path).resolve().as_uri() + '?mode=ro', uri=True)


def ledger(path):
    if not Path(path).exists():
        return None
    with closing(ro(path)) as db:
        db.row_factory = sqlite3.Row
        tables = {}
        for name in ('usage_ledger_entries', 'usage_ledger_call_metadata', 'usage_ledger_call_recovery'):
            exists = db.execute('SELECT 1 FROM sqlite_master WHERE type=? AND name=?', ('table', name)).fetchone()
            if exists:
                rows = [dict(row) for row in db.execute('SELECT * FROM ' + name + ' ORDER BY entry_id')]
                for row in rows:
                    for field in ('metadata_json', 'entry_json'):
                        if field in row:
                            row[field] = json.loads(row[field])
                tables[name] = rows
        return tables


def area_facts(database, usage):
    with closing(ro(database)) as db:
        workspaces = []
        for wid, raw in db.execute('SELECT workspace_id,exchange_json FROM workspace_exchange ORDER BY workspace_id'):
            value = json.loads(raw)
            conversations = []
            for conv in value.get('conversations', []):
                conversations.append({'id': conv['id'], 'title': conv.get('title'),
                    'messages': [{k: message[k] for k in FACT_KEYS if k in message}
                                 for message in conv.get('messages', [])]})
            workspaces.append({'id': wid, 'conversations': conversations})
        receipts = {}
        for table in ['desktop_ordinary_chat_attempts', 'desktop_ordinary_chat_context_sources', 'desktop_conversation_title_attempts']:
            db.row_factory = sqlite3.Row
            receipts[table] = sorted([dict(row) for row in db.execute('SELECT * FROM '+table)], key=lambda row: json.dumps(row, sort_keys=True))
    return {'workspaces': workspaces, 'ledger': ledger(usage), 'receipts': receipts}


def read(root):
    return {area: area_facts(root / rel / 'workspace.sqlite3', root / rel / DBS[2])
            for area, rel in [('CHAT', Path()), ('WORK', Path('work-area'))]}


def seed(root, kind):
    assert kind == 'cancelled'
    assert not (root / 'backup-call-facts-seeded.json').exists(), 'Fixture already seeded'
    assert all(not c['messages'] for a in read(root).values() for w in a['workspaces'] for c in w['conversations']), 'Only empty new fixture roots may be seeded'
    source = owned(SOURCES[kind])
    originals = {}
    for rel in DBS:
        src, dst = source / rel, root / rel
        if not src.exists():
            continue
        wal = Path(str(src) + '-wal')
        assert not wal.exists() or wal.stat().st_size == 0
        target_wal = Path(str(dst) + '-wal')
        assert not target_wal.exists() or target_wal.stat().st_size == 0, 'Target must be closed and checkpointed'
        originals[rel] = hashlib.sha256(src.read_bytes()).hexdigest()
        if dst.exists():
            saved = STAGE / 'initial-empty-databases' / root.name / rel
            saved.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(dst, saved)
        dst.parent.mkdir(parents=True, exist_ok=True)
        candidate = dst.with_name(dst.name + '.seed-' + uuid.uuid4().hex)
        connection = ro(src)
        target = sqlite3.connect(candidate)
        connection.backup(target)
        target.close()
        connection.close()
        os.replace(candidate, dst)
        assert hashlib.sha256(src.read_bytes()).hexdigest() == originals[rel]
    if kind == 'window':
        # A synthetic old call with genuinely unknown planning window, beside known calls.
        with closing(sqlite3.connect(root / DBS[2])) as db:
            first = db.execute("SELECT execution_id FROM usage_ledger_entries WHERE kind='FINAL_MEASURED' ORDER BY occurred_at_ms,entry_id LIMIT 1").fetchone()[0]
            rows = db.execute('SELECT m.entry_id,m.metadata_json FROM usage_ledger_call_metadata m JOIN usage_ledger_entries e ON e.entry_id=m.entry_id WHERE e.execution_id=?', (first,)).fetchall()
            for entry_id, raw in rows:
                meta = json.loads(raw)
                meta['contextWindowTokens'] = None
                db.execute('UPDATE usage_ledger_call_metadata SET metadata_json=? WHERE entry_id=?', (json.dumps(meta), entry_id))
            db.commit()
        assert any(row['metadata_json']['contextWindowTokens'] is None for row in read(root)['CHAT']['ledger']['usage_ledger_call_metadata'])
    result = {'kind': kind, 'source': str(source), 'sourceHashesUnchanged': originals,
              'configurationOrCredentialsCopied': False}
    (root / 'backup-call-facts-seeded.json').write_text(json.dumps(result), encoding='utf-8')
    return result


def mutate(root, area):
    assert area in ('CHAT', 'WORK')
    directory = root if area == 'CHAT' else root / 'work-area'
    with closing(sqlite3.connect(directory / DBS[2])) as db:
        rows = db.execute('SELECT entry_id,metadata_json FROM usage_ledger_call_metadata').fetchall()
        for entry_id, raw in rows:
            meta = json.loads(raw)
            meta['contextWindowTokens'] = 65536
            db.execute('UPDATE usage_ledger_call_metadata SET metadata_json=? WHERE entry_id=?', (json.dumps(meta), entry_id))
        db.commit()
    return {'area': area, 'syntheticPostBackupWindowChange': 65536}


def inspect_archive(path, name):
    assert name and all(c.isalnum() or c == '-' for c in name), 'Fixed evidence child name required'
    path = Path(path).resolve()
    assert path.is_relative_to(REPO / '.runtime-qa')
    folder = STAGE / ('archive-snapshots-' + name)
    folder.mkdir(exist_ok=False)
    with zipfile.ZipFile(path) as archive:
        manifest = json.loads(archive.read('manifest.json'))
        for entry, local in [('database/nanfeng-ai.snapshot', 'workspace.sqlite3'),
                             ('database/desktop-usage-ledger-v1.snapshot', 'usage.sqlite3')]:
            if entry in archive.namelist():
                (folder / local).write_bytes(archive.read(entry))
        facts = area_facts(folder / 'workspace.sqlite3', folder / 'usage.sqlite3')
        return {'entries': archive.namelist(), 'manifest': manifest, 'facts': facts,
                'archiveSha256': hashlib.sha256(path.read_bytes()).hexdigest()}


command, raw_root = sys.argv[1:3]
if command == 'archive':
    result = inspect_archive(raw_root, sys.argv[3])
else:
    root = owned(raw_root)
    if command == 'seed':
        result = seed(root, sys.argv[3])
    elif command == 'read':
        result = read(root)
    elif command == 'mutate':
        result = mutate(root, sys.argv[3])
    else:
        raise ValueError('Unknown fixed fixture action')
print(json.dumps(result, ensure_ascii=False))
