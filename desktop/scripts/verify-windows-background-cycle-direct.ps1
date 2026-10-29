param(
    [Parameter(Mandatory)][string]$Root,
    [Parameter(Mandatory)][int]$ForegroundPid,
    [ValidateSet('CHAT','WORK')][string]$DataArea = 'CHAT',
    [switch]$RejectFailureWrite
)
$ErrorActionPreference = 'Stop'
$desktopRoot = Split-Path -Parent $PSScriptRoot
$receipt = Get-Content -LiteralPath (Join-Path $desktopRoot 'output/windows-acceptance/current.json') -Raw | ConvertFrom-Json
if ($receipt.identifier -ne 'com.nanzhufeng.ai.desktop.windowsacceptance') { throw '验收候选身份无效' }
$expected = [IO.Path]::GetFullPath((Join-Path $desktopRoot ("output/windows-acceptance-binaries/{0}/nanfeng-ai-desktop-spike.exe" -f $receipt.sha256)))
if ((Get-FileHash -LiteralPath $expected -Algorithm SHA256).Hash -ne $receipt.sha256) { throw '验收候选哈希不符' }
$rootPath = [IO.Path]::GetFullPath($Root)
$markerPath = Join-Path $rootPath '.nanfeng-windows-acceptance.json'
$marker = Get-Content -LiteralPath $markerPath -Raw | ConvertFrom-Json
if ($marker.identifier -ne $receipt.identifier -or -not $rootPath.StartsWith('D:\CodexTemp\nanfeng-ai-windows-acceptance.', [StringComparison]::OrdinalIgnoreCase)) { throw '合成根不符' }
$foreground = Get-CimInstance Win32_Process -Filter ("ProcessId={0}" -f $ForegroundPid)
if (-not $foreground -or [IO.Path]::GetFullPath([string]$foreground.ExecutablePath) -ne $expected) { throw '前台 PID 身份不符' }
Stop-Process -Id $ForegroundPid -Force -ErrorAction Stop
for ($i=0; $i -lt 40 -and (Get-Process -Id $ForegroundPid -ErrorAction SilentlyContinue); $i++) { Start-Sleep -Milliseconds 100 }
if (Get-Process -Id $ForegroundPid -ErrorAction SilentlyContinue) { throw '前台 PID 未退出' }

$database = if ($DataArea -eq 'WORK') { Join-Path $rootPath 'work-area/workspace.sqlite3' } else { Join-Path $rootPath 'workspace.sqlite3' }
$inject = @'
import sqlite3,sys,time
p=sys.argv[1]
c=sqlite3.connect(p)
c.execute('pragma foreign_keys=on')
if sys.argv[2]=='reject':
 c.execute("CREATE TRIGGER reject_background_route_failure BEFORE UPDATE ON desktop_reminder_runs_v1 WHEN NEW.status='FAILED' BEGIN SELECT RAISE(ABORT,'synthetic-only'); END")
w=c.execute('select id from workspaces').fetchall()
assert len(w)==1,w
now=int(time.time()*1000)
c.execute('insert into desktop_reminder_drafts_v1(draft_id,workspace_id,status,title,instruction,schedule_kind,anchor_local,timezone_id,missed_policy,retry_count,created_at_ms,updated_at_ms) values(?,?,?,?,?,?,?,?,?,?,?,?)',('synthetic-background-draft',w[0][0],'CONFIRMED','隔离后台唤醒','只核对本机后台调度','ONCE','2026-09-26T12:00','Asia/Shanghai','RUN_ONCE',0,now,now))
c.execute('insert into desktop_reminder_plans_v1(plan_id,source_draft_id,workspace_id,title,instruction,schedule_kind,anchor_local,timezone_id,missed_policy,status,next_run_at_ms,created_at_ms,updated_at_ms) values(?,?,?,?,?,?,?,?,?,?,?,?,?)',('synthetic-background-plan','synthetic-background-draft',w[0][0],'隔离后台唤醒','只核对本机后台调度','ONCE','2026-09-26T12:00','Asia/Shanghai','RUN_ONCE','ACTIVE',now-5000,now,now))
c.commit()
assert c.execute('select count(*) from desktop_reminder_runs_v1').fetchone()[0]==0
print('injected=one-due-synthetic-plan')
'@
& python -c $inject $database $(if($RejectFailureWrite){'reject'}else{'normal'})
if ($LASTEXITCODE -ne 0) { throw '合成到期计划准备失败' }

