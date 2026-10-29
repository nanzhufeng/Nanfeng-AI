"""Terminate only a verified candidate when new uncommitted WAL frames appear.

No SQL writes, clock changes, production access, or fault hooks. A held process
handle binds identity across the observation and termination.
"""
import ctypes
import hashlib
import json
import struct
import sys
import time
from pathlib import Path
from ctypes import wintypes

launch = json.loads(Path(sys.argv[1]).read_text(encoding='utf-8-sig'))
root = Path(launch['root']).resolve(strict=True)
assert json.loads((root / '.nanfeng-windows-acceptance.json').read_text(encoding='utf-8-sig'))['identifier'] == 'com.nanzhufeng.ai.desktop.windowsacceptance'
assert launch['diagnostic'] is True and launch['startupMode'] == 'diagnostic'
assert launch['identifier'] == 'com.nanzhufeng.ai.desktop.windowsacceptance'
kernel = ctypes.WinDLL('kernel32', use_last_error=True)
kernel.OpenProcess.argtypes = [wintypes.DWORD, wintypes.BOOL, wintypes.DWORD]
kernel.OpenProcess.restype = wintypes.HANDLE
kernel.QueryFullProcessImageNameW.argtypes = [wintypes.HANDLE, wintypes.DWORD, wintypes.LPWSTR, ctypes.POINTER(wintypes.DWORD)]
kernel.TerminateProcess.argtypes = [wintypes.HANDLE, wintypes.UINT]
kernel.CloseHandle.argtypes = [wintypes.HANDLE]
handle = kernel.OpenProcess(0x1000 | 0x0001, False, launch['pid'])
assert handle, ctypes.get_last_error()
try:
    buffer = ctypes.create_unicode_buffer(32768)
    size = wintypes.DWORD(len(buffer))
    assert kernel.QueryFullProcessImageNameW(handle, 0, buffer, ctypes.byref(size))
    executable = Path(buffer.value).resolve(strict=True)
    assert executable == Path(launch['executable']).resolve(strict=True)
    assert hashlib.sha256(executable.read_bytes()).hexdigest().upper() == launch['sha256']
    wal = root / 'workspace.sqlite3-wal'
    initial = wal.read_bytes() if wal.exists() else b''
    status = {'root': str(root), 'pid': launch['pid'], 'baselineBytes': len(initial), 'state': 'READY', 'terminationAccepted': False}
    output = root / 'wal-interrupt-observation.json'
    output.write_text(json.dumps(status), encoding='utf-8')
    print(json.dumps({'state': 'READY'}), flush=True)
    deadline = time.monotonic() + 45
    while time.monotonic() < deadline:
        try:
            data = wal.read_bytes()
        except (FileNotFoundError, PermissionError):
            time.sleep(.001)
            continue
        if len(data) <= len(initial) or len(data) < 32 or (initial and data[:32] != initial[:32]):
            time.sleep(.001)
            continue
        magic, version, page_size = struct.unpack('>III', data[:12])
        assert magic in (0x377f0682, 0x377f0683) and version == 3007000
        if page_size == 1:
            page_size = 65536
        frame_size = page_size + 24
        first = max(0, (len(initial) - 32) // frame_size)
        complete = (len(data) - 32) // frame_size
        frames = []
        for index in range(first, complete):
            offset = 32 + index * frame_size
            page, commit_size = struct.unpack('>II', data[offset:offset + 8])
            if data[offset + 8:offset + 16] != data[16:24]:
                break
            frames.append({'index': index, 'page': page, 'commitDatabasePages': commit_size})
        if frames and all(frame['page'] > 0 and frame['commitDatabasePages'] == 0 for frame in frames):
            accepted = bool(kernel.TerminateProcess(handle, 73))
            (root / 'wal-uncommitted-snapshot.bin').write_bytes(data)
            status.update(state='INTERRUPTED_UNCOMMITTED_WAL' if accepted else 'TERMINATION_FAILED', terminationAccepted=accepted, observedBytes=len(data), pageSize=page_size, newFrames=frames, observedUtc=time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime()), observedWalSha256=hashlib.sha256(data).hexdigest())
            output.write_text(json.dumps(status), encoding='utf-8')
            print(json.dumps({'state': status['state'], 'newFrameCount': len(frames)}), flush=True)
            if not accepted:
                raise RuntimeError(ctypes.get_last_error())
            break
        time.sleep(.001)
    else:
        status.update(state='NOT_OBSERVED', terminationAccepted=False)
        output.write_text(json.dumps(status), encoding='utf-8')
        raise RuntimeError('No isolated uncommitted WAL growth observed; no pass claimed')
finally:
    kernel.CloseHandle(handle)
