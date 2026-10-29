"""Own the temporary MSVC service and wait for natural exit before returning."""
from pathlib import Path
import ctypes as C
from ctypes import wintypes as W
import hashlib,importlib.util,json,os,re,struct,subprocess,sys,time,uuid
SCRIPT=Path(__file__).resolve();DESKTOP=SCRIPT.parents[1];REPO=DESKTOP.parent
TOOLS=Path(r'C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Tools\MSVC\14.44.35207\bin\Hostx64\x64')
PY=Path(sys.executable);LINK=TOOLS/'link.exe';COMPILER=TOOLS/'cl.exe';SERVICE=TOOLS/'vctip.exe'
sha=lambda p:hashlib.sha256(Path(p).read_bytes()).hexdigest()
spec=importlib.util.spec_from_file_location('nfai_owned_job',SCRIPT.with_name('run-windows-node-bounded.py'));bounded=importlib.util.module_from_spec(spec);spec.loader.exec_module(bounded)
k=C.WinDLL('kernel32',use_last_error=True)
for fn,args,result in [
 ('OpenJobObjectW',[W.DWORD,W.BOOL,W.LPCWSTR],W.HANDLE),('GetCurrentProcess',[],W.HANDLE),
 ('IsProcessInJob',[W.HANDLE,W.HANDLE,C.POINTER(W.BOOL)],W.BOOL),('OpenProcess',[W.DWORD,W.BOOL,W.DWORD],W.HANDLE),
 ('QueryFullProcessImageNameW',[W.HANDLE,W.DWORD,W.LPWSTR,C.POINTER(W.DWORD)],W.BOOL),
 ('CloseHandle',[W.HANDLE],W.BOOL),('QueryInformationJobObject',[W.HANDLE,C.c_int,C.c_void_p,W.DWORD,C.c_void_p],W.BOOL),
 ('CreateToolhelp32Snapshot',[W.DWORD,W.DWORD],W.HANDLE),('Process32FirstW',[W.HANDLE,C.POINTER(bounded.ProcessEntry)],W.BOOL),('Process32NextW',[W.HANDLE,C.POINTER(bounded.ProcessEntry)],W.BOOL),
]:getattr(k,fn).argtypes=args;getattr(k,fn).restype=result
def verify_job():
    name=os.environ.get('NFAI_BOUNDED_JOB_NAME','');assert re.fullmatch(r'NanfengAIBounded-[0-9a-f-]{36}',name)
    job=k.OpenJobObjectW(4,False,name);assert job
    member=W.BOOL();assert k.IsProcessInJob(k.GetCurrentProcess(),job,C.byref(member)) and member.value
    return job
def foreign_tools(allowed_service=None):
    snapshot=k.CreateToolhelp32Snapshot(2,0);assert snapshot and snapshot!=C.c_void_p(-1).value
    found=[]
    try:
        row=bounded.ProcessEntry();row.size=C.sizeof(row);more=k.Process32FirstW(snapshot,C.byref(row))
        while more:
            if row.exe.lower() in ('cl.exe','link.exe','vctip.exe') and row.pid not in (os.getppid(),allowed_service):found.append(int(row.pid))
            more=k.Process32NextW(snapshot,C.byref(row))
    finally:k.CloseHandle(snapshot)
    return found
def read_arguments():
    raw=sys.stdin.buffer.read(2*1024*1024+1);assert len(raw)<=2*1024*1024 and len(raw)>=4
    count=struct.unpack_from('<I',raw)[0];assert count<=10000
    offset=4;args=[]
    for _ in range(count):
        assert offset+4<=len(raw);units=struct.unpack_from('<I',raw,offset)[0];offset+=4
        assert units<=65536 and offset+units*2<=len(raw)
        arg=raw[offset:offset+units*2].decode('utf-16le',errors='surrogatepass');assert '\0' not in arg
        args.append(arg);offset+=units*2
    assert offset==len(raw)
    return args
