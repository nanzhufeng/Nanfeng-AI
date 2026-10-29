param([string]$RootParent = 'D:\CodexTemp')
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
function Stop-VerifiedLaunch($candidateLaunch) {
    $process = Get-CimInstance Win32_Process -Filter ("ProcessId={0}" -f $candidateLaunch.pid)
    if (-not $process) { return }
    if ([IO.Path]::GetFullPath([string]$process.ExecutablePath) -ne $runExecutable -or
        (Get-FileHash -LiteralPath $runExecutable -Algorithm SHA256).Hash -ne $receipt.sha256) { throw 'PID 路径或哈希不符，拒绝停止。' }
    Stop-Process -Id $candidateLaunch.pid -Force -ErrorAction Stop
    $deadline = [DateTime]::UtcNow.AddSeconds(15)
    while ((Get-Process -Id $candidateLaunch.pid -ErrorAction SilentlyContinue) -and [DateTime]::UtcNow -lt $deadline) { Start-Sleep -Milliseconds 100 }
    if (Get-Process -Id $candidateLaunch.pid -ErrorAction SilentlyContinue) { throw '验收进程未停止。' }
}
$launch = $null
try {
    $launch = & (Join-Path $PSScriptRoot 'start-windows-acceptance.ps1') -RootParent $RootParent -StartupMode normal -Visible -DebugPort $port | ConvertFrom-Json
    $root = [IO.Path]::GetFullPath([string]$launch.root)
    if (-not $root.StartsWith($parent + '\', [StringComparison]::OrdinalIgnoreCase) -or
        -not (Test-Path -LiteralPath (Join-Path $root '.nanfeng-windows-acceptance.json') -PathType Leaf)) { throw '运行根不是隔离合成根。' }
    if ($launch.sha256 -ne $receipt.sha256 -or [IO.Path]::GetFullPath([string]$launch.executable) -ne $runExecutable) { throw '运行副本身份不符。' }
    $process = Get-CimInstance Win32_Process -Filter ("ProcessId={0}" -f $launch.pid)
    if (-not $process -or [IO.Path]::GetFullPath([string]$process.ExecutablePath) -ne $runExecutable) { throw '运行进程路径不符。' }
    $result = & node --max-old-space-size=2048 (Join-Path $PSScriptRoot 'verify-windows-conversation-purge-commit-native.mjs') $port (Join-Path $root 'recycle-purge-confirm.png') $root
    if ($LASTEXITCODE -ne 0) { throw '原生会话永久删除 GUI 提交验收失败。' }
    $parsed = $result | ConvertFrom-Json
    if (-not $parsed.singleGuiCommitted -or -not $parsed.bulkGuiCommitted -or -not $parsed.sharedAssetRetainedThenRemoved -or -not $parsed.coldReloadEmpty -or -not $parsed.purgeUndoRedoRejected) { throw 'GUI 提交结果不完整。' }
    $database = Join-Path $root 'workspace.sqlite3'
    $databaseCheck = & python -c "import json,sqlite3,sys; c=sqlite3.connect('file:'+sys.argv[1]+'?mode=ro',uri=True); w=sys.argv[2]; rows=c.execute('SELECT entity,action,object_id,before_exchange_json,after_exchange_json,undone FROM domain_intents WHERE workspace_id=?',(w,)).fetchall(); print(json.dumps({'integrity':c.execute('PRAGMA integrity_check').fetchone()[0],'retiredCount':len(rows),'allSanitized':all(r==('purged','purged','purged','{}','{}',1) for r in rows)}))" $database $parsed.workspaceId | ConvertFrom-Json
    if ($LASTEXITCODE -ne 0 -or $databaseCheck.integrity -ne 'ok' -or $databaseCheck.retiredCount -lt 6 -or -not $databaseCheck.allSanitized) { throw 'CHAT SQLite 快照隔离或完整性失败。' }
    $firstPid = [int]$launch.pid
    Stop-VerifiedLaunch $launch
    $launch = $null
    $listener.Start()
    try { $reopenPort = $listener.LocalEndpoint.Port } finally { $listener.Stop() }
    $launch = & (Join-Path $PSScriptRoot 'start-windows-acceptance.ps1') -Root $root -RootParent $RootParent -StartupMode normal -Visible -DebugPort $reopenPort | ConvertFrom-Json
    if ([IO.Path]::GetFullPath([string]$launch.root) -ne $root -or $launch.sha256 -ne $receipt.sha256 -or
        [IO.Path]::GetFullPath([string]$launch.executable) -ne $runExecutable) { throw '同根重启身份不符。' }
    $process = Get-CimInstance Win32_Process -Filter ("ProcessId={0}" -f $launch.pid)
    if (-not $process -or [IO.Path]::GetFullPath([string]$process.ExecutablePath) -ne $runExecutable) { throw '同根重启进程路径不符。' }
    $reopenResult = & node --max-old-space-size=2048 (Join-Path $PSScriptRoot 'verify-windows-conversation-purge-reopen-native.mjs') $reopenPort $root $parsed.workspaceId $parsed.conversationIds[0] $parsed.conversationIds[1] $parsed.attachmentSha256
    if ($LASTEXITCODE -ne 0) { throw '同根进程重启回读失败。' }
    $reopened = $reopenResult | ConvertFrom-Json
    if (-not $reopened.purgeUndoRedoRejected) { throw '同根重启后撤销重做保护缺证据。' }
    [pscustomobject]@{ candidateSha256=$receipt.sha256; root=$root; result=$parsed; sqlite=$databaseCheck; reopen=$reopened; pids=@($firstPid,[int]$launch.pid) } | ConvertTo-Json -Depth 5
} finally {
    if ($launch) { Stop-VerifiedLaunch $launch }
}
