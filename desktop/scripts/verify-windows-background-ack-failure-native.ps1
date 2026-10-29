param([ValidateSet(0,1)][int]$ExpectedFailureExit=0)
$ErrorActionPreference='Stop'
$desktopRoot=Split-Path -Parent $PSScriptRoot
$receipt=Get-Content (Join-Path $desktopRoot 'output/windows-acceptance/current.json') -Raw | ConvertFrom-Json
if($receipt.identifier -ne 'com.nanzhufeng.ai.desktop.windowsacceptance'){throw '候选身份无效'}
$expected=[IO.Path]::GetFullPath((Join-Path $desktopRoot ("output/windows-acceptance-binaries/{0}/nanfeng-ai-desktop-spike.exe" -f $receipt.sha256)))
$source=[IO.Path]::GetFullPath((Join-Path $desktopRoot ("output/windows-acceptance/{0}/NanfengAI-Windows-Acceptance.exe" -f $receipt.sha256)))
if([IO.Path]::GetFullPath($receipt.executable) -ne $source -or (Get-FileHash $source -Algorithm SHA256).Hash -ne $receipt.sha256){throw '候选hash无效'}
$launch=$null
$root=''
$port=0
function Start-Normal([bool]$reopen){
    $listener=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,0)
    $listener.Start()
    try{$script:port=$listener.LocalEndpoint.Port}finally{$listener.Stop()}
    $arguments=@{RootParent='D:\CodexTemp';StartupMode='normal';DebugPort=$port}
    if($reopen){$arguments.Root=$root}
    $script:launch=& (Join-Path $PSScriptRoot 'start-windows-acceptance.ps1') @arguments | ConvertFrom-Json
    if($launch.sha256 -ne $receipt.sha256 -or [IO.Path]::GetFullPath($launch.executable) -ne $expected){throw '启动身份不符'}
    $script:root=$launch.root
    Start-Sleep -Seconds 3
}
function Stop-Normal{
    if(-not $launch){return}
    $actual=Get-CimInstance Win32_Process -Filter ("ProcessId={0}" -f $launch.pid)
    if($actual){
        if([IO.Path]::GetFullPath($actual.ExecutablePath) -ne $expected -or (Get-FileHash $expected -Algorithm SHA256).Hash -ne $receipt.sha256){throw 'PID身份不符'}
        Stop-Process -Id $launch.pid -Force
    }
    $script:launch=$null
}
function Invoke-Native([string]$command,[string]$area='CHAT'){
    $expression='window.__TAURI__.core.invoke('+ (ConvertTo-Json $command -Compress)+',{}, {headers:{"x-nanfeng-data-area":"'+$area+'"}}).catch(error=>({__error:String(error)}))'
    $raw=$expression | & node --max-old-space-size=2048 (Join-Path $PSScriptRoot 'windows-acceptance-cdp-eval.mjs') $port 2>$null
    if($LASTEXITCODE -ne 0){throw 'IPC回读失败'}
    $value=$raw|ConvertFrom-Json
    if($value.__error){throw $value.__error}
    return $value
}
function Run-Cycle{
    $process=Start-Process -FilePath $expected -ArgumentList @('--nanfeng-background-cycle-v1',('--nanfeng-background-root-v1='+$root)) -WindowStyle Hidden -PassThru
    try{
        if(-not $process.WaitForExit(30000)){throw '后台周期超时'}
        return $process.ExitCode
    }finally{
        if(-not $process.HasExited){
            $actual=Get-CimInstance Win32_Process -Filter ("ProcessId={0}" -f $process.Id)
            if($actual -and [IO.Path]::GetFullPath($actual.ExecutablePath) -eq $expected){Stop-Process -Id $process.Id -Force}
        }
    }
}
$python=@'
import json,sqlite3,sys,time,pathlib
root=pathlib.Path(sys.argv[1]); action=sys.argv[2]
assert root.parent==pathlib.Path('D:/CodexTemp') and root.name.startswith('nanfeng-ai-windows-acceptance.')
assert json.loads((root/'.nanfeng-windows-acceptance.json').read_text())['identifier']=='com.nanzhufeng.ai.desktop.windowsacceptance'
def prepare(c, reject):
 w=c.execute('select id from workspaces').fetchall(); assert len(w)==1
 now=int(time.time()*1000)
 c.execute("update desktop_app_settings set monitor_notifications=0 where id=1")
 c.execute("insert into desktop_reminder_drafts_v1(draft_id,workspace_id,status,title,instruction,schedule_kind,anchor_local,timezone_id,missed_policy,retry_count,created_at_ms,updated_at_ms) values(?,?,?,?,?,?,?,?,?,?,?,?)",('ack-draft',w[0][0],'CONFIRMED','合成回执','仅合成本机材料','ONCE','2026-09-26T12:00','UTC','RUN_ONCE',0,now,now))
 c.execute("insert into desktop_reminder_plans_v1(plan_id,source_draft_id,workspace_id,title,instruction,schedule_kind,anchor_local,timezone_id,missed_policy,status,created_at_ms,updated_at_ms) values(?,?,?,?,?,?,?,?,?,?,?,?)",('ack-plan','ack-draft',w[0][0],'合成回执','仅合成本机材料','ONCE','2026-09-26T12:00','UTC','RUN_ONCE','COMPLETED',now,now))
 c.execute("insert into desktop_reminder_runs_v1(run_id,plan_id,attempt_id,scheduled_at_ms,started_at_ms,completed_at_ms,status,result,notification_state) values(?,?,?,?,?,?,?,?,?)",('ack-run','ack-plan','ack-attempt',now,now,now,'SUCCEEDED','合成结果','PENDING'))
 if reject: c.execute("create trigger synthetic_ack_reject before update of notification_state on desktop_reminder_runs_v1 when OLD.run_id='ack-run' and NEW.notification_state='SUPPRESSED' begin select raise(ABORT,'synthetic ack rejection'); end")
