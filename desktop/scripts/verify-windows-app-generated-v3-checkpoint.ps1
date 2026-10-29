param([string]$RootParent = 'D:\CodexTemp', [ValidateSet('CHAT','WORK')][string]$Area = 'WORK')
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
$launcher = Join-Path $PSScriptRoot 'start-windows-acceptance.ps1'
$cdpHelper = Join-Path $PSScriptRoot 'windows-acceptance-cdp-eval.mjs'
$running = [System.Collections.Generic.List[object]]::new()
$stopped = [System.Collections.Generic.List[int]]::new()
$root = ''
function New-DebugPort {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $listener.Start()
    try { return $listener.LocalEndpoint.Port } finally { $listener.Stop() }
}
function Invoke-Cdp($launch, [string]$expression) {
    $value = $expression | & node $cdpHelper $launch.debugPort 2>$null
    if ($LASTEXITCODE -ne 0 -or -not $value) { throw '隔离 WebView2 IPC 未响应。' }
    return ($value | ConvertFrom-Json -NoEnumerate)
}
function Start-Checked([bool]$pause) {
    $port = New-DebugPort
    $params = @{RootParent=$RootParent;StartupMode='normal';Visible=$true;DebugPort=$port}
    if ($root) { $params.Root=$root }
    if ($pause) { $params.BackupSwitchPause=$Area }
    $launch = & $launcher @params | ConvertFrom-Json
    if (-not $root) { $script:root=[string]$launch.root }
    $running.Add($launch)
    if ($launch.root -ne $root -or $launch.sha256 -ne $receipt.sha256 -or [IO.Path]::GetFullPath([string]$launch.executable) -ne $runExecutable) { throw '验收运行副本身份不符。' }
    $process = Get-CimInstance Win32_Process -Filter ("ProcessId={0}" -f $launch.pid)
    if (-not $process -or [IO.Path]::GetFullPath([string]$process.ExecutablePath) -ne $runExecutable) { throw '验收进程路径不符。' }
    return $launch
}
function Stop-Checked($launch) {
    $process = Get-CimInstance Win32_Process -Filter ("ProcessId={0}" -f $launch.pid)
    if (-not $process) { $running.Remove($launch) | Out-Null; return }
    if ([IO.Path]::GetFullPath([string]$process.ExecutablePath) -ne $runExecutable -or (Get-FileHash -LiteralPath $runExecutable -Algorithm SHA256).Hash -ne $receipt.sha256) { throw 'PID 路径或哈希不符，拒绝停止。' }
    Stop-Process -Id $launch.pid -Force -ErrorAction Stop
    $deadline = [DateTime]::UtcNow.AddSeconds(15)
    while ((Get-Process -Id $launch.pid -ErrorAction SilentlyContinue) -and [DateTime]::UtcNow -lt $deadline) { Start-Sleep -Milliseconds 100 }
    if (Get-Process -Id $launch.pid -ErrorAction SilentlyContinue) { throw '验收进程未停止。' }
    $running.Remove($launch) | Out-Null
    $stopped.Add([int]$launch.pid)
}
function Wait-Ready($launch) {
    $deadline = [DateTime]::UtcNow.AddSeconds(30)
    while ([DateTime]::UtcNow -lt $deadline) {
        try {
            $ready = Invoke-Cdp $launch "(async()=>{const h={headers:{'x-nanfeng-data-area':'$Area'}};return !!(await window.__TAURI__.core.invoke('read_desktop_local_backup_status',{},h))})()"
            if ($ready -eq $true) { return }
        } catch { Start-Sleep -Milliseconds 250 }
    }
    throw "隔离 $Area 备份 IPC 未就绪。"
}
function File-Sha([string]$path) { return (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() }
function Sqlite-Row([string]$path, [string]$sql) {
    $code = 'import sqlite3,sys,json; c=sqlite3.connect("file:"+sys.argv[1]+"?mode=ro",uri=True); print(json.dumps(c.execute(sys.argv[2]).fetchone(),ensure_ascii=False))'
    $row = & python -c $code $path $sql | ConvertFrom-Json -NoEnumerate
    if ($LASTEXITCODE -ne 0) { throw 'SQLite 合成回读失败。' }
    return ,$row
}
function Sqlite-Rows([string]$path, [string]$sql) {
    $code = 'import sqlite3,sys,json; c=sqlite3.connect("file:"+sys.argv[1]+"?mode=ro",uri=True); print(json.dumps(c.execute(sys.argv[2]).fetchall(),ensure_ascii=False,sort_keys=True))'
    $rows = & python -c $code $path $sql
    if ($LASTEXITCODE -ne 0) { throw 'SQLite 合成业务行回读失败。' }
    return [string]$rows
}
try {
    $first = Start-Checked $false
    Wait-Ready $first
    Stop-Checked $first
    if (-not $root.StartsWith($parent + '\', [StringComparison]::OrdinalIgnoreCase) -or -not (Test-Path -LiteralPath (Join-Path $root '.nanfeng-windows-acceptance.json') -PathType Leaf)) { throw '新建根不是受控合成根。' }
    $work = Join-Path $root 'work-area'
    $areaRoot = if ($Area -eq 'CHAT') { $root } else { $work }
    $ledger = Join-Path $areaRoot 'usage-ledger/usage-ledger-v1.sqlite3'
    $asset = Join-Path $areaRoot 'assets/v3-synthetic.txt'
    New-Item -ItemType Directory -Path (Split-Path -Parent $ledger) -Force | Out-Null
    New-Item -ItemType Directory -Path (Split-Path -Parent $asset) -Force | Out-Null
    $code = @'
import sqlite3,sys
c=sqlite3.connect(sys.argv[1])
c.executescript("CREATE TABLE usage_ledger_entries(entry_id TEXT PRIMARY KEY,input_tokens INTEGER NOT NULL); INSERT INTO usage_ledger_entries VALUES('app-generated-v3',37); PRAGMA user_version=1;")
c.commit()
c.close()
'@
    & python -c $code $ledger
    if ($LASTEXITCODE -ne 0) { throw '合成费用账本建立失败。' }
    [IO.File]::WriteAllText($asset, 'app generated v3 synthetic asset')
    $workDatabase = Join-Path $work 'workspace.sqlite3'
    $chatDatabase = Join-Path $root 'workspace.sqlite3'
    $sharedDatabase = Join-Path $root 'app-configuration.sqlite3'
    foreach ($path in @($workDatabase,$chatDatabase,$sharedDatabase)) { if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw '双区或共享 SQLite 缺失。' } }
    $beforeChatRows = Sqlite-Rows $chatDatabase 'SELECT * FROM workspaces ORDER BY id'
    $beforeWorkRows = Sqlite-Rows $workDatabase 'SELECT * FROM workspaces ORDER BY id'
    $beforeSharedRows = Sqlite-Rows $sharedDatabase 'SELECT * FROM desktop_provider_settings ORDER BY provider_id'
    $beforeLedger = Sqlite-Row $ledger 'SELECT entry_id,input_tokens FROM usage_ledger_entries LIMIT 1'
    $backupPath = Join-Path $root ("v3-synthetic-{0}.nfai-backup" -f $Area.ToLowerInvariant())
    $paused = Start-Checked $true
    Wait-Ready $paused
    $h = "{headers:{'x-nanfeng-data-area':'$Area'}}"
    $pathJson = $backupPath | ConvertTo-Json -Compress
    $artifact = Invoke-Cdp $paused "window.__TAURI__.core.invoke('export_desktop_local_backup',{selectedPath:$pathJson},$h)"
    if (-not (Test-Path -LiteralPath $backupPath -PathType Leaf) -or $artifact.byteCount -le 0) { throw "应用导出 $Area 备份失败。" }
    $preflight = Invoke-Cdp $paused "window.__TAURI__.core.invoke('preflight_desktop_local_backup',{selectedPath:$pathJson},$h)"
    if (-not $preflight.fingerprint) { throw "应用 $Area 预检缺少指纹。" }
    $fingerprintJson = [string]$preflight.fingerprint | ConvertTo-Json -Compress
    $started = Invoke-Cdp $paused "(()=>{void window.__TAURI__.core.invoke('restore_desktop_local_backup',{fingerprint:$fingerprintJson,replaceLocal:true},$h);return 'started'})()"
    if ($started -ne 'started') { throw '应用恢复未启动。' }
    $statePath = Join-Path $areaRoot 'local-backup/v1/state.json'
    $marker = Join-Path $root ("local-backup-{0}-after-first-asset.reached" -f $Area)
    $deadline = [DateTime]::UtcNow.AddSeconds(30)
    $state = $null
    while ([DateTime]::UtcNow -lt $deadline) {
        if ((Test-Path -LiteralPath $marker -PathType Leaf) -and (Test-Path -LiteralPath $statePath -PathType Leaf)) {
            $state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
            if ($state.operation -eq 'INTERRUPTED' -and $state.phase -eq 'SWITCHING' -and $state.checkpointId -like 'checkpoint-v3-*') { break }
        }
        Start-Sleep -Milliseconds 150
    }
    if (-not $state -or $state.phase -ne 'SWITCHING' -or $state.checkpointId -notlike 'checkpoint-v3-*') { throw '应用未到达 v3 首资产切换停点。' }
    $checkpoint = Join-Path $areaRoot ("local-backup/v1/recovery/{0}" -f $state.checkpointId)
    $manifestPath = Join-Path $checkpoint 'checkpoint-manifest-v3.json'
    $ledgerSnapshot = Join-Path $checkpoint 'databases/desktop-usage-ledger-v1.snapshot'
    $mainSnapshot = Join-Path $checkpoint 'workspace.snapshot'
    $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
    if ($manifest.version -ne 3 -or $manifest.workspaceSha256 -ne (File-Sha $mainSnapshot) -or $manifest.usageLedgerSha256 -ne (File-Sha $ledgerSnapshot)) { throw '应用生成的 v3 清单与实际快照不符。' }
    $savedLedger = [IO.File]::ReadAllBytes($ledgerSnapshot)
    Stop-Checked $paused
    $beforeHashes = @{}
    foreach ($path in @($chatDatabase,$workDatabase,$sharedDatabase,$ledger,$asset)) { $beforeHashes[$path] = File-Sha $path }
    [IO.File]::Delete($ledgerSnapshot)
    $failed = Start-Checked $false
    Start-Sleep -Seconds 3
    $probe = "(async()=>{const h={headers:{'x-nanfeng-data-area':'$Area'}};return await window.__TAURI__.core.invoke('read_desktop_local_backup_status',{},h)})()" | & node $cdpHelper $failed.debugPort 2>$null
    if ($LASTEXITCODE -eq 0) { throw '应用生成的 v3 账本快照丢失后仍进入业务页。' }
    foreach ($path in $beforeHashes.Keys) { if ((File-Sha $path) -ne $beforeHashes[$path]) { throw 'v3 缺账本期间活动文件变化。' } }
    $blocked = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
    if ($blocked.operation -ne 'INTERRUPTED' -or $blocked.phase -ne 'SWITCHING') { throw '缺账本误推进恢复状态。' }
    Stop-Checked $failed
    [IO.File]::WriteAllBytes($ledgerSnapshot, $savedLedger)
    $recovered = Start-Checked $false
    Wait-Ready $recovered
    $finalState = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
    if ($finalState.operation -ne 'INTERRUPTED' -or $finalState.phase -ne 'ROLLED_BACK_INTERRUPTED') { throw '有效快照同根冷启未完成回滚。' }
    $afterLedger = Sqlite-Row $ledger 'SELECT entry_id,input_tokens FROM usage_ledger_entries LIMIT 1'
    if ($afterLedger[0] -ne $beforeLedger[0] -or $afterLedger[1] -ne $beforeLedger[1] -or
        (Sqlite-Rows $chatDatabase 'SELECT * FROM workspaces ORDER BY id') -ne $beforeChatRows -or
        (Sqlite-Rows $workDatabase 'SELECT * FROM workspaces ORDER BY id') -ne $beforeWorkRows -or
        (Sqlite-Rows $sharedDatabase 'SELECT * FROM desktop_provider_settings ORDER BY provider_id') -ne $beforeSharedRows -or
        (File-Sha $asset) -ne $beforeHashes[$asset]) { throw '同根回滚账本、双区、共享配置或资产事实不一致。' }
    Stop-Checked $recovered
    [pscustomobject]@{candidateSha256=$receipt.sha256;root=$root;area=$Area;backupBytes=$artifact.byteCount;checkpointId=$state.checkpointId;manifestFromApp=$true;manifestAndSnapshotHashesMatch=$true;missingLedgerRejected=$true;activeFilesUnchanged=$beforeHashes.Count;rollbackState=$finalState.phase;ledger=$afterLedger;stoppedPids=@($stopped)} | ConvertTo-Json -Depth 5
} finally {
    foreach ($launch in @($running)) {
        try { Stop-Checked $launch } catch { Write-Warning '某验收进程无法核路径停止，需人工核对。' }
    }
}
