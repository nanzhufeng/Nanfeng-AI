"""Verify passive pipe readiness against a unique synthetic owned pipe only."""
import ctypes as C
from ctypes import wintypes as W
import json,os,re,sys,uuid

assert sys.argv[1:]==['--self-test']
name=os.environ.get('NFAI_BOUNDED_JOB_NAME','')
assert re.fullmatch(r'NanfengAIBounded-[0-9a-f-]{36}',name)
k=C.WinDLL('kernel32',use_last_error=True)
class Overlapped(C.Structure):
    _fields_=[('internal',C.c_size_t),('internalHigh',C.c_size_t),('offset',W.DWORD),('offsetHigh',W.DWORD),('event',W.HANDLE)]
assert C.sizeof(Overlapped)==32
signatures={
 'WaitNamedPipeW':([W.LPCWSTR,W.DWORD],W.BOOL),
 'CreateNamedPipeW':([W.LPCWSTR,W.DWORD,W.DWORD,W.DWORD,W.DWORD,W.DWORD,W.DWORD,C.c_void_p],W.HANDLE),
 'CreateEventW':([C.c_void_p,W.BOOL,W.BOOL,W.LPCWSTR],W.HANDLE),
 'ConnectNamedPipe':([W.HANDLE,C.POINTER(Overlapped)],W.BOOL),
 'CancelIoEx':([W.HANDLE,C.POINTER(Overlapped)],W.BOOL),
 'GetOverlappedResult':([W.HANDLE,C.POINTER(Overlapped),C.POINTER(W.DWORD),W.BOOL],W.BOOL),
 'CloseHandle':([W.HANDLE],W.BOOL),
 'OpenJobObjectW':([W.DWORD,W.BOOL,W.LPCWSTR],W.HANDLE),
 'GetCurrentProcess':([],W.HANDLE),
 'IsProcessInJob':([W.HANDLE,W.HANDLE,C.POINTER(W.BOOL)],W.BOOL),
}
for fn,(args,result) in signatures.items():getattr(k,fn).argtypes=args;getattr(k,fn).restype=result
job=k.OpenJobObjectW(4,False,name);assert job
try:
    member=W.BOOL();assert k.IsProcessInJob(k.GetCurrentProcess(),job,C.byref(member)) and member.value
finally:k.CloseHandle(job)
pipe_name=r'\\.\pipe\NanfengAIProbe_'+uuid.uuid4().hex
ready=lambda:bool(k.WaitNamedPipeW(pipe_name,1))
result={'ownedJobVerified':True,'syntheticPipeOnly':True,'realVctipPipeAccessed':False,'clientConnectionOpened':False}
result['beforeCreate']=ready();assert not result['beforeCreate']
result['startingMessageWithoutPipe']=ready();assert not result['startingMessageWithoutPipe']
pipe=event=None;operation=None;pending=False
try:
    pipe=k.CreateNamedPipeW(pipe_name,3|0x40000000,0,1,4096,4096,10,None)
    assert pipe and pipe!=C.c_void_p(-1).value,C.get_last_error()
    event=k.CreateEventW(None,True,False,None);assert event
    operation=Overlapped();operation.event=event
    connected=k.ConnectNamedPipe(pipe,C.byref(operation));error=C.get_last_error()
    assert connected or error==997,error
    pending=not connected
    result['pendingConnect']=ready();assert result['pendingConnect']
finally:
    try:
        if pending:
            cancelled=k.CancelIoEx(pipe,C.byref(operation));assert cancelled or C.get_last_error()==1168
            transferred=W.DWORD()
            complete=k.GetOverlappedResult(pipe,C.byref(operation),C.byref(transferred),True)
            assert complete or C.get_last_error()==995
            result['pendingOperationDrained']=True
    finally:
        if pipe and pipe!=C.c_void_p(-1).value:k.CloseHandle(pipe)
        if event:k.CloseHandle(event)
result['afterClose']=ready();assert not result['afterClose']
result.update(passResult=True,handlesClosed=True)
print(json.dumps(result))
