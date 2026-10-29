"""Read-only verification of explicitly marked, closed W06 synthetic roots."""
import json
import re
import sqlite3
import subprocess
import sys
from pathlib import Path


def read_db(path):
    connection = sqlite3.connect(path.as_uri() + '?mode=ro', uri=True)
    connection.row_factory = sqlite3.Row
    return connection


def require(value, reason):
    if not value:
        raise AssertionError(reason)


base = Path(sys.argv[1]).resolve(strict=True)
results = []
names = sys.argv[2:] or ('chat-observed-native', 'work-native', 'terminals-native',
                         'context-project-native', 'context-other-project-native')
for name in names:
    require(re.fullmatch(r'[a-z0-9-]+', name), 'receipt name must be a local case label')
    receipt = json.loads((base / (name + '.txt')).read_text(encoding='utf-8-sig'))
    root = Path(receipt['root']).resolve(strict=True)
    marker = json.loads((root / '.nanfeng-windows-acceptance.json').read_text(encoding='utf-8-sig'))
    require(marker['identifier'] == 'com.nanzhufeng.ai.desktop.windowsacceptance', 'unmarked root')
    require(receipt['configuredModelUi'] is False, 'requires synthetic configuration')
    integrity = json.loads(subprocess.check_output([
        sys.executable, str(Path(__file__).with_name('check-stability-synthetic-sqlite.py')),
        str(root), '--closed-browser'], encoding='utf-8'))
    require(integrity['allOk'], 'SQLite integrity/FK')
    areas = {}
    for area, relative in (('CHAT', 'workspace.sqlite3'), ('WORK', 'work-area/workspace.sqlite3')):
        with read_db(root / relative) as connection:
            attempts = [dict(row) for row in connection.execute('SELECT * FROM desktop_ordinary_chat_attempts')]
            diagnostics = [dict(row) for row in connection.execute('SELECT * FROM desktop_ordinary_chat_diagnostics')]
            sources = [dict(row) for row in connection.execute('SELECT * FROM desktop_ordinary_chat_context_sources ORDER BY ordinal')]
            exchanges = [json.loads(row[0]) for row in connection.execute('SELECT exchange_json FROM workspace_exchange')]
        conversations = [conversation for exchange in exchanges for conversation in exchange['conversations']]
        target = [conversation for conversation in conversations if conversation['id'] == receipt['conversationId']]
        areas[area] = {'attempts': attempts, 'diagnostics': diagnostics, 'target': target, 'sources': sources, 'exchanges': exchanges}
    origin = areas[receipt['originArea']]
    other = areas['WORK' if receipt['originArea'] == 'CHAT' else 'CHAT']
    require(not other['attempts'] and not other['target'], 'cross-area attempt/conversation leak')
    require(len(origin['target']) == 1, 'unique conversation owner')
    conversation = origin['target'][0]
    context_summary = None
    if receipt['contextIds']:
        ids = receipt['contextIds']
        require(conversation['projectId'] == ids['targetProject'], 'conversation project owner')
        knowledge = [item for ex in origin['exchanges'] for item in ex['knowledge'] if item['id'] == ids['knowledge']]
        memory = [item for ex in origin['exchanges'] for item in ex['memory'] if item['id'] == ids['memory']]
        require(len(knowledge) == len(memory) == 1, 'context objects retained')
        require(knowledge[0]['projectId'] == ids['project'] and memory[0]['scopeId'] == ids['project'], 'context project ownership')
        selected_knowledge = [s for s in origin['sources'] if s['source_id'] == ids['knowledge'] and s['source_kind'] == '资料库']
        selected_memory = [s for s in origin['sources'] if s['source_id'] == ids['memory'] and s['source_kind'] == 'Memory']
        if ids['project'] == ids['targetProject']:
            require(len(selected_knowledge) == len(selected_memory) == 1, 'same project selected sources')
            require(selected_knowledge[0]['ordinal'] < selected_memory[0]['ordinal'], 'knowledge precedes memory')
        else:
            require(not selected_knowledge and not selected_memory, 'other-project context leak')
        require(not any(s['source_id'] in (ids['knowledge'], ids['memory']) for s in other['sources']), 'cross-area context record leak')
        context_summary = {'ids': ids, 'sameProject': ids['project'] == ids['targetProject'], 'persistedSources': origin['sources']}
    messages = conversation['messages']
    require(len({message['id'] for message in messages}) == len(messages), 'duplicate message')
    require(conversation['currentLeafId'] == receipt['coldLeafId'], 'cold leaf mismatch')
    attempts = origin['attempts']
    expected = 3 if receipt['cancelledId'] and receipt['failedId'] else 1
    require(len(attempts) == expected == receipt['totalProviderRequests'], 'attempt/request count mismatch')
    summaries = []
    for attempt in attempts:
        matching = [message for message in messages if message['id'] == attempt['assistant_message_id']]
        require(len(matching) == 1, 'unique assistant link')
        message = matching[0]
        require(message['attemptId'] == attempt['attempt_id'], 'attempt attribution')
        require(any(m['id'] == attempt['user_message_id'] for m in messages), 'user link')
        require(attempt['workspace_id'] == receipt['workspaceId'] and attempt['conversation_id'] == receipt['conversationId'], 'owner IDs')
        require(attempt['terminal_at_ms'] is not None, 'unfinished attempt')
        text = ''.join(block.get('text', '') for block in message['blocks'])
        if attempt['state'] == 'COMPLETED':
            require(message['delivery'] == 'COMPLETE' and text == receipt['completedAnswer'], 'completed content')
            require(attempt['input_tokens'] == 9 and attempt['output_tokens'] == 6, 'measured usage')
            require(attempt['charge_micros'] == message['chargeMicros'] == receipt['chargeMicros'] == 5, 'measured charge')
            require(attempt['actual_model_id'] == message['actualModelId'] == attempt['requested_model_id'], 'actual model')
        else:
            require(message['delivery'] == attempt['state'], 'terminal state')
            require(attempt['charge_micros'] is None and attempt['actual_model_id'] is None, 'unknown usage/model fabricated')
            require(message.get('chargeMicros') is None and message.get('actualModelId') is None, 'message fabricated usage/model')
            if attempt['state'] == 'CANCELLED':
                require(message['id'] == receipt['cancelledId'] and bool(text) and '取消后不应出现' not in text, 'cancel partial/late delta')
            elif attempt['state'] == 'FAILED':
                require(message['id'] == receipt['failedId'] and attempt['safe_error_code'] == 'RATE_LIMIT', 'HTTP failure')
                require(any(d['attempt_id'] == attempt['attempt_id'] and d['http_status'] == 429 for d in origin['diagnostics']), 'HTTP429 diagnostic')
            else:
                raise AssertionError('unexpected state')
        summaries.append({key: attempt[key] for key in ('attempt_id', 'assistant_message_id', 'state', 'safe_error_code', 'actual_model_id', 'input_tokens', 'output_tokens', 'charge_micros', 'cost_source')})
    ledger_relative = ('work-area/' if receipt['originArea'] == 'WORK' else '') + 'usage-ledger/usage-ledger-v1.sqlite3'
    with read_db(root / ledger_relative) as connection:
        entries = [dict(row) for row in connection.execute('SELECT * FROM usage_ledger_entries')]
    other_ledger_relative = ('work-area/' if receipt['originArea'] == 'CHAT' else '') + 'usage-ledger/usage-ledger-v1.sqlite3'
    other_ledger_path = root / other_ledger_relative
    if other_ledger_path.exists():
        with read_db(other_ledger_path) as connection:
            require(connection.execute('SELECT count(*) FROM usage_ledger_entries').fetchone()[0] == 0, 'cross-area ledger leak')
    completed = [attempt for attempt in attempts if attempt['state'] == 'COMPLETED']
    require(len(completed) == len(entries) == 1, 'duplicate/missing measured ledger entry')
    entry = entries[0]
    require(entry['attempt_id'] == completed[0]['attempt_id'], 'ledger attempt attribution')
    require(entry['conversation_id'] == receipt['conversationId'] and entry['branch_leaf_message_id'] == completed[0]['assistant_message_id'], 'ledger branch attribution')
    require(entry['kind'] == 'FINAL_MEASURED' and entry['fact_grade'] == 'PROVIDER_REPORTED', 'ledger fact grade')
    require(entry['charge_micros'] == 5 and entry['input_tokens'] == 9 and entry['output_tokens'] == 6, 'ledger usage')
    require(entry['actual_model_id'] == completed[0]['actual_model_id'], 'ledger actual model')
    results.append({'case': name, 'root': str(root), 'candidateSha256': receipt['candidateSha256'],
                    'originArea': receipt['originArea'], 'attempts': summaries, 'ledgerEntries': len(entries), 'ledgerPath': ledger_relative,
                    'sqlite': integrity, 'context': context_summary, 'allAssertionsPassed': True,
                    'scope': 'Synthetic loopback; abrupt guarded shutdown/cold reopen; not real provider or normal-close evidence.'})
output = {'allAssertionsPassed': True, 'results': results}
(base / 'independent.json').write_text(json.dumps(output, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(json.dumps({'allAssertionsPassed': True, 'cases': len(results), 'sqliteCounts': [r['sqlite']['count'] for r in results], 'attemptCounts': [len(r['attempts']) for r in results]}))
