"""Read-only marked search-task deletion, area isolation and reference check."""
import hashlib
import json
import sqlite3
import subprocess
import sys
from pathlib import Path


def load(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


base = Path(sys.argv[1]).resolve(strict=True)
receipt = json.loads((base / 'complete-flow-native.txt').read_text(encoding='utf-8-sig').splitlines()[-1])
root = Path(receipt['root']).resolve(strict=True)
assert load(root / '.nanfeng-windows-acceptance.json')['identifier'] == 'com.nanzhufeng.ai.desktop.windowsacceptance'
evidence = load(root / 'search-task-lifecycle-evidence.json')
assert receipt['pass'] is evidence['pass'] is True
assert receipt['candidateSha256'] == evidence['candidateSha256']
assert evidence['startupMode'] == 'normal' and evidence['realService'] is False
assert not evidence.get('forcedCleanup') and evidence['cancelledBeforeVisibleDelete']
for key in ['firstNormalClose', 'secondNormalClose']:
    close = evidence[key]
    assert close['accepted'] and close['responding'] and close['title']
assert len(evidence['beforeHits']) == 2
kept = next(t for t in evidence['tasks']['CHAT'] if t['name'] == 'kept')
deleted = next(t for t in evidence['tasks']['CHAT'] if t['name'] == 'deleted')
assert {h['entryId'] for h in evidence['beforeHits']} == {kept['id'] + ':source', deleted['id'] + ':source'}
stale = evidence['staleUi']
assert stale['searchPanel'] and stale['pane'] != 'transcription'
assert '转写任务已删除' in stale['error'] and stale['hits'] == [kept['id'] + ':source']
baseline = json.loads((base / 'baseline-settled-native.txt').read_text(encoding='utf-8-sig').splitlines()[0])
assert baseline['staleUi']['pane'] == 'transcription' and not baseline['staleUi']['searchPanel']
assert baseline['staleUi']['selectedTaskId'] == baseline['keptTaskId'] != baseline['deletedTaskId']
integrity = json.loads(subprocess.check_output([sys.executable,
    str(Path(__file__).with_name('check-stability-synthetic-sqlite.py')), str(root), '--closed-browser'], encoding='utf-8'))
assert integrity['allOk']
source = root / 'synthetic-search-task-inputs/searchtasksynthetic-deleted.png'
digest = hashlib.sha256(source.read_bytes()).hexdigest()
for area in ['CHAT', 'WORK']:
    area_root = root if area == 'CHAT' else root / 'work-area'
    with sqlite3.connect((area_root / 'workspace.sqlite3').as_uri() + '?mode=ro', uri=True) as c:
        c.row_factory = sqlite3.Row
        tasks = [dict(row) for row in c.execute('SELECT * FROM desktop_transcription_tasks')]
        index = [dict(row) for row in c.execute('SELECT * FROM desktop_local_search_index')]
        assert len(tasks) == len(index) == (0 if area == 'CHAT' else 1)
        asset = c.execute('SELECT * FROM desktop_attachment_assets WHERE sha256=?', (digest,)).fetchone()
        assert asset['reference_count'] == (0 if area == 'CHAT' else 1)
        if area == 'CHAT':
            assert asset['last_unreferenced_at_ms'] is not None
            assert evidence['beforeCold']['CHAT']['tasks'] == []
        else:
            task = tasks[0]
            expected = evidence['beforeCold']['WORK']['tasks'][0]
            assert task['id'] == expected['id'] == evidence['tasks']['WORK'][0]['id']
            assert task['state'] == expected['state'] == 'QUEUED'
            assert task['source_sha256'] == digest and task['attempt_count'] == task['provider_request_count'] == 0
            assert task['created_at_ms'] == expected['createdAtMillis'] and task['updated_at_ms'] == expected['updatedAtMillis']
            assert index[0]['conversation_id'] == 'transcription-task:' + task['id']
            assert index[0]['attachment_id'] == task['source_attachment_id']
            assert hashlib.sha256((area_root / 'assets' / digest).read_bytes()).hexdigest() == digest
        for table in ['desktop_ordinary_chat_attempts', 'desktop_ocr_attempts_v1', 'domain_intents']:
            assert c.execute(f'SELECT COUNT(*) FROM {table}').fetchone()[0] == 0
with sqlite3.connect((root / 'usage-ledger/usage-ledger-v1.sqlite3').as_uri() + '?mode=ro', uri=True) as c:
    assert c.execute('SELECT COUNT(*) FROM usage_ledger_entries').fetchone()[0] == 0
pids = ','.join(str(int(pid)) for pid in evidence['pids'])
remaining = subprocess.check_output(['pwsh', '-NoProfile', '-Command',
    f'@(Get-Process -Id {pids} -ErrorAction SilentlyContinue).Count'], encoding='utf-8').strip()
assert remaining == '0'
print(json.dumps({'allOk': True, 'candidateSha256': receipt['candidateSha256'], 'root': str(root),
    'integrity': integrity, 'oldCandidateWrongTaskReproduced': True, 'chatTasksAndIndex': 0,
    'workTasksAndIndex': 1, 'chatSharedAssetReferences': 0, 'workAssetReferences': 1,
    'businessAttemptsAndLedger': 0, 'normalCloseCount': 2, 'remainingOwnedProcesses': 0}, ensure_ascii=False, indent=2))
