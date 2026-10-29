param(
    [Parameter(Mandatory = $true)][string]$SourceCheckpoint,
    [string]$RootParent = 'D:\CodexTemp',
    [switch]$ManifestV3
)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
if (-not $IsWindows -and $PSVersionTable.PSEdition -eq 'Core') { throw '仅支持 Windows 原生验收。' }
$desktopRoot = Split-Path -Parent $PSScriptRoot
$receipt = Get-Content -LiteralPath (Join-Path $desktopRoot 'output/windows-acceptance/current.json') -Raw | ConvertFrom-Json
if ($receipt.identifier -ne 'com.nanzhufeng.ai.desktop.windowsacceptance' -or $receipt.sha256 -notmatch '^[A-F0-9]{64}$') { throw '验收收据身份无效。' }
$sourceExecutable = [IO.Path]::GetFullPath((Join-Path $desktopRoot ("output/windows-acceptance/{0}/NanfengAI-Windows-Acceptance.exe" -f $receipt.sha256)))
$runExecutable = [IO.Path]::GetFullPath((Join-Path $desktopRoot ("output/windows-acceptance-binaries/{0}/nanfeng-ai-desktop-spike.exe" -f $receipt.sha256)))
if ([IO.Path]::GetFullPath([string]$receipt.executable) -ne $sourceExecutable -or (Get-FileHash -LiteralPath $sourceExecutable -Algorithm SHA256).Hash -ne $receipt.sha256) { throw '验收 EXE 与收据不符。' }
$parent = [IO.Path]::GetFullPath($RootParent).TrimEnd('\')
$source = [IO.Path]::GetFullPath($SourceCheckpoint)
$sourceRoot = [IO.DirectoryInfo]::new($source).Parent.Parent.Parent.Parent.Parent.FullName
if (-not $sourceRoot.StartsWith($parent + '\', [StringComparison]::OrdinalIgnoreCase) -or
    -not (Test-Path -LiteralPath (Join-Path $sourceRoot '.nanfeng-windows-acceptance.json') -PathType Leaf) -or
    -not (Test-Path -LiteralPath (Join-Path $source 'workspace.snapshot') -PathType Leaf) -or
    -not (Test-Path -LiteralPath (Join-Path $source 'databases/desktop-usage-ledger-v1.snapshot') -PathType Leaf)) {
    throw '源检查点必须属于指定父目录下已标记的合成根，且包含主库与费用账本快照。'
}
$launcher = Join-Path $PSScriptRoot 'start-windows-acceptance.ps1'
$cdpHelper = Join-Path $PSScriptRoot 'windows-acceptance-cdp-eval.mjs'
$running = [System.Collections.Generic.List[object]]::new()
$stopped = [System.Collections.Generic.List[int]]::new()
$root = ''
function Stop-Checked($launch) {
    $process = Get-CimInstance Win32_Process -Filter ("ProcessId={0}" -f $launch.pid)
    if (-not $process) { $running.Remove($launch) | Out-Null; return }
    if ([IO.Path]::GetFullPath([string]$process.ExecutablePath) -ne $runExecutable -or
        (Get-FileHash -LiteralPath $runExecutable -Algorithm SHA256).Hash -ne $receipt.sha256) { throw 'PID 路径或哈希不符，拒绝停止。' }
    Stop-Process -Id $launch.pid -Force -ErrorAction Stop
    $deadline = [DateTime]::UtcNow.AddSeconds(15)
    while ((Get-Process -Id $launch.pid -ErrorAction SilentlyContinue) -and [DateTime]::UtcNow -lt $deadline) { Start-Sleep -Milliseconds 100 }
    if (Get-Process -Id $launch.pid -ErrorAction SilentlyContinue) { throw '验收进程未停止。' }
    $running.Remove($launch) | Out-Null
    $stopped.Add([int]$launch.pid)
}
function New-DebugPort {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $listener.Start()
    try { return $listener.LocalEndpoint.Port } finally { $listener.Stop() }
}
function Start-Checked([bool]$withDebug) {
    $port = if ($withDebug) { New-DebugPort } else { 0 }
    $params = @{RootParent=$RootParent; StartupMode='normal'; Visible=$true; DebugPort=$port}
    if ($root) { $params.Root=$root }
    $launch = & $launcher @params | ConvertFrom-Json
    if (-not $root) { $script:root = [string]$launch.root }
    $running.Add($launch)
    if ($launch.root -ne $root -or $launch.sha256 -ne $receipt.sha256 -or
        [IO.Path]::GetFullPath([string]$launch.executable) -ne $runExecutable) { throw '运行副本身份不符。' }
    $process = Get-CimInstance Win32_Process -Filter ("ProcessId={0}" -f $launch.pid)
    if (-not $process -or [IO.Path]::GetFullPath([string]$process.ExecutablePath) -ne $runExecutable) { throw '验收进程路径不符。' }
    return $launch
}
function File-Sha([string]$path) { return (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash }
function Sqlite-Row([string]$path, [string]$sql) {
    $code = 'import sqlite3,sys,json; c=sqlite3.connect("file:"+sys.argv[1]+"?mode=ro",uri=True); print(json.dumps(c.execute(sys.argv[2]).fetchone()))'
    $row = & python -c $code $path $sql | ConvertFrom-Json -NoEnumerate
    if ($LASTEXITCODE -ne 0) { throw 'SQLite 合成回读失败。' }
    return ,$row
}
function Sqlite-Rows([string]$path, [string]$sql) {
    $code = 'import sqlite3,sys,json; c=sqlite3.connect("file:"+sys.argv[1]+"?mode=ro",uri=True); print(json.dumps(c.execute(sys.argv[2]).fetchall(),ensure_ascii=False,sort_keys=True))'
    $rows = & python -c $code $path $sql
    if ($LASTEXITCODE -ne 0) { throw 'SQLite 业务行回读失败。' }
    return [string]$rows
}
try {
    $baseline = Start-Checked $false
    Start-Sleep -Seconds 2
    Stop-Checked $baseline
    $marker = Join-Path $root '.nanfeng-windows-acceptance.json'
    if (-not $root.StartsWith($parent + '\', [StringComparison]::OrdinalIgnoreCase) -or -not (Test-Path -LiteralPath $marker -PathType Leaf)) { throw '新建根不在受控合成目录。' }
    $work = Join-Path $root 'work-area'
    $workDatabase = Join-Path $work 'workspace.sqlite3'
    $chatDatabase = Join-Path $root 'workspace.sqlite3'
    $sharedDatabase = Join-Path $root 'app-configuration.sqlite3'
    foreach ($path in @($workDatabase,$chatDatabase,$sharedDatabase)) { if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw '双区或共享 SQLite 未建立。' } }
    $ledger = Join-Path $work 'usage-ledger/usage-ledger-v1.sqlite3'
    New-Item -ItemType Directory -Path (Split-Path -Parent $ledger) -Force | Out-Null
    $code = @'
import sqlite3,sys
c=sqlite3.connect(sys.argv[1])
c.executescript("CREATE TABLE usage_ledger_entries(entry_id TEXT PRIMARY KEY,input_tokens INTEGER NOT NULL); INSERT INTO usage_ledger_entries VALUES('current-synthetic',100); PRAGMA user_version=1;")
c.commit()
c.close()
'@
    & python -c $code $ledger
    if ($LASTEXITCODE -ne 0) { throw '合成账本建立失败。' }
    $asset = Join-Path $work 'assets/current-synthetic.txt'
    New-Item -ItemType Directory -Path (Split-Path -Parent $asset) -Force | Out-Null
    [IO.File]::WriteAllText($asset, 'current synthetic asset')
    $checkpointId = if ($ManifestV3) { "checkpoint-v3-$PID-$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())" } else { Split-Path -Leaf $source }
    $checkpoint = Join-Path $work ("local-backup/v1/recovery/{0}" -f $checkpointId)
    New-Item -ItemType Directory -Path (Split-Path -Parent $checkpoint) -Force | Out-Null
    Copy-Item -LiteralPath $source -Destination $checkpoint -Recurse -ErrorAction Stop
    $manifestPath = Join-Path $checkpoint 'checkpoint-manifest-v3.json'
    if ($ManifestV3) {
        $manifest = @{version=3;workspaceSha256=(File-Sha (Join-Path $checkpoint 'workspace.snapshot')).ToLowerInvariant();usageLedgerSha256=(File-Sha (Join-Path $checkpoint 'databases/desktop-usage-ledger-v1.snapshot')).ToLowerInvariant()} | ConvertTo-Json -Compress
        [IO.File]::WriteAllText($manifestPath, $manifest, [Text.UTF8Encoding]::new($false))
    }
    $statePath = Join-Path $work 'local-backup/v1/state.json'
    $state = @{operation='FAILED';phase='ROLLBACK_FAILED';fingerprint=$null;checkpointId=$checkpointId;processId=$null} | ConvertTo-Json -Compress
    [IO.File]::WriteAllText($statePath, $state, [Text.UTF8Encoding]::new($false))
    $ledgerSnapshot = Join-Path $checkpoint 'databases/desktop-usage-ledger-v1.snapshot'
    $healthyLedger = [IO.File]::ReadAllBytes($ledgerSnapshot)
    $expectedTitle = Sqlite-Row (Join-Path $checkpoint 'workspace.snapshot') 'SELECT title FROM workspaces LIMIT 1'
    $expectedUsage = Sqlite-Row $ledgerSnapshot 'SELECT entry_id,input_tokens FROM usage_ledger_entries LIMIT 1'
    $before = @{}
    foreach ($path in @($chatDatabase,$workDatabase,$sharedDatabase,$ledger,$asset)) { $before[$path] = File-Sha $path }
    $chatRowsBefore = Sqlite-Rows $chatDatabase 'SELECT * FROM workspaces ORDER BY id'
    $sharedRowsBefore = Sqlite-Rows $sharedDatabase 'SELECT * FROM desktop_provider_settings ORDER BY provider_id'
    if ($ManifestV3) {
        $savedManifest = [IO.File]::ReadAllBytes($manifestPath)
        [IO.File]::Delete($manifestPath)
        $missingManifest = Start-Checked $true
        Start-Sleep -Seconds 3
        $manifestProbe = "(async()=>{const h={headers:{'x-nanfeng-data-area':'WORK'}};return await window.__TAURI__.core.invoke('read_desktop_local_backup_status',{},h)})()" | & node $cdpHelper $missingManifest.debugPort 2>$null
        if ($LASTEXITCODE -eq 0) { throw '缺失 v3 清单时业务 WebView2 仍可读取备份状态。' }
        foreach ($path in $before.Keys) { if ((File-Sha $path) -ne $before[$path]) { throw '缺失 v3 清单期间活动文件发生变化。' } }
        if ((Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json).phase -ne 'ROLLBACK_FAILED') { throw '缺失 v3 清单误推进回滚状态。' }
        Stop-Checked $missingManifest
        [IO.File]::WriteAllBytes($manifestPath, $savedManifest)

        [IO.File]::Delete($ledgerSnapshot)
        $missingLedger = Start-Checked $true
        Start-Sleep -Seconds 3
        $ledgerProbe = "(async()=>{const h={headers:{'x-nanfeng-data-area':'WORK'}};return await window.__TAURI__.core.invoke('read_desktop_local_backup_status',{},h)})()" | & node $cdpHelper $missingLedger.debugPort 2>$null
        if ($LASTEXITCODE -eq 0) { throw '缺失 v3 账本快照时业务 WebView2 仍可读取备份状态。' }
        foreach ($path in $before.Keys) { if ((File-Sha $path) -ne $before[$path]) { throw '缺失 v3 账本快照期间活动文件发生变化。' } }
        if ((Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json).phase -ne 'ROLLBACK_FAILED') { throw '缺失 v3 账本快照误推进回滚状态。' }
        Stop-Checked $missingLedger
        [IO.File]::WriteAllBytes($ledgerSnapshot, $healthyLedger)
    }
    [IO.File]::WriteAllBytes($ledgerSnapshot, [Text.Encoding]::UTF8.GetBytes('damaged synthetic ledger snapshot'))
    $failed = Start-Checked $true
    Start-Sleep -Seconds 3
    $probe = "(async()=>{const h={headers:{'x-nanfeng-data-area':'WORK'}};return await window.__TAURI__.core.invoke('read_desktop_local_backup_status',{},h)})()" | & node $cdpHelper $failed.debugPort 2>$null
    if ($LASTEXITCODE -eq 0) { throw '损坏费用账本快照时业务 WebView2 仍可读取备份状态。' }
    foreach ($path in $before.Keys) { if ((File-Sha $path) -ne $before[$path]) { throw '损坏账本快照期间活动文件发生变化。' } }
    $failedState = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
    if ($failedState.operation -ne 'FAILED' -or $failedState.phase -ne 'ROLLBACK_FAILED') { throw '失败状态被误清除。' }
    if ((File-Sha $ledgerSnapshot) -eq (File-Sha $ledger)) { throw '活动账本被损坏快照覆盖。' }
    Stop-Checked $failed
    [IO.File]::Delete($ledgerSnapshot)
    $wrongCode = 'import sqlite3,sys; c=sqlite3.connect(sys.argv[1]); c.executescript("PRAGMA user_version=1; CREATE TABLE unrelated(value TEXT);"); c.close()'
    & python -c $wrongCode $ledgerSnapshot
    if ($LASTEXITCODE -ne 0) { throw '错误 Schema 合成快照建立失败。' }
    $wrongSchema = Start-Checked $true
    Start-Sleep -Seconds 3
    $wrongProbe = "(async()=>{const h={headers:{'x-nanfeng-data-area':'WORK'}};return await window.__TAURI__.core.invoke('read_desktop_local_backup_status',{},h)})()" | & node $cdpHelper $wrongSchema.debugPort 2>$null
    if ($LASTEXITCODE -eq 0) { throw '错误 Schema 账本快照时业务 WebView2 仍可读取备份状态。' }
    foreach ($path in $before.Keys) { if ((File-Sha $path) -ne $before[$path]) { throw '错误 Schema 账本快照期间活动文件发生变化。' } }
    $wrongState = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
    if ($wrongState.operation -ne 'FAILED' -or $wrongState.phase -ne 'ROLLBACK_FAILED') { throw '错误 Schema 账本快照误推进回滚状态。' }
    Stop-Checked $wrongSchema
    [IO.File]::WriteAllBytes($ledgerSnapshot, $healthyLedger)
    $reopened = Start-Checked $true
    $deadline = [DateTime]::UtcNow.AddSeconds(30)
    $ready = $false
    while ([DateTime]::UtcNow -lt $deadline) {
        try {
            $expression = "(async()=>{const h={headers:{'x-nanfeng-data-area':'WORK'}};const s=await window.__TAURI__.core.invoke('read_desktop_local_backup_status',{},h);return !!s})()"
            $value = $expression | & node $cdpHelper $reopened.debugPort 2>$null
            if ($LASTEXITCODE -eq 0 -and ($value | ConvertFrom-Json) -eq $true) { $ready=$true; break }
        } catch { Start-Sleep -Milliseconds 250 }
    }
    if (-not $ready) { throw '修复快照后的同根 WebView2/备份 IPC 未就绪。' }
    $restoredTitle = Sqlite-Row $workDatabase 'SELECT title FROM workspaces LIMIT 1'
    $restoredUsage = Sqlite-Row $ledger 'SELECT entry_id,input_tokens FROM usage_ledger_entries LIMIT 1'
    if ($restoredTitle[0] -ne $expectedTitle[0] -or $restoredUsage[0] -ne $expectedUsage[0] -or $restoredUsage[1] -ne $expectedUsage[1]) { throw '同根恢复内容与健康 checkpoint 不一致。' }
    if ((Sqlite-Rows $chatDatabase 'SELECT * FROM workspaces ORDER BY id') -ne $chatRowsBefore -or
        (Sqlite-Rows $sharedDatabase 'SELECT * FROM desktop_provider_settings ORDER BY provider_id') -ne $sharedRowsBefore) {
        throw '另一数据区或共享模型配置业务行被回滚覆盖。'
    }
    $recoveredState = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
    if ($recoveredState.operation -ne 'FAILED' -or $recoveredState.phase -ne 'ROLLED_BACK') { throw '同根重启未完成回滚状态。' }
    Stop-Checked $reopened
    [pscustomobject]@{candidateSha256=$receipt.sha256;root=$root;checkpointId=$checkpointId;missingV3ManifestRejected=[bool]$ManifestV3;missingV3LedgerRejected=[bool]$ManifestV3;damagedLedgerRejected=$true;wrongSchemaRejected=$true;activeFilesUnchanged=$before.Count;recoveredTitle=$restoredTitle[0];recoveredUsage=$restoredUsage;chatAndSharedUnchanged=$true;webviewAndBackupIpcReady=$true;stoppedPids=@($stopped)} | ConvertTo-Json -Depth 4
} finally {
    foreach ($launch in @($running)) {
        try { Stop-Checked $launch } catch { Write-Warning '无法核验并停止某验收 PID；需按精确副本路径人工核对。' }
    }
}
