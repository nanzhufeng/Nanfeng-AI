"""Run repository Node tests in an owned Windows Job, never attach other PIDs.

The suspended child joins the Job before execution. Process commit is limited
independently of V8; descendants inherit the Job and die when it closes.
See https://learn.microsoft.com/en-us/windows/win32/procthread/job-objects
"""
import argparse
import ctypes as C
from ctypes import wintypes as W
import datetime
import json
import msvcrt
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time
import uuid
import hashlib


class BasicLimits(C.Structure):
    _fields_ = [("process_time", C.c_int64), ("job_time", C.c_int64),
                ("flags", W.DWORD), ("min_ws", C.c_size_t), ("max_ws", C.c_size_t),
                ("active_limit", W.DWORD), ("affinity", C.c_size_t),
                ("priority", W.DWORD), ("scheduling", W.DWORD)]


class Limits(C.Structure):
    _fields_ = [("basic", BasicLimits), ("io", C.c_uint64 * 6),
                ("process_limit", C.c_size_t), ("job_limit", C.c_size_t),
                ("peak_process", C.c_size_t), ("peak_job", C.c_size_t)]


class Accounting(C.Structure):
    _fields_ = [("times", C.c_int64 * 4), ("faults", W.DWORD),
                ("total", W.DWORD), ("active", W.DWORD), ("terminated", W.DWORD)]


class Startup(C.Structure):
    _fields_ = [("cb", W.DWORD), ("reserved", W.LPWSTR), ("desktop", W.LPWSTR),
                ("title", W.LPWSTR), ("position_size_counts", W.DWORD * 7),
                ("flags", W.DWORD), ("show", W.WORD), ("reserved_size", W.WORD),
                ("reserved_data", C.c_void_p), ("stdin", W.HANDLE),
                ("stdout", W.HANDLE), ("stderr", W.HANDLE)]


class Process(C.Structure):
    _fields_ = [("process", W.HANDLE), ("thread", W.HANDLE),
                ("pid", W.DWORD), ("tid", W.DWORD)]


class MemoryStatus(C.Structure):
    _fields_ = [("length", W.DWORD), ("load", W.DWORD),
                ("total_physical", C.c_uint64), ("free_physical", C.c_uint64),
                ("total_commit", C.c_uint64), ("free_commit", C.c_uint64),
                ("total_virtual", C.c_uint64), ("free_virtual", C.c_uint64),
                ("extended_virtual", C.c_uint64)]


class ProcessEntry(C.Structure):
    _fields_ = [("size", W.DWORD), ("usage", W.DWORD), ("pid", W.DWORD),
                ("heap", C.c_size_t), ("module", W.DWORD), ("threads", W.DWORD),
                ("parent", W.DWORD), ("priority", W.LONG), ("flags", W.DWORD),
                ("exe", W.WCHAR * 260)]


def owned_job_process_images(kernel, job):
    """Read image paths only after a PID-list entry is confirmed in this Job."""
    capacity = 32
    while capacity <= 512:
        class IdList(C.Structure):
            _fields_ = [("assigned", W.DWORD), ("listed", W.DWORD),
                        ("ids", C.c_size_t * capacity)]
        listing = IdList()
        success = kernel.QueryInformationJobObject(job, 3, C.byref(listing), C.sizeof(listing), None)
        if not success and C.get_last_error() != 234:  # ERROR_MORE_DATA
            raise C.WinError(C.get_last_error())
        if not success or listing.assigned > listing.listed:
            capacity = max(capacity * 2, int(listing.assigned))
            continue
        if listing.listed > capacity:
            raise RuntimeError("Invalid owned Job PID list")
        pids = sorted(set(int(listing.ids[i]) for i in range(listing.listed)))
        break
    else:
        raise RuntimeError("Owned Job PID list exceeds diagnostic bound")
    rows = []
    for pid in pids:
        row = {"pid": pid, "inOwnedJob": None}
        handle = kernel.OpenProcess(0x1000, False, pid)  # QUERY_LIMITED_INFORMATION
        if not handle:
            row.update(status="open-failed", errorWin32=C.get_last_error())
            rows.append(row)
            continue
        try:
            member = W.BOOL()
            if not kernel.IsProcessInJob(handle, job, C.byref(member)):
                row.update(status="membership-query-failed", errorWin32=C.get_last_error())
            else:
                row["inOwnedJob"] = bool(member.value)
                if not member.value:
                    row["status"] = "no-longer-in-owned-job"
                else:
                    path = C.create_unicode_buffer(32768)
                    length = W.DWORD(len(path))
                    if kernel.QueryFullProcessImageNameW(handle, 0, path, C.byref(length)):
                        row.update(status="queried", imagePath=path.value)
                    else:
                        row.update(status="image-query-failed", errorWin32=C.get_last_error())
        finally:
            kernel.CloseHandle(handle)
        rows.append(row)
    return rows


