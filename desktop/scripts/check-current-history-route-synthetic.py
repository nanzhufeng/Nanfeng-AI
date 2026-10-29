"""Read-only current synthetic history retry/context ownership checks."""
import json
import sqlite3
import subprocess
import sys
from pathlib import Path

base = Path(sys.argv[1]).resolve(strict=True)
results = []
for name in ['current-native', 'context-native']:
    receipt = json.loads((base / (name + '.txt')).read_text(encoding='utf-8-sig').splitlines()[-1])
    root = Path(receipt['root']).resolve(strict=True)
    assert json.loads((root / '.nanfeng-windows-acceptance.json').read_text(encoding='utf-8-sig'))['identifier'] == 'com.nanzhufeng.ai.desktop.windowsacceptance'
    integrity = json.loads(subprocess.check_output([sys.executable,
        str(Path(__file__).with_name('check-stability-synthetic-sqlite.py')), str(root), '--closed-browser'], encoding='utf-8'))
    assert integrity['allOk']
    with sqlite3.connect((root / 'work-area/workspace.sqlite3').as_uri() + '?mode=ro', uri=True) as c:
        c.row_factory = sqlite3.Row
        candidates = [dict(r) for r in c.execute('SELECT * FROM desktop_history_knowledge_candidates_v1')]
        checkpoints = [dict(r) for r in c.execute('SELECT * FROM desktop_history_knowledge_checkpoints_v1')]
        assert len(candidates) == len(checkpoints) == 1
        candidate = candidates[0]
        assert candidate['source_hash'] == checkpoints[0]['source_hash']
        assert candidate['checkpoint_id'] == checkpoints[0]['checkpoint_id']
        attempts = [dict(r) for r in c.execute('SELECT * FROM desktop_ordinary_chat_attempts ORDER BY created_at_ms')]
        assert all(r['state'] == 'COMPLETED' for r in attempts)
        exchange = json.loads(c.execute('SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?', (receipt['workspaceId'],)).fetchone()[0])
        if name == 'current-native':
            assert candidate['candidate_id'] == receipt['failedCandidateId']
            assert candidate['attempt_id'] == receipt['retriedAttemptId'] != receipt['failedAttemptId']
            assert candidate['checkpoint_id'] == receipt['checkpointId']
            assert candidate['status'] == checkpoints[0]['status'] == 'PENDING_REVIEW' and candidate['retry_count'] == 1
            assert len(attempts) == 2 and not exchange['knowledge'] and not exchange['memory']
            assert receipt['lateDestinationUnchanged'] and receipt['coldNoAutoRetry'] and receipt['historyRequests'] == 2
        else:
            assert candidate['status'] == checkpoints[0]['status'] == 'ACCEPTED' and candidate['retry_count'] == 0
            knowledge = next(k for k in exchange['knowledge'] if k['id'] == receipt['knowledgeId'])
            assert knowledge['title'] == '已审阅合成标题' and knowledge['body'] == '已审阅合成正文' and knowledge['tags'] == []
            intents = [dict(r) for r in c.execute("SELECT * FROM domain_intents WHERE entity='knowledge'")]
            assert len(intents) == 1 and intents[0]['action'] == 'create' and intents[0]['object_id'] == knowledge['id']
            assert len(attempts) == 5
            selections = []
            for a in attempts[-3:]:
                sources = [dict(r) for r in c.execute('SELECT * FROM desktop_ordinary_chat_context_sources WHERE attempt_id=?', (a['attempt_id'],))]
                selections.append(any(s['source_kind'] == '资料库' and s['title'] == knowledge['title'] for s in sources))
                assert knowledge['body'] not in json.dumps(sources, ensure_ascii=False)
            assert selections == [False, True, False]
    with sqlite3.connect((root / 'work-area/usage-ledger/usage-ledger-v1.sqlite3').as_uri() + '?mode=ro', uri=True) as c:
        c.row_factory = sqlite3.Row
        history = [dict(r) for r in c.execute("SELECT * FROM usage_ledger_entries WHERE requested_model_id='fixture-history-knowledge'")]
    assert len(history) == (2 if name == 'current-native' else 1)
    assert all(r['input_tokens'] == 20 and r['output_tokens'] == 8 and r['charge_micros'] == 7 and r['fact_grade'] == 'PROVIDER_REPORTED' for r in history)
    if name == 'current-native':
        assert {r['attempt_id'] for r in history} == {receipt['failedAttemptId'], receipt['retriedAttemptId']}
    with sqlite3.connect((root / 'workspace.sqlite3').as_uri() + '?mode=ro', uri=True) as c:
        assert c.execute('SELECT count(*) FROM desktop_history_knowledge_candidates_v1').fetchone()[0] == 0
        assert c.execute('SELECT count(*) FROM desktop_ordinary_chat_attempts').fetchone()[0] == 0
    results.append({'case': name, 'candidateSha256': receipt['candidateSha256'], 'root': str(root),
                    'integrity': integrity, 'historyLedgerEntries': len(history), 'ordinaryAttempts': len(attempts), 'chatHistoryCandidates': 0})
print(json.dumps({'pass': True, 'cases': results}, ensure_ascii=False, indent=2))
