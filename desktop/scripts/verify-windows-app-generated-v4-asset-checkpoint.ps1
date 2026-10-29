param(
    [string]$RootParent = 'D:\CodexTemp',
    [ValidateSet('CHAT','WORK')][string]$Area = 'WORK',
    [ValidateSet('MissingAsset','TamperAsset','ExtraAsset','MissingManifest','TamperManifest','AfterPreflightMissingAsset','AfterPreflightTamperAsset','AfterPreflightTamperDatabase','AfterPreflightTamperLedger','AfterPreflightTamperManifest','CopyAbort','PublishKill','LedgerPublishKill','AssetPublishKill','AssetClearKill','SecondAssetClearKill','SecondAssetPublishKill','StagedAssetTamper')][string]$Fault = 'MissingAsset'
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
function Start-Checked([bool]$pause, [bool]$pauseRollback = $false, [bool]$abortCopy = $false, [bool]$pausePublish = $false, [bool]$pauseLedgerPublish = $false, [bool]$pauseAssetPublish = $false, [bool]$pauseAssetClear = $false, [bool]$pauseSecondAssetClear = $false, [bool]$pauseSecondAssetPublish = $false) {
    $port = New-DebugPort
    $params = @{RootParent=$RootParent;StartupMode='normal';Visible=$true;DebugPort=$port}
    if ($root) { $params.Root=$root }
    if ($pause) { $params.BackupSwitchPause=$Area }
    if ($pauseRollback) { $params.BackupRollbackPause=$Area }
    if ($abortCopy) { $params.BackupRollbackCopyAbort=$Area }
    if ($pausePublish) { $params.BackupRollbackPublishPause=$Area }
    if ($pauseLedgerPublish) { $params.BackupRollbackLedgerPublishPause=$Area }
    if ($pauseAssetPublish) { $params.BackupRollbackAssetPublishPause=$Area }
    if ($pauseAssetClear) { $params.BackupRollbackAssetClearPause=$Area }
    if ($pauseSecondAssetClear) { $params.BackupRollbackSecondAssetClearPause=$Area }
    if ($pauseSecondAssetPublish) { $params.BackupRollbackSecondAssetPublishPause=$Area }
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
    $asset = Join-Path $areaRoot 'assets/v4-synthetic.txt'
    $package = Join-Path $areaRoot 'packages/v4-synthetic.txt'
    New-Item -ItemType Directory -Path (Split-Path -Parent $ledger) -Force | Out-Null
    New-Item -ItemType Directory -Path (Split-Path -Parent $asset) -Force | Out-Null
    New-Item -ItemType Directory -Path (Split-Path -Parent $package) -Force | Out-Null
    $code = @'
import sqlite3,sys
c=sqlite3.connect(sys.argv[1])
c.executescript("CREATE TABLE usage_ledger_entries(entry_id TEXT PRIMARY KEY,input_tokens INTEGER NOT NULL); INSERT INTO usage_ledger_entries VALUES('app-generated-v4',37); PRAGMA user_version=1;")
c.commit()
c.close()
'@
    & python -c $code $ledger
    if ($LASTEXITCODE -ne 0) { throw '合成费用账本建立失败。' }
    [IO.File]::WriteAllText($asset, 'app generated v4 synthetic asset')
    [IO.File]::WriteAllText($package, 'app generated v4 synthetic package')
    $workDatabase = Join-Path $work 'workspace.sqlite3'
    $chatDatabase = Join-Path $root 'workspace.sqlite3'
    $sharedDatabase = Join-Path $root 'app-configuration.sqlite3'
    foreach ($path in @($workDatabase,$chatDatabase,$sharedDatabase)) { if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw '双区或共享 SQLite 缺失。' } }
    $beforeChatRows = Sqlite-Rows $chatDatabase 'SELECT * FROM workspaces ORDER BY id'
    $beforeWorkRows = Sqlite-Rows $workDatabase 'SELECT * FROM workspaces ORDER BY id'
    $beforeSharedRows = Sqlite-Rows $sharedDatabase 'SELECT * FROM desktop_provider_settings ORDER BY provider_id'
    $beforeLedger = Sqlite-Row $ledger 'SELECT entry_id,input_tokens FROM usage_ledger_entries LIMIT 1'
    $backupPath = Join-Path $root ("v4-synthetic-{0}.nfai-backup" -f $Area.ToLowerInvariant())
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
            if ($state.operation -eq 'INTERRUPTED' -and $state.phase -eq 'SWITCHING' -and $state.checkpointId -match '^checkpoint-v4-[0-9]+-[0-9]+$') { break }
        }
        Start-Sleep -Milliseconds 150
    }
    if (-not $state -or $state.phase -ne 'SWITCHING' -or $state.checkpointId -notmatch '^checkpoint-v4-[0-9]+-[0-9]+$') { throw '应用未到达 v4 首资产切换停点。' }
    $checkpoint = [IO.Path]::GetFullPath((Join-Path $areaRoot ("local-backup/v1/recovery/{0}" -f $state.checkpointId)))
    if (-not $checkpoint.StartsWith(([IO.Path]::GetFullPath($areaRoot).TrimEnd('\') + '\'),[StringComparison]::OrdinalIgnoreCase)) { throw '检查点路径逸出合成根。' }
    $manifestPath = Join-Path $checkpoint 'checkpoint-manifest-v4.json'
    $ledgerSnapshot = Join-Path $checkpoint 'databases/desktop-usage-ledger-v1.snapshot'
    $mainSnapshot = Join-Path $checkpoint 'workspace.snapshot'
    $assetSnapshot = Join-Path $checkpoint 'assets/attachments/v1/v4-synthetic.txt'
    $packageSnapshot = Join-Path $checkpoint 'assets/workspace-packages/v1/v4-synthetic.txt'
    $heldAssetRoot = Join-Path $checkpoint 'rollback-displaced-assets'
    $heldAsset = Join-Path $heldAssetRoot 'root-0/v4-synthetic.txt'
    $heldPackage = Join-Path $heldAssetRoot 'root-1/v4-synthetic.txt'
    $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
    if ($manifest.version -ne 4 -or $manifest.workspaceSha256 -ne (File-Sha $mainSnapshot) -or $manifest.usageLedgerSha256 -ne (File-Sha $ledgerSnapshot) -or $manifest.assets.'attachments/v1/v4-synthetic.txt' -ne (File-Sha $assetSnapshot) -or $manifest.assets.'workspace-packages/v1/v4-synthetic.txt' -ne (File-Sha $packageSnapshot)) { throw '应用生成的 v4 清单与实际快照不符。' }
    $savedAsset = [IO.File]::ReadAllBytes($assetSnapshot)
    $savedMain = [IO.File]::ReadAllBytes($mainSnapshot)
    $savedLedger = [IO.File]::ReadAllBytes($ledgerSnapshot)
    $savedManifest = [IO.File]::ReadAllBytes($manifestPath)
    $savedManifestHash = File-Sha $manifestPath
    Stop-Checked $paused
    $beforeHashes = @{}
    foreach ($path in @($chatDatabase,$workDatabase,$sharedDatabase,$ledger,$asset,$package)) { $beforeHashes[$path] = File-Sha $path }
    $extraAsset = Join-Path (Split-Path -Parent $assetSnapshot) 'v4-unexpected.txt'
    $afterPreflight = $Fault -like 'AfterPreflight*'
    $ledgerPublishKill = $Fault -in @('LedgerPublishKill', 'AssetPublishKill', 'AssetClearKill', 'SecondAssetClearKill', 'SecondAssetPublishKill')
    $assetPublishKill = $Fault -eq 'AssetPublishKill'
    $assetClearKill = $Fault -eq 'AssetClearKill'
    $secondAssetClearKill = $Fault -eq 'SecondAssetClearKill'
    $secondAssetPublishKill = $Fault -eq 'SecondAssetPublishKill'
    $secondAssetKill = $secondAssetClearKill -or $secondAssetPublishKill
    $publishKill = $Fault -in @('PublishKill', 'LedgerPublishKill', 'AssetPublishKill', 'AssetClearKill', 'SecondAssetClearKill', 'SecondAssetPublishKill')
    if (-not $afterPreflight) {
        switch ($Fault) {
            'MissingAsset' { [IO.File]::Delete($assetSnapshot) }
            'TamperAsset' { [IO.File]::WriteAllText($assetSnapshot, 'tampered synthetic asset') }
            'ExtraAsset' { [IO.File]::WriteAllText($extraAsset, 'unexpected synthetic asset') }
            'MissingManifest' { [IO.File]::Delete($manifestPath) }
            'TamperManifest' {
                $changed = $manifest | ConvertTo-Json -Depth 20 -Compress
                $changed = $changed.Replace([string]$manifest.workspaceSha256, ('0' * 64))
                [IO.File]::WriteAllText($manifestPath, $changed)
            }
        }
    }
    $failed = Start-Checked $false $afterPreflight ($Fault -eq 'CopyAbort') ($Fault -eq 'PublishKill') ($Fault -in @('LedgerPublishKill','StagedAssetTamper')) $assetPublishKill $assetClearKill $secondAssetClearKill $secondAssetPublishKill
    if ($afterPreflight) {
        $rollbackMarker = Join-Path $root ("local-backup-{0}-rollback-preflight.reached" -f $Area)
        $rollbackContinue = Join-Path $root ("local-backup-{0}-rollback-preflight.continue" -f $Area)
        $deadline = [DateTime]::UtcNow.AddSeconds(30)
        while (-not (Test-Path -LiteralPath $rollbackMarker -PathType Leaf) -and [DateTime]::UtcNow -lt $deadline) { Start-Sleep -Milliseconds 100 }
        if (-not (Test-Path -LiteralPath $rollbackMarker -PathType Leaf)) { throw '应用未到达回滚预检后停点。' }
        switch ($Fault) {
            'AfterPreflightMissingAsset' { [IO.File]::Delete($assetSnapshot) }
            'AfterPreflightTamperAsset' { [IO.File]::WriteAllText($assetSnapshot, 'changed after rollback preflight') }
            'AfterPreflightTamperDatabase' { [IO.File]::WriteAllText($mainSnapshot, 'changed after rollback preflight') }
            'AfterPreflightTamperLedger' { [IO.File]::WriteAllText($ledgerSnapshot, 'changed after rollback preflight') }
            'AfterPreflightTamperManifest' { [IO.File]::WriteAllText($manifestPath, 'changed after rollback preflight') }
        }
        $resume = [IO.File]::Open($rollbackContinue,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
        try { $resume.Flush($true) } finally { $resume.Dispose() }
    }
    if ($Fault -eq 'StagedAssetTamper') {
        $publishMarker = Join-Path $root ("local-backup-{0}-rollback-publish-ledger.reached" -f $Area)
        $publishContinue = Join-Path $root ("local-backup-{0}-rollback-publish-ledger.continue" -f $Area)
        $deadline = [DateTime]::UtcNow.AddSeconds(30)
        while (-not (Test-Path -LiteralPath $publishMarker -PathType Leaf) -and [DateTime]::UtcNow -lt $deadline) { Start-Sleep -Milliseconds 100 }
        if (-not (Test-Path -LiteralPath $publishMarker -PathType Leaf)) { throw '应用未到达暂存资产发布前停点。' }
        $stagedAsset = Join-Path $checkpoint 'rollback-copy-stage/assets/attachments/v1/v4-synthetic.txt'
        if ((File-Sha $stagedAsset) -ne [string]$manifest.assets.'attachments/v1/v4-synthetic.txt') { throw '暂存资产故障前已不匹配清单。' }
        [IO.File]::WriteAllText($stagedAsset, 'tampered synthetic staged asset')
        $resume = [IO.File]::Open($publishContinue,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
        try { $resume.Flush($true) } finally { $resume.Dispose() }
        Start-Sleep -Seconds 3
        $probe = "(async()=>{const h={headers:{'x-nanfeng-data-area':'$Area'}};return await window.__TAURI__.core.invoke('read_desktop_local_backup_status',{},h)})()" | & node $cdpHelper $failed.debugPort 2>$null
        if ($LASTEXITCODE -eq 0) { throw '暂存资产篡改后应用仍进入业务页。' }
        $publishedDatabase = if ($Area -eq 'CHAT') { $chatDatabase } else { $workDatabase }
        if ((File-Sha $publishedDatabase) -ne [string]$manifest.workspaceSha256 -or (File-Sha $ledger) -ne [string]$manifest.usageLedgerSha256) { throw '暂存资产故障前已发布的库不匹配清单。' }
        $mustStay = @($beforeHashes.Keys | Where-Object { $_ -ne $publishedDatabase -and $_ -ne $ledger })
        foreach ($path in $mustStay) { if ((File-Sha $path) -ne $beforeHashes[$path]) { throw "暂存资产故障改动了活动文件：$path" } }
        if (Test-Path -LiteralPath $heldAssetRoot) { throw '暂存资产故障已移出旧活动资产。' }
        if (Test-Path -LiteralPath (Join-Path $checkpoint 'rollback-copy-stage')) { throw '暂存资产故障后复制暂存未清理。' }
        $blocked = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
        if ($blocked.operation -ne 'INTERRUPTED' -or $blocked.phase -ne 'SWITCHING') { throw '暂存资产故障误推进恢复状态。' }
        Stop-Checked $failed
    } elseif ($publishKill) {
        $point = if ($secondAssetClearKill) { 'rollback-clear-second-asset' } elseif ($secondAssetPublishKill) { 'rollback-publish-second-asset' } elseif ($assetClearKill) { 'rollback-clear-asset' } elseif ($assetPublishKill) { 'rollback-publish-asset' } elseif ($ledgerPublishKill) { 'rollback-publish-ledger' } else { 'rollback-publish' }
        $publishMarker = Join-Path $root ("local-backup-{0}-{1}.reached" -f $Area,$point)
        $deadline = [DateTime]::UtcNow.AddSeconds(30)
        while (-not (Test-Path -LiteralPath $publishMarker -PathType Leaf) -and [DateTime]::UtcNow -lt $deadline) { Start-Sleep -Milliseconds 100 }
        if (-not (Test-Path -LiteralPath $publishMarker -PathType Leaf)) { throw '应用未到达活动发布后停点。' }
        if (-not (Test-Path -LiteralPath (Join-Path $checkpoint 'rollback-copy-stage') -PathType Container)) { throw '发布停点缺少未清理暂存。' }
        $publishedDatabase = if ($Area -eq 'CHAT') { $chatDatabase } else { $workDatabase }
        if ((File-Sha $publishedDatabase) -ne [string]$manifest.workspaceSha256) { throw '发布停点的活动主库与检查点清单不匹配。' }
        if ($ledgerPublishKill -and (File-Sha $ledger) -ne [string]$manifest.usageLedgerSha256) { throw '账本发布停点的活动账本与检查点清单不匹配。' }
        if ($assetClearKill -and (Test-Path -LiteralPath (Split-Path -Parent $asset))) { throw '首资产清理停点的活动资产根仍存在。' }
        if (($assetPublishKill -or $secondAssetKill) -and (File-Sha $asset) -ne [string]$manifest.assets.'attachments/v1/v4-synthetic.txt') { throw '资产发布停点的首活动资产与检查点清单不匹配。' }
        if ($secondAssetClearKill -and (Test-Path -LiteralPath (Split-Path -Parent $package))) { throw '第二资产移出停点的活动目录仍存在。' }
        if ($secondAssetPublishKill -and (File-Sha $package) -ne [string]$manifest.assets.'workspace-packages/v1/v4-synthetic.txt') { throw '第二资产发布停点的活动内容与清单不匹配。' }
        if (($assetClearKill -or $assetPublishKill -or $secondAssetKill) -and ((-not (Test-Path -LiteralPath $heldAsset -PathType Leaf)) -or (File-Sha $heldAsset) -ne $beforeHashes[$asset])) { throw '首资产切换停点未保留原活动资产。' }
        if ($secondAssetKill -and ((-not (Test-Path -LiteralPath $heldPackage -PathType Leaf)) -or (File-Sha $heldPackage) -ne $beforeHashes[$package])) { throw '第二资产切换停点未保留原活动资产。' }
        $mustStay = @($beforeHashes.Keys | Where-Object { $_ -ne $publishedDatabase -and (-not $ledgerPublishKill -or $_ -ne $ledger) -and (-not ($assetPublishKill -or $assetClearKill -or $secondAssetKill) -or $_ -ne $asset) -and (-not $secondAssetKill -or $_ -ne $package) })
        foreach ($path in $mustStay) { if ((File-Sha $path) -ne $beforeHashes[$path]) { throw "v4 发布停点的其他活动文件变化：$path" } }
        $blocked = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
        if ($blocked.operation -ne 'INTERRUPTED' -or $blocked.phase -ne 'SWITCHING') { throw '首库发布停点的恢复状态无效。' }
        Stop-Checked $failed
    } else {
        Start-Sleep -Seconds 3
        $probe = "(async()=>{const h={headers:{'x-nanfeng-data-area':'$Area'}};return await window.__TAURI__.core.invoke('read_desktop_local_backup_status',{},h)})()" | & node $cdpHelper $failed.debugPort 2>$null
        if ($LASTEXITCODE -eq 0) { throw "应用生成的 v4 检查点故障 $Fault 后仍进入业务页。" }
        $mustStay = @($beforeHashes.Keys)
        foreach ($path in $mustStay) { if ((File-Sha $path) -ne $beforeHashes[$path]) { throw "v4 故障 $Fault 期间非目标活动文件变化。" } }
        $blocked = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
        if ($blocked.operation -ne 'INTERRUPTED' -or $blocked.phase -ne 'SWITCHING') { throw "故障 $Fault 误推进恢复状态。" }
        if ($Fault -eq 'CopyAbort' -and (Test-Path -LiteralPath (Join-Path $checkpoint 'rollback-copy-stage'))) { throw '回滚复制受控中断后暂存未清理。' }
        Stop-Checked $failed
    }
    switch ($Fault) {
        'MissingAsset' { [IO.File]::WriteAllBytes($assetSnapshot, $savedAsset) }
        'TamperAsset' { [IO.File]::WriteAllBytes($assetSnapshot, $savedAsset) }
        'ExtraAsset' { [IO.File]::Delete($extraAsset) }
        'MissingManifest' { [IO.File]::WriteAllBytes($manifestPath, $savedManifest) }
        'TamperManifest' { [IO.File]::WriteAllBytes($manifestPath, $savedManifest) }
        'AfterPreflightMissingAsset' { [IO.File]::WriteAllBytes($assetSnapshot, $savedAsset) }
        'AfterPreflightTamperAsset' { [IO.File]::WriteAllBytes($assetSnapshot, $savedAsset) }
        'AfterPreflightTamperDatabase' { [IO.File]::WriteAllBytes($mainSnapshot, $savedMain) }
        'AfterPreflightTamperLedger' { [IO.File]::WriteAllBytes($ledgerSnapshot, $savedLedger) }
        'AfterPreflightTamperManifest' { [IO.File]::WriteAllBytes($manifestPath, $savedManifest) }
    }
    if ((File-Sha $mainSnapshot) -ne [string]$manifest.workspaceSha256 -or
        (File-Sha $ledgerSnapshot) -ne [string]$manifest.usageLedgerSha256 -or
        (File-Sha $assetSnapshot) -ne [string]$manifest.assets.'attachments/v1/v4-synthetic.txt' -or
        (File-Sha $manifestPath) -ne $savedManifestHash) {
        throw '检查点材料修复回读不匹配。'
    }
    Start-Sleep -Seconds 2
    $pauseEnv = [Environment]::GetEnvironmentVariable('NANFENG_AI_WINDOWS_BACKUP_ROLLBACK_PAUSE','Process')
    if ($pauseEnv) { throw '验收脚本进程仍带有回滚停点环境变量。' }
    if ([Environment]::GetEnvironmentVariable('NANFENG_AI_WINDOWS_BACKUP_ROLLBACK_COPY_ABORT','Process')) { throw '验收脚本进程仍带有回滚复制故障点环境变量。' }
    if ([Environment]::GetEnvironmentVariable('NANFENG_AI_WINDOWS_BACKUP_ROLLBACK_PUBLISH_PAUSE','Process')) { throw '验收脚本进程仍带有回滚发布停点环境变量。' }
    if ([Environment]::GetEnvironmentVariable('NANFENG_AI_WINDOWS_BACKUP_ROLLBACK_LEDGER_PUBLISH_PAUSE','Process')) { throw '验收脚本进程仍带有回滚账本发布停点环境变量。' }
    if ([Environment]::GetEnvironmentVariable('NANFENG_AI_WINDOWS_BACKUP_ROLLBACK_ASSET_PUBLISH_PAUSE','Process')) { throw '验收脚本进程仍带有回滚资产发布停点环境变量。' }
    if ([Environment]::GetEnvironmentVariable('NANFENG_AI_WINDOWS_BACKUP_ROLLBACK_ASSET_CLEAR_PAUSE','Process')) { throw '验收脚本进程仍带有回滚资产清理停点环境变量。' }
    if ([Environment]::GetEnvironmentVariable('NANFENG_AI_WINDOWS_BACKUP_ROLLBACK_SECOND_ASSET_CLEAR_PAUSE','Process')) { throw '验收脚本进程仍带有回滚第二资产移出停点环境变量。' }
    if ([Environment]::GetEnvironmentVariable('NANFENG_AI_WINDOWS_BACKUP_ROLLBACK_SECOND_ASSET_PUBLISH_PAUSE','Process')) { throw '验收脚本进程仍带有回滚第二资产发布停点环境变量。' }
    $recovered = Start-Checked $false
    try { Wait-Ready $recovered } catch {
        $diagnosticState = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
        $live = [bool](Get-Process -Id $recovered.pid -ErrorAction SilentlyContinue)
        throw "修复后冷启未就绪：phase=$($diagnosticState.phase), pidAlive=$live, pauseEnv=$pauseEnv"
    }
    $finalState = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
    if ($finalState.operation -ne 'INTERRUPTED' -or $finalState.phase -ne 'ROLLED_BACK_INTERRUPTED') { throw '有效快照同根冷启未完成回滚。' }
    if ($publishKill -and (Test-Path -LiteralPath (Join-Path $checkpoint 'rollback-copy-stage'))) { throw '发布强杀后的暂存未在同根恢复时清理。' }
    if (Test-Path -LiteralPath $heldAssetRoot) { throw '同根恢复后旧资产保留区未清理。' }
    $afterLedger = Sqlite-Row $ledger 'SELECT entry_id,input_tokens FROM usage_ledger_entries LIMIT 1'
    if ($afterLedger[0] -ne $beforeLedger[0] -or $afterLedger[1] -ne $beforeLedger[1] -or
        (Sqlite-Rows $chatDatabase 'SELECT * FROM workspaces ORDER BY id') -ne $beforeChatRows -or
        (Sqlite-Rows $workDatabase 'SELECT * FROM workspaces ORDER BY id') -ne $beforeWorkRows -or
        (Sqlite-Rows $sharedDatabase 'SELECT * FROM desktop_provider_settings ORDER BY provider_id') -ne $beforeSharedRows -or
        (File-Sha $asset) -ne $beforeHashes[$asset] -or (File-Sha $package) -ne $beforeHashes[$package]) { throw '同根回滚账本、双区、共享配置或资产事实不一致。' }
    Stop-Checked $recovered
    [pscustomobject]@{candidateSha256=$receipt.sha256;root=$root;area=$Area;fault=$Fault;backupBytes=$artifact.byteCount;checkpointId=$state.checkpointId;manifestFromApp=$true;manifestAndSnapshotHashesMatch=$true;faultRejected=(-not $publishKill);stageTamperRejected=($Fault -eq 'StagedAssetTamper');crashAfterDatabasePublish=$publishKill;crashAfterLedgerPublish=$ledgerPublishKill;crashAfterFirstAssetClear=$assetClearKill;crashAfterFirstAssetPublish=$assetPublishKill;crashAfterSecondAssetClear=$secondAssetClearKill;crashAfterSecondAssetPublish=$secondAssetPublishKill;oldAssetHeld=($assetClearKill -or $assetPublishKill -or $secondAssetKill);holdCleared=$true;unaffectedFilesUnchanged=$mustStay.Count;rollbackState=$finalState.phase;ledger=$afterLedger;stoppedPids=@($stopped)} | ConvertTo-Json -Depth 5
} finally {
    foreach ($launch in @($running)) {
        try { Stop-Checked $launch } catch { Write-Warning '某验收进程无法核路径停止，需人工核对。' }
    }
}
