"""Read-only verification of marked native history retry ledger preservation."""
import json
import sqlite3
import subprocess
import sys
from pathlib import Path


def open_readonly(path):
    connection = sqlite3.connect(path.as_uri() + '?mode=ro', uri=True)
    connection.row_factory = sqlite3.Row
    return connection


base = Path(sys.argv[1]).resolve(strict=True)
facts = []
for name in ['current-native', 'existing-native']:
    observations = json.loads((base / (name + '-parsed.json')).read_text(encoding='utf-8'))
    receipt = observations[-1]
    root = Path(receipt['root']).resolve(strict=True)
    assert json.loads((root / '.nanfeng-windows-acceptance.json').read_text(encoding='utf-8-sig'))['identifier'] == 'com.nanzhufeng.ai.desktop.windowsacceptance'
    integrity = json.loads(subprocess.check_output([sys.executable,
        str(Path(__file__).with_name('check-stability-synthetic-sqlite.py')), str(root), '--closed-browser'], encoding='utf-8'))
    assert integrity['allOk']
    assert receipt['lateDestinationUnchanged'] and receipt['coldNoAutoRetry']
    assert receipt['historyRequests'] == 2 and receipt['providerRequests'] == 4
    with open_readonly(root / 'work-area/workspace.sqlite3') as c:
        candidates = [dict(r) for r in c.execute('SELECT * FROM desktop_history_knowledge_candidates_v1')]
        checkpoints = [dict(r) for r in c.execute('SELECT * FROM desktop_history_knowledge_checkpoints_v1')]
        assert len(candidates) == len(checkpoints) == 1
        candidate = candidates[0]
        assert candidate['candidate_id'] == receipt['failedCandidateId']
        assert candidate['attempt_id'] == receipt['retriedAttemptId'] != receipt['failedAttemptId']
        assert candidate['checkpoint_id'] == checkpoints[0]['checkpoint_id'] == receipt['checkpointId']
        assert candidate['source_hash'] == checkpoints[0]['source_hash']
        assert candidate['status'] == checkpoints[0]['status'] == 'PENDING_REVIEW'
        assert candidate['retry_count'] == 1
        attempts = [dict(r) for r in c.execute('SELECT * FROM desktop_ordinary_chat_attempts')]
        assert len(attempts) == 2 and all(r['state'] == 'COMPLETED' for r in attempts)
        exchange = json.loads(c.execute('SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?', (receipt['workspaceId'],)).fetchone()[0])
        assert not exchange['knowledge'] and not exchange['memory']
        assert c.execute("SELECT count(*) FROM domain_intents WHERE entity IN ('knowledge','memory','relation')").fetchone()[0] == 0
    with open_readonly(root / 'work-area/usage-ledger/usage-ledger-v1.sqlite3') as c:
        rows = [dict(r) for r in c.execute("SELECT * FROM usage_ledger_entries WHERE requested_model_id='fixture-history-knowledge'")]
        assert len(rows) == 2
        assert {r['attempt_id'] for r in rows} == {receipt['failedAttemptId'], receipt['retriedAttemptId']}
        assert c.execute("SELECT count(*) FROM sqlite_master WHERE type='trigger' AND name='synthetic_history_ledger_append_denied'").fetchone()[0] == 0
    for r in rows:
        assert r['entry_id'] == 'usage-' + r['attempt_id']
        assert r['invocation_id'] == r['attempt_id']
        assert r['replay_token'] == 'history-knowledge-final-' + r['attempt_id']
        assert r['execution_id'] == candidate['candidate_id']
        assert r['conversation_id'] == candidate['conversation_id']
        assert r['branch_leaf_message_id'] == json.loads(candidate['source_message_ids_json'])[-1]
        assert r['input_tokens'] == 20 and r['output_tokens'] == 8 and r['charge_micros'] == 7
        assert r['actual_model_id'] == 'fixture-history-knowledge'
        assert r['fact_grade'] == 'PROVIDER_REPORTED' and r['kind'] == 'FINAL_MEASURED'
        assert r['currency_code'] == 'USD' and r['cost_source'] == 'PROVIDER_RESPONSE'
        assert not {'body', 'api_key', 'credential'} & r.keys()
    original_row = next(r for r in rows if r['attempt_id'] == receipt['failedAttemptId'])
    if name == 'current-native':
        blocked = observations[0]
        assert blocked['stage'] == 'retry-ledger-block-observed'
        assert blocked['original'] == blocked['blocked']
        assert not blocked['unexpectedNetwork'] and blocked['historyRequests'] == 1
        assert blocked['original']['chargeMicros'] == 7 and blocked['original']['status'] == 'FAILED'
        assert original_row['occurred_at_ms'] == blocked['original']['updatedAtMs']
        assert receipt['blockedRetryPreservedCandidate'] and receipt['repairedPreviousLedger']
    else:
        assert receipt['previousLedgerBefore'] == receipt['previousLedgerAfter'] == original_row
    with open_readonly(root / 'workspace.sqlite3') as c:
        assert c.execute('SELECT count(*) FROM desktop_history_knowledge_candidates_v1').fetchone()[0] == 0
        assert c.execute('SELECT count(*) FROM desktop_ordinary_chat_attempts').fetchone()[0] == 0
    facts.append({'case': name, 'candidateSha256': receipt['candidateSha256'], 'root': str(root),
                  'integrity': integrity, 'historyLedgerEntries': 2, 'ordinaryAttempts': 2,
                  'originalEntryPreserved': True, 'newAttemptSeparate': True})
assert len({r['candidateSha256'] for r in facts}) == 1
print(json.dumps({'pass': True, 'cases': facts}, ensure_ascii=False, indent=2))
