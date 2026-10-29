"""Closed synthetic candidate root only: independent read-only persisted checks."""
import hashlib
import json
import sqlite3
import subprocess
import sys
import uuid
from pathlib import Path

EXPECTED_SHA = '6B4133EB211F3432F1C6EC0D02DA8B3A20B214069A198CED99E7BAF94F76F1A5'
SCRATCH = Path('D:/CodexTemp').resolve()
assert len(sys.argv) == 2, 'Provide the finished synthetic root'
root = Path(sys.argv[1]).resolve(strict=True)
assert root.parent.parent == SCRATCH and root.parent.name.startswith('nfa-bounded-chat-concurrent.'), 'Outside the controlled synthetic parent'
uuid.UUID(root.parent.name.split('.', 1)[1])
assert root.name.startswith('nanfeng-ai-windows-acceptance.')
uuid.UUID(root.name.split('.', 1)[1])
marker = json.loads((root / '.nanfeng-windows-acceptance.json').read_text(encoding='utf-8-sig'))
assert marker['identifier'] == 'com.nanzhufeng.ai.desktop.windowsacceptance'
result = json.loads((root / 'chat-concurrent-candidate-native.json').read_text(encoding='utf-8-sig'))
assert result['pass'] and result['syntheticProvider'] and not result['realService']
assert Path(result['root']).resolve() == root
assert result['candidateSha256'] == EXPECTED_SHA and result['requests'] == 10
assert result['coldExact'] and result['intermediateColdExact']
assert result['verifierSourceUnchangedDuringRun']
assert hashlib.sha256(Path(__file__).with_name('verify-windows-installed-chat-concurrent-native.mjs').read_bytes()).hexdigest().upper() == result['verifierSha256']
assert result['installedStabilityAccepted'] is False
assert len(result['jobMembership']) == 3 and all(v['verified'] for v in result['jobMembership'])
assert len(result['closes']) == len(result['pids']) == 3
assert all(c['accepted'] and c['responding'] and c['title'] and c['exitCode'] == 0 for c in result['closes'])
assert sorted(c['pid'] for c in result['closes']) == sorted(result['pids'])
binding = result['candidateBinding']
assert binding['sha256'] == EXPECTED_SHA
proof_path = Path(binding['buildProofPath']).resolve(strict=True)
assert proof_path == (Path(__file__).parents[2]/'docs/review/20261005-chat-sync-owner/installed-mainline-input.json').resolve()
assert hashlib.sha256(proof_path.read_bytes()).hexdigest().upper() == binding['buildProofSha256']
proof = json.loads(proof_path.read_text(encoding='utf-8-sig'))
assert proof['buildExitCode'] == 0 and proof['candidate']['sha256'] == '15B1319D084BF786576130CE52451D90540753396F2D6106AAA34C684EEC3D06'
assert proof['installed']['newExeSha256'] == EXPECTED_SHA
source_exe = (Path(proof['installed']['installDir'])/'nanfeng-ai-desktop-spike.exe').resolve(strict=True)
assert source_exe == Path('D:/CodexTemp/nfai-installed-acceptance.7d2cb30e-9606-4d88-bd91-bbc0fec53c92/nanfeng-ai-desktop-spike.exe').resolve()
assert Path(binding['sourceExecutable']).resolve() == source_exe
digest = hashlib.sha256()
with source_exe.open('rb') as stream:
    for chunk in iter(lambda: stream.read(1024 * 1024), b''):
        digest.update(chunk)
assert digest.hexdigest().upper() == EXPECTED_SHA
ids = ','.join(str(int(pid)) for pid in result['pids'])
assert all(int(pid) > 0 for pid in result['pids'])
quoted_root = "'" + str(root).replace("'", "''") + "'"
ps = f"@(Get-CimInstance Win32_Process | Where-Object {{ $_.ProcessId -in @({ids}) -or (($_.Name -in @('nanfeng-ai-desktop-spike.exe','msedgewebview2.exe')) -and $_.CommandLine -and $_.CommandLine.Contains({quoted_root})) }}).Count"
assert subprocess.check_output(['pwsh', '-NoProfile', '-Command', ps], encoding='utf-8').strip() == '0', 'Owned process/profile remains; no SQLite opened'

def readonly(path):
    assert path.resolve(strict=True).is_relative_to(root)
    wal = Path(str(path) + '-wal')
    assert not wal.exists() or wal.stat().st_size == 0, 'Uncheckpointed WAL: immutable read refused'
    c = sqlite3.connect(path.as_uri() + '?mode=ro&immutable=1', uri=True)
    c.row_factory = sqlite3.Row
    return c

def text(message):
    return ''.join(block['text'] for block in message['blocks'] if block['kind'] == 'TEXT')

fields = [('completedA', 0), ('completedB', 1), ('regeneratedA', 3), ('completedBNext', 4),
          ('retriedB', 6), ('completedDuringRetry', 7), ('changedModel', 8), ('generatedEdited', 9)]