def run(args):
    if os.name != "nt" or C.sizeof(C.c_void_p) != 8:
        raise RuntimeError("Only Windows x64 is supported")
    assert C.sizeof(Limits) == 144 and C.sizeof(Startup) == 104
    desktop = Path(__file__).resolve().parent.parent
    repo = desktop.parent
    review = (repo / "docs/review").resolve(strict=True)
    output = Path(args.output).resolve()
    if not output.is_relative_to(review) or output == review or output.exists():
        raise ValueError("Output must be a new directory inside repository docs/review")
    if any(parent.is_symlink() or parent.is_junction() for parent in output.parents if parent.exists()):
        raise ValueError("Reparse output ancestor rejected")
    production_candidate = bool(getattr(args, 'production_candidate', False))
    no_bundle = bool(getattr(args, 'gui_build_no_bundle', False))
    if no_bundle and (not args.gui_build or not production_candidate or args.nsis_bundle):
        raise ValueError('No-bundle mode only supports a production GUI build without packaging')
    if production_candidate:
        if not args.gui_build or args.gui_build_profile != 'release' or args.alignment_installer or args.alignment_repack:
            raise ValueError('Production candidate requires Release mode without acceptance installer flags')
        production_root = desktop / 'output/windows-production-candidate'
        configured_output = os.environ.get('NANFENG_AI_WINDOWS_OUTPUT_DIR')
        if not configured_output or Path(configured_output).resolve() != production_root.resolve():
            raise ValueError('Production candidate output must be the dedicated repository candidate directory')
        if production_root.is_symlink() or production_root.is_junction() or any(parent.is_symlink() or parent.is_junction() for parent in production_root.parents if parent.exists()):
            raise ValueError('Production candidate output must not be redirected')
        owner_path = production_root / '.nanfeng-production-build-output.json'
        owner = json.loads(owner_path.read_text('utf-8'))
        if owner != {'schemaVersion': 1, 'kind': 'production-build-output', 'applicationIdentifier': 'com.nanzhufeng.ai.desktop', 'root': str(production_root.resolve())}:
            raise ValueError('Production candidate output ownership does not match')
    node = shutil.which("node")
    if not node or Path(node).name.lower() != "node.exe":
        raise RuntimeError("Node.exe is unavailable")
    if args.small_tests and (args.probe or args.gui_build or args.native_smoke or args.all or not 1 <= len(args.files) <= 5):
        raise ValueError("Small tests require one to five explicit test files only")
    if args.observe_toolchain and not ((args.gui_build and not args.nsis_bundle) or args.probe in ('descendants', 'msvc-vctip-local', 'msvc-owned-linker')):
        raise ValueError('Toolchain observation requires compilation or the fixed descendant gate probe')
    memory = 512 if args.probe in ("msvc-lifetime", "msvc-vctip-local", "msvc-owned-linker") else 128 if args.probe else 512 if args.small_tests else 1024 if args.native_smoke else 3072
    job_memory = 1024 if args.probe in ("msvc-lifetime", "msvc-vctip-local", "msvc-owned-linker") else 256 if args.probe else 1024 if args.small_tests else 2048 if args.native_smoke else 3072 if args.gui_build else 6144
    if args.rust_tests:
        if args.probe or args.gui_build or args.native_smoke or args.small_tests or args.all or args.files or args.owned_msvc_linker or not args.rust_tests.replace(':', '').replace('_', '').isalnum():
            raise ValueError('Rust library tests require one explicit selector and no other mode')
        memory = job_memory = 6144
    if args.gui_build_memory_mib is not None:
        if not args.gui_build or args.nsis_bundle:
            raise ValueError("Measured native memory override is limited to a compiling GUI build")
        memory = job_memory = args.gui_build_memory_mib
    command = [node, "--max-old-space-size=2048"]
    owned_link_environment = {}
    owned_link_identity = None
    if args.owned_msvc_linker:
        if not (args.gui_build and args.gui_build_profile == 'release' and not args.nsis_bundle):
            raise ValueError('Owned MSVC linker requires isolated compiling Release mode')
        cache = (desktop / 'src-tauri/target/nanfeng-msvc-link-owner').resolve(strict=True)
        manifest = json.loads((cache / 'owner-manifest.json').read_text('utf-8'))
        scripts = desktop / 'scripts'
        tools = Path('C:/Program Files (x86)/Microsoft Visual Studio/2022/BuildTools/VC/Tools/MSVC/14.44.35207/bin/Hostx64/x64')
        bound = {'sourceSha256': scripts / 'windows-msvc-owned-linker.rs',
                 'ownerSha256': scripts / 'windows-msvc-owned-linker.py',
                 'readySha256': scripts / 'read-owned-vctip-readiness.py',
                 'compilerEnvSha256': scripts / 'read-windows-msvc-environment.py',
                 'linkSha256': tools / 'link.exe', 'vctipSha256': tools / 'vctip.exe', 'clSha256': tools / 'cl.exe',
                 'shimSha256': cache / 'link.exe'}
        for key, path in bound.items():
            if hashlib.sha256(path.read_bytes()).hexdigest() != manifest[key]:
                raise ValueError('Owned linker identity mismatch: ' + key)
        proof = json.loads((review / '20261018-windows-vctip-instance-timing/owned-linker-self-test.json').read_text('utf-8'))
        if not proof['pass'] or not proof.get('cppWindowsSdkCompilePassed') or not proof.get('longSqliteCompilePassed') or any(proof[key] != manifest[key] for key in bound):
            raise ValueError('Owned linker lacks matching executable self-test')
        probe_receipt = json.loads((review / '20261018-windows-vctip-instance-timing/cpp-owner-long-compile-test/receipt.json').read_text('utf-8'))
        if not (probe_receipt['probe'] == 'msvc-owned-linker' and probe_receipt['exitCode'] == 0
                and probe_receipt['stopReason'] is None and probe_receipt['descendantsBeforeCleanup'] == 0
                and probe_receipt['ownedProcessesRemaining'] == 0):
            raise ValueError('C/C++ owner self-test did not pass the outer zero-residue gate')
        if hashlib.sha256((cache / 'cl.exe').read_bytes()).hexdigest() != manifest['shimSha256']:
            raise ValueError('Owned C/C++ compiler shim mismatch')
        linker_key = 'CARGO_TARGET_X86_64_PC_WINDOWS_MSVC_LINKER'
        if os.environ.get(linker_key) or any(key.startswith('NFAI_MSVC_') for key in os.environ):
            raise ValueError('Existing linker override rejected')
        if any(key in ('CC','CXX','HOST_CC','HOST_CXX','TARGET_CC','TARGET_CXX') or key.startswith(('CC_','CXX_')) for key in os.environ):
            raise ValueError('Existing C/C++ compiler override rejected')
        owned_link_environment = {linker_key: str(cache / 'link.exe'),
            'CC': str(cache / 'cl.exe'), 'CXX': str(cache / 'cl.exe'),
            'NFAI_MSVC_LINK_PYTHON': sys.executable,
            'NFAI_MSVC_LINK_OWNER_SCRIPT': str(bound['ownerSha256']),
            'NFAI_MSVC_REAL_LINK_SHA256': manifest['linkSha256'],
            'NFAI_MSVC_VCTIP_SHA256': manifest['vctipSha256'],
            'NFAI_MSVC_CL_SHA256': manifest['clSha256'],
            'NFAI_MSVC_COMPILER_ENV_SHA256': manifest['compilerEnvSha256'],
            'NFAI_MSVC_LINK_RECEIPTS': str(output / 'owned-link-receipts')}
        owned_link_identity = {key: manifest[key] for key in bound}
    if args.rust_tests:
        cargo = shutil.which('cargo')
        if not cargo or Path(cargo).name.lower() != 'cargo.exe':
            raise RuntimeError('Cargo.exe is unavailable')
        command = [cargo, 'test', '--locked', '--offline', '--manifest-path', str(desktop / 'src-tauri/Cargo.toml'), '--target', 'x86_64-pc-windows-msvc', '--lib', args.rust_tests, '--', '--test-threads=1']
    elif args.gui_build:
        if args.gui_build_profile == "release" and args.nsis_bundle and not (args.alignment_installer or production_candidate):
            raise ValueError("Release NSIS is limited to the isolated alignment installer")
        if args.probe or args.all or args.files or args.native_smoke:
            raise ValueError("Isolated GUI build cannot be combined with probes or tests")
        if args.nsis_bundle and production_candidate:
            command += [str(desktop / "scripts/repack-windows-production-candidate.mjs")]
        elif args.nsis_bundle:
            command += [str(desktop / "scripts/repack-windows-alignment-installer.mjs"), "--profile=" + args.gui_build_profile] if args.alignment_repack else [str(desktop / "node_modules/@tauri-apps/cli/tauri.js"), "bundle", *(["--debug"] if args.gui_build_profile == "debug" else []), "--bundles", "nsis",
                        "--config", "src-tauri/tauri.windows-alignment-installer.conf.json" if args.alignment_installer else "src-tauri/tauri.windows-acceptance.conf.json", "--target", "x86_64-pc-windows-msvc"]
        elif production_candidate:
            command += [str(desktop / "scripts/build-windows.mjs"),
                        "--preserve-output", "--target=x86_64-pc-windows-msvc"]
            if no_bundle:
                command += ['--no-bundle']
        else:
            command += [str(desktop / "scripts/build-windows.mjs"), "--acceptance",
                        "--no-bundle", "--preserve-output", "--target=x86_64-pc-windows-msvc"]
            if args.gui_build_profile == "debug":
                command += ["--debug"]
    elif args.native_smoke:
        if args.probe or args.all or args.files:
            raise ValueError("Native smoke cannot be combined with probes or tests")
        candidate = json.loads((desktop / "output/windows-acceptance/current.json").read_text("utf-8"))
        expected = desktop / "output/windows-acceptance" / candidate["sha256"] / "NanfengAI-Windows-Acceptance.exe"
        if candidate["identifier"] != "com.nanzhufeng.ai.desktop.windowsacceptance" or Path(candidate["executable"]).resolve() != expected.resolve():
            raise ValueError("Native smoke requires the immutable isolated acceptance GUI")
        if hashlib.sha256(expected.read_bytes()).hexdigest().upper() != candidate["sha256"]:
            raise ValueError("Native GUI hash mismatch")
        scratch = Path("D:/CodexTemp")
        if scratch.is_symlink() or scratch.is_junction() or scratch.resolve() != scratch:
            raise ValueError("Native smoke scratch root must not be redirected")
        native_parent = scratch / ("nfa-bounded-gui-smoke." + str(uuid.uuid4()))
        if args.installed_concurrent:
            native_parent = scratch / ("nfa-bounded-chat-concurrent." + str(uuid.uuid4()))
            command += [str(desktop / "scripts/verify-windows-installed-chat-concurrent-native.mjs"), str(native_parent),
                        str(desktop.parent / "docs/review/20261005-chat-sync-owner/installed-mainline-input.json")]
        elif args.installed_editors:
            command += [str(desktop / "scripts/verify-windows-installed-chat-editors-native.mjs"), str(native_parent),
                        str(desktop.parent / "docs/review/20261005-chat-sync-owner/installed-mainline-input.json")]
        elif args.installed_shell_activation:
            native_parent = scratch / ("nfa-bounded-installed-shell-activation." + str(uuid.uuid4()))
            command += [str(desktop / "scripts/verify-windows-installed-shell-notification-activation-native.mjs"), str(native_parent)]
        elif args.installed_activation:
            native_parent = scratch / ("nfa-bounded-installed-activation." + str(uuid.uuid4()))
            command += [str(desktop / "scripts/verify-windows-installed-real-notification-activation-native.mjs"), str(native_parent)]
        elif args.installed_reminder:
            native_parent = scratch / ("nfa-bounded-installed-reminder." + str(uuid.uuid4()))
            command += [str(desktop / "scripts/verify-windows-installed-reminder-notification-native.mjs"), str(native_parent), "CHAT"]
        elif args.installed_stability:
            native_parent = scratch / ("nfa-bounded-installed-stability." + str(uuid.uuid4()))
            command += [str(desktop / "scripts/monitor-windows-chat-stability-native.mjs"), str(native_parent), args.installed_stability,
                        str(desktop.parent / "docs/review/20261005-chat-sync-owner/installed-current-monitor-input.json")]
        elif args.owned_migration_plan:
            plan_path = Path(args.owned_migration_plan).resolve(strict=True)
            review = desktop.parent / "docs/review"
            if not plan_path.is_relative_to(review) or plan_path.name != "upgrade-plan.json" or plan_path.is_symlink():
                raise ValueError("Owned migration plan must be a regular review plan")
            plan = json.loads(plan_path.read_text("utf-8-sig"))
            owned_root = Path("D:/CodexTemp/nanfeng-ai-windows-acceptance.00afec83-701e-4004-b210-18e974d9163b")
            owned_install = Path("D:/CodexTemp/nfai-installed-acceptance.7d2cb30e-9606-4d88-bd91-bbc0fec53c92")
            installed = owned_install / "nanfeng-ai-desktop-spike.exe"
            if Path(plan["root"]).resolve() != owned_root or Path(plan["installDir"]).resolve() != owned_install or hashlib.sha256(installed.read_bytes()).hexdigest().upper() != plan["expectedInstalledExeSha256"]:
                raise ValueError("Owned migration installation/root/observed payload mismatch")
            native_parent = scratch
            command += [str(desktop / "scripts/verify-windows-owned-migration-native.mjs"), str(scratch), str(owned_root), str(installed), plan["expectedInstalledExeSha256"]]
        elif args.native_concurrent:
            native_parent = scratch / ("nfa-bounded-chat-concurrent." + str(uuid.uuid4()))
            command += [str(desktop.parent / "docs/review/20261005-chat-submission-claim/verify-notification-chat-concurrent-native.mjs"), str(native_parent),
                        str(desktop.parent / "docs/review/20261005-chat-submission-claim/notification-candidate-build.json")]
        else:
            command += [str(desktop / "scripts/verify-windows-chat-package-smoke-native.mjs"), str(native_parent), str(output / "gui-input.json")]
        if args.native_cleanup:
            command += ["--cleanup-roundtrip"]
        if args.native_editors or args.installed_editors:
            command += ["--editor-roundtrip"]
    elif args.probe == "smoke":
        command += ["-e", "console.log(JSON.stringify({probe:'smoke',ok:true}))"]
    elif args.probe == "memory":
        child = "console.log('BOUNDED_MEMORY_PROBE_STARTED');const a=[];for(let i=0;i<32;i++){a.push(Buffer.alloc(16*1024*1024,1));console.log('ALLOCATED_MIB='+((i+1)*16));}"
        parent = "const{spawn}=require('node:child_process');const c=spawn(process.execPath,['--max-old-space-size=2048','-e'," + json.dumps(child) + "],{stdio:'inherit'});console.log('OWNED_CHILD_PID='+c.pid);c.on('exit',(code,signal)=>{console.log(JSON.stringify({childExitCode:code,childSignal:signal}));process.exit(code===0?0:42)});"
        command += ["-e", parent]
    elif args.probe == "timeout":
        command += ["-e", "const{spawn}=require('node:child_process');const c=spawn(process.execPath,['-e','setInterval(()=>{},1000)'],{stdio:'inherit'});console.log('OWNED_CHILD_PID='+c.pid);setInterval(()=>{},1000)"]
    elif args.probe == "descendants":
        child = "console.log('OWNED_DESCENDANT_READY');setTimeout(()=>process.exit(0),10000)"
        parent = "const{spawn}=require('node:child_process');let ready=false;const c=spawn(process.execPath,['--max-old-space-size=2048','-e',"+json.dumps(child)+"],{stdio:['ignore','pipe','pipe'],detached:true,windowsHide:true});c.stdout.on('data',d=>{if(!ready&&d.toString().includes('OWNED_DESCENDANT_READY')){ready=true;console.log('OWNED_CHILD_PID='+c.pid);c.stdout.destroy();c.stderr.destroy();c.unref();setTimeout(()=>process.exit(0),150)}});c.stderr.on('data',d=>process.stderr.write(d));c.on('error',()=>process.exit(2));c.on('exit',code=>{if(!ready)process.exit(code||3)});"
        command += ["-e", parent]
    elif args.probe == "msvc-lifetime":
        command += [str(desktop / "scripts/probe-windows-msvc-child-lifetime.mjs")]
    elif args.probe == "msvc-vctip-local":
        command += [str(desktop / "scripts/probe-windows-vctip-local.mjs")]
    elif args.probe == "msvc-owned-linker":
        command += [str(desktop / "scripts/prepare-windows-owned-msvc-linker.mjs"), '--wait-idle']
    elif args.probe in ("pipe-readiness-self-test", "msvc-environment"):
        py = r'C:\Users\Administrator\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe'
        child = str(desktop / 'scripts' / ('read-windows-msvc-environment.py' if args.probe == 'msvc-environment' else 'probe-windows-pipe-readiness.py'))
        command += ['-e', "const{execFileSync}=require('node:child_process');process.stdout.write(execFileSync("+json.dumps(py)+",['-X','utf8',"+json.dumps(child)+",'--self-test'],{encoding:'utf8',windowsHide:true,timeout:25000}))"]
    elif args.probe == "native-syntax":
        command += ["--check", str(desktop / "scripts/verify-windows-chat-package-smoke-native.mjs")]
    else:
        files = list((desktop / "tests").glob("*.test.mjs")) if args.all else [Path(f).resolve(strict=True) for f in args.files]
        if not files:
            raise ValueError("Specify repository test files or --all")
        for f in files:
            if f.parent.resolve() != (desktop / "tests").resolve() or not f.name.endswith(".test.mjs") or f.is_symlink():
                raise ValueError("Only direct repository desktop/tests/*.test.mjs files are allowed")
        command += ["--test", "--test-concurrency=1", "--test-reporter=tap", *map(str, sorted(files))]
    if args.probe and (args.all or args.files):
        raise ValueError("Diagnostic probes cannot run product tests")
    output.mkdir()
    if args.native_smoke:
        (output / "gui-input.json").write_text(json.dumps({"rebuiltGuiSha256": candidate["sha256"],
             "rebuiltGuiRuntimeSource": str(expected), "artifactKind": "compiled isolated GUI; no installer",
             "rootParent": str(native_parent)}, indent=2) + "\n", encoding="utf-8")
    kernel = C.WinDLL("kernel32", use_last_error=True)
    signatures = {
        "CreateJobObjectW": ([C.c_void_p, W.LPCWSTR], W.HANDLE),
        "SetInformationJobObject": ([W.HANDLE, C.c_int, C.c_void_p, W.DWORD], W.BOOL),
        "QueryInformationJobObject": ([W.HANDLE, C.c_int, C.c_void_p, W.DWORD, C.c_void_p], W.BOOL),
        "CreateProcessW": ([W.LPCWSTR, W.LPWSTR, C.c_void_p, C.c_void_p, W.BOOL, W.DWORD, C.c_void_p, W.LPCWSTR, C.POINTER(Startup), C.POINTER(Process)], W.BOOL),
        "AssignProcessToJobObject": ([W.HANDLE, W.HANDLE], W.BOOL),
        "ResumeThread": ([W.HANDLE], W.DWORD),
        "WaitForSingleObject": ([W.HANDLE, W.DWORD], W.DWORD),
        "GetExitCodeProcess": ([W.HANDLE, C.POINTER(W.DWORD)], W.BOOL),
        "TerminateProcess": ([W.HANDLE, W.UINT], W.BOOL),
        "TerminateJobObject": ([W.HANDLE, W.UINT], W.BOOL),
        "CloseHandle": ([W.HANDLE], W.BOOL),
        "GlobalMemoryStatusEx": ([C.POINTER(MemoryStatus)], W.BOOL),
        "CreateToolhelp32Snapshot": ([W.DWORD, W.DWORD], W.HANDLE),
        "Process32FirstW": ([W.HANDLE, C.POINTER(ProcessEntry)], W.BOOL),
        "Process32NextW": ([W.HANDLE, C.POINTER(ProcessEntry)], W.BOOL),
        "OpenProcess": ([W.DWORD, W.BOOL, W.DWORD], W.HANDLE),
        "IsProcessInJob": ([W.HANDLE, W.HANDLE, C.POINTER(W.BOOL)], W.BOOL),
        "QueryFullProcessImageNameW": ([W.HANDLE, W.DWORD, W.LPWSTR, C.POINTER(W.DWORD)], W.BOOL),
        "GetProcessTimes": ([W.HANDLE, C.POINTER(W.FILETIME), C.POINTER(W.FILETIME), C.POINTER(W.FILETIME), C.POINTER(W.FILETIME)], W.BOOL),
    }
    for name, (inputs, result) in signatures.items():
        function = getattr(kernel, name)
        function.argtypes, function.restype = inputs, result
    def checked(value):
        if not value:
            raise C.WinError(C.get_last_error())
        return value
    def query(kind, value):
        checked(kernel.QueryInformationJobObject(job, kind, C.byref(value), C.sizeof(value), None))
        return value
    observations = {}
    # Full MSVC/SDK environment capture exceeded 1024 identities at 329KB.
    # Diagnostic storage stays bounded; this does not change any Job success gate.
    observation_bytes = 512
    observation_started = time.monotonic()
    def observe_toolchain(job):
        nonlocal observation_bytes
        rows = {r['pid']: r for r in owned_job_process_images(kernel, job) if r.get('inOwnedJob')}
        snapshot = checked(kernel.CreateToolhelp32Snapshot(2, 0))
        try:
            entry = ProcessEntry(); entry.size = C.sizeof(entry)
            found = kernel.Process32FirstW(snapshot, C.byref(entry))
            while found:
                row = rows.get(int(entry.pid))
                if row:
                    handle = kernel.OpenProcess(0x1000, False, entry.pid)
                    if handle:
                        try:
                            member = W.BOOL(); created, exited, system, user = (W.FILETIME() for _ in range(4))
                            if kernel.IsProcessInJob(handle, job, C.byref(member)) and member.value and kernel.GetProcessTimes(handle, C.byref(created), C.byref(exited), C.byref(system), C.byref(user)):
                                identity = int(created.dwHighDateTime) << 32 | int(created.dwLowDateTime)
                                key = f'{entry.pid}:{identity}'
                                elapsed = round((time.monotonic() - observation_started) * 1000)
                                if key not in observations:
                                    item = {**row, 'creationFileTime': identity, 'parentPid': int(entry.parent), 'firstSeenMs': elapsed, 'lastSeenMs': elapsed}
                                    size = len(json.dumps(item).encode('utf-8')) + 128
                                    if len(observations) >= 4096 or observation_bytes + size > 2 * 1024**2:
                                        raise RuntimeError('Owned observation storage bound exceeded')
                                    observations[key] = item
                                    observation_bytes += size
                                observations[key]['lastSeenMs'] = elapsed
                        finally: kernel.CloseHandle(handle)
                found = kernel.Process32NextW(snapshot, C.byref(entry))
        finally: kernel.CloseHandle(snapshot)
    job = None
    process = Process()
    assigned = False
    previous_options = os.environ.get("NODE_OPTIONS")
    previous_job_name = os.environ.get("NFAI_BOUNDED_JOB_NAME")
    receipt = {"probe": args.probe, "guiBuild": args.gui_build, "guiBuildProfile": args.gui_build_profile if args.gui_build else None, "nsisBundleOnly": args.nsis_bundle,
               "nativeSmoke": args.native_smoke, "productionCandidate": production_candidate, "guiBuildNoBundle": no_bundle,
               "installedConcurrent": args.installed_concurrent, "installedEditors": args.installed_editors, "nativeCleanup": args.native_cleanup, "nativeEditors": args.native_editors, "nativeConcurrent": args.native_concurrent, "installedStability": args.installed_stability, "installedReminder": args.installed_reminder, "installedActivation": args.installed_activation, "installedShellActivation": args.installed_shell_activation, "ownedMigrationPlan": args.owned_migration_plan,
               "smallTests": args.small_tests,
               "productTestsExecuted": not bool(args.probe or args.gui_build or args.native_smoke),
               "processLimitMiB": memory, "jobLimitMiB": job_memory,
               "heapLimitMiB": 2048, "timeoutSeconds": args.timeout,
               "startedUtc": datetime.datetime.now(datetime.timezone.utc).isoformat()}
    receipt['rustTestSelector'] = args.rust_tests
    if args.rust_tests: receipt['heapLimitMiB'] = None  # No Node test process; inherited NODE_OPTIONS still bounds incidental Node children.
    if owned_link_identity:
        receipt['ownedMsvcLinker'] = owned_link_identity
    if args.observe_toolchain:
        receipt['toolchainObservationLimits'] = {'identities': 4096, 'storageBytes': 2 * 1024**2}
    try:
        available = MemoryStatus()
        available.length = C.sizeof(available)
        checked(kernel.GlobalMemoryStatusEx(C.byref(available)))
        receipt["freeSystemCommitBeforeStart"] = int(available.free_commit)
        minimum = 2 * job_memory * 1024**2
        receipt["minimumSystemCommitHeadroomBytes"] = minimum
        if available.free_commit < minimum:
            raise RuntimeError("Insufficient system commit headroom; no child started")
        if args.native_smoke:
            snapshot = kernel.CreateToolhelp32Snapshot(2, 0)
            if snapshot == C.c_void_p(-1).value:
                raise C.WinError(C.get_last_error())
            try:
                entry = ProcessEntry()
                entry.size = C.sizeof(entry)
                found = kernel.Process32FirstW(snapshot, C.byref(entry))
                while found:
                    if entry.exe.lower() in ("nanfeng-ai-desktop-spike.exe", "nanfengai-windows-acceptance.exe"):
                        raise RuntimeError("Existing Native process prevents serial smoke; no child started")
                    found = kernel.Process32NextW(snapshot, C.byref(entry))
                if C.get_last_error() != 18:
                    raise C.WinError(C.get_last_error())
            finally:
                kernel.CloseHandle(snapshot)
            receipt.update(candidateSha256=candidate["sha256"], rootParent=str(native_parent), nativeProcessSerialGate=True)
        job_name = "NanfengAIBounded-" + str(uuid.uuid4())
        job = checked(kernel.CreateJobObjectW(None, job_name))
        receipt["ownedJobName"] = job_name
        limit = Limits()
        limit.basic.flags = 0x2000 | 0x100 | 0x200  # kill on close, process and job commit
        limit.process_limit, limit.job_limit = memory * 1024**2, job_memory * 1024**2
        checked(kernel.SetInformationJobObject(job, 9, C.byref(limit), C.sizeof(limit)))
        configured = query(9, Limits())
        if configured.process_limit != limit.process_limit or configured.job_limit != limit.job_limit:
            raise RuntimeError("Windows did not retain the requested Job limits")
        receipt["jobLimitsReadBackExact"] = True
        with (output / "node.log").open("xb", buffering=0) as log, open(os.devnull, "rb", buffering=0) as empty:
            os.set_inheritable(log.fileno(), True)
            os.set_inheritable(empty.fileno(), True)
            startup = Startup()
            startup.cb, startup.flags, startup.show = C.sizeof(startup), 0x101, 0
            startup.stdin = msvcrt.get_osfhandle(empty.fileno())
            startup.stdout = startup.stderr = msvcrt.get_osfhandle(log.fileno())
            os.environ["NODE_OPTIONS"] = "--max-old-space-size=2048"
            os.environ["NFAI_BOUNDED_JOB_NAME"] = job_name
            os.environ.update(owned_link_environment)
            checked(kernel.CreateProcessW(str(command[0]), C.create_unicode_buffer(subprocess.list2cmdline(command)), None, None,
                                          True, 0x08000000 | 0x4, None, str(desktop), C.byref(startup), C.byref(process)))
            checked(kernel.AssignProcessToJobObject(job, process.process))
            assigned = True
            receipt.update(pid=int(process.pid), jobAssignedBeforeResume=True)
            if kernel.ResumeThread(process.thread) == 0xFFFFFFFF:
                raise C.WinError(C.get_last_error())
            receipt["controllerPid"] = os.getpid()
            (output / "startup-receipt.json").write_text(json.dumps(receipt, indent=2) + "\n", encoding="utf-8")
            deadline = time.monotonic() + args.timeout
            stopped = None
            while True:
                if args.observe_toolchain:
                    observe_toolchain(job)
                state = kernel.WaitForSingleObject(process.process, 50)
                if state == 0:
                    break
                if state != 258:
                    raise C.WinError(C.get_last_error())
                if time.monotonic() >= deadline or (output / "node.log").stat().st_size > 16 * 1024**2:
                    stopped = "timeout" if time.monotonic() >= deadline else "output_limit"
                    checked(kernel.TerminateJobObject(job, 124))
                    kernel.WaitForSingleObject(process.process, 5000)
                    break
            exit_code = W.DWORD()
            checked(kernel.GetExitCodeProcess(process.process, C.byref(exit_code)))
            limits = query(9, Limits())
            accounting = query(1, Accounting())
            receipt["activeAtRootExit"] = int(accounting.active)
            # Job exit accounting/console teardown can trail the signalled root.
            # A bounded grace does not extend the test timeout or memory budget.
            grace_end = time.monotonic() + 1
            while accounting.active and not stopped and time.monotonic() < grace_end:
                time.sleep(0.05)
                accounting = query(1, Accounting())
            receipt.update(exitCode=int(exit_code.value), stopReason=stopped,
                           peakProcessBytes=int(limits.peak_process), peakJobBytes=int(limits.peak_job),
                           descendantsBeforeCleanup=int(accounting.active), ownedProcessCount=int(accounting.total))
            receipt["descendantProcessesBeforeCleanup"] = []
            if accounting.active:
                receipt["descendantSnapshotAtUtc"] = datetime.datetime.now(datetime.timezone.utc).isoformat()
                try:
                    receipt["descendantProcessesBeforeCleanup"] = owned_job_process_images(kernel, job)
                except Exception as error:
                    # Diagnostics must never bypass Job cleanup or change the exit gate.
                    receipt["descendantSnapshotError"] = {"type": type(error).__name__, "win32": getattr(error, "winerror", None)}
                checked(kernel.TerminateJobObject(job, 125))
                end = time.monotonic() + 5
                while query(1, Accounting()).active and time.monotonic() < end:
                    time.sleep(0.05)
            receipt["ownedProcessesRemaining"] = int(query(1, Accounting()).active)
    except Exception as error:
        receipt["error"] = str(error)
        raise
    finally:
        for key in owned_link_environment:
            os.environ.pop(key, None)
        if previous_options is None:
            os.environ.pop("NODE_OPTIONS", None)
        else:
            os.environ["NODE_OPTIONS"] = previous_options
        if previous_job_name is None:
            os.environ.pop("NFAI_BOUNDED_JOB_NAME", None)
        else:
            os.environ["NFAI_BOUNDED_JOB_NAME"] = previous_job_name
        if process.process and not assigned:
            kernel.TerminateProcess(process.process, 125)
            kernel.WaitForSingleObject(process.process, 5000)
        for handle in [job, process.thread, process.process]:
            if handle:
                kernel.CloseHandle(handle)
        receipt["endedUtc"] = datetime.datetime.now(datetime.timezone.utc).isoformat()
        if args.observe_toolchain:
            (output / 'owned-toolchain-observation.json').write_text(json.dumps({'readOnly': True, 'scope': 'verified owned Job only; no command lines or environment', 'processes': list(observations.values())}, indent=2), encoding='utf-8')
            receipt['toolchainObservationIdentities'] = len(observations)
        (output / "receipt.json").write_text(json.dumps(receipt, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(receipt))
    if args.probe == "memory":
        log_text = (output / "node.log").read_text("utf-8", errors="replace")
        passed = receipt["exitCode"] == 42 and "BOUNDED_MEMORY_PROBE_STARTED" in log_text and "ALLOCATED_MIB=16" in log_text and "ERR_MEMORY_ALLOCATION_FAILED" in log_text
    elif args.probe == "timeout":
        passed = receipt["stopReason"] == "timeout" and receipt["ownedProcessCount"] >= 2
    else:
        passed = receipt["exitCode"] == 0 and not receipt["stopReason"] and receipt["descendantsBeforeCleanup"] == 0
    return 0 if passed and receipt["ownedProcessesRemaining"] == 0 else 1


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True)
    parser.add_argument("--probe", choices=["smoke", "memory", "timeout", "native-syntax", "descendants", "msvc-lifetime", "msvc-vctip-local", "msvc-owned-linker", "pipe-readiness-self-test", "msvc-environment"])
    parser.add_argument("--timeout", type=int, default=120)
    parser.add_argument("--all", action="store_true")
    parser.add_argument("--gui-build", action="store_true", help="Fixed isolated no-bundle build only")
    parser.add_argument('--production-candidate', action='store_true', help='Full Release build into the explicitly owned production candidate output; no installation or launch')
    parser.add_argument('--gui-build-no-bundle', action='store_true', help='Stage the normal production EXE without creating an installer')
    parser.add_argument('--observe-toolchain', action='store_true', help='Read-only bounded identities/parents in this owned compiler Job')
    parser.add_argument('--owned-msvc-linker', action='store_true', help='Explicit verified per-link owner for isolated Release compilation')
    parser.add_argument("--gui-build-profile", choices=["debug", "release"], default="debug",
                        help="Explicit profile; Release NSIS is limited to the alignment installer")
    parser.add_argument("--gui-build-memory-mib", type=int, choices=[3072, 6144],
                        help="Explicit measured native build limit; Node heaps remain 2048MiB")
    parser.add_argument("--installed-concurrent", action="store_true", help="Fixed current owned installed EXE concurrent chat case")
    parser.add_argument("--installed-editors", action="store_true", help="Fixed current owned installed EXE editor save/cold case")
    parser.add_argument("--installed-shell-activation", action="store_true", help="Fixed current-installed Shell protocol hot activation")
    parser.add_argument("--installed-activation", action="store_true", help="Fixed completed synthetic run current-installed cold activation")
    parser.add_argument("--installed-reminder", action="store_true", help="Fixed current installed synthetic reminder/notification case")
    parser.add_argument("--installed-stability", choices=["smoke","24h"], help="Fixed verified current installed runtime stability case")
    parser.add_argument("--owned-migration-plan", help="Only the already owned isolated installation and data root")
    parser.add_argument("--nsis-bundle", action="store_true", help="Fixed isolated existing debug binary NSIS bundle; no compile")
    parser.add_argument("--alignment-installer", action="store_true", help="Separate acceptance installer product; retains existing acceptance installation and protocol")
    parser.add_argument("--alignment-repack", action="store_true", help="Adapt only current generated installer startup and shortcut entry points")
    parser.add_argument("--native-smoke", action="store_true", help="Fixed isolated current-GUI smoke only")
    parser.add_argument("--native-cleanup", action="store_true", help="Current GUI synthetic conversation purge and cold read")
    parser.add_argument("--native-editors", action="store_true", help="Same isolated smoke with memory/reminder save roundtrip")
    parser.add_argument("--native-concurrent", action="store_true", help="Fixed current candidate ordinary chat concurrency case")
    parser.add_argument('--rust-tests', help='One explicit offline/locked Windows library-test selector; owned 6GiB Job')
    parser.add_argument("--small-tests", action="store_true", help="Explicit small suite with stricter 512MiB/process and 1GiB/tree")
    parser.add_argument("files", nargs="*")
    options = parser.parse_args()
    if options.production_candidate and (not options.gui_build or options.gui_build_profile != 'release' or options.alignment_installer or options.alignment_repack):
        parser.error('Production candidate requires Release mode without acceptance installer flags')
    if options.gui_build_profile == "release" and (not options.gui_build or (options.nsis_bundle and not (options.alignment_installer or options.production_candidate))):
        parser.error("Release profile requires --gui-build; NSIS additionally requires --alignment-installer")
    if options.alignment_installer and not options.nsis_bundle:
        parser.error("Alignment installer requires --nsis-bundle")
    if options.alignment_repack and not (options.alignment_installer and options.nsis_bundle):
        parser.error("Alignment repack requires the isolated alignment NSIS mode")
    if options.nsis_bundle:
        options.gui_build = True
    if sum(bool(v) for v in [options.installed_concurrent,options.installed_editors,options.native_concurrent,options.native_editors,options.native_cleanup,options.owned_migration_plan,options.installed_stability,options.installed_reminder,options.installed_activation,options.installed_shell_activation]) > 1:
        parser.error("Choose one Native behavior case")
    if options.installed_concurrent or options.installed_editors or options.native_cleanup or options.native_editors or options.native_concurrent or options.owned_migration_plan or options.installed_stability or options.installed_reminder or options.installed_activation or options.installed_shell_activation:
        options.native_smoke = True
    if options.installed_stability == "24h":
        options.timeout = 90000
    elif not 1 <= options.timeout <= (1800 if options.rust_tests or options.gui_build and options.gui_build_profile == "release" and not options.nsis_bundle else 600):
        parser.error("Timeout must be 1..600 seconds; isolated Release compilation permits up to 1800")
    raise SystemExit(run(options))
