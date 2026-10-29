"""Read-only marked synthetic history cancellation/rejection/deletion verification."""
import json
import sqlite3
import subprocess
import sys
from pathlib import Path

base = Path(sys.argv[1]).resolve(strict=True)
facts = []
for name in ['fixed-native', 'delete-fixed-native', 'reject-fixed-native']:
    receipt = json.loads((base / (name + '.txt')).read_text(encoding='utf-8-sig').splitlines()[-1])
    root = Path(receipt['root']).resolve(strict=True)
    assert json.loads((root / '.nanfeng-windows-acceptance.json').read_text(encoding='utf-8-sig'))['identifier'] == 'com.nanzhufeng.ai.desktop.windowsacceptance'
    integrity = json.loads(subprocess.check_output([sys.executable,
        str(Path(__file__).with_name('check-stability-synthetic-sqlite.py')), str(root), '--closed-browser'], encoding='utf-8'))
    assert integrity['allOk']
    with sqlite3.connect((root / 'work-area/workspace.sqlite3').as_uri() + '?mode=ro', uri=True) as c:
        c.row_factory = sqlite3.Row
        rows = [dict(r) for r in c.execute('SELECT * FROM desktop_history_knowledge_candidates_v1')]
        checkpoints = [dict(r) for r in c.execute('SELECT * FROM desktop_history_knowledge_checkpoints_v1')]
        assert len(rows) == len(checkpoints) == 1
        row = rows[0]
        assert row['source_hash'] == checkpoints[0]['source_hash'] and row['status'] == checkpoints[0]['status']
        exchange = json.loads(c.execute('SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?', (receipt['workspaceId'],)).fetchone()[0])
        intents = [dict(r) for r in c.execute("SELECT * FROM domain_intents WHERE entity IN ('knowledge','memory','relation')")]
        if name == 'fixed-native':
            assert receipt['localDisableCancelledActive'] and receipt['coldNoAutoRetry']
            assert row['status'] == 'FAILED' and row['safe_error_code'] == 'CANCELLED' and row['body'] is None
            assert row['attempt_id'] == receipt['cancelledAttemptId'] != receipt['failedAttemptId']
            assert row['retry_count'] == 1 and row['checkpoint_id'] == receipt['checkpointId']
            assert all(row[k] is None for k in ['actual_model_id', 'input_tokens', 'output_tokens', 'cached_input_tokens', 'reasoning_tokens', 'charge_micros', 'currency_code', 'cost_source'])
            assert c.execute('SELECT history_library_enabled FROM desktop_app_settings').fetchone()[0] == 0
            assert c.execute('SELECT paused FROM desktop_history_knowledge_schedule_v1').fetchone()[0] == 1
            assert not intents and not exchange['knowledge']
        else:
            assert row['candidate_id'] == receipt['candidateId'] and row['status'] == 'DELETED'
            if name == 'delete-fixed-native':
                assert receipt['deletedAcceptedCandidate'] and row['knowledge_id'] == receipt['knowledgeId']
                assert len(intents) == 1 and intents[0]['entity'] == 'knowledge' and intents[0]['action'] == 'create'
                knowledge = next(k for k in exchange['knowledge'] if k['id'] == row['knowledge_id'])
                assert knowledge['status'] == 'ACTIVE' and knowledge['revision'] == 1
                assert knowledge['title'] == '已审阅合成标题' and knowledge['body'] == '已审阅合成正文' and knowledge['tags'] == []
            else:
                assert receipt['rejectedAndDeleted'] and receipt['noKnowledgeCreated'] and receipt['coldNoAutoRetry']
                assert row['knowledge_id'] is None and not intents and not exchange['knowledge']
    with sqlite3.connect((root / 'work-area/usage-ledger/usage-ledger-v1.sqlite3').as_uri() + '?mode=ro', uri=True) as c:
        c.row_factory = sqlite3.Row
        ledger = [dict(r) for r in c.execute("SELECT * FROM usage_ledger_entries WHERE requested_model_id='fixture-history-knowledge'")]
    assert len(ledger) == 1
    assert ledger[0]['charge_micros'] == 7 and ledger[0]['input_tokens'] == 20 and ledger[0]['output_tokens'] == 8 and ledger[0]['fact_grade'] == 'PROVIDER_REPORTED'
    if name == 'fixed-native':
        assert ledger[0]['attempt_id'] == receipt['failedAttemptId']
        assert ledger[0]['attempt_id'] != receipt['cancelledAttemptId']
    with sqlite3.connect((root / 'workspace.sqlite3').as_uri() + '?mode=ro', uri=True) as c:
        assert c.execute('SELECT count(*) FROM desktop_history_knowledge_candidates_v1').fetchone()[0] == 0
        assert c.execute('SELECT count(*) FROM domain_intents').fetchone()[0] == 0
    facts.append({'case': name, 'candidateSha256': receipt['candidateSha256'], 'root': str(root), 'integrity': integrity,
                  'candidateStatus': row['status'], 'knowledgeDomainIntents': len(intents), 'historyLedgerEntries': 1})
assert len({r['candidateSha256'] for r in facts}) == 1
print(json.dumps({'pass': True, 'cases': facts}, ensure_ascii=False, indent=2))