expected = [result[name] for name, _ in fields] + [result['stopReceipt']['attempt']]
assert len({a['attemptId'] for a in expected}) == 9
with readonly(root / 'workspace.sqlite3') as c:
    exchange = json.loads(c.execute('SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?', (result['workspaceId'],)).fetchone()[0])
    assert exchange['conversations'] == result['after']['exchange']['conversations']
    conversations = {v['id']: v for v in exchange['conversations']}
    golden_path = Path(__file__).resolve().parents[2] / 'protocol/fixtures/nfai.exchange.v1.golden.json'
    golden = json.loads(golden_path.read_text(encoding='utf-8'))['conversations']
    owned_ids = {result['conversationA'], result['conversationB'], result['stopReceipt']['conversationId']}
    assert set(conversations) == owned_ids | {v['id'] for v in golden}
    assert all(conversations[v['id']] == v for v in golden), 'Golden baseline conversation changed'
    conversations = {key:value for key,value in conversations.items() if key in owned_ids}
    titles = [dict(row) for row in c.execute('SELECT * FROM desktop_conversation_title_attempts WHERE workspace_id=?', (result['workspaceId'],))]
    assert len(titles) == len(result['titleRequests'])
    assert all(row['state'] == 'COMPLETED' for row in titles)
    rows = [dict(row) for row in c.execute('SELECT * FROM desktop_ordinary_chat_attempts WHERE workspace_id=?', (result['workspaceId'],))]
    assert {row['attempt_id'] for row in rows} == {a['attemptId'] for a in expected}
    attempts = {row['attempt_id']: row for row in rows}
    for projection in expected:
        row = attempts[projection['attemptId']]
        for column, key in [('state','state'), ('conversation_id','conversationId'), ('user_message_id','userMessageId'), ('assistant_message_id','assistantMessageId'), ('provider_id','providerId'), ('requested_model_id','requestedModelId'), ('retry_count','retryCount')]:
            assert row[column] == projection[key], column
        nodes = {n['id']: n for n in conversations[row['conversation_id']]['messages']}
        answer = nodes[row['assistant_message_id']]
        assert answer['parentId'] == row['user_message_id'] and answer['attemptId'] == row['attempt_id']
    for name, ordinal in fields:
        projection = result[name]
        row = attempts[projection['attemptId']]
        request = result['requestChecks'][ordinal]
        assert row['state'] == 'COMPLETED'
        assert row['requested_model_id'] == request['model']
        assert hashlib.sha256(row['idempotency_key'].encode()).hexdigest() == request['idempotencyKeySha256']
        assert row['input_tokens'] == 5 and row['output_tokens'] == 7
    retry = attempts[result['retriedB']['attemptId']]
    assert retry['retry_count'] == result['retryOverlap']['failedB']['retryCount'] + 1
    assert result['requestChecks'][5]['idempotencyKeySha256'] == result['requestChecks'][6]['idempotencyKeySha256']
    stopped = attempts[result['stopReceipt']['attempt']['attemptId']]
    assert stopped['state'] == 'CANCELLED'
    assert text(next(n for n in conversations[stopped['conversation_id']]['messages'] if n['id'] == stopped['assistant_message_id'])) == 'SYNTHETIC_STOP_C_PARTIAL'
    draft = c.execute('SELECT text FROM desktop_conversation_drafts_v1 WHERE workspace_id=? AND conversation_key=?', (result['workspaceId'], result['conversationB'])).fetchone()
    assert draft[0] == result['finalDraft']['text'] == 'SYNTHETIC_B_AFTER_RETRY_DRAFT'
with readonly(root / 'usage-ledger/usage-ledger-v1.sqlite3') as c:
    ledger = [dict(row) for row in c.execute('SELECT * FROM usage_ledger_entries WHERE conversation_id IN (?,?,?)', tuple(sorted(conversations)))]
    for name, _ in fields:
        projection = result[name]
        measured = [row for row in ledger if row['attempt_id'] == projection['attemptId'] and row['input_tokens'] == 5 and row['output_tokens'] == 7]
        assert len(measured) == 1, 'Completed attempt lacks a unique measured ledger row'
        row = measured[0]
        assert row['conversation_id'] == projection['conversationId']
        assert row['charge_micros'] == projection['chargeMicros']
        assert row['currency_code'] == projection['currencyCode'] and row['cost_source'] == projection['costSource']
    assert not any({'body','authorization','api_key','provider_payload'} & row.keys() for row in ledger)
    serialized = json.dumps(ledger)
    assert 'SYNTHETIC_' not in serialized and 'nfai-synthetic-ui-' not in serialized
with readonly(root / 'work-area/workspace.sqlite3') as c:
    assert c.execute('SELECT count(*) FROM desktop_ordinary_chat_attempts').fetchone()[0] == 0
    for row in c.execute('SELECT exchange_json FROM workspace_exchange'):
        assert not any(v['id'] in conversations for v in json.loads(row[0])['conversations'])
checker = Path(__file__).resolve().parents[2] / 'desktop/scripts/check-stability-synthetic-sqlite.py'
integrity = json.loads(subprocess.check_output([sys.executable, str(checker), str(root), '--closed-browser'], encoding='utf-8'))
assert integrity['allOk']
print(json.dumps({'pass':True,'candidateSha256':EXPECTED_SHA,'root':str(root),'distinctAttempts':9,'completedAttempts':8,'cancelledAttempts':1,'retrySameAttemptAndKey':True,'conversationAndDraftPersisted':True,'workAreaIsolated':True,'ownedProcessesGone':True,'integrity':integrity}, ensure_ascii=False, indent=2))
