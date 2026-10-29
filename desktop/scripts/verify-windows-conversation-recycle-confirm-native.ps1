param([string]$RootParent = 'D:\CodexTemp', [switch]$FavoriteLifecycle, [switch]$StartupSettings)
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
$listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
$listener.Start()
try { $port = $listener.LocalEndpoint.Port } finally { $listener.Stop() }
$launch = $null
try {
    $launch = & (Join-Path $PSScriptRoot 'start-windows-acceptance.ps1') -RootParent $RootParent -StartupMode normal -Visible -DebugPort $port -DraftReadPause:$StartupSettings | ConvertFrom-Json
    $root = [IO.Path]::GetFullPath([string]$launch.root)
    if (-not $root.StartsWith($parent + '\', [StringComparison]::OrdinalIgnoreCase) -or
        -not (Test-Path -LiteralPath (Join-Path $root '.nanfeng-windows-acceptance.json') -PathType Leaf)) { throw '运行根不是隔离合成根。' }
    if ($launch.sha256 -ne $receipt.sha256 -or [IO.Path]::GetFullPath([string]$launch.executable) -ne $runExecutable) { throw '运行副本身份不符。' }
    $process = Get-CimInstance Win32_Process -Filter ("ProcessId={0}" -f $launch.pid)
    if (-not $process -or [IO.Path]::GetFullPath([string]$process.ExecutablePath) -ne $runExecutable) { throw '运行进程路径不符。' }
    $nodeArguments = @($port, (Join-Path $root 'recycle-confirm.png'))
    if ($FavoriteLifecycle) { $nodeArguments += '--favorite-lifecycle' }
    if ($StartupSettings) { $nodeArguments += '--startup-settings' }
    $result = & node --max-old-space-size=2048 (Join-Path $PSScriptRoot 'verify-windows-conversation-recycle-confirm-native.mjs') @nodeArguments
    if ($LASTEXITCODE -ne 0) { throw '原生会话回收站确认/取消验收失败。' }
    [pscustomobject]@{ candidateSha256=$receipt.sha256; root=$root; result=($result | ConvertFrom-Json); pid=[int]$launch.pid } | ConvertTo-Json -Depth 5
} finally {
    if ($launch) {
        $process = Get-CimInstance Win32_Process -Filter ("ProcessId={0}" -f $launch.pid)
        if ($process) {
            if ([IO.Path]::GetFullPath([string]$process.ExecutablePath) -ne $runExecutable -or
                (Get-FileHash -LiteralPath $runExecutable -Algorithm SHA256).Hash -ne $receipt.sha256) { throw 'PID 路径或哈希不符，拒绝停止。' }
            Stop-Process -Id $launch.pid -Force -ErrorAction Stop
            $deadline = [DateTime]::UtcNow.AddSeconds(15)
            while ((Get-Process -Id $launch.pid -ErrorAction SilentlyContinue) -and [DateTime]::UtcNow -lt $deadline) { Start-Sleep -Milliseconds 100 }
            if (Get-Process -Id $launch.pid -ErrorAction SilentlyContinue) { throw '验收进程未停止。' }
        }
    }
}
