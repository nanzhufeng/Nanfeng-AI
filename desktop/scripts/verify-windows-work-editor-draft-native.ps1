param([string]$RootParent = 'D:\CodexTemp', [switch]$EarlyWork)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
if (-not $IsWindows) { throw '仅支持 Windows 原生验收。' }
$parent = [IO.Path]::GetFullPath($RootParent).TrimEnd('\')
if ($parent -ne 'D:\CodexTemp') { throw '仅允许 D:\CodexTemp 下的标记合成根。' }
$desktopRoot = Split-Path -Parent $PSScriptRoot
$receipt = Get-Content -LiteralPath (Join-Path $desktopRoot 'output/windows-acceptance/current.json') -Raw -Encoding UTF8 | ConvertFrom-Json
if ($receipt.identifier -ne 'com.nanzhufeng.ai.desktop.windowsacceptance' -or $receipt.sha256 -notmatch '^[A-F0-9]{64}$') { throw '隔离候选收据无效。' }
$source = [IO.Path]::GetFullPath((Join-Path $desktopRoot ("output/windows-acceptance/{0}/NanfengAI-Windows-Acceptance.exe" -f $receipt.sha256)))
$runExecutable = [IO.Path]::GetFullPath((Join-Path $desktopRoot ("output/windows-acceptance-binaries/{0}/nanfeng-ai-desktop-spike.exe" -f $receipt.sha256)))
if ([IO.Path]::GetFullPath([string]$receipt.executable) -ne $source -or (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash -ne $receipt.sha256) { throw '候选收据与 EXE 不匹配。' }
function Stop-Verified([object]$launch) {
    if (-not $launch) { return }
    $process = Get-Process -Id $launch.pid -ErrorAction SilentlyContinue
    if (-not $process) { return }
    if ([IO.Path]::GetFullPath([string]$process.Path) -ne $runExecutable -or (Get-FileHash -LiteralPath $runExecutable -Algorithm SHA256).Hash -ne $receipt.sha256) { throw 'PID 路径或哈希不符，拒绝停止。' }
    Stop-Process -Id $launch.pid -Force -ErrorAction Stop
    $deadline = [DateTime]::UtcNow.AddSeconds(15)
    while ((Get-Process -Id $launch.pid -ErrorAction SilentlyContinue) -and [DateTime]::UtcNow -lt $deadline) { Start-Sleep -Milliseconds 100 }
    if (Get-Process -Id $launch.pid -ErrorAction SilentlyContinue) { throw '受控进程未退出。' }
}
$root = ''
$workspaceId = ''
$results = @()
$pids = @()
foreach ($phase in @('create', 'reopen')) {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $listener.Start()
    try { $port = [int]$listener.LocalEndpoint.Port } finally { $listener.Stop() }
    $launch = $null
    try {
        $arguments = @{ RootParent = $parent; StartupMode = 'normal'; Visible = $true; DebugPort = $port }
        if ($root) { $arguments.Root = $root }
        $launch = & (Join-Path $PSScriptRoot 'start-windows-acceptance.ps1') @arguments | ConvertFrom-Json
        $currentRoot = [IO.Path]::GetFullPath([string]$launch.root)
        if (-not $currentRoot.StartsWith($parent + '\', [StringComparison]::OrdinalIgnoreCase) -or -not (Test-Path -LiteralPath (Join-Path $currentRoot '.nanfeng-windows-acceptance.json') -PathType Leaf)) { throw '未启动于标记合成根。' }
        if ($root -and $root -ne $currentRoot) { throw '重开时数据根改变。' }
        $root = $currentRoot
        if ($launch.sha256 -ne $receipt.sha256 -or [IO.Path]::GetFullPath([string]$launch.executable) -ne $runExecutable) { throw '运行身份不符。' }
        $pids += [int]$launch.pid
        $nodeArgs = @((Join-Path $PSScriptRoot 'verify-windows-work-editor-draft-native.mjs'), $port, $phase, $workspaceId)
        if ($EarlyWork -and $phase -eq 'create') { $nodeArgs += '--early-work' }
        $resultText = & node --max-old-space-size=2048 @nodeArgs
        if ($LASTEXITCODE -ne 0) { throw "原生编辑草稿 $phase 验收失败。" }
        $result = $resultText | ConvertFrom-Json
        if ($phase -eq 'create') { $workspaceId = [string]$result.workspaceId }
        $results += $result
    } finally {
        Stop-Verified $launch
    }
}
$integrity = & python -c 'import pathlib,sqlite3,sys,json; p=pathlib.Path(sys.argv[1]); files=sorted(p.rglob("*.sqlite3")); checks=[sqlite3.connect("file:"+str(f)+"?mode=ro",uri=True).execute("PRAGMA quick_check").fetchone()[0] for f in files]; print(json.dumps({"count":len(files),"allOk":all(x=="ok" for x in checks)}))' $root | ConvertFrom-Json
if ($LASTEXITCODE -ne 0 -or -not $integrity.allOk) { throw '合成 SQLite 完整性失败。' }
[pscustomobject]@{ candidateSha256 = $receipt.sha256; root = $root; workspaceId = $workspaceId; results = $results; sqlite = $integrity; pids = $pids } | ConvertTo-Json -Depth 6
