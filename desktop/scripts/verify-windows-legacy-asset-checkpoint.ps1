param([string]$RootParent = 'D:\CodexTemp', [ValidateSet(2,3)][int[]]$Versions = @(2,3))
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
if (-not $IsWindows -and $PSVersionTable.PSEdition -eq 'Core') { throw '仅支持 Windows 原生验收。' }
$desktopRoot = Split-Path -Parent $PSScriptRoot
$receipt = Get-Content -LiteralPath (Join-Path $desktopRoot 'output/windows-acceptance/current.json') -Raw | ConvertFrom-Json
if ($receipt.identifier -ne 'com.nanzhufeng.ai.desktop.windowsacceptance' -or $receipt.sha256 -notmatch '^[A-F0-9]{64}$') { throw '验收收据无效。' }
$sourceExecutable = [IO.Path]::GetFullPath((Join-Path $desktopRoot ("output/windows-acceptance/{0}/NanfengAI-Windows-Acceptance.exe" -f $receipt.sha256)))
$runExecutable = [IO.Path]::GetFullPath((Join-Path $desktopRoot ("output/windows-acceptance-binaries/{0}/nanfeng-ai-desktop-spike.exe" -f $receipt.sha256)))
if ([IO.Path]::GetFullPath([string]$receipt.executable) -ne $sourceExecutable -or
    (Get-FileHash -LiteralPath $sourceExecutable -Algorithm SHA256).Hash -ne $receipt.sha256) { throw '候选 EXE 与收据不符。' }
