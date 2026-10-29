"""Read-only independent checks; only marked acceptance roots are permitted."""
import hashlib
import importlib.util
import json
import sys
from pathlib import Path

spec = importlib.util.spec_from_file_location('fault', Path(__file__).with_name('fault-cloud-restore-synthetic.py'))
fault = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fault)
MARKDOWN = '# 合成 OCR 正文\n\n第二行仅用于隔离验收。'

def check(value):
    root = fault.marked(value)
    receipt = json.loads((root / 'ocr-current-chain-native.json').read_text(encoding='utf-8'))
    assert receipt['pass'] and receipt['startupMode'] == 'NORMAL' and not receipt['forcedCleanup']
    assert all(x['accepted'] and x['responding'] for x in receipt['closes'])
    c = fault.connect(root / 'workspace.sqlite3')
    assert c.execute('PRAGMA integrity_check').fetchone()[0] == 'ok'
    assert not c.execute('PRAGMA foreign_key_check').fetchall()
    c.row_factory = __import__('sqlite3').Row
    tasks = [dict(x) for x in c.execute('SELECT * FROM desktop_transcription_tasks')]
    assert len(tasks) == 1
    task = tasks[0]
    assert task['id'] == receipt['taskId'] and task['model_id'] == 'glm-ocr'
    source = (root / 'assets' / task['source_sha256']).read_bytes()
    assert len(source) == task['source_byte_count']
    assert hashlib.sha256(source).hexdigest() == task['source_sha256']
    attempts = [dict(x) for x in c.execute('SELECT * FROM desktop_ocr_attempts_v1 ORDER BY attempt_number')]
    assert all(x['task_id'] == task['id'] and x['requested_model_id'] == 'glm-ocr' for x in attempts)
    assert (task['input_tokens'], task['output_tokens'], task['estimated_charge_micros']) == (12, 8, 4)
    u = fault.connect(root / 'usage-ledger' / 'usage-ledger-v1.sqlite3')
    u.row_factory = __import__('sqlite3').Row
    usage = [dict(x) for x in u.execute('SELECT * FROM usage_ledger_entries')]
    ocr = [x for x in usage if x['requested_model_id'] == 'glm-ocr']
    assert len(ocr) == 1
    assert (ocr[0]['input_tokens'], ocr[0]['output_tokens'], ocr[0]['charge_micros'], ocr[0]['currency_code']) == (12, 8, 4, 'CNY')
    segments = c.execute('SELECT text FROM desktop_transcription_segments ORDER BY ordinal').fetchall()
    ordinary = [dict(x) for x in c.execute('SELECT * FROM desktop_ordinary_chat_attempts')]
    cancelled = receipt.get('cancelledWhileProviderPending', False)
    if cancelled:
        assert task['state'] == 'CANCELLED' and task['attempt_count'] == task['provider_request_count'] == 1
        assert task['result_attachment_id'] is None and task['result_sha256'] is None
        assert len(attempts) == 1 and attempts[0]['state'] == attempts[0]['safe_error_code'] == 'CANCELLED'
        assert (attempts[0]['input_tokens'], attempts[0]['output_tokens'], attempts[0]['charge_micros']) == (12, 8, 4)
        assert not segments and not ordinary and len(usage) == 1 and not receipt['chatRequests']
    else:
        assert task['state'] == 'COMPLETED' and task['attempt_count'] == task['provider_request_count'] == 2
        assert len(attempts) == 2 and attempts[0]['state'] == 'FAILED' and attempts[0]['safe_error_code'] == 'RATE_LIMIT'
        assert all(attempts[0][x] is None for x in ['input_tokens', 'output_tokens', 'charge_micros'])
        assert attempts[1]['state'] == 'COMPLETED'
        result = (root / 'assets' / task['result_sha256']).read_bytes()
        assert hashlib.sha256(result).hexdigest() == task['result_sha256'] and len(result) == task['result_byte_count']
        assert result.decode('utf-8') == MARKDOWN and [x[0] for x in segments] == [MARKDOWN]
        assert len(ordinary) == 1 and ordinary[0]['state'] == 'COMPLETED'
        assert (ordinary[0]['input_tokens'], ordinary[0]['output_tokens'], ordinary[0]['charge_micros']) == (9, 4, 5)
        assert len(usage) == 2
        assert len(receipt['chatRequests']) == 1 and not receipt['chatRequests'][0]['rawSourcePresent']
        exchange = json.loads(c.execute('SELECT exchange_json FROM workspace_exchange').fetchone()[0])
        conv = next(x for x in exchange['conversations'] if x['id'] == receipt['sentConversationId'])
        user = next(x for x in conv['messages'] if x['role'] == 'user')
        assets = [x['asset'] for x in user['blocks'] if x['kind'] == 'ASSET_REF']
        assert len(assets) == 1 and assets[0]['sha256'] == task['result_sha256']
        assert assets[0]['id'] == task['result_attachment_id'] and assets[0]['mimeType'] == 'text/markdown'
    assert c.execute('SELECT count(*) FROM desktop_conversation_drafts_v1').fetchone()[0] == 0
    assert c.execute('SELECT count(*) FROM desktop_conversation_draft_attachments_v1').fetchone()[0] == 0
    w = fault.connect(root / 'work-area' / 'workspace.sqlite3')
    assert w.execute('PRAGMA integrity_check').fetchone()[0] == 'ok' and not w.execute('PRAGMA foreign_key_check').fetchall()
    for table in ['desktop_transcription_tasks', 'desktop_ocr_attempts_v1', 'desktop_ordinary_chat_attempts', 'desktop_conversation_attachments']:
        assert w.execute('SELECT count(*) FROM ' + table).fetchone()[0] == 0
    c.close(); w.close(); u.close()
    return {'root': str(root), 'pass': True, 'cancelled': cancelled, 'sourceBytes': len(source), 'sourceHashExact': True, 'ocrAttemptCount': len(attempts), 'usageEntryCount': len(usage), 'workAttemptsAndAttachments': 0, 'draftCleared': True}

if __name__ == '__main__':
    print(json.dumps([check(x) for x in sys.argv[1:]], ensure_ascii=False, indent=2))
