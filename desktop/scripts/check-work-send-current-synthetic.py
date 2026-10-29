"""Independent checks of marked synthetic WORK first-send lifecycle."""
import importlib.util
import json
import sqlite3
import sys
from pathlib import Path

spec = importlib.util.spec_from_file_location('fault', Path(__file__).with_name('fault-cloud-restore-synthetic.py'))
fault = importlib.util.module_from_spec(spec); spec.loader.exec_module(fault)

def check(value):
    root = fault.marked(value)
    n = json.loads((root / 'work-send-current-native.json').read_text(encoding='utf-8'))
    assert n['pass'] and not n['forcedCleanup'] and len(n['normalCloses']) == 3
    assert all(x['accepted'] and x['responding'] for x in n['normalCloses'])
    for area, rel in [('CHAT', 'workspace.sqlite3'), ('WORK', 'work-area/workspace.sqlite3')]:
        c = fault.connect(root / rel); c.row_factory = sqlite3.Row
        assert c.execute('PRAGMA integrity_check').fetchone()[0] == 'ok' and not c.execute('PRAGMA foreign_key_check').fetchall()
        exchange = json.loads(c.execute('SELECT exchange_json FROM workspace_exchange').fetchone()[0])
        expected = n['beforeCold'][area][0]['exchange']
        assert exchange == expected
        attempts = [dict(x) for x in c.execute('SELECT * FROM desktop_ordinary_chat_attempts')]
        if area == 'CHAT':
            assert exchange == n['chatBefore'][0]['exchange'] and not attempts
        else:
            assert len(attempts) == 2 and len(exchange['conversations']) == 2
            assert next(x for x in exchange['conversations'] if x['id'] == n['unscoped']['conv']['id']) == n['unscoped']['conv']
            assert next(x for x in exchange['conversations'] if x['id'] == n['scoped']['conv']['id']) == n['scoped']['conv']
            assert n['unscoped']['conv']['projectId'] is None and n['scoped']['conv']['projectId'] == n['projectId']
            for x in attempts:
                assert x['state'] == 'COMPLETED' and x['actual_model_id'] == 'openai/gpt-5.6-terra'
                assert (x['input_tokens'], x['output_tokens'], x['charge_micros'], x['currency_code']) == (32, 8, 2, 'USD')
            assert c.execute('SELECT count(*) FROM desktop_conversation_drafts_v1').fetchone()[0] == 0
            u = fault.connect(root / 'work-area/usage-ledger/usage-ledger-v1.sqlite3')
            usage = u.execute('SELECT input_tokens,output_tokens,charge_micros,currency_code FROM usage_ledger_entries').fetchall()
            assert len(usage) == 2 and all(tuple(x) == (32, 8, 2, 'USD') for x in usage); u.close()
        assert c.execute('SELECT count(*) FROM desktop_conversation_title_attempts').fetchone()[0] == 0
        assert c.execute('SELECT count(*) FROM desktop_transcription_tasks').fetchone()[0] == 0
        c.close()
    assert len(n['requests']) == 2 and all(x['payload']['model'] == 'openai/gpt-5.6-terra' for x in n['requests'])
    return {'root': str(root), 'pass': True, 'workConversations': 2, 'workOrdinaryAttempts': 2, 'workUsageEntries': 2, 'workChargeMicros': 4, 'chatExchangeExact': True, 'chatAttempts': 0, 'coldWholeExchangeExact': True, 'draftCleared': True, 'projectBindingExact': True}

if __name__ == '__main__':
    print(json.dumps(check(sys.argv[1]), ensure_ascii=False, indent=2))
