"""Independently read marked native OCR interruption/retry evidence and SQLite."""
import hashlib
import json
import sqlite3
import subprocess
import sys
from pathlib import Path


def readonly(path):
    c = sqlite3.connect(path.as_uri() + '?mode=ro', uri=True)
    c.row_factory = sqlite3.Row
    return c


base = Path(sys.argv[1]).resolve(strict=True)
results = []
for name in ['chat-current-native', 'work-current-native']:
    receipt = json.loads((base / (name + '.txt')).read_text(encoding='utf-8-sig').splitlines()[-1])
    root = Path(receipt['root']).resolve(strict=True)
    assert json.loads((root / '.nanfeng-windows-acceptance.json').read_text(encoding='utf-8-sig'))['identifier'] == 'com.nanzhufeng.ai.desktop.windowsacceptance'
    evidence = json.loads((root / 'ocr-interruption-evidence.json').read_text(encoding='utf-8'))
    assert evidence['pass'] and not evidence.get('forcedCleanup')
    assert evidence['candidateSha256'] == receipt['candidateSha256']
    assert evidence['providerRequests'] == 2 and evidence['startupMode'] == 'normal'
    assert evidence['ledgerBeforeCold'] == evidence['ledgerAfterCold']
    for close in ['normalClose', 'finalNormalClose']:
        assert evidence[close]['responding'] and evidence[close]['accepted'] and evidence[close]['title']
    area = evidence['dataArea']
    owner = root / 'work-area' if area == 'WORK' else root
    peer = root if area == 'WORK' else root / 'work-area'
    integrity = json.loads(subprocess.check_output([sys.executable,
        str(Path(__file__).with_name('check-stability-synthetic-sqlite.py')), str(root), '--closed-browser'], encoding='utf-8'))
    assert integrity['allOk']
    with readonly(owner / 'workspace.sqlite3') as c:
        attempts = [dict(r) for r in c.execute('SELECT * FROM desktop_ocr_attempts_v1 ORDER BY attempt_number')]
        assert len(attempts) == 2
        assert attempts[0] == evidence['recoveredAttemptRows'][0]
        assert attempts[0]['state'] == 'UNKNOWN' and attempts[0]['safe_error_code'] == 'APP_INTERRUPTED'
        assert all(attempts[0][k] is None for k in ['provider_request_id', 'input_tokens', 'output_tokens', 'charge_micros', 'page_count'])
        assert attempts[1]['state'] == 'COMPLETED' and attempts[1]['safe_error_code'] is None
        assert attempts[1]['input_tokens'] == 12 and attempts[1]['output_tokens'] == 8 and attempts[1]['charge_micros'] == 4
        assert attempts[1]['provider_request_id'] == 'nfai-synthetic-ocr-request' and attempts[1]['page_count'] == 1
        assert {a['task_id'] for a in attempts} == {evidence['taskId']}
        assert {a['attempt_number'] for a in attempts} == {1, 2}
        assert len({a['attempt_id'] for a in attempts}) == 2
        task = dict(c.execute('SELECT * FROM desktop_transcription_tasks WHERE id=?', (evidence['taskId'],)).fetchone())
        assert task['state'] == 'COMPLETED' and task['attempt_count'] == task['provider_request_count'] == 2
        assert task['result_attachment_id'] == evidence['cold']['resultAttachmentId']
        attachment_rows = [dict(r) for r in c.execute('SELECT * FROM desktop_conversation_attachments WHERE workspace_id=? AND attachment_id IN (?,?)',
            (evidence['workspaceId'], evidence['sourceAttachmentId'], task['result_attachment_id']))]
        assert len(attachment_rows) == 2
        for a in attachment_rows:
            asset = owner / 'assets' / a['sha256']
            data = asset.read_bytes()
            assert len(data) == a['byte_count'] and hashlib.sha256(data).hexdigest() == a['sha256']
            if a['attachment_id'] == task['result_attachment_id']:
                assert data.decode('utf-8') == '# 合成 OCR 正文\n\n第二行仅用于隔离验收。'
        assert c.execute('SELECT count(*) FROM desktop_ordinary_chat_attempts').fetchone()[0] == 0
    with readonly(owner / 'usage-ledger/usage-ledger-v1.sqlite3') as c:
        entries = [dict(r) for r in c.execute('SELECT * FROM usage_ledger_entries')]
        assert len(entries) == 1
        entry = entries[0]
        assert entry['attempt_id'] == attempts[1]['attempt_id'] != attempts[0]['attempt_id']
        assert entry['entry_id'] == 'usage-' + attempts[1]['attempt_id']
        assert entry['input_tokens'] == 12 and entry['output_tokens'] == 8 and entry['charge_micros'] == 4
        assert entry['currency_code'] == 'CNY' and entry['cost_source'] == 'LOCAL_ESTIMATE'
        assert entry['conversation_id'] == evidence['taskId']
    with readonly(peer / 'workspace.sqlite3') as c:
        assert c.execute('SELECT count(*) FROM desktop_transcription_tasks').fetchone()[0] == 0
        assert c.execute('SELECT count(*) FROM desktop_ocr_attempts_v1').fetchone()[0] == 0
        assert c.execute('SELECT count(*) FROM desktop_ordinary_chat_attempts').fetchone()[0] == 0
    results.append({'case': name, 'candidateSha256': receipt['candidateSha256'], 'root': str(root),
                    'dataArea': area, 'invocationScope': evidence['invocationScope'],
                    'unknownAttemptExactPreserved': True, 'independentSuccessfulAttempt': True,
                    'historyLedgerEntries': 1, 'integrity': integrity})
assert len({r['candidateSha256'] for r in results}) == 1
print(json.dumps({'pass': True, 'cases': results}, ensure_ascii=False, indent=2))