$args = @('--nanfeng-background-cycle-v1', ('--nanfeng-background-root-v1=' + $rootPath))
$stopped = [System.Collections.Generic.List[int]]::new()
$exitCodes = [System.Collections.Generic.List[int]]::new()
for ($cycle = 1; $cycle -le 2; $cycle++) {
    $process = Start-Process -FilePath $expected -ArgumentList $args -WindowStyle Hidden -PassThru
    try {
        if (-not $process.WaitForExit(30000)) { throw "后台周期 $cycle 未在 30 秒内退出" }
        # The production owner persists FAILED/SERVICE_DISABLED and returns Err.
        # The first cycle must report that failure; the second has no due work.
        $expectedExit = if ($cycle -eq 1) { 1 } else { 0 }
        if ($process.ExitCode -ne $expectedExit) { throw "后台周期 $cycle 退出码 $($process.ExitCode)，预期 $expectedExit" }
        if($RejectFailureWrite -and $cycle -eq 1){
            & python -c "import sqlite3,sys;c=sqlite3.connect('file:'+sys.argv[1].replace(chr(92),'/')+'?mode=ro',uri=True);r=c.execute('select status from desktop_reminder_runs_v1').fetchall();p=c.execute('select status from desktop_reminder_plans_v1').fetchall();assert r==[('RUNNING',)] and p==[('RUNNING',)],(r,p)" $database
            if($LASTEXITCODE -ne 0){throw '拒写后未完成原始运行事实不符'}
        }
        $exitCodes.Add($process.ExitCode)
        $stopped.Add($process.Id)
    } finally {
        if (-not $process.HasExited) {
            $actual = Get-CimInstance Win32_Process -Filter ("ProcessId={0}" -f $process.Id)
            if ($actual -and [IO.Path]::GetFullPath([string]$actual.ExecutablePath) -eq $expected) {
                Stop-Process -Id $process.Id -Force -ErrorAction Stop
            }
        }
    }
}
$verify = @'
import json,sqlite3,sys
c=sqlite3.connect('file:'+sys.argv[1].replace('\\','/')+'?mode=ro',uri=True)
r=c.execute('select status,safe_error_code from desktop_reminder_runs_v1 where plan_id=?',('synthetic-background-plan',)).fetchall()
p=c.execute('select status,next_run_at_ms from desktop_reminder_plans_v1 where plan_id=?',('synthetic-background-plan',)).fetchone()
print(json.dumps({'runs':r,'plan':p,'integrity':c.execute('pragma quick_check').fetchone()[0]}))
'@
$result = & python -c $verify $database | ConvertFrom-Json
 $expectedStatus=if($RejectFailureWrite){'UNKNOWN'}else{'FAILED'}
 $expectedCode=if($RejectFailureWrite){'PROCESS_INTERRUPTED'}else{'SERVICE_DISABLED'}
if ($LASTEXITCODE -ne 0 -or $result.integrity -ne 'ok' -or $result.runs.Count -ne 1 -or $result.runs[0][0] -ne $expectedStatus -or $result.runs[0][1] -ne $expectedCode) { throw ('后台执行事实不符：' + ($result | ConvertTo-Json -Compress)) }
if($RejectFailureWrite){
    & python -c "import sqlite3,sys;c=sqlite3.connect(sys.argv[1]);c.execute('DROP TRIGGER reject_background_route_failure');c.commit()" $database
    if($LASTEXITCODE -ne 0){throw '合成拒写停点解除失败'}
}
[pscustomobject]@{candidateSha256=$receipt.sha256;root=$rootPath;dataArea=$DataArea;foregroundPid=$ForegroundPid;runStatus=$result.runs[0][0];safeErrorCode=$result.runs[0][1];runCount=$result.runs.Count;headlessPids=@($stopped);headlessExitCodes=@($exitCodes)} | ConvertTo-Json -Compress
