"""Read-only current budget/context/ledger and unsubmitted draft verification."""
import json
import sqlite3
import subprocess
import sys
from pathlib import Path


def load(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


base = Path(sys.argv[1]).resolve(strict=True)
receipt = json.loads((base / 'controlled-cold-native.txt').read_text(encoding='utf-8-sig').splitlines()[-1])
root = Path(receipt['root']).resolve(strict=True)
assert load(root / '.nanfeng-windows-acceptance.json')['identifier'] == 'com.nanzhufeng.ai.desktop.windowsacceptance'
evidence = load(root / 'context-budget-evidence.json')
ui = load(root / 'context-budget-sources-ui-evidence.json')
assert receipt['pass'] is evidence['pass'] is ui['pass'] is True
assert receipt['candidateSha256'] == evidence['candidateSha256'] == ui['candidateSha256']
assert evidence['historyLibraryDisabledBeforeCold'] and ui['noHttp'] and ui['draftRetained']
assert not evidence.get('forcedCleanup') and not ui.get('forcedCleanup')
for close in [evidence['firstNormalClose'], evidence['secondNormalClose'], ui['normalClose']]:
    assert close['accepted'] and close['responding'] and close['title']
first, second = evidence['firstRequest'], evidence['secondRequest']
assert first['maxOutputTokens'] == 512 and first['estimatedInputTokens'] == 1413 <= 2048 - 512
assert first['sentTurns'] == 4 and first['latestWhole'] and first['oldestRemoved']
assert first['smallKnowledgeWhole'] and first['largeKnowledgeAbsent'] and first['memoryWhole']
assert second['maxOutputTokens'] == 1024 and second['requestedCatalogOutputTokens'] == 65536 and second['latestWhole']
assert all(evidence['refusal'][k] for k in ['noAdditionalHttp', 'conversationUnchanged', 'draftRetained'])
assert len(ui['sourceUi']) == 2 and all(v['recordExact'] and v['smallKnowledgeVisible'] and v['memoryVisible'] and v['largeKnowledgeAbsent'] for v in ui['sourceUi'])
integrity = json.loads(subprocess.check_output([sys.executable,
    str(Path(__file__).with_name('check-stability-synthetic-sqlite.py')), str(root), '--closed-browser'], encoding='utf-8'))
assert integrity['allOk']
with sqlite3.connect((root / 'workspace.sqlite3').as_uri() + '?mode=ro', uri=True) as c:
    c.row_factory = sqlite3.Row
    exchange = json.loads(c.execute('SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?', (evidence['workspaceId'],)).fetchone()[0])
    assert exchange == evidence['beforeCold']['exchange']
    conversation = next(v for v in exchange['conversations'] if v['id'] == evidence['conversationId'])
    nodes = {m['id']: m for m in conversation['messages']}
    assert len(nodes) == len(conversation['messages']) == 27
    for i in range(23):
        node = nodes['synthetic-budget-old-' + str(i)]
        assert node['blocks'] == [{'kind': 'TEXT', 'ordinal': 0, 'text': f'OLD_BUDGET_{i}_BEGIN ' + 'x' * 1200 + f' OLD_BUDGET_{i}_END'}]
        assert node['parentId'] == ('synthetic-budget-old-' + str(i - 1) if i else None)
    assert sum(m['role'] == 'user' for m in nodes.values()) == 25
    assert not any('CURRENT_OVERSIZED_' in str(m['blocks']) for m in nodes.values())
    attempts = [dict(r) for r in c.execute('SELECT * FROM desktop_ordinary_chat_attempts')]
    assert len(attempts) == 2 and len({a['idempotency_key'] for a in attempts}) == 2
    for attempt in attempts:
        assert attempt['state'] == 'COMPLETED' and attempt['requested_model_id'] == attempt['actual_model_id'] == 'openai/gpt-5.6-terra'
        assert attempt['input_tokens'] == 32 and attempt['output_tokens'] == 8 and attempt['charge_micros'] == 2
        assert attempt['cost_source'] == 'PROVIDER_REPORTED' and attempt['currency_code'] == 'USD'
        assert nodes[attempt['assistant_message_id']]['delivery'] == 'COMPLETE'
        sources = [(r['source_kind'], r['source_id']) for r in c.execute('SELECT * FROM desktop_ordinary_chat_context_sources WHERE attempt_id=? ORDER BY ordinal', (attempt['attempt_id'],))]
        assert ('资料库', 'synthetic-budget-small-knowledge') in sources
        assert ('Memory', 'synthetic-budget-memory') in sources
        assert all(source_id != 'synthetic-budget-large-knowledge' for _, source_id in sources)
    draft = c.execute('SELECT text FROM desktop_conversation_drafts_v1 WHERE workspace_id=? AND conversation_key=?', (evidence['workspaceId'], evidence['conversationId'])).fetchone()[0]
    assert draft == '预算超限 CURRENT_OVERSIZED_BEGIN ' + '超' * 4096 + ' CURRENT_OVERSIZED_END'
    assert c.execute('SELECT COUNT(*) FROM desktop_history_knowledge_candidates_v1').fetchone()[0] == 0
    assert c.execute('SELECT COUNT(*) FROM desktop_conversation_title_attempts').fetchone()[0] == 0
with sqlite3.connect((root / 'usage-ledger/usage-ledger-v1.sqlite3').as_uri() + '?mode=ro', uri=True) as c:
    c.row_factory = sqlite3.Row
    ledger = [dict(r) for r in c.execute('SELECT * FROM usage_ledger_entries')]
    assert len(ledger) == 2 and {r['attempt_id'] for r in ledger} == {a['attempt_id'] for a in attempts}
    assert all(r['input_tokens'] == 32 and r['output_tokens'] == 8 and r['charge_micros'] == 2 and r['currency_code'] == 'USD' for r in ledger)
with sqlite3.connect((root / 'work-area/workspace.sqlite3').as_uri() + '?mode=ro', uri=True) as c:
    assert c.execute('SELECT COUNT(*) FROM desktop_ordinary_chat_attempts').fetchone()[0] == 0
    assert c.execute('SELECT COUNT(*) FROM workspace_exchange WHERE workspace_id=?', (evidence['workspaceId'],)).fetchone()[0] == 0
pids = ','.join(str(int(pid)) for pid in evidence['pids'] + ui['pids'])
assert subprocess.check_output(['pwsh', '-NoProfile', '-Command',
    f'@(Get-Process -Id {pids} -ErrorAction SilentlyContinue).Count'], encoding='utf-8').strip() == '0'
print(json.dumps({'allOk': True, 'candidateSha256': receipt['candidateSha256'], 'root': str(root),
    'integrity': integrity, 'originalMessagesExact': 23, 'completedAttempts': 2,
    'rejectedInputStillDraft': True, 'sourcePairsExact': True, 'syntheticChargeMicrosPerAttempt': 2,
    'normalCloseCount': 3, 'noPeerAttempts': True, 'remainingOwnedProcesses': 0}, ensure_ascii=False, indent=2))
