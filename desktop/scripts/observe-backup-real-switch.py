"""Observe actual persisted switching; never inject a product pause or state."""
import ctypes
from ctypes import wintypes
import hashlib
import json
import os
from pathlib import Path
import sys
import time

root, area, pid, executable, digest = sys.argv[1:]
root = Path(root).resolve()
pid = int(pid)
marker = json.loads((root / '.nanfeng-windows-acceptance.json').read_text(encoding='utf-8'))
assert marker['identifier'] == 'com.nanzhufeng.ai.desktop.windowsacceptance'
assert root.name.startswith('nanfeng-ai-windows-acceptance.')
assert area in ('CHAT', 'WORK')
assert hashlib.file_digest(open(executable, 'rb'), 'sha256').hexdigest().upper() == digest
kernel = ctypes.WinDLL('kernel32', use_last_error=True)
kernel.OpenProcess.argtypes = [wintypes.DWORD, wintypes.BOOL, wintypes.DWORD]
kernel.OpenProcess.restype = wintypes.HANDLE
kernel.QueryFullProcessImageNameW.argtypes = [wintypes.HANDLE, wintypes.DWORD, wintypes.LPWSTR, ctypes.POINTER(wintypes.DWORD)]
kernel.TerminateProcess.argtypes = [wintypes.HANDLE, wintypes.UINT]
kernel.CloseHandle.argtypes = [wintypes.HANDLE]
handle = kernel.OpenProcess(0x1000 | 0x0001 | 0x00100000, False, pid)
assert handle, ctypes.get_last_error()
buffer = ctypes.create_unicode_buffer(32768)
size = wintypes.DWORD(len(buffer))
assert kernel.QueryFullProcessImageNameW(handle, 0, buffer, ctypes.byref(size))
assert os.path.normcase(os.path.abspath(buffer.value)) == os.path.normcase(os.path.abspath(executable))
owner = (root if area == 'CHAT' else root / 'work-area') / 'local-backup/v1'
output = root / f'real-switch-observer-{area}.json'
record = dict(pid=pid, area=area, executable=buffer.value, sha256=digest, hookUsed=False,
              startedUtc=time.time(), hit=False, observations=[], polls=0)
try:
    from backup_real_switch_baseline import snapshot
    record['fullBaseline'] = snapshot(root, area)
    print('READY', flush=True)
    deadline = time.monotonic() + 30
    previous = None
    while time.monotonic() < deadline:
        record['polls'] += 1
        try:
            raw = (owner / 'state.json').read_bytes()
            state = json.loads(raw)
            phase = state.get('phase')
            if phase != previous:
                record['observations'].append(dict(utc=time.time(), monotonic=time.monotonic(), state=state))
                previous = phase
            checkpoint_id = state.get('checkpointId')
            if phase == 'SWITCHING' and state.get('operation') == 'INTERRUPTED' and state.get('processId') == pid:
                assert isinstance(checkpoint_id, str) and checkpoint_id.startswith('checkpoint-v4-')
                assert all(c.isalnum() or c == '-' for c in checkpoint_id)
                old_db = owner / f'switch-old-{checkpoint_id}' / 'workspace.sqlite3'
                if old_db.is_file():
                    record['beforeTerminate'] = dict(utc=time.time(), monotonic=time.monotonic(), state=state,
                        oldDatabasePresent=True, oldDatabaseBytes=old_db.stat().st_size,
                        manifestPresent=(owner/'recovery'/checkpoint_id/'checkpoint-manifest-v4.json').is_file())
                    # This handle still identifies exactly the preverified process.
                    assert kernel.TerminateProcess(handle, 137), ctypes.get_last_error()
                    record['terminateUtc'] = time.time()
                    record['hit'] = True
                    break
        except (OSError, json.JSONDecodeError):
            pass
        time.sleep(0.0005)
    if not record['hit']:
        record['reason'] = 'No actual persisted SWITCHING plus moved old database observed within 30s.'
    time.sleep(0.1)
    try:
        record['afterTerminateState'] = json.loads((owner/'state.json').read_bytes())
    except (OSError, json.JSONDecodeError) as error:
        record['afterStateError'] = str(error)
finally:
    kernel.CloseHandle(handle)
    with output.open('x', encoding='utf-8') as stream:
        json.dump(record, stream, ensure_ascii=False, indent=2)
print(json.dumps(dict(hit=record['hit'], output=str(output))), flush=True)