def owned_action(command,kind):
    job=verify_job();service=None;record={'kind':kind,'registryCommandsExecuted':False,'shellUsed':False,'generatedExeExecuted':False,'pass':False}
    idle_seconds=15 if kind=='msvc-compile' else 5
    drain_seconds=max(20,2*idle_seconds+5)
    root=REPO/'.runtime-qa'/('msvc-link-owner-'+uuid.uuid4().hex);root.mkdir()
    (root/'.nanfeng-tool-probe.json').write_text(json.dumps({'kind':'owned-msvc-linker','synthetic':True}))
    for name in ['local','roaming','temp','program']:(root/name).mkdir()
    receipt_root=Path(os.environ['NFAI_MSVC_LINK_RECEIPTS']).resolve();assert receipt_root.is_relative_to(REPO/'docs/review');receipt_root.mkdir(parents=True,exist_ok=True)
    try:
        record['phase']='tool-identity'
        assert sha(LINK)==os.environ['NFAI_MSVC_REAL_LINK_SHA256'] and sha(SERVICE)==os.environ['NFAI_MSVC_VCTIP_SHA256']
        assert sha(COMPILER)==os.environ['NFAI_MSVC_CL_SHA256']
        record['phase']='foreign-guard'
        assert not foreign_tools(),'Other compiler/telemetry service prevents owned link'
        action_environment=None
        if kind=='msvc-compile':
            record['phase']='compiler-environment'
            helper=SCRIPT.with_name('read-windows-msvc-environment.py')
            assert sha(helper)==os.environ['NFAI_MSVC_COMPILER_ENV_SHA256']
            module_spec=importlib.util.spec_from_file_location('nfai_compiler_environment',helper)
            module=importlib.util.module_from_spec(module_spec);module_spec.loader.exec_module(module)
            compiler_values=module.compiler_environment()
            action_environment={**os.environ,**compiler_values}
            record['compilerEnvironmentKeys']=sorted(compiler_values)
            record['compilerSearchDirectoriesVerified']=True
        record['phase']='service-start'
        env={'SystemRoot':os.environ['SystemRoot'],'WINDIR':os.environ['WINDIR'],'PATH':str(TOOLS)+';'+str(Path(os.environ['SystemRoot'])/'System32'),'LOCALAPPDATA':str(root/'local'),'APPDATA':str(root/'roaming'),'TEMP':str(root/'temp'),'TMP':str(root/'temp'),'PROGRAMDATA':str(root/'program'),'USERPROFILE':str(root)}
        service_arguments=['-upload:skip',f'-timeout:{idle_seconds}']
        service=subprocess.Popen([str(SERVICE),*service_arguments],env=env,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,creationflags=0x08000000)
        record.update(servicePid=service.pid,serviceArguments=service_arguments,naturalDrainLimitSeconds=drain_seconds)
        began=time.monotonic();available=None
        while time.monotonic()-began<5:
            available=json.loads(subprocess.check_output([str(PY),'-X','utf8',str(SCRIPT.with_name('read-owned-vctip-readiness.py')),str(service.pid)],text=True,creationflags=0x08000000,timeout=1))
            if available['pipeAvailable']:break
            if service.poll() is not None:break
            time.sleep(.025)
        assert available and available['ownedServiceAlive'] and available['pipeAvailable'],'Owned service pipe not ready'
        assert not foreign_tools(service.pid),'Concurrent foreign compiler/service prevents link'
        record['pipeReadyAfterMs']=round((time.monotonic()-began)*1000);record['serviceCreationFileTime']=str(available['creationFileTime'])
        start=time.monotonic()
        done=subprocess.run(command,env=action_environment,stdin=subprocess.DEVNULL,creationflags=0x08000000,timeout=120)
        record.update(actionExitCode=done.returncode,actionMs=round((time.monotonic()-start)*1000),serviceAliveAtActionEnd=service.poll() is None)
        assert done.returncode==0 and record['serviceAliveAtActionEnd'],'Link action failed or owned service expired during action'
        output,error=service.communicate(timeout=drain_seconds);assert len(output)+len(error)<=65536
        record.update(serviceExitCode=service.returncode,serviceNaturalExit=True)
        assert service.returncode==0
        remaining=[r for r in bounded.owned_job_process_images(k,job) if r.get('imagePath','').lower().endswith('vctip.exe')]
        assert not remaining,'Unexpected owned telemetry service remains after link'
        record['pass']=True
        return 0
    except Exception as error:
        record['errorType']=type(error).__name__;record['error']=str(error)
        if service and service.poll() is None:
            try:service.communicate(timeout=drain_seconds)
            except subprocess.TimeoutExpired:record['serviceStillRunning']=True
        return 1
    finally:
        record['syntheticRoot']=str(root)
        (receipt_root/(uuid.uuid4().hex+'.json')).write_text(json.dumps(record,indent=2),encoding='utf-8')
        k.CloseHandle(job)
