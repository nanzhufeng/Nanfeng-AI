param([string]$RootParent = 'D:\CodexTemp', [switch]$OverflowHistory, [switch]$VerifyOpenRouterRegistry,
    [ValidateSet('ordinary','default','complex')][string]$AutoTask = 'ordinary')
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
if (-not $IsWindows -and $PSVersionTable.PSEdition -eq 'Core') { throw '仅支持 Windows 原生验收。' }
$desktopRoot = Split-Path -Parent $PSScriptRoot
$receipt = Get-Content -LiteralPath (Join-Path $desktopRoot 'output/windows-acceptance/current.json') -Raw | ConvertFrom-Json
if ($receipt.identifier -ne 'com.nanzhufeng.ai.desktop.windowsacceptance' -or $receipt.sha256 -notmatch '^[A-F0-9]{64}$') { throw '隔离候选收据无效。' }
$sourceExecutable = [IO.Path]::GetFullPath((Join-Path $desktopRoot ("output/windows-acceptance/{0}/NanfengAI-Windows-Acceptance.exe" -f $receipt.sha256)))
$runExecutable = [IO.Path]::GetFullPath((Join-Path $desktopRoot ("output/windows-acceptance-binaries/{0}/nanfeng-ai-desktop-spike.exe" -f $receipt.sha256)))
if ([IO.Path]::GetFullPath([string]$receipt.executable) -ne $sourceExecutable -or
    (Get-FileHash -LiteralPath $sourceExecutable -Algorithm SHA256).Hash -ne $receipt.sha256) { throw '候选收据与 EXE 不匹配。' }
$launcher = Join-Path $PSScriptRoot 'start-windows-acceptance.ps1'
$verifier = Join-Path $PSScriptRoot 'verify-windows-answer-information-native.mjs'
$overflowSeeder = Join-Path $PSScriptRoot 'seed-answer-information-overflow.py'
$running = [System.Collections.Generic.List[object]]::new()
$stopped = [System.Collections.Generic.List[int]]::new()
$root = ''

function New-LoopbackPort {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $listener.Start()
    try { return $listener.LocalEndpoint.Port } finally { $listener.Stop() }
}
function Start-Acceptance([int]$providerPort) {
    $debugPort = New-LoopbackPort
    $params = @{ RootParent=$RootParent; StartupMode='normal'; Visible=$true; DebugPort=$debugPort; OrdinaryChatTestEndpoint="http://127.0.0.1:$providerPort/" }
    if ($root) { $params.Root = $root }
    $launch = & $launcher @params | ConvertFrom-Json
    if (-not $root) { $script:root = [string]$launch.root }
    if ($launch.root -ne $root -or $launch.sha256 -ne $receipt.sha256 -or
        [IO.Path]::GetFullPath([string]$launch.executable) -ne $runExecutable) { throw '运行副本身份或数据根不匹配。' }
    $running.Add($launch)
    $process = Get-CimInstance Win32_Process -Filter ("ProcessId={0}" -f $launch.pid)
    if (-not $process -or [IO.Path]::GetFullPath([string]$process.ExecutablePath) -ne $runExecutable) { throw '隔离进程路径不匹配。' }
    return $launch
}
function Stop-Acceptance($launch) {
    $process = Get-CimInstance Win32_Process -Filter ("ProcessId={0}" -f $launch.pid)
    if (-not $process) { $running.Remove($launch) | Out-Null; return }
    if ([IO.Path]::GetFullPath([string]$process.ExecutablePath) -ne $runExecutable -or
        (Get-FileHash -LiteralPath $runExecutable -Algorithm SHA256).Hash -ne $receipt.sha256) { throw '拒绝停止身份不符的进程。' }
    Stop-Process -Id $launch.pid -Force
    $deadline = [DateTime]::UtcNow.AddSeconds(15)
    while ((Get-Process -Id $launch.pid -ErrorAction SilentlyContinue) -and [DateTime]::UtcNow -lt $deadline) { Start-Sleep -Milliseconds 100 }
    if (Get-Process -Id $launch.pid -ErrorAction SilentlyContinue) { throw '验收进程未停止。' }
    $running.Remove($launch) | Out-Null
    $stopped.Add([int]$launch.pid)
}

try {
    $providerPort = New-LoopbackPort
    $first = Start-Acceptance $providerPort
    $evidence = Join-Path $root 'answer-info-send.json'
    $registryFlag = if ($VerifyOpenRouterRegistry) { 'registry' } else { 'none' }
    & node $verifier send $first.debugPort $providerPort $evidence $evidence 'ordinary' $registryFlag $AutoTask
    if ($LASTEXITCODE -ne 0) { throw '原生发送与回答信息验收失败。' }
    Stop-Acceptance $first

    if ($OverflowHistory) {
        $sendEvidence = Get-Content -LiteralPath $evidence -Raw | ConvertFrom-Json
        & python $overflowSeeder $RootParent $root $sendEvidence.assistantMessageId
        if ($LASTEXITCODE -ne 0) { throw '合成历史溢出夹具失败。' }
    }

    $second = Start-Acceptance $providerPort
    $reopenEvidence = Join-Path $root 'answer-info-reopen.json'
    $overflowFlag = if ($OverflowHistory) { 'overflow' } else { 'ordinary' }
    & node $verifier reopen $second.debugPort $providerPort $reopenEvidence $evidence $overflowFlag $registryFlag $AutoTask
    if ($LASTEXITCODE -ne 0) { throw '同根冷启回答信息验收失败。' }
    Stop-Acceptance $second

    $send = Get-Content -LiteralPath $evidence -Raw | ConvertFrom-Json
    $reopen = Get-Content -LiteralPath $reopenEvidence -Raw | ConvertFrom-Json
    [pscustomobject]@{
        candidateSha256 = $receipt.sha256
        root = $root
        send = $send
        reopen = $reopen
        stoppedPids = @($stopped)
    } | ConvertTo-Json -Depth 8
} finally {
    foreach ($launch in @($running)) { Stop-Acceptance $launch }
}