$parent = [IO.Path]::GetFullPath($RootParent).TrimEnd('\')
$launcher = Join-Path $PSScriptRoot 'start-windows-acceptance.ps1'
$cdpHelper = Join-Path $PSScriptRoot 'windows-acceptance-cdp-eval.mjs'
$running = [System.Collections.Generic.List[object]]::new()
$stopped = [System.Collections.Generic.List[int]]::new()

function Assert-Root([string]$root) {
    $root = [IO.Path]::GetFullPath($root)
    if ((Split-Path -Parent $root) -ne $parent -or
        (Split-Path -Leaf $root) -notmatch '^nanfeng-ai-windows-acceptance\.[a-fA-F0-9-]{8,64}$' -or
        -not (Test-Path -LiteralPath (Join-Path $root '.nanfeng-windows-acceptance.json') -PathType Leaf)) {
        throw '只允许操作当前指定父目录下的标记合成根。'
    }
}
function New-DebugPort {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $listener.Start()
    try { return $listener.LocalEndpoint.Port } finally { $listener.Stop() }
}
function Start-Checked([string]$root = '') {
    $port = New-DebugPort
    $params = @{RootParent=$RootParent; StartupMode='normal'; Visible=$true; DebugPort=$port}
    if ($root) { Assert-Root $root; $params.Root=$root }
    $launch = & $launcher @params | ConvertFrom-Json
    Assert-Root ([string]$launch.root)
    if ($launch.sha256 -ne $receipt.sha256 -or
        [IO.Path]::GetFullPath([string]$launch.executable) -ne $runExecutable) { throw '运行副本身份不符。' }
    $running.Add($launch)
    $process = Get-CimInstance Win32_Process -Filter ("ProcessId={0}" -f $launch.pid)
    if ($process -and [IO.Path]::GetFullPath([string]$process.ExecutablePath) -ne $runExecutable) { throw '验收 PID 路径不符。' }
    return $launch
}
function Stop-Checked($launch) {
    $process = Get-CimInstance Win32_Process -Filter ("ProcessId={0}" -f $launch.pid)
    if ($process) {
        if ([IO.Path]::GetFullPath([string]$process.ExecutablePath) -ne $runExecutable -or
            (Get-FileHash -LiteralPath $runExecutable -Algorithm SHA256).Hash -ne $receipt.sha256) {
            throw 'PID 或 EXE 哈希不符，拒绝停止。'
        }
        Stop-Process -Id $launch.pid -Force -ErrorAction Stop
        $deadline = [DateTime]::UtcNow.AddSeconds(15)
        while ((Get-Process -Id $launch.pid -ErrorAction SilentlyContinue) -and [DateTime]::UtcNow -lt $deadline) { Start-Sleep -Milliseconds 100 }
        if (Get-Process -Id $launch.pid -ErrorAction SilentlyContinue) { throw '验收进程未停止。' }
        $stopped.Add([int]$launch.pid)
    }
    $running.Remove($launch) | Out-Null
}
function Wait-Ipc($launch, [int]$seconds) {
    $deadline = [DateTime]::UtcNow.AddSeconds($seconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        try {
            $probe = "(async()=>{const h={headers:{'x-nanfeng-data-area':'CHAT'}};return !!(await window.__TAURI__.core.invoke('read_desktop_local_backup_status',{},h))})()" | & node $cdpHelper $launch.debugPort 2>$null
            if ($LASTEXITCODE -eq 0 -and ($probe | ConvertFrom-Json) -eq $true) { return $true }
        } catch {}
        Start-Sleep -Milliseconds 250
    }
    return $false
}
function Sqlite-Value([string]$path, [string]$query) {
    $code = 'import sqlite3,sys,json; c=sqlite3.connect("file:"+sys.argv[1]+"?mode=ro",uri=True); print(json.dumps(c.execute(sys.argv[2]).fetchone(),ensure_ascii=True))'
    $result = & python -c $code $path $query
    if ($LASTEXITCODE -ne 0) { throw 'SQLite 回读失败。' }
    return ,($result | ConvertFrom-Json -NoEnumerate)
}
function File-Sha([string]$path) { return (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash }
$results = [System.Collections.Generic.List[object]]::new()
try {
    foreach ($version in $Versions) {
        $baseline = Start-Checked
        $root = [string]$baseline.root
        if (-not (Wait-Ipc $baseline 45)) { throw "v$version 初始原生 WebView2/IPC 未就绪。" }
        $windowTitle = (Get-Process -Id $baseline.pid -ErrorAction Stop).MainWindowTitle
        if ([string]::IsNullOrWhiteSpace($windowTitle)) { throw "v$version 初始窗口标题为空。" }
        Stop-Checked $baseline
        Assert-Root $root
        $chatDb = Join-Path $root 'workspace.sqlite3'
        $workDb = Join-Path $root 'work-area/workspace.sqlite3'
        $sharedDb = Join-Path $root 'app-configuration.sqlite3'
        foreach ($db in @($chatDb,$workDb,$sharedDb)) { if (-not (Test-Path -LiteralPath $db -PathType Leaf)) { throw '双区或共享 SQLite 缺失。' } }
        $owner = Join-Path $root 'local-backup/v1'
        $checkpointId = "checkpoint-v$version-$PID-$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())"
        $checkpoint = Join-Path $owner "recovery/$checkpointId"
        New-Item -ItemType Directory -Path $checkpoint -ErrorAction Stop | Out-Null
        $snapshot = Join-Path $checkpoint 'workspace.snapshot'
        $asset = Join-Path $root 'assets/legacy-synthetic.txt'
        New-Item -ItemType Directory -Path (Split-Path -Parent $asset) -Force | Out-Null
        $oldBytes = [Text.Encoding]::UTF8.GetBytes("legacy-old-v$version")
        [IO.File]::WriteAllBytes($asset, $oldBytes)
        # Legacy checkpoints copy the directory shape, including empty roots.
        # Deliberately omit only the synthetic attachment file for the fault.
        foreach ($pair in @(
            @('assets','attachments/v1'),
            @('packages','workspace-packages/v1'),
            @('p6k-zip-import-assets','zip-import-assets/v1'),
            @('exchange-v2/archives','workspace-exchange-archives/v2')
        )) {
            $local = Join-Path $root $pair[0]
            if (-not (Test-Path -LiteralPath $local -PathType Container)) { continue }
            $package = Join-Path (Join-Path $checkpoint 'assets') $pair[1]
            New-Item -ItemType Directory -Path $package -Force | Out-Null
            if ($pair[0] -ne 'assets') {
                foreach ($entry in Get-ChildItem -LiteralPath $local -Force) {
                    Copy-Item -LiteralPath $entry.FullName -Destination $package -Recurse -ErrorAction Stop
                }
            }
        }
        $snapshotCode = 'import sqlite3,sys; src=sqlite3.connect(sys.argv[1]); dst=sqlite3.connect(sys.argv[2]); src.backup(dst); dst.close(); src.close()'
        & python -c $snapshotCode $chatDb $snapshot
        if ($LASTEXITCODE -ne 0) { throw '合成 checkpoint SQLite 快照失败。' }
        $oldTitle = Sqlite-Value $snapshot 'SELECT title FROM workspaces ORDER BY id LIMIT 1'
        if (@($oldTitle).Count -ne 1 -or [string]::IsNullOrWhiteSpace([string]$oldTitle[0])) { throw '初始工作区标题不可用。' }
        if ($version -eq 3) {
            $manifest = @{version=3;workspaceSha256=(File-Sha $snapshot).ToLowerInvariant();usageLedgerSha256=$null} | ConvertTo-Json -Compress
            [IO.File]::WriteAllText((Join-Path $checkpoint 'checkpoint-manifest-v3.json'), $manifest, [Text.UTF8Encoding]::new($false))
        }
        $changeCode = 'import sqlite3,sys; c=sqlite3.connect(sys.argv[1]); c.execute("UPDATE workspaces SET title=? WHERE id=(SELECT id FROM workspaces ORDER BY id LIMIT 1)",(sys.argv[2],)); c.commit(); c.close()'
        $changedTitle = "synthetic-changed-v$version"
        & python -c $changeCode $chatDb $changedTitle
        if ($LASTEXITCODE -ne 0 -or (Sqlite-Value $chatDb 'SELECT title FROM workspaces ORDER BY id LIMIT 1')[0] -ne $changedTitle) { throw '合成活动主库变更失败。' }
        [IO.File]::WriteAllText($asset, "legacy-new-v$version", [Text.UTF8Encoding]::new($false))
        $statePath = Join-Path $owner 'state.json'
        $state = @{operation='INTERRUPTED';phase='SWITCHING';fingerprint=$null;checkpointId=$checkpointId;processId=$null} | ConvertTo-Json -Compress
        [IO.File]::WriteAllText($statePath, $state, [Text.UTF8Encoding]::new($false))
        $before = @{}
        foreach ($path in @($chatDb,$workDb,$sharedDb,$asset)) { $before[$path] = File-Sha $path }
        $rejected = Start-Checked $root
        $unexpectedReady = Wait-Ipc $rejected 6
        if ($unexpectedReady) { throw "v$version 缺失资产快照时业务 IPC 意外可用。" }
        foreach ($path in $before.Keys) { if ((File-Sha $path) -ne $before[$path]) { throw "v$version 拒绝恢复前活动 owner 发生变化。" } }
        $failedState = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
        if ($failedState.operation -ne 'INTERRUPTED' -or $failedState.phase -ne 'SWITCHING' -or
            -not (Test-Path -LiteralPath $snapshot -PathType Leaf)) { throw "v$version 拒绝恢复时状态或检查点被清理。" }
        Stop-Checked $rejected
        $checkpointAsset = Join-Path $checkpoint 'assets/attachments/v1/legacy-synthetic.txt'
        New-Item -ItemType Directory -Path (Split-Path -Parent $checkpointAsset) -Force | Out-Null
        [IO.File]::WriteAllBytes($checkpointAsset, $oldBytes)
        [IO.File]::WriteAllBytes($asset, $oldBytes)
        $reopened = Start-Checked $root
        if (-not (Wait-Ipc $reopened 30)) { throw "v$version 修复资产后同根 WebView2/IPC 未就绪。" }
        $restoredTitle = Sqlite-Value $chatDb 'SELECT title FROM workspaces ORDER BY id LIMIT 1'
        $recoveredState = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
        if ($restoredTitle[0] -ne $oldTitle[0] -or (File-Sha $asset) -ne (File-Sha $checkpointAsset) -or
            $recoveredState.operation -ne 'INTERRUPTED' -or $recoveredState.phase -ne 'ROLLED_BACK_INTERRUPTED') {
            throw "v$version 同根恢复业务或状态不符。"
        }
        $integrity = @{}
        foreach ($db in @($chatDb,$workDb,$sharedDb)) { $integrity[$db] = (Sqlite-Value $db 'PRAGMA integrity_check')[0] }
        if (@($integrity.Values | Where-Object { $_ -ne 'ok' }).Count -gt 0) { throw "v$version SQLite 完整性失败。" }
        Stop-Checked $reopened
        $v2LedgerRecovered = $null
        if ($version -eq 2) {
            $ledger = Join-Path $root 'usage-ledger/usage-ledger-v1.sqlite3'
            $ledgerSnapshot = Join-Path $checkpoint 'databases/desktop-usage-ledger-v1.snapshot'
            New-Item -ItemType Directory -Path (Split-Path -Parent $ledger) -Force | Out-Null
            $createLedger = 'import sqlite3,sys; c=sqlite3.connect(sys.argv[1]); c.executescript("PRAGMA user_version=1; CREATE TABLE usage_ledger_entries(entry_id TEXT PRIMARY KEY,input_tokens INTEGER NOT NULL); INSERT INTO usage_ledger_entries VALUES(''legacy-synthetic'',42);"); c.close()'
            & python -c $createLedger $ledger
            if ($LASTEXITCODE -ne 0) { throw 'v2 合成费用账本创建失败。' }
            New-Item -ItemType Directory -Path (Split-Path -Parent $ledgerSnapshot) -Force | Out-Null
            Copy-Item -LiteralPath $ledger -Destination $ledgerSnapshot -ErrorAction Stop
            $ledgerSnapshotBytes = [IO.File]::ReadAllBytes($ledgerSnapshot)
            $changeLedger = 'import sqlite3,sys; c=sqlite3.connect(sys.argv[1]); c.execute("UPDATE usage_ledger_entries SET input_tokens=99"); c.commit(); c.close()'
            & python -c $changeLedger $ledger
            if ($LASTEXITCODE -ne 0) { throw 'v2 合成活动费用账本变更失败。' }
            [IO.File]::Delete($ledgerSnapshot)
            & python -c $changeCode $chatDb 'synthetic-ledger-candidate'
            if ($LASTEXITCODE -ne 0) { throw 'v2 合成活动主库变更失败。' }
            [IO.File]::WriteAllText($statePath, $state, [Text.UTF8Encoding]::new($false))
            $ledgerBefore = @{}
            foreach ($path in @($chatDb,$workDb,$sharedDb,$asset,$ledger)) { $ledgerBefore[$path] = File-Sha $path }
            $ledgerRejected = Start-Checked $root
            if (Wait-Ipc $ledgerRejected 6) { throw 'v2 缺费用账本快照时业务 IPC 意外可用。' }
            foreach ($path in $ledgerBefore.Keys) { if ((File-Sha $path) -ne $ledgerBefore[$path]) { throw 'v2 缺费用账本快照时活动 owner 发生变化。' } }
            $failedState = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
            if ($failedState.operation -ne 'INTERRUPTED' -or $failedState.phase -ne 'SWITCHING') { throw 'v2 缺费用账本快照时状态被推进。' }
            Stop-Checked $ledgerRejected
            [IO.File]::WriteAllBytes($ledgerSnapshot, $ledgerSnapshotBytes)
            $ledgerReopened = Start-Checked $root
            if (-not (Wait-Ipc $ledgerReopened 30)) { throw 'v2 修复费用账本后同根 WebView2/IPC 未就绪。' }
            $tokens = Sqlite-Value $ledger 'SELECT input_tokens FROM usage_ledger_entries LIMIT 1'
            $ledgerTitle = Sqlite-Value $chatDb 'SELECT title FROM workspaces ORDER BY id LIMIT 1'
            $ledgerState = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
            if ($tokens[0] -ne 42 -or $ledgerTitle[0] -ne $oldTitle[0] -or
                $ledgerState.phase -ne 'ROLLED_BACK_INTERRUPTED' -or (File-Sha $asset) -ne (File-Sha $checkpointAsset)) {
                throw 'v2 修复费用账本后同根恢复内容不符。'
            }
            foreach ($db in @($chatDb,$workDb,$sharedDb,$ledger)) { if ((Sqlite-Value $db 'PRAGMA integrity_check')[0] -ne 'ok') { throw 'v2 费用账本恢复后 SQLite 完整性失败。' } }
            Stop-Checked $ledgerReopened
            $v2LedgerRecovered = $true
        }
        $results.Add([pscustomobject]@{version=$version;root=$root;candidateSha256=$receipt.sha256;windowTitle=$windowTitle;checkpointId=$checkpointId;missingAssetRejected=$true;activeFilesUnchanged=$before.Count;checkpointRetained=$true;sameRootRecovery=$true;restoredTitle=$restoredTitle[0];assetMatchesCheckpoint=$true;sqliteIntegrityOk=$integrity.Count;v2MissingLedgerRejectedAndRecovered=$v2LedgerRecovered})
    }
    [pscustomobject]@{candidateSha256=$receipt.sha256;cases=@($results);stoppedPids=@($stopped)} | ConvertTo-Json -Depth 5
} finally {
    foreach ($launch in @($running)) { Stop-Checked $launch }
}
