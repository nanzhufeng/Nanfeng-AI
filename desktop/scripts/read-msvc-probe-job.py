"""Read only image identities in this diagnostic child's already-owned Job."""
import ctypes as C
from ctypes import wintypes as W
import importlib.util
import json
import os
from pathlib import Path
import re

name = os.environ.get('NFAI_BOUNDED_JOB_NAME', '')
assert re.fullmatch(r'NanfengAIBounded-[0-9a-f-]{36}', name)
spec = importlib.util.spec_from_file_location('bounded', Path(__file__).with_name('run-windows-node-bounded.py'))
bounded = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bounded)
k = C.WinDLL('kernel32', use_last_error=True)
signatures = {
    'OpenJobObjectW': ([W.DWORD, W.BOOL, W.LPCWSTR], W.HANDLE),
    'GetCurrentProcess': ([], W.HANDLE),
    'QueryInformationJobObject': ([W.HANDLE, C.c_int, C.c_void_p, W.DWORD, C.c_void_p], W.BOOL),
    'OpenProcess': ([W.DWORD, W.BOOL, W.DWORD], W.HANDLE),
    'IsProcessInJob': ([W.HANDLE, W.HANDLE, C.POINTER(W.BOOL)], W.BOOL),
    'QueryFullProcessImageNameW': ([W.HANDLE, W.DWORD, W.LPWSTR, C.POINTER(W.DWORD)], W.BOOL),
    'CloseHandle': ([W.HANDLE], W.BOOL),
}
for function, (args, result) in signatures.items():
    getattr(k, function).argtypes = args
    getattr(k, function).restype = result
job = k.OpenJobObjectW(4, False, name)  # QUERY only, never modify/terminate.
assert job, C.get_last_error()
try:
    member = W.BOOL()
    assert k.IsProcessInJob(k.GetCurrentProcess(), job, C.byref(member)) and member.value
    print(json.dumps(bounded.owned_job_process_images(k, job)))
finally:
    k.CloseHandle(job)
