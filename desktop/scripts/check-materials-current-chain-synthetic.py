"""Independent read-only accounting and byte checks on marked synthetic roots."""
import hashlib
import importlib.util
import json
import sqlite3
import sys
from pathlib import Path

spec = importlib.util.spec_from_file_location('fault', Path(__file__).with_name('fault-cloud-restore-synthetic.py'))
fault = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fault)

def check(value):
    root = fault.marked(value)
    receipt = json.loads((root / 'ordinary-materials-current-native.json').read_text(encoding='utf-8'))
    assert receipt['pass'] and not receipt['forcedCleanup'] and receipt['startupMode'] == 'NORMAL'
    assert len(receipt['closes']) == 2 and all(x['accepted'] and x['responding'] for x in receipt['closes'])
    c = fault.connect(root / 'workspace.sqlite3'); c.row_factory = sqlite3.Row
    assert c.execute('PRAGMA integrity_check').fetchone()[0] == 'ok' and not c.execute('PRAGMA foreign_key_check').fetchall()
    attachments = [dict(x) for x in c.execute('SELECT * FROM desktop_conversation_attachments')]
    for asset in attachments:
        data = (root / 'assets' / asset['sha256']).read_bytes()
        assert len(data) == asset['byte_count'] and hashlib.sha256(data).hexdigest() == asset['sha256']
    ordinary = [dict(x) for x in c.execute('SELECT * FROM desktop_ordinary_chat_attempts')]
    titles = [dict(x) for x in c.execute('SELECT * FROM desktop_conversation_title_attempts')]
    mixed = receipt.get('autoTask') == 'mixed'
    assert len(ordinary) == (1 if mixed else 7) and len(titles) == (1 if mixed else 0)
    expected_models = {'openai/gpt-5.6-sol', 'deepseek-flash'} if mixed else {'openai/gpt-5.6-terra'}
    assert {x['actual_model_id'] for x in ordinary + titles} == expected_models
    for attempt in ordinary + titles:
        assert attempt['state'] == 'COMPLETED'
        assert (attempt['input_tokens'], attempt['output_tokens'], attempt['charge_micros'], attempt['currency_code']) == (8, 4, 4, 'USD')
    exchange = json.loads(c.execute('SELECT exchange_json FROM workspace_exchange').fetchone()[0])
    targets = {x['conversation_id'] for x in ordinary}
    conversations = [x for x in exchange['conversations'] if x['id'] in targets]
    assert len(conversations) == (1 if mixed else 6)
    for conv in conversations:
        assert next(x for x in conv['messages'] if x['id'] == conv['currentLeafId'])['delivery'] == 'COMPLETE'
        for message in conv['messages']:
            for block in message['blocks']:
                if block['kind'] == 'ASSET_REF':
                    asset = block['asset']; data = (root / 'assets' / asset['sha256']).read_bytes()
                    assert len(data) == asset['byteCount'] and hashlib.sha256(data).hexdigest() == asset['sha256']
    if mixed:
        user = next(x for x in conversations[0]['messages'] if x['role'] == 'user')
        assert {x['asset']['mimeType'] for x in user['blocks'] if x['kind'] == 'ASSET_REF'} == {'application/pdf', 'image/png'}
        assert receipt['actualSendUi'] and receipt['pasteUi'] and 'GPT-5.6 Sol' in receipt['visibleRecipient']
    else:
        conv = next(x for x in conversations if x['id'] == receipt['pdfConversationId'])
        users = [x for x in conv['messages'] if x['role'] == 'user']
        assert len(users) == 2 and not any(x['kind'] == 'ASSET_REF' for x in users[-1]['blocks'])
        assert receipt['unsupportedPdfRejected'] and receipt['oldPdfNotResent']
    assert c.execute('SELECT count(*) FROM desktop_transcription_tasks').fetchone()[0] == 0
    u = fault.connect(root / 'usage-ledger' / 'usage-ledger-v1.sqlite3')
    usage = u.execute('SELECT requested_model_id,input_tokens,output_tokens,charge_micros,currency_code FROM usage_ledger_entries').fetchall()
    assert len(usage) == len(ordinary) + len(titles)
    assert {x[0] for x in usage} == expected_models and all(tuple(x[1:]) == (8, 4, 4, 'USD') for x in usage)
    w = fault.connect(root / 'work-area' / 'workspace.sqlite3')
    assert w.execute('PRAGMA integrity_check').fetchone()[0] == 'ok' and not w.execute('PRAGMA foreign_key_check').fetchall()
    for table in ['desktop_ordinary_chat_attempts', 'desktop_conversation_title_attempts', 'desktop_conversation_attachments', 'desktop_transcription_tasks']:
        assert w.execute('SELECT count(*) FROM ' + table).fetchone()[0] == 0
    c.close(); w.close(); u.close()
    return {'root': str(root), 'pass': True, 'privateAttachmentCount': len(attachments), 'allPrivateHashesExact': True, 'ordinaryCount': len(ordinary), 'titleCount': len(titles), 'usageCount': len(usage), 'workAttemptsAndAttachments': 0, 'ocrTasks': 0}

if __name__ == '__main__':
    print(json.dumps([check(x) for x in sys.argv[1:]], ensure_ascii=False, indent=2))
