"""Closed-profile, read-only checks for the marked synthetic input lifecycle case."""
import hashlib
import json
import sqlite3
import subprocess
import sys
from pathlib import Path

receipt = json.loads(Path(sys.argv[1]).read_text(encoding='utf-8-sig'))
assert receipt['pass']
root = Path(receipt['root']).resolve(strict=True)
marker = json.loads((root / '.nanfeng-windows-acceptance.json').read_text(encoding='utf-8-sig'))
assert marker['identifier'] == 'com.nanzhufeng.ai.desktop.windowsacceptance'
integrity = json.loads(subprocess.check_output([
    sys.executable, str(Path(__file__).with_name('check-stability-synthetic-sqlite.py')),
    str(root), '--closed-browser'], encoding='utf-8'))
assert integrity['allOk']
facts = []
delayed = {a['delayedHash'] for a in receipt['areas'].values()}
for area, relative in [('CHAT', Path('.')), ('WORK', Path('work-area'))]:
    area_root = root / relative
    c = sqlite3.connect((area_root / 'workspace.sqlite3').as_uri() + '?mode=ro', uri=True)
    c.row_factory = sqlite3.Row
    expected = receipt['areas'][area]
    assets = {r['sha256']: dict(r) for r in c.execute('SELECT * FROM desktop_attachment_assets')}
    metadata = [dict(r) for r in c.execute('SELECT * FROM desktop_conversation_attachments')]
    drafts = [dict(r) for r in c.execute('SELECT * FROM desktop_conversation_drafts_v1')]
    draft_refs = [dict(r) for r in c.execute('SELECT * FROM desktop_conversation_draft_attachments_v1')]
    attempts = [dict(r) for r in c.execute('SELECT * FROM desktop_ordinary_chat_attempts')]
    titles = [dict(r) for r in c.execute('SELECT * FROM desktop_conversation_title_attempts')]
    exchange = json.loads(c.execute('SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?', (expected['workspaceId'],)).fetchone()[0])
    assert not delayed.intersection(assets)
    assert not delayed.intersection(r['sha256'] for r in metadata)
    for digest in delayed:
        assert not (area_root / 'assets' / digest).exists()
    for digest, byte_count in [(receipt['pngHash'], 76), (expected['textHash'], 31)]:
        data = (area_root / 'assets' / digest).read_bytes()
        assert len(data) == byte_count and hashlib.sha256(data).hexdigest() == digest
    assert (area_root / 'assets' / expected['textHash']).read_bytes() == expected['material'].encode('utf-8')
    image = assets[receipt['pngHash']]
    assert image['reference_count'] == 0 and image['last_unreferenced_at_ms'] is not None
    assert assets[expected['textHash']]['reference_count'] == 1
    if area == 'CHAT':
        assert not drafts and not draft_refs
        assert len(attempts) == len(titles) == 1
        assert attempts[0]['state'] == titles[0]['state'] == 'COMPLETED'
        assert attempts[0]['charge_micros'] == titles[0]['charge_micros'] == 4
        conversation = next(v for v in exchange['conversations'] if v['id'] == receipt['conversationId'])
        user = next(m for m in conversation['messages'] if m['role'] == 'user')
        refs = [b['asset'] for b in user['blocks'] if b['kind'] == 'ASSET_REF']
        assert len(refs) == 1 and refs[0]['sha256'] == expected['textHash']
        assert not conversation['autoTitlePending']
    else:
        assert not attempts and not titles
        assert len(drafts) == len(draft_refs) == 1
        assert drafts[0]['text'] == expected['text']
        assert draft_refs[0]['attachment_id'] == next(a['id'] for a in expected['attachments'] if a['sha256'] == expected['textHash'])
    c.close()
    facts.append({'area': area, 'pngReferences': 0, 'pngBytesRetained': True,
                  'textReferences': 1, 'textBytes': 31, 'delayedAssetsAbsent': True,
                  'ordinaryAttempts': len(attempts), 'titleAttempts': len(titles)})
with sqlite3.connect((root / 'usage-ledger/usage-ledger-v1.sqlite3').as_uri() + '?mode=ro', uri=True) as c:
    c.row_factory = sqlite3.Row
    ledger = [dict(r) for r in c.execute('SELECT * FROM usage_ledger_entries')]
assert len(ledger) == 2 and all(r['charge_micros'] == 4 and r['fact_grade'] == 'PROVIDER_REPORTED' for r in ledger)
assert receipt['ordinaryRequests'] == receipt['titleRequests'] == 1
print(json.dumps({'pass': True, 'root': str(root), 'candidateSha256': receipt['candidateSha256'],
                  'integrity': integrity, 'areas': facts, 'ledgerEntries': 2,
                  'scope': 'Synthetic assets only; zero reference/grace armed, not elapsed 24h deletion.'}, ensure_ascii=False, indent=2))
