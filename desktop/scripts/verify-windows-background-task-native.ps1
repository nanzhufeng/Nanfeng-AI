param([string]$RootParent = 'D:\CodexTemp', [ValidateSet('CHAT','WORK')][string]$DataArea = 'CHAT', [switch]$WaitForScheduledTrigger)
$ErrorActionPreference = 'Stop'
$desktopRoot = Split-Path -Parent $PSScriptRoot
$receipt = Get-Content -LiteralPath (Join-Path $desktopRoot 'output/windows-acceptance/current.json') -Raw | ConvertFrom-Json
if ($receipt.identifier -ne 'com.nanzhufeng.ai.desktop.windowsacceptance') { throw '候选身份无效' }
$sourceExe = [IO.Path]::GetFullPath((Join-Path $desktopRoot ("output/windows-acceptance/{0}/NanfengAI-Windows-Acceptance.exe" -f $receipt.sha256)))
$runningExe = [IO.Path]::GetFullPath((Join-Path $desktopRoot ("output/windows-acceptance-binaries/{0}/nanfeng-ai-desktop-spike.exe" -f $receipt.sha256)))
if ([IO.Path]::GetFullPath([string]$receipt.executable) -ne $sourceExe -or (Get-FileHash -LiteralPath $sourceExe -Algorithm SHA256).Hash -ne $receipt.sha256) { throw '候选哈希无效' }
$taskName = '\com.nanzhufeng.ai.desktop.windowsacceptance.background-v1'
& schtasks.exe /Query /TN $taskName 2>$null | Out-Null
if ($LASTEXITCODE -eq 0) { throw '隔离验收任务名已经存在；拒绝覆盖' }
$launcher = Join-Path $PSScriptRoot 'start-windows-acceptance.ps1'
$cdpHelper = Join-Path $PSScriptRoot 'windows-acceptance-cdp-eval.mjs'
$launch = $null
$root = ''
$port = 0
$planId = ''
$stopped = [System.Collections.Generic.List[int]]::new()

