"""Passive availability query, only with a verified live service in this Job."""
import ctypes as C
from ctypes import wintypes as W
import json,os,re,sys
assert len(sys.argv)==2 and sys.argv[1].isdigit()
pid=int(sys.argv[1]);assert pid>0
name=os.environ.get('NFAI_BOUNDED_JOB_NAME','');assert re.fullmatch(r'NanfengAIBounded-[0-9a-f-]{36}',name)
k=C.WinDLL('kernel32',use_last_error=True)
for fn,args,result in [
 ('OpenJobObjectW',[W.DWORD,W.BOOL,W.LPCWSTR],W.HANDLE),
 ('GetCurrentProcess',[],W.HANDLE),
 ('IsProcessInJob',[W.HANDLE,W.HANDLE,C.POINTER(W.BOOL)],W.BOOL),
 ('OpenProcess',[W.DWORD,W.BOOL,W.DWORD],W.HANDLE),
 ('QueryFullProcessImageNameW',[W.HANDLE,W.DWORD,W.LPWSTR,C.POINTER(W.DWORD)],W.BOOL),
 ('GetExitCodeProcess',[W.HANDLE,C.POINTER(W.DWORD)],W.BOOL),
 ('GetProcessTimes',[W.HANDLE,C.POINTER(W.FILETIME),C.POINTER(W.FILETIME),C.POINTER(W.FILETIME),C.POINTER(W.FILETIME)],W.BOOL),
 ('WaitNamedPipeW',[W.LPCWSTR,W.DWORD],W.BOOL),
 ('CloseHandle',[W.HANDLE],W.BOOL),
]:getattr(k,fn).argtypes=args;getattr(k,fn).restype=result
job=k.OpenJobObjectW(4,False,name);assert job
handle=None
out={'pid':pid,'ownedServiceAlive':False,'pipeAvailable':False,'clientConnectionOpened':False}
try:
    member=W.BOOL();assert k.IsProcessInJob(k.GetCurrentProcess(),job,C.byref(member)) and member.value
    handle=k.OpenProcess(0x1000,False,pid)
    if handle:
        assert k.IsProcessInJob(handle,job,C.byref(member)) and member.value,'Service not owned by current Job'
        path=C.create_unicode_buffer(32768);length=W.DWORD(len(path));assert k.QueryFullProcessImageNameW(handle,0,path,C.byref(length))
        expected=r'C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Tools\MSVC\14.44.35207\bin\Hostx64\x64\vctip.exe'
        assert path.value.casefold()==expected.casefold(),'Unexpected owned executable'
        status=W.DWORD();assert k.GetExitCodeProcess(handle,C.byref(status))
        a,b,c,d=(W.FILETIME() for _ in range(4));assert k.GetProcessTimes(handle,C.byref(a),C.byref(b),C.byref(c),C.byref(d))
        out['creationFileTime']=str((int(a.dwHighDateTime)<<32)|int(a.dwLowDateTime))
        out['ownedServiceAlive']=status.value==259
        if out['ownedServiceAlive']:
            out['pipeAvailable']=bool(k.WaitNamedPipeW(r'\\.\pipe\VCTIP_14.44.35228.0_PIPE',1))
finally:
    if handle:k.CloseHandle(handle)
    k.CloseHandle(job)
print(json.dumps(out))
