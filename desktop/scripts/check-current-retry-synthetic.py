"""Read-only current-candidate retry lineage, diagnostics, material and ledger checks."""
import hashlib
import json
import sqlite3
import subprocess
import sys
from pathlib import Path


def require(value, reason):
    if not value:
        raise AssertionError(reason)


def connect(path):
    c = sqlite3.connect(path.as_uri() + '?mode=ro', uri=True)
    c.row_factory = sqlite3.Row
    return c


base = Path(sys.argv[1]).resolve(strict=True)
results = []
for name in ('deepseek-corrected-native', 'qwen-native'):
    receipt = json.loads((base / (name + '.txt')).read_text(encoding='utf-8-sig'))
    root = Path(receipt['root']).resolve(strict=True)
    marker = json.loads((root / '.nanfeng-windows-acceptance.json').read_text(encoding='utf-8-sig'))
    require(marker['identifier'] == 'com.nanzhufeng.ai.desktop.windowsacceptance', 'unmarked root')
    integrity = json.loads(subprocess.check_output([
        sys.executable, str(Path(__file__).with_name('check-stability-synthetic-sqlite.py')),
        str(root), '--closed-browser'], encoding='utf-8'))
    require(integrity['allOk'], 'SQLite integrity/FK')
    with connect(root / 'workspace.sqlite3') as c:
        attempts = [dict(r) for r in c.execute('SELECT * FROM desktop_ordinary_chat_attempts')]
        titles = [dict(r) for r in c.execute('SELECT * FROM desktop_conversation_title_attempts')]
        diagnostics = [dict(r) for r in c.execute('SELECT * FROM desktop_ordinary_chat_diagnostics')]
        exchange = json.loads(c.execute('SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?', (receipt['workspaceId'],)).fetchone()[0])
        attachments = [dict(r) for r in c.execute('SELECT * FROM desktop_conversation_attachments')]
    require(len(attempts) == len(titles) == 1, 'ordinary/title attempts duplicated')
    attempt = attempts[0]
    title = titles[0]
    require(attempt['attempt_id'] == receipt['attemptId'] and attempt['retry_count'] == 1, 'explicit retry generation')
    require(attempt['state'] == title['state'] == 'COMPLETED', 'completed retry/title')
    require(attempt['charge_micros'] == title['charge_micros'] == 4, 'separate measured charges')
    require(any(d['state'] == 'FAILED' and d['http_status'] == 503 and d['safe_error_code'] == 'SERVICE_UNAVAILABLE' for d in diagnostics), 'original HTTP503 diagnostic retained')
    conversation = next(x for x in exchange['conversations'] if x['id'] == receipt['conversationId'])
    messages = conversation['messages']
    users = [m for m in messages if m['role'] == 'user']
    failed = [m for m in messages if m['delivery'] == 'FAILED']
    completed = [m for m in messages if m['role'] == 'assistant' and m['delivery'] == 'COMPLETE']
    require(len(users) == len(failed) == len(completed) == 1 and len(messages) == 3, 'immutable retry sibling tree')
    require(failed[0]['parentId'] == completed[0]['parentId'] == users[0]['id'], 'retry lineage')
    require(failed[0]['id'] != completed[0]['id'], 'new visible assistant node')
    require(failed[0]['attemptId'] == completed[0]['attemptId'] == attempt['attempt_id'], 'same durable attempt owner')
    require(failed[0].get('chargeMicros') is None and failed[0].get('actualModelId') is None, 'old failure usage/model not fabricated')
    require(conversation['currentLeafId'] == attempt['assistant_message_id'] == completed[0]['id'], 'current leaf points to retry')
    require(completed[0]['chargeMicros'] == 4 and completed[0]['actualModelId'] == attempt['actual_model_id'], 'retry attribution')
    require(conversation['title'] == '附件联网重试验收' and not conversation['autoTitlePending'], 'title persisted')
    facts = receipt['requestFacts']
    ordinary = [r for r in facts if r['stream']]
    title_requests = [r for r in facts if not r['stream']]
    require(len(ordinary) == 2 and len(title_requests) == 1, 'explicit request cardinality')
    require(ordinary[0] == ordinary[1], 'original route/model/idempotency key retained')
    require(ordinary[0]['idempotencyKey'] == attempt['idempotency_key'], 'durable idempotency key')
    require(ordinary[0]['model'] == attempt['requested_model_id'] == attempt['actual_model_id'], 'retry model attribution')
    require(title_requests[0]['model'] == title['requested_model_id'] == title['actual_model_id'], 'title model attribution')
    with connect(root / 'usage-ledger/usage-ledger-v1.sqlite3') as c:
        ledger = [dict(r) for r in c.execute('SELECT * FROM usage_ledger_entries')]
    require(len(ledger) == 2 and {r['attempt_id'] for r in ledger} == {attempt['attempt_id'], title['attempt_id']}, 'exactly separate ordinary/title ledger')
    require(all(r['kind'] == 'FINAL_MEASURED' and r['fact_grade'] == 'PROVIDER_REPORTED' and r['charge_micros'] == 4 for r in ledger), 'measured ledger facts')
    require(all(r['branch_leaf_message_id'] == completed[0]['id'] and r['conversation_id'] == conversation['id'] for r in ledger), 'ledger branch ownership')
    material = None
    if name == 'qwen-native':
        references = [b['asset'] for b in users[0]['blocks'] if b['kind'] == 'ASSET_REF']
        require(len(references) == 1, 'one original material reference')
        ref = references[0]
        metadata = next(a for a in attachments if a['attachment_id'] == ref['id'])
        data = (root / 'assets' / metadata['sha256']).read_bytes()
        require(hashlib.sha256(data).hexdigest() == metadata['sha256'] and len(data) == metadata['byte_count'], 'material hash/size')
        require(data == 'W06 合成 TXT 正文，仅用于回环验收。\n'.encode('utf-8'), 'original material retained')
        require(attempt['web_search_route'] == 'QWEN_CHAT_COMPLETIONS', 'original search route')
        material = {'id': ref['id'], 'sha256': metadata['sha256'], 'bytes': len(data)}
    with connect(root / 'work-area/workspace.sqlite3') as c:
        require(c.execute('SELECT count(*) FROM desktop_ordinary_chat_attempts').fetchone()[0] == 0, 'WORK attempt leak')
    results.append({'case': name, 'root': str(root), 'candidateSha256': receipt['candidateSha256'],
                    'attemptId': attempt['attempt_id'], 'retryCount': 1, 'originalFailureMessageId': failed[0]['id'],
                    'retryMessageId': completed[0]['id'], 'requestFacts': facts, 'material': material,
                    'ordinaryChargeMicros': 4, 'titleChargeMicros': 4, 'ledgerCount': len(ledger),
                    'sqlite': integrity, 'allAssertionsPassed': True})
(base / 'independent.json').write_text(json.dumps({'allAssertionsPassed': True, 'results': results}, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(json.dumps({'allAssertionsPassed': True, 'cases': len(results), 'sqliteCounts': [r['sqlite']['count'] for r in results]}))