function New-Port {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $listener.Start()
    try { return [int]$listener.LocalEndpoint.Port } finally { $listener.Stop() }
}
function Invoke-Cdp([string]$expression) {
    $raw = $expression | & node --max-old-space-size=2048 $cdpHelper $port 2>$null
    if ($LASTEXITCODE -ne 0) { throw 'CDP 调用失败' }
    return ($raw | ConvertFrom-Json)
}
function Invoke-Native([string]$command, [object]$arguments) {
    $commandJson = ConvertTo-Json -InputObject $command -Compress
    $argsJson = ConvertTo-Json -InputObject $arguments -Compress -Depth 12
    $expression = "window.__TAURI__.core.invoke($commandJson,$argsJson,{headers:{'x-nanfeng-data-area':'$DataArea'}}).catch(error=>({__error:String(error)}))"
    $value = Invoke-Cdp $expression
    if ($value.__error) { throw ("{0}: {1}" -f $command, $value.__error) }
    return $value
}
function Start-Candidate([bool]$reopen) {
    $script:port = New-Port
    $args = @{RootParent=$RootParent; StartupMode='normal'; Visible=$true; DebugPort=$port}
    if ($reopen) { $args.Root = $root }
    $script:launch = & $launcher @args | ConvertFrom-Json
    if ($launch.sha256 -ne $receipt.sha256 -or [IO.Path]::GetFullPath([string]$launch.executable) -ne $runningExe) { throw '启动候选身份不符' }
    $script:root = [IO.Path]::GetFullPath([string]$launch.root)
    if (-not $root.StartsWith(([IO.Path]::GetFullPath($RootParent).TrimEnd('\') + '\'), [StringComparison]::OrdinalIgnoreCase)) { throw '合成根越界' }
    $marker = Get-Content -LiteralPath (Join-Path $root '.nanfeng-windows-acceptance.json') -Raw | ConvertFrom-Json
    if ($marker.identifier -ne $receipt.identifier) { throw '合成根标记不符' }
    for ($i = 0; $i -lt 100; $i++) {
        try { if (Invoke-Cdp 'Boolean(window.__TAURI__?.core?.invoke)') { return } } catch { Start-Sleep -Milliseconds 100 }
        Start-Sleep -Milliseconds 100
    }
    throw '候选 IPC 未就绪'
}
function Stop-Candidate {
    if (-not $launch) { return }
    $pidValue = [int]$launch.pid
    $process = Get-CimInstance Win32_Process -Filter ("ProcessId={0}" -f $pidValue)
    if (-not $process -or [IO.Path]::GetFullPath([string]$process.ExecutablePath) -ne $runningExe -or (Get-FileHash -LiteralPath $runningExe -Algorithm SHA256).Hash -ne $receipt.sha256) { throw 'PID 身份不符，拒绝停止' }
    Stop-Process -Id $pidValue -Force -ErrorAction Stop
    $stopped.Add($pidValue)
    $script:launch = $null
}
function Assert-TaskIdentity {
    $taskXml = (& schtasks.exe /Query /TN $taskName /XML 2>$null) -join "`n"
    if ($LASTEXITCODE -ne 0 -or -not $taskXml.Contains($runningExe) -or -not $taskXml.Contains($root) -or -not $taskXml.Contains('PT1M')) { throw '任务计划程序记录与隔离候选不符' }
    return $taskXml
}
function Read-RunCount {
    $database = if($DataArea -eq 'WORK'){Join-Path $root 'work-area/workspace.sqlite3'}else{Join-Path $root 'workspace.sqlite3'}
    $python = 'import sqlite3,sys; c=sqlite3.connect("file:"+sys.argv[1].replace("\\","/")+"?mode=ro",uri=True); print(c.execute("select count(*) from desktop_reminder_runs_v1 where plan_id=?",(sys.argv[2],)).fetchone()[0])'
    $count = & python -c $python $database $planId
    if ($LASTEXITCODE -ne 0) { throw '合成提醒运行账无法读取' }
    return [int]$count
}

try {
    Start-Candidate $false
    $workspace = Invoke-Native 'ensure_desktop_local_workspace' @{}
    $draft = Invoke-Native 'create_desktop_manual_reminder_draft' @{args=@{workspaceId=$workspace.summary.id;conversationId=$null;timezoneId='Asia/Shanghai'}}
    $draftId = [string]$draft.drafts[0].draftId
    if (-not $draftId) { throw '合成提醒草案未创建' }
    $anchorLocal = [DateTime]::UtcNow.AddHours(8).AddMinutes(10).ToString('yyyy-MM-ddTHH:mm', [Globalization.CultureInfo]::InvariantCulture)
    $confirmed = Invoke-Native 'confirm_desktop_reminder_draft' @{args=@{draftId=$draftId;title='隔离后台唤醒';instruction='仅核对本机后台调度';scheduleKind='ONCE';anchorLocal=$anchorLocal;timezoneId='Asia/Shanghai';missedPolicy='RUN_ONCE'}}
    $plan = $confirmed.plans | Where-Object { $_.sourceDraftId -eq $draftId } | Select-Object -First 1
    if (-not $plan -or $plan.status -ne 'ACTIVE') { throw '合成提醒计划未启用' }
    $script:planId = [string]$plan.planId
    $runtime = Invoke-Native 'read_desktop_background_runtime' @{}
    if (-not $runtime.desiredEnabled -or -not $runtime.installed -or $runtime.safeCode -ne 'INSTALLED') { throw ('后台注册未成功：' + $root + ' ' + ($runtime | ConvertTo-Json -Compress)) }
    $null = Assert-TaskIdentity
    Stop-Candidate

    $database = if($DataArea -eq 'WORK'){Join-Path $root 'work-area/workspace.sqlite3'}else{Join-Path $root 'workspace.sqlite3'}
    $nowMs = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    $python = 'import sqlite3,sys; c=sqlite3.connect(sys.argv[1]); p=sys.argv[2]; now=int(sys.argv[3]); row=c.execute("select status from desktop_reminder_plans_v1 where plan_id=?",(p,)).fetchone(); assert row==("ACTIVE",),row; assert c.execute("update desktop_reminder_plans_v1 set next_run_at_ms=? where plan_id=? and status=''ACTIVE''",(now-5000,p)).rowcount==1; c.commit()'
    & python -c $python $database $planId $nowMs
    if ($LASTEXITCODE -ne 0) { throw '合成到期点注入失败' }
    if ((Read-RunCount) -ne 0) { throw '关闭界面前已有提醒运行账' }
    if(-not $WaitForScheduledTrigger){
        & schtasks.exe /Run /TN $taskName | Out-Null
        if ($LASTEXITCODE -ne 0) { throw '任务计划程序未启动后台周期' }
    }
    $runCount = 0
    for ($i = 0; $i -lt 320; $i++) {
        $runCount = Read-RunCount
        if ($runCount -gt 0) { break }
        Start-Sleep -Milliseconds 250
    }
    if ($runCount -ne 1) { throw '后台周期未在关闭界面后生成单次提醒运行账' }

    Start-Candidate $true
    $reopened = Invoke-Native 'read_desktop_reminders' @{}
    $savedPlan = $reopened.plans | Where-Object { $_.planId -eq $planId } | Select-Object -First 1
    if (-not $savedPlan -or $savedPlan.lastSafeErrorCode -ne 'SERVICE_DISABLED' -or (Read-RunCount) -ne 1) { throw ('后台结果未准确回读：' + ($reopened | ConvertTo-Json -Compress -Depth 5)) }
    $null = Invoke-Native 'delete_desktop_reminder_plan' @{planId=$planId}
    & schtasks.exe /Query /TN $taskName 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) { throw '删除最后计划后测试任务仍存在' }
    Stop-Candidate
    $facts=& python (Join-Path $PSScriptRoot 'read-windows-acceptance-background-facts.py') $root | ConvertFrom-Json
    $peer=if($DataArea -eq 'WORK'){'CHAT'}else{'WORK'}
    if($LASTEXITCODE -ne 0 -or $facts.$DataArea.runCount -ne 1 -or $facts.$peer.runCount -ne 0 -or $facts.$DataArea.runs[0].status -ne 'FAILED' -or $facts.$DataArea.runs[0].safeErrorCode -ne 'SERVICE_DISABLED'){throw '实际后台运行账或原区隔离不符'}
    [pscustomobject]@{candidateSha256=$receipt.sha256;root=$root;dataArea=$DataArea;taskName=$taskName;planId=$planId;facts=$facts;runCount=$runCount;safeErrorCode=$savedPlan.lastSafeErrorCode;trigger=$(if($WaitForScheduledTrigger){'SCHEDULED_TIME_TRIGGER'}else{'TASK_SCHEDULER_RUN'});stoppedPids=@($stopped)} | ConvertTo-Json -Depth 7
} finally {
    Stop-Candidate
    $taskXml = (& schtasks.exe /Query /TN $taskName /XML 2>$null) -join "`n"
    if ($LASTEXITCODE -eq 0) {
        if (-not $root -or -not $taskXml.Contains($runningExe) -or -not $taskXml.Contains($root)) { throw '隔离任务身份不符，拒绝清理' }
        & schtasks.exe /Delete /TN $taskName /F | Out-Null
        if ($LASTEXITCODE -ne 0) { throw '隔离任务清理失败' }
    }
}
