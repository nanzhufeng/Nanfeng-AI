"""Read-only current marked branch lineage, request attribution and ledger check."""
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
receipt = json.loads((base / 'fixed-native.txt').read_text(encoding='utf-8-sig').splitlines()[-1])
root = Path(receipt['root']).resolve(strict=True)
assert json.loads((root / '.nanfeng-windows-acceptance.json').read_text(encoding='utf-8-sig'))['identifier'] == 'com.nanzhufeng.ai.desktop.windowsacceptance'
assert receipt['requestCount'] == 6 and receipt['screenshot'] is None
for key in ['beforeColdClose', 'normalClose']:
    assert receipt[key]['accepted'] and receipt[key]['responding'] and receipt[key]['title']
integrity = json.loads(subprocess.check_output([sys.executable,
    str(Path(__file__).with_name('check-stability-synthetic-sqlite.py')), str(root), '--closed-browser'], encoding='utf-8'))
assert integrity['allOk']
with readonly(root / 'workspace.sqlite3') as c:
    exchange = json.loads(c.execute('SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?', (receipt['workspaceId'],)).fetchone()[0])
    conversation = next(v for v in exchange['conversations'] if v['id'] == receipt['conversationId'])
    nodes = {v['id']: v for v in conversation['messages']}
    assert len(nodes) == len(conversation['messages']) == 9
    ui = receipt['visibleEditAndGenerate']
    original, edited, ui_user = [nodes[i] for i in [receipt['originalUserId'], receipt['editedUserId'], ui['userId']]]
    for node, text, revision in [(original, '合成原问题', 1), (edited, '合成编辑后的问题', 2), (ui_user, '合成界面编辑后的问题', 3)]:
        assert node['role'] == 'user' and node['parentId'] is None and node['revision'] == revision
        assert node['blocks'] == [{'kind': 'TEXT', 'ordinal': 0, 'text': text}]
    assert edited['editedFromMessageId'] == original['id']
    assert ui_user['editedFromMessageId'] == edited['id']
    answer_ids = receipt['answerIds'] + [ui['answerId'], ui['regeneratedId'], ui['changedModelId']]
    assert len(set(answer_ids)) == 6
    attempts = [dict(r) for r in c.execute('SELECT * FROM desktop_ordinary_chat_attempts WHERE conversation_id=? ORDER BY created_at_ms', (conversation['id'],))]
    assert len(attempts) == 6 and len({a['attempt_id'] for a in attempts}) == 6
    attempt_by_answer = {a['assistant_message_id']: a for a in attempts}
    for i, answer_id in enumerate(answer_ids):
        answer = nodes[answer_id]
        attempt = attempt_by_answer[answer_id]
        expected_user = edited['id'] if i < 3 else ui_user['id']
        assert answer['role'] == 'assistant' and answer['delivery'] == 'COMPLETE'
        assert answer['parentId'] == attempt['user_message_id'] == expected_user
        assert attempt['state'] == 'COMPLETED' and attempt['requested_model_id'] == attempt['actual_model_id'] == receipt['requests'][i]['model']
        assert attempt['idempotency_key'] == receipt['requests'][i]['idempotencyKey']
        assert attempt['input_tokens'] == 8 and attempt['output_tokens'] == 4 and attempt['charge_micros'] == 4
        if i in [1, 2, 4, 5]:
            assert answer['originMessageId'] == answer_ids[i - 1]
            assert answer['previousAttemptId'] == attempt_by_answer[answer_ids[i - 1]]['attempt_id']
    assert len({r['idempotencyKey'] for r in receipt['requests']}) == 6
    assert [r['userTextPresent'] for r in receipt['requests']] == [True, True, True, False, False, False]
    assert [r['uiEditedTextPresent'] for r in receipt['requests']] == [False, False, False, True, True, True]
    assert receipt['models'][0] == receipt['models'][1] != receipt['models'][2]
    assert receipt['models'][3] == receipt['models'][4] != receipt['models'][5]
    assert conversation['currentLeafId'] == ui['changedModelId']
    assert not any(v['parentId'] == original['id'] for v in nodes.values())
    assert {v['id'] for v in nodes.values() if not any(child['parentId'] == v['id'] for child in nodes.values())} == {original['id'], *answer_ids}
    intents = {r[0]: r[1] for r in c.execute("SELECT action,count(*) FROM domain_intents WHERE entity='conversation' GROUP BY action")}
    assert intents == {'create': 1, 'editUserMessage': 2, 'replyToSavedUser': 2, 'regenerateAnswer': 2, 'changeModelAnswer': 2, 'switchLeaf': 6}
with readonly(root / 'usage-ledger/usage-ledger-v1.sqlite3') as c:
    ledger = [dict(r) for r in c.execute('SELECT * FROM usage_ledger_entries WHERE conversation_id=?', (conversation['id'],))]
    assert len(ledger) == 6 and {r['attempt_id'] for r in ledger} == {a['attempt_id'] for a in attempts}
    for row in ledger:
        assert row['input_tokens'] == 8 and row['output_tokens'] == 4 and row['charge_micros'] == 4
        assert row['currency_code'] == 'USD' and row['cost_source'] == 'PROVIDER_RESPONSE'
        assert row['kind'] == 'FINAL_MEASURED' and row['fact_grade'] == 'PROVIDER_REPORTED'
        assert not {'body', 'authorization', 'api_key'} & row.keys()
with readonly(root / 'work-area/workspace.sqlite3') as c:
    assert c.execute('SELECT count(*) FROM desktop_ordinary_chat_attempts').fetchone()[0] == 0
    assert c.execute('SELECT count(*) FROM domain_intents').fetchone()[0] == 0
    for row in c.execute('SELECT exchange_json FROM workspace_exchange'):
        assert not any(v['id'] == conversation['id'] for v in json.loads(row[0])['conversations'])
print(json.dumps({'pass': True, 'candidateSha256': receipt['candidateSha256'], 'root': str(root),
    'nodes': 9, 'users': 3, 'assistantAnswers': 6, 'distinctAttempts': 6, 'ledgerEntries': 6,
    'originalNodesPreserved': True, 'contextBranchesIsolated': True, 'workAreaIsolated': True,
    'twoNormalCloses': True, 'integrity': integrity}, ensure_ascii=False, indent=2))
