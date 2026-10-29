"""Read-only credential/configuration checks for the marked model settings run."""
import json
import sqlite3
import subprocess
import sys
from pathlib import Path


def read_json(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


base = Path(sys.argv[1]).resolve(strict=True)
receipt = json.loads((base / 'fixed-flow-native.txt').read_text(encoding='utf-8-sig').splitlines()[-1])
root = Path(receipt['root']).resolve(strict=True)
assert read_json(root / '.nanfeng-windows-acceptance.json')['identifier'] == 'com.nanzhufeng.ai.desktop.windowsacceptance'
evidence = read_json(root / 'model-service-owner-evidence.json')
assert receipt['pass'] is evidence['pass'] is True
assert evidence['candidateSha256'] == receipt['candidateSha256']
assert evidence['startupMode'] == 'normal' and evidence['realService'] is False
assert not evidence.get('forcedCleanup', False)
for field in ['firstNormalClose', 'secondNormalClose']:
    close = evidence[field]
    assert close['accepted'] and close['responding'] and close['title']
before, after = evidence['overlap']['before'], evidence['overlap']['after']
assert before['chatBusy'] and before['workBusy']
assert not after['chatBusy'] and after['workBusy']
for field in ['workNotice', 'workError', 'workProvider']:
    assert after[field] == before[field]
assert after['workProvider'] == 'QWEN'
assert evidence['workConnectionFeedback']['failed'] is False
requests = evidence['requests']
assert len(requests) == 2
assert [r['model'] for r in requests] == ['deepseek-flash', 'qwen3.7-plus']
assert all(r['path'] == '/chat/completions' and r['smallProbeOnly'] and r['syntheticBearerOnly'] for r in requests)
integrity = json.loads(subprocess.check_output([sys.executable,
    str(Path(__file__).with_name('check-stability-synthetic-sqlite.py')), str(root), '--closed-browser'], encoding='utf-8'))
assert integrity['allOk']
with sqlite3.connect((root / 'app-configuration.sqlite3').as_uri() + '?mode=ro', uri=True) as connection:
    connection.row_factory = sqlite3.Row
    rows = {r['provider_id']: dict(r) for r in connection.execute('SELECT * FROM desktop_provider_settings')}
    assert rows['DEEPSEEK']['enabled'] == rows['QWEN']['enabled'] == 1
    assert rows['DEEPSEEK']['preset_id'] == 'DEEPSEEK_V4_FLASH' and rows['DEEPSEEK']['revision'] == 1
    assert rows['QWEN']['preset_id'] == 'QWEN_3_7_PLUS' and rows['QWEN']['revision'] == 2
for settings in evidence['settingsAfterDelete']:
    assert settings['credentialStored'] is (settings['providerId'] == 'DEEPSEEK')
credential_directory = root / 'provider-credentials-v1'
credentials = read_json(credential_directory / 'credentials.json')
assert list(credentials['providers']) == ['DEEPSEEK']
assert set(credentials['providers']['DEEPSEEK']) == {'nonce', 'ciphertext'}
assert all(credentials['providers']['DEEPSEEK'].values())
assert not (credential_directory / 'model-setting-pending-v1.json').exists()
needles = [b'nfai-synthetic-model-' + suffix for suffix in [b'DEEPSEEK', b'QWEN', b'QWEN-UPDATED']]
scanned = 0
for path in root.rglob('*'):
    if path.is_file() and 'webview2' not in path.relative_to(root).parts:
        assert path.stat().st_size < 50 * 1024 * 1024, 'Bounded marked-root scan only'
        assert not any(needle in path.read_bytes() for needle in needles), f'Plaintext fake credential in {path.name}'
        scanned += 1
for workspace in [root / 'workspace.sqlite3', root / 'work-area/workspace.sqlite3']:
    assert workspace.exists()
    with sqlite3.connect(workspace.as_uri() + '?mode=ro', uri=True) as connection:
        for table in ['desktop_ordinary_chat_attempts', 'domain_intents']:
            assert connection.execute(f'SELECT COUNT(*) FROM {table}').fetchone()[0] == 0
pids = ','.join(str(int(pid)) for pid in evidence['pids'])
remaining = subprocess.check_output(['pwsh', '-NoProfile', '-Command',
    f'@(Get-Process -Id {pids} -ErrorAction SilentlyContinue).Count'], encoding='utf-8').strip()
assert remaining == '0'
print(json.dumps({'allOk': True, 'root': str(root), 'candidateSha256': receipt['candidateSha256'],
    'integrity': integrity, 'normalCloseCount': 2, 'requestCount': 2, 'credentialProviders': ['DEEPSEEK'],
    'qwenRevision': 2, 'noPendingRecord': True, 'plaintextScannedApplicationFiles': scanned,
    'noBusinessAttemptsOrDomainIntents': True, 'remainingOwnedProcesses': 0}, ensure_ascii=False, indent=2))
