"""One-shot accounting fault, restricted to marked synthetic acceptance roots."""
import json
import sqlite3
import sys
from pathlib import Path

root = Path(sys.argv[1]).resolve()
marker = json.loads((root / '.nanfeng-windows-acceptance.json').read_text(encoding='utf-8-sig'))
assert marker['identifier'] == 'com.nanzhufeng.ai.desktop.windowsacceptance'
assert root.name.startswith('nanfeng-ai-windows-acceptance.')
area, action = sys.argv[2:4]
assert area in ('CHAT', 'WORK') and action in ('deny', 'allow')
database = (root / 'work-area' if area == 'WORK' else root) / 'usage-ledger/usage-ledger-v1.sqlite3'
connection = sqlite3.connect(database.as_uri() + '?mode=rw', uri=True)
with connection:
    if action == 'deny':
        connection.execute("CREATE TRIGGER acceptance_reminder_usage_denied BEFORE INSERT ON usage_ledger_entries WHEN NEW.replay_token LIKE 'reminder-final-%' BEGIN SELECT RAISE(ABORT,'synthetic reminder accounting denied'); END")
    else:
        connection.execute('DROP TRIGGER acceptance_reminder_usage_denied')
connection.close()
print(json.dumps({'area': area, 'action': action, 'syntheticOnly': True}))