def main():
    if sys.argv[1:]==['--sqlite-compile-self-test']:
        source=Path.home()/'.cargo/registry/src/index.crates.io-1949cf8c6b5b557f/libsqlite3-sys-0.35.0/sqlite3/sqlite3.c'
        assert source.is_file() and source.stat().st_size>8*1024**2
        root=REPO/'.runtime-qa'/('msvc-sqlite-compile-'+uuid.uuid4().hex);root.mkdir()
        (root/'.nanfeng-tool-probe.json').write_text(json.dumps({'synthetic':True,'kind':'long-sqlite-compiler'}))
        output=root/'sqlite3-probe.obj'
        defines=['SQLITE_CORE','SQLITE_DEFAULT_FOREIGN_KEYS=1','SQLITE_ENABLE_API_ARMOR','SQLITE_ENABLE_COLUMN_METADATA','SQLITE_ENABLE_DBSTAT_VTAB','SQLITE_ENABLE_FTS3','SQLITE_ENABLE_FTS3_PARENTHESIS','SQLITE_ENABLE_FTS5','SQLITE_ENABLE_JSON1','SQLITE_ENABLE_LOAD_EXTENSION=1','SQLITE_ENABLE_MEMORY_MANAGEMENT','SQLITE_ENABLE_RTREE','SQLITE_ENABLE_STAT4','SQLITE_SOUNDEX','SQLITE_THREADSAFE=1','SQLITE_USE_URI','HAVE_USLEEP=1','HAVE_ISNAN','_POSIX_THREAD_SAFE_FUNCTIONS']
        code=owned_action([str(COMPILER),'-nologo','-MD','-O2','-Brepro','-W0',*['-D'+v for v in defines],'-Fo'+str(output),'-c',str(source)],'msvc-compile')
        if code:return code
        assert output.is_file() and output.stat().st_size>100000
        print(json.dumps({'longSqliteCompilePassed':True,'sourceBytes':source.stat().st_size,'sourceSha256':sha(source),'objectBytes':output.stat().st_size,'objectExecuted':False,'businessDatabaseOpened':False}))
        return 0
    if sys.argv[1:]==['--wait-idle']:
        job=verify_job();started=time.monotonic();checks=0
        try:
            while foreign_tools():
                checks+=1
                if time.monotonic()-started>=450:
                    print(json.dumps({'guardReleased':False,'waitedSeconds':round(time.monotonic()-started,3),'checks':checks,'toolsStarted':False}));return 2
                time.sleep(.1)
            print(json.dumps({'guardReleased':True,'waitedSeconds':round(time.monotonic()-started,3),'checks':checks,'toolsStarted':False}));return 0
        finally:k.CloseHandle(job)
    if sys.argv[1:]==['--bridge']:
        return owned_action([str(LINK),*read_arguments()],'msvc-link')
    if sys.argv[1:]==['--compiler-bridge']:
        return owned_action([str(COMPILER),*read_arguments()],'msvc-compile')
    if len(sys.argv)==3 and sys.argv[1]=='--bootstrap':
        target=Path(sys.argv[2]).resolve();cache=(DESKTOP/'src-tauri/target').resolve()
        assert target.is_relative_to(cache/'nanfeng-msvc-link-owner')
        target.parent.mkdir(parents=True,exist_ok=True)
        command=[str(Path.home()/'.cargo/bin/rustc.exe'),'--edition=2021','--crate-name','nfai_owned_msvc_link_bridge','-C','opt-level=1','-C','debuginfo=0',str(SCRIPT.with_suffix('.rs')),'-o',str(target)]
        return owned_action(command,'bootstrap-bridge')
    raise ValueError('Only fixed bridge/bootstrap modes supported')
if __name__=='__main__':raise SystemExit(main())