facts={}
for area,path in [('CHAT',root/'workspace.sqlite3'),('WORK',root/'work-area/workspace.sqlite3')]:
 c=sqlite3.connect(path); c.execute('pragma foreign_keys=on')
 if action=='prepare': prepare(c,area=='CHAT')
 elif action=='release' and area=='CHAT': c.execute('drop trigger synthetic_ack_reject')
 c.commit()
 facts[area]={'receipt':c.execute("select status,notification_state,notification_safe_code from desktop_reminder_runs_v1 where run_id='ack-run'").fetchone(),'integrity':c.execute('pragma integrity_check').fetchone()[0]}
 c.close()
print(json.dumps(facts,ensure_ascii=True))
'@
function Facts([string]$action){
    $raw=& python -c $python $root $action
    if($LASTEXITCODE -ne 0){throw '合成SQL失败'}
    return ($raw|ConvertFrom-Json)
}
try{
    Start-Normal $false
    $null=Invoke-Native 'ensure_desktop_local_workspace'
    $null=Invoke-Native 'ensure_desktop_local_workspace' 'WORK'
    Stop-Normal
    $prepared=Facts 'prepare'
    $failedExit=Run-Cycle
    $failed=Facts 'read'
    if($failedExit -ne $ExpectedFailureExit -or $failed.CHAT.receipt[1] -ne 'PENDING' -or $failed.WORK.receipt[1] -ne 'SUPPRESSED' -or $failed.CHAT.integrity -ne 'ok' -or $failed.WORK.integrity -ne 'ok'){throw ('失败收据不符 '+($failed|ConvertTo-Json -Compress -Depth 5)+' exit='+$failedExit)}
    Write-Output ([pscustomobject]@{phase='rejected-ack';candidateSha256=$receipt.sha256;root=$root;exitCode=$failedExit;facts=$failed}|ConvertTo-Json -Depth 5 -Compress)
    Start-Normal $true
    $failedPending=@(Invoke-Native 'read_pending_desktop_reminder_notifications')
    $peerPending=@(Invoke-Native 'read_pending_desktop_reminder_notifications' 'WORK')
    if($failedPending.Count -ne 1 -or $failedPending[0].runId -ne 'ack-run' -or $peerPending.Count -ne 0){throw '失败冷启回读不符'}
    Stop-Normal
    Write-Output ([pscustomobject]@{phase='rejected-ack-cold';candidateSha256=$receipt.sha256;root=$root;chatPendingCount=$failedPending.Count;workPendingCount=$peerPending.Count}|ConvertTo-Json -Compress)
    $null=Facts 'release'
    $successExit=Run-Cycle
    $success=Facts 'read'
    if($successExit -ne 0 -or $success.CHAT.receipt[1] -ne 'SUPPRESSED' -or $success.CHAT.receipt[2] -ne 'SETTING_DISABLED' -or $success.WORK.receipt[1] -ne 'SUPPRESSED'){throw '正常收尾不符'}
    Start-Normal $true
    $pending=@(Invoke-Native 'read_pending_desktop_reminder_notifications')
    $projection=Invoke-Native 'read_desktop_reminders'
    $plan=@($projection.plans|Where-Object planId -EQ 'ack-plan')
    if($pending.Count -ne 0 -or $plan.Count -ne 1){throw '冷启回读不符'}
    Stop-Normal
    Write-Output ([pscustomobject]@{phase='released-ack-cold';candidateSha256=$receipt.sha256;root=$root;exitCode=$successExit;facts=$success;coldPendingCount=$pending.Count;layer='SYNTHETIC_NATIVE_HEADLESS_ACK_NOT_PROVIDER_SUCCESS_OR_SCHEDULER_TRIGGER'}|ConvertTo-Json -Depth 5 -Compress)
}finally{Stop-Normal}
