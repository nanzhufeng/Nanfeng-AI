"""Read-only audit of synthetic native search attempts and persisted facts."""
import hashlib
import json
import sqlite3
import sys
from pathlib import Path

root = Path(sys.argv[1]).resolve(strict=True)
assert root.is_relative_to(Path('D:/CodexTemp').resolve())
assert json.loads((root / '.nanfeng-windows-acceptance.json').read_text('utf-8-sig'))['identifier'] == 'com.nanzhufeng.ai.desktop.windowsacceptance'
r = json.loads((root / 'chat-web-search-current-native.json').read_text('utf-8'))
assert r['coldExact'] and r['syntheticSearchResults'] and not r['realWebSearchExecuted']
assert r['ordinaryProviderRequests'] == 4 and r['titleProviderRequests'] == 2 and r['providerRequests'] == 6
assert len(r['closes']) == 2 and all(x['accepted'] and x['responding'] and x['exitCode'] == 0 for x in r['closes'])

def connect(path):
    return sqlite3.connect(path.resolve().as_uri() + '?mode=ro', uri=True)

count = 0
for path in root.rglob('*'):
    if not path.is_file():
        continue
    with path.open('rb') as stream:
        header = stream.read(16)
    if header != b'SQLite format 3\x00':
        continue
    with connect(path) as c:
        assert c.execute('PRAGMA integrity_check').fetchall() == [('ok',)]
        assert not c.execute('PRAGMA foreign_key_check').fetchall()
    count += 1

expected = [('success', 'COMPLETED', None, 1), ('no_sources', 'FAILED', 'WEB_SEARCH_NO_SOURCES', 0), ('incomplete', 'FAILED', 'OUTPUT_LIMIT', 0), ('offline', 'COMPLETED', None, 0)]
assert len(r['rounds']) == len(r['requestFacts']) == 4
with connect(root / 'workspace.sqlite3') as c:
    assert c.execute('SELECT count(*) FROM desktop_ordinary_chat_attempts').fetchone()[0] == 4
    for item, request, (phase, state, error, verified) in zip(r['rounds'], r['requestFacts'], expected):
        assert item['phase'] == request['mode'] == phase
        assert request['currentUserMarker'] and request['stream'] == (phase == 'offline')
        assert request['forceSearch'] == request['searchTool'] == (phase != 'offline')
        a = item['attempt']
        row = c.execute('SELECT conversation_id,assistant_message_id,state,safe_error_code,input_tokens,output_tokens,cached_input_tokens,charge_micros,web_search_route,web_search_verified,retry_count FROM desktop_ordinary_chat_attempts WHERE attempt_id=?', (a['attemptId'],)).fetchone()
        assert row == (item['conversationId'], a['assistantMessageId'], state, error, 42, 9, 2, 12, 'NONE' if phase == 'offline' else 'DEEPSEEK_MESSAGES', verified, 0)
        ledger = [x for x in r['ledger']['records'] if x['conversationId'] == item['conversationId']]
        assert len(ledger) == 1
        entry = ledger[0]
        assert (entry['requestState'], entry['safeErrorCode'], entry['inputTokens'], entry['outputTokens'], entry['cachedInputTokens'], entry['chargeMicros']) == (state, error, 42, 9, 2, 12)
        if state == 'FAILED':
            assert item['record'] is None and '重新生成' in item['actualInfo']
        else:
            record = item['record']
            assert record['attemptId'] == a['attemptId']
            assert record['webSearchRequested'] == (phase == 'success') and record['webSearchVerified'] == bool(verified)
            assert ('已实际使用' if phase == 'success' else '本次未使用') in item['actualInfo']
    assert json.loads(c.execute('SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?', (r['workspaceId'],)).fetchone()[0]) == r['after']['exchange']
with connect(root / 'work-area/workspace.sqlite3') as c:
    assert json.loads(c.execute('SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?', (r['workId'],)).fetchone()[0]) == r['workBefore']['exchange']
    assert c.execute('SELECT count(*) FROM desktop_ordinary_chat_attempts').fetchone()[0] == 0
print(json.dumps({'pass': True, 'candidateSha256': r['candidateSha256'], 'sqliteCount': count, 'persistedAttemptsExact': 4, 'failedMeasurementsRetained': 2, 'coldExact': True, 'hiddenWorkExchangeExact': True, 'ordinaryProviderRequests': 4, 'titleProviderRequests': 2, 'realWebSearchExecuted': False}))
