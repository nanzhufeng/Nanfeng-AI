"""Read-only cold reminder routing audit; fixture is not OS/Provider evidence."""
import json
import sqlite3
import sys
from pathlib import Path

root = Path(sys.argv[1]).resolve(strict=True)
assert root.is_relative_to(Path('D:/CodexTemp').resolve())
assert json.loads((root / '.nanfeng-windows-acceptance.json').read_text('utf-8-sig'))['identifier'] == 'com.nanzhufeng.ai.desktop.windowsacceptance'
r = json.loads((root / 'chat-notification-cold-route-native.json').read_text('utf-8'))
assert r['coldActivatedExact'] and r['deletedTargetRefused'] and r['providerRequests'] == 0
assert r['fixture'].startswith('explicit SQLite SUCCEEDED/SENT synthetic run')
assert len(r['closes']) == 3 and all(x['accepted'] and x['responding'] and x['exitCode'] == 0 for x in r['closes'])
assert r['route'] == {'pane': 'reminders', 'page': 'reminders', 'planId': r['plan']['planId'], 'workspaceId': r['workspaceId'], 'dataArea': 'CHAT'}
assert r['deletedRoute']['pane'] == 'chat' and r['deletedRoute']['planId'] is None
assert r['deletedRoute']['workspaceId'] == r['workspaceId']

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
    with connect(path) as connection:
        assert connection.execute('PRAGMA integrity_check').fetchall() == [('ok',)]
        assert not connection.execute('PRAGMA foreign_key_check').fetchall()
    count += 1

with connect(root / 'workspace.sqlite3') as connection:
    assert json.loads(connection.execute('SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?', (r['workspaceId'],)).fetchone()[0]) == r['before']['exchange']
    assert connection.execute('SELECT status,title,instruction FROM desktop_reminder_plans_v1 WHERE plan_id=?', (r['plan']['planId'],)).fetchone() == ('DELETED', '', '')
    run = connection.execute('SELECT plan_id,status,result,input_tokens,output_tokens,charge_micros FROM desktop_reminder_runs_v1 WHERE run_id=?', (r['runId'],)).fetchone()
    assert run == (r['plan']['planId'], 'SUCCEEDED', None, None, None, None)
    assert connection.execute('SELECT count(*) FROM desktop_ordinary_chat_attempts').fetchone()[0] == 0
    assert connection.execute('SELECT count(*) FROM desktop_reminder_runs_v1').fetchone()[0] == 1
with connect(root / 'work-area/workspace.sqlite3') as connection:
    assert connection.execute('SELECT count(*) FROM desktop_reminder_plans_v1').fetchone()[0] == 0
    assert connection.execute('SELECT count(*) FROM desktop_reminder_runs_v1').fetchone()[0] == 0
print(json.dumps({'pass': True, 'candidateSha256': r['candidateSha256'], 'sqliteCount': count, 'coldCommandLineExactVisiblePlan': True, 'deletedNoBodyIdentityRetained': True, 'deletedTargetDidNotNavigate': True, 'ordinaryExchangePreserved': True, 'fixtureNotMeasuredExecution': True, 'providerRequests': 0}))
