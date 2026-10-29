param([string]$RootParent = 'D:\CodexTemp', [switch]$Dst)
$ErrorActionPreference = 'Stop'
$desktopRoot = Split-Path -Parent $PSScriptRoot
$receipt = Get-Content -LiteralPath (Join-Path $desktopRoot 'output/windows-acceptance/current.json') -Raw | ConvertFrom-Json
if ($receipt.identifier -ne 'com.nanzhufeng.ai.desktop.windowsacceptance') { throw '候选身份无效' }
$sourceExe = [IO.Path]::GetFullPath((Join-Path $desktopRoot ("output/windows-acceptance/{0}/NanfengAI-Windows-Acceptance.exe" -f $receipt.sha256)))
$runningExe = [IO.Path]::GetFullPath((Join-Path $desktopRoot ("output/windows-acceptance-binaries/{0}/nanfeng-ai-desktop-spike.exe" -f $receipt.sha256)))
if ([IO.Path]::GetFullPath([string]$receipt.executable) -ne $sourceExe -or (Get-FileHash -LiteralPath $sourceExe -Algorithm SHA256).Hash -ne $receipt.sha256) { throw '候选哈希无效' }
$launcher = Join-Path $PSScriptRoot 'start-windows-acceptance.ps1'
$cdpHelper = Join-Path $PSScriptRoot 'windows-acceptance-cdp-eval.mjs'
$launch = $null
$root = ''
$port = 0
$stopped = [System.Collections.Generic.List[int]]::new()

function New-Port {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $listener.Start()
    try { return [int]$listener.LocalEndpoint.Port } finally { $listener.Stop() }
}
function Start-Candidate([bool]$reopen) {
    $script:port = New-Port
    $args = @{RootParent=$RootParent; StartupMode='diagnostic'; Visible=$true; DebugPort=$port}
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
function Invoke-Cdp([string]$expression) {
    $raw = $expression | & node --max-old-space-size=2048 $cdpHelper $port 2>$null
    if ($LASTEXITCODE -ne 0) { throw 'CDP 调用失败' }
    return ($raw | ConvertFrom-Json)
}
function Invoke-Native([string]$command, [object]$arguments) {
    $commandJson = ConvertTo-Json -InputObject $command -Compress
    $argsJson = ConvertTo-Json -InputObject $arguments -Compress -Depth 12
    $expression = "window.__TAURI__.core.invoke($commandJson,$argsJson,{headers:{'x-nanfeng-data-area':'CHAT'}}).catch(error=>({__error:String(error)}))"
    $value = Invoke-Cdp $expression
    if ($value.__error) { throw ("{0}: {1}" -f $command, $value.__error) }
    return $value
}

try {
    Start-Candidate $false
    if ($Dst) {
        $workspace = Invoke-Native 'ensure_desktop_local_workspace' @{}
        $cases = @(
            @{title='合成秋季重复小时';kind='ONCE';anchor='2026-11-01T01:30';expected=([DateTimeOffset]::Parse('2026-11-01T05:30:00Z')).ToUnixTimeMilliseconds()},
            @{title='合成春季不存在小时';kind='DAILY';anchor='2027-03-14T02:30';expected=([DateTimeOffset]::Parse('2027-03-14T07:00:00Z')).ToUnixTimeMilliseconds()}
        )
        $planIds = [System.Collections.Generic.List[string]]::new()
        foreach ($case in $cases) {
            $draft = Invoke-Native 'create_desktop_manual_reminder_draft' @{args=@{workspaceId=$workspace.summary.id;conversationId=$null;timezoneId='America/New_York'}}
            $draftId = [string](($draft.drafts | Where-Object { $_.status -eq 'PENDING_REVIEW' } | Select-Object -First 1).draftId)
            if (-not $draftId) { throw 'DST 手工草案未创建' }
            $confirmed = Invoke-Native 'confirm_desktop_reminder_draft' @{args=@{draftId=$draftId;title=$case.title;instruction='只核对合成时间';scheduleKind=$case.kind;anchorLocal=$case.anchor;timezoneId='America/New_York';missedPolicy='RUN_ONCE'}}
            $plan = $confirmed.plans | Where-Object { $_.sourceDraftId -eq $draftId } | Select-Object -First 1
            if (-not $plan -or [int64]$plan.nextRunAtMs -ne [int64]$case.expected) { throw ('DST 计划时间错误：' + $case.title) }
            $planIds.Add([string]$plan.planId)
        }
        Stop-Candidate
        Start-Candidate $true
        $reopened = Invoke-Native 'read_desktop_reminders' @{}
        for ($i = 0; $i -lt $cases.Count; $i++) {
            $saved = $reopened.plans | Where-Object { $_.planId -eq $planIds[$i] } | Select-Object -First 1
            if (-not $saved -or [int64]$saved.nextRunAtMs -ne [int64]$cases[$i].expected) { throw ('DST 冷启时间错误：' + $cases[$i].title) }
        }
        Stop-Candidate
        [pscustomobject]@{candidateSha256=$receipt.sha256;root=$root;timezoneId='America/New_York';cases=$cases;planIds=@($planIds);stoppedPids=@($stopped)} | ConvertTo-Json -Depth 5
        return
    }
    $workspace = Invoke-Native 'ensure_desktop_local_workspace' @{}
    $draft = Invoke-Native 'create_desktop_manual_reminder_draft' @{args=@{workspaceId=$workspace.summary.id;conversationId=$null;timezoneId='Asia/Shanghai'}}
    $draftId = [string]$draft.drafts[0].draftId
    if (-not $draftId) { throw '手工提醒草案未创建' }
    $local = [DateTime]::UtcNow.AddHours(8).AddMinutes(-7)
    $anchorLocal = $local.ToString('yyyy-MM-ddTHH:mm', [Globalization.CultureInfo]::InvariantCulture)
    $anchorDate = [DateTime]::ParseExact($anchorLocal, 'yyyy-MM-ddTHH:mm', [Globalization.CultureInfo]::InvariantCulture)
    $expectedNextMs = [DateTimeOffset]::new($anchorDate, [TimeSpan]::FromHours(8)).ToUnixTimeMilliseconds() + 3600000
    $confirmed = Invoke-Native 'confirm_desktop_reminder_draft' @{args=@{draftId=$draftId;title='合成每小时提醒';instruction='只核对合成时间';scheduleKind='HOURLY';anchorLocal=$anchorLocal;timezoneId='Asia/Shanghai';missedPolicy='SKIP'}}
    $plan = $confirmed.plans | Where-Object { $_.sourceDraftId -eq $draftId } | Select-Object -First 1
    if (-not $plan -or [int64]$plan.nextRunAtMs -ne $expectedNextMs) { throw '每小时下次触发未保持锚点分钟' }
    $planId = [string]$plan.planId
    Stop-Candidate
    Start-Candidate $true
    $reopened = Invoke-Native 'read_desktop_reminders' @{}
    $saved = $reopened.plans | Where-Object { $_.planId -eq $planId } | Select-Object -First 1
    if (-not $saved -or [int64]$saved.nextRunAtMs -ne $expectedNextMs) { throw '同根冷启计划时间不一致' }
    Stop-Candidate
    [pscustomobject]@{candidateSha256=$receipt.sha256;root=$root;startupMode='diagnostic';planId=$planId;anchorLocal=$anchorLocal;nextRunAtMs=$expectedNextMs;stoppedPids=@($stopped)} | ConvertTo-Json -Depth 5
} finally { Stop-Candidate }
