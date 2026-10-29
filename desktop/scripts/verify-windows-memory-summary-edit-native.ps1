param([string]$RootParent = 'D:\CodexTemp', [switch]$RevisionConflict)
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
$parent = [IO.Path]::GetFullPath($RootParent).TrimEnd('\')

function Stop-VerifiedAcceptance([object]$Launch) {
    if (-not $Launch) { return }
    $process = Get-Process -Id $Launch.pid -ErrorAction SilentlyContinue
    if (-not $process) { return }
    if ([IO.Path]::GetFullPath([string]$process.Path) -ne $runExecutable -or
        (Get-FileHash -LiteralPath $runExecutable -Algorithm SHA256).Hash -ne $receipt.sha256) { throw 'PID 路径或哈希不符，拒绝停止。' }
    Stop-Process -Id $Launch.pid -Force -ErrorAction Stop
    $deadline = [DateTime]::UtcNow.AddSeconds(15)
    while ((Get-Process -Id $Launch.pid -ErrorAction SilentlyContinue) -and [DateTime]::UtcNow -lt $deadline) { Start-Sleep -Milliseconds 100 }
    if (Get-Process -Id $Launch.pid -ErrorAction SilentlyContinue) { throw '验收进程未停止。' }
}

$root = ''
$workspaceId = ''
$results = @()
foreach ($phase in @('edit', 'reopen')) {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $listener.Start()
    try { $port = $listener.LocalEndpoint.Port } finally { $listener.Stop() }
    $launch = $null
    try {
        $launchArgs = @{ RootParent = $RootParent; StartupMode = 'normal'; Visible = $true; DebugPort = $port }
        if ($root) { $launchArgs.Root = $root }
        $launch = & (Join-Path $PSScriptRoot 'start-windows-acceptance.ps1') @launchArgs | ConvertFrom-Json
        $currentRoot = [IO.Path]::GetFullPath([string]$launch.root)
        if (-not $currentRoot.StartsWith($parent + '\', [StringComparison]::OrdinalIgnoreCase) -or
            -not (Test-Path -LiteralPath (Join-Path $currentRoot '.nanfeng-windows-acceptance.json') -PathType Leaf)) { throw '运行根不是隔离合成根。' }
        if ($root -and $currentRoot -ne $root) { throw '同根重启切换了数据根。' }
        $root = $currentRoot
        if ($launch.sha256 -ne $receipt.sha256 -or [IO.Path]::GetFullPath([string]$launch.executable) -ne $runExecutable) { throw '运行副本身份不符。' }
        $process = Get-Process -Id $launch.pid -ErrorAction SilentlyContinue
        if (-not $process -or [IO.Path]::GetFullPath([string]$process.Path) -ne $runExecutable) { throw '运行进程路径不符。' }
        $nodeArgs = @('--max-old-space-size=2048', (Join-Path $PSScriptRoot 'verify-windows-memory-summary-edit-native.mjs'), $port, $phase, $workspaceId)
        if ($RevisionConflict) { $nodeArgs += '--revision-conflict' }
        $resultText = & node @nodeArgs
        if ($LASTEXITCODE -ne 0) { throw "原生记忆编辑 $phase 验收失败。" }
        $result = $resultText | ConvertFrom-Json
        if ($phase -eq 'edit') { $workspaceId = [string]$result.workspaceId }
        $results += $result
    } finally {
        Stop-VerifiedAcceptance $launch
    }
}
[pscustomobject]@{ candidateSha256 = $receipt.sha256; root = $root; results = $results } | ConvertTo-Json -Depth 5
