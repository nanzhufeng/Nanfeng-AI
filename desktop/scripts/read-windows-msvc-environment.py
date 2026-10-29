"""Capture only compiler environment through a private pipe, never diagnostics."""
from pathlib import Path
import hashlib,importlib.util,json,os,subprocess,sys
HERE=Path(__file__).resolve()
VCVARS=Path(r'C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvars64.bat')
NAMES=('INCLUDE','LIB','LIBPATH','PATH')

def verify_job():
    spec=importlib.util.spec_from_file_location('nfai_env_owner',HERE.with_name('windows-msvc-owned-linker.py'))
    owner=importlib.util.module_from_spec(spec);spec.loader.exec_module(owner)
    job=owner.verify_job();owner.k.CloseHandle(job)

def compiler_environment():
    verify_job()
    for p in (VCVARS,Path(sys.executable),HERE):
        assert p.is_file() and not any(c in str(p) for c in '%!^&\r\n"')
    cmd=Path(os.environ['SystemRoot'])/'System32/cmd.exe'
    command=f'call "{VCVARS}" >nul && "{sys.executable}" -X utf8 "{HERE}" --emit'
    line=f'"{cmd}" /d /s /c "{command}"'
    result=subprocess.run(line,executable=str(cmd),env={**os.environ,'VSCMD_SKIP_SENDTELEMETRY':'1'},stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,creationflags=0x08000000,timeout=20)
    assert result.returncode==0 and len(result.stdout)<=131072 and len(result.stderr)<=8192,'Compiler environment capture failed'
    values=json.loads(result.stdout.decode('utf-8-sig'));assert set(values)==set(NAMES)
    assert values['INCLUDE'] and values['LIB'] and all('\0' not in v for v in values.values())
    for name in ('INCLUDE','LIB'):
        assert all(Path(v).is_dir() for v in values[name].split(';') if v),'Compiler search directory missing'
    return values

if __name__=='__main__':
    verify_job()
    if sys.argv[1:]==['--emit']:
        assert os.environ.get('VSCMD_SKIP_SENDTELEMETRY')=='1'
        print(json.dumps({name:os.environ.get(name,'') for name in NAMES}))
    elif sys.argv[1:]==['--self-test']:
        before={name:os.environ.get(name) for name in NAMES};values=compiler_environment()
        assert before=={name:os.environ.get(name) for name in NAMES}
        print(json.dumps({'pass':True,'keys':list(values),'searchDirectoryCounts':{name:len([v for v in values[name].split(';') if v]) for name in ('INCLUDE','LIB')},'rawEnvironmentLogged':False,'globalEnvironmentChanged':False,'compilerOrLinkerExecuted':False,'vcvarsSha256':hashlib.sha256(VCVARS.read_bytes()).hexdigest()}))
    else:raise ValueError('Only private emit or metadata-only self-test supported')
