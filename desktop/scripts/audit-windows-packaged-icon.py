"""Read PE resources without executing the binary; compare every approved ICO payload."""
import ctypes as C
from ctypes import wintypes as W
import hashlib
import json
from pathlib import Path
import struct
import sys

k=C.WinDLL('kernel32',use_last_error=True)
k.LoadLibraryExW.argtypes=[W.LPCWSTR,W.HANDLE,W.DWORD];k.LoadLibraryExW.restype=W.HMODULE
k.FindResourceW.argtypes=[W.HMODULE,C.c_void_p,C.c_void_p];k.FindResourceW.restype=W.HANDLE
k.SizeofResource.argtypes=[W.HMODULE,W.HANDLE];k.SizeofResource.restype=W.DWORD
k.LoadResource.argtypes=[W.HMODULE,W.HANDLE];k.LoadResource.restype=W.HANDLE
k.LockResource.argtypes=[W.HANDLE];k.LockResource.restype=C.c_void_p
k.FreeLibrary.argtypes=[W.HMODULE];k.FreeLibrary.restype=W.BOOL
CALLBACK=C.WINFUNCTYPE(W.BOOL,W.HMODULE,C.c_void_p,C.c_void_p,C.c_ssize_t)
k.EnumResourceNamesW.argtypes=[W.HMODULE,C.c_void_p,CALLBACK,C.c_ssize_t];k.EnumResourceNamesW.restype=W.BOOL

def resource(module,name,kind):
    found=k.FindResourceW(module,name,kind)
    if not found:raise C.WinError(C.get_last_error())
    size=k.SizeofResource(module,found)
    if size>4*1024*1024:raise ValueError('Icon resource exceeds bound')
    return C.string_at(k.LockResource(k.LoadResource(module,found)),size)

def audit(path,expected):
    module=k.LoadLibraryExW(str(path.resolve()),None,0x22)
    if not module:raise C.WinError(C.get_last_error())
    groups=[]
    @CALLBACK
    def visit(module,kind,name,_):
        try:
            data=resource(module,name,14);count=struct.unpack_from('<H',data,4)[0];frames={}
            if count>64:raise ValueError('Too many icon frames')
            for i in range(count):
                width,height,_,_,_,_,size,ref=struct.unpack_from('<BBBBHHIH',data,6+14*i)
                payload=resource(module,ref,3)
                if len(payload)!=size:raise ValueError('Icon resource size mismatch')
                frames[(width or 256,height or 256)]=hashlib.sha256(payload).hexdigest()
            groups.append(frames);return True
        except Exception as error:
            groups.append({'error':str(error)});return False
    try:
        if not k.EnumResourceNamesW(module,14,visit,0):raise ValueError('PE group icon enumeration failed')
    finally:k.FreeLibrary(module)
    return {'path':str(path),'matchingApprovedGroup':any(g==expected for g in groups),'groups':[{'frames':len(g),'dimensions':[str(x) for x in g]} for g in groups]}

def main():
    root=Path(__file__).resolve().parents[1];raw=(root/'src-tauri/icons/icon.ico').read_bytes()
    reserved,kind,count=struct.unpack_from('<HHH',raw);assert reserved==0 and kind==1 and count==9
    expected={}
    for i in range(count):
        width,height,_,_,_,_,size,offset=struct.unpack_from('<BBBBHHII',raw,6+16*i)
        expected[(width or 256,height or 256)]=hashlib.sha256(raw[offset:offset+size]).hexdigest()
    results=[audit(Path(path),expected) for path in sys.argv[1:]]
    print(json.dumps(results,ensure_ascii=False,indent=2))
    if not results or not all(r['matchingApprovedGroup'] for r in results):raise SystemExit(1)

if __name__=='__main__':main()
