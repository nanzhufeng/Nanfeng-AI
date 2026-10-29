param(
    [Parameter(Mandatory)][string]$Root,
    [Parameter(Mandatory)][string]$Backup,
    [Parameter(Mandatory)][string]$WorkspaceId,
    [Parameter(Mandatory)][string]$ProjectId,
    [Parameter(Mandatory)][string]$AssetName,
    [ValidateSet('CHAT', 'WORK')][string]$Area = 'CHAT'
)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
if (-not $IsWindows) { throw '仅支持 Windows 原生隔离验收。' }
$parent = 'D:\CodexTemp'
$rootPath = [IO.Path]::GetFullPath($Root)
$backupPath = [IO.Path]::GetFullPath($Backup)
if (-not $rootPath.StartsWith($parent + '\', [StringComparison]::OrdinalIgnoreCase) -or
    -not (Test-Path -LiteralPath (Join-Path $rootPath '.nanfeng-windows-acceptance.json') -PathType Leaf) -or
    -not $backupPath.StartsWith($parent + '\', [StringComparison]::OrdinalIgnoreCase) -or
    -not [IO.Path]::GetFileName($backupPath).StartsWith('nfai-normal-backup.') -or
    -not (Test-Path -LiteralPath $backupPath -PathType Leaf) -or
    $AssetName -notmatch '^synthetic-backup-normal-[a-f0-9]{32}\.txt$') { throw '只允许已标记根中的当前合成备份。' }
$areaRoot = if ($Area -eq 'WORK') { Join-Path $rootPath 'work-area' } else { $rootPath }
$asset = Join-Path $areaRoot ('assets\' + $AssetName)
if ([IO.File]::ReadAllText($asset) -ne 'SYNTHETIC-NORMAL-BACKUP-FIXTURE') { throw '合成资产初始字节不符。' }
$desktopRoot = Split-Path -Parent $PSScriptRoot
$receipt = Get-Content -LiteralPath (Join-Path $desktopRoot 'output/windows-acceptance/current.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$sourceExe = [IO.Path]::GetFullPath([string]$receipt.executable)
$runExe = [IO.Path]::GetFullPath((Join-Path $desktopRoot ("output/windows-acceptance-binaries/{0}/nanfeng-ai-desktop-spike.exe" -f $receipt.sha256)))
if ($receipt.identifier -ne 'com.nanzhufeng.ai.desktop.windowsacceptance' -or (Get-FileHash -LiteralPath $sourceExe -Algorithm SHA256).Hash -ne $receipt.sha256) { throw '隔离候选收据不符。' }
$cdpHelper = Join-Path $PSScriptRoot 'windows-acceptance-cdp-eval.mjs'
$launch = $null
$pids = @()
function New-Port {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $listener.Start()
    try { return [int]$listener.LocalEndpoint.Port } finally { $listener.Stop() }
}
function Invoke-Cdp([int]$port, [string]$expression) {
    $text = $expression | & node --max-old-space-size=2048 $cdpHelper $port
    if ($LASTEXITCODE -ne 0 -or -not $text) { throw '隔离 WebView2 IPC 失败。' }
    return $text | ConvertFrom-Json
}
function Start-Candidate {
    $port = New-Port
    $started = & (Join-Path $PSScriptRoot 'start-windows-acceptance.ps1') -RootParent $parent -Root $rootPath -StartupMode normal -Visible:$true -DebugPort $port | ConvertFrom-Json
    $script:launch = $started
    if ([IO.Path]::GetFullPath([string]$started.root) -ne $rootPath -or $started.sha256 -ne $receipt.sha256 -or [IO.Path]::GetFullPath([string]$started.executable) -ne $runExe) { throw '候选运行身份不符。' }
    $deadline = [DateTime]::UtcNow.AddSeconds(30)
    while ([DateTime]::UtcNow -lt $deadline) {
        try {
            $text = 'Boolean(window.__TAURI__?.core?.invoke)' | & node --max-old-space-size=2048 $cdpHelper $port 2>$null
            if ($LASTEXITCODE -eq 0 -and $text -eq 'true') { return @{ launch = $started; port = $port } }
        }
        catch { }
        Start-Sleep -Milliseconds 250
    }
    throw '隔离 WebView2 未就绪。'
}
function Stop-Candidate($started) {
    if (-not $started) { return }
    $process = Get-Process -Id $started.pid -ErrorAction SilentlyContinue
    if (-not $process) { return }
    if ([IO.Path]::GetFullPath([string]$process.Path) -ne $runExe -or (Get-FileHash -LiteralPath $runExe -Algorithm SHA256).Hash -ne $receipt.sha256) { throw 'PID 路径或哈希不符，拒绝停止。' }
    Stop-Process -Id $started.pid -Force -ErrorAction Stop
    $deadline = [DateTime]::UtcNow.AddSeconds(15)
    while ((Get-Process -Id $started.pid -ErrorAction SilentlyContinue) -and [DateTime]::UtcNow -lt $deadline) { Start-Sleep -Milliseconds 100 }
    if (Get-Process -Id $started.pid -ErrorAction SilentlyContinue) { throw '受控 PID 未退出。' }
}
try {
    $running = Start-Candidate
    $launch = $running.launch
    $pids += [int]$launch.pid
    $peerArea = if ($Area -eq 'CHAT') { 'WORK' } else { 'CHAT' }
    $peerAreaJson = $peerArea | ConvertTo-Json -Compress
    $peerIntentJson = ('synthetic-backup-peer-' + [Guid]::NewGuid().ToString('N')) | ConvertTo-Json -Compress
    $peerAssetName = 'synthetic-backup-peer-' + [Guid]::NewGuid().ToString('N') + '.txt'
    $peerAssetRoot = if ($peerArea -eq 'WORK') { Join-Path $rootPath 'work-area\assets' } else { Join-Path $rootPath 'assets' }
    $peerAsset = Join-Path $peerAssetRoot $peerAssetName
    $peer = Invoke-Cdp $running.port "(async()=>{const h={headers:{'x-nanfeng-data-area':$peerAreaJson}};const call=(n,a={})=>window.__TAURI__.core.invoke(n,a,h);const w=await call('ensure_desktop_local_workspace');const made=await call('mutate_desktop_domain',{args:{intentId:$peerIntentJson,workspaceId:w.summary.id,entity:'project',action:'create',fields:{title:'跨区隔离合成项目',description:'另一数据区恢复时应保留'}}});return {workspaceId:w.summary.id,projectId:made.objectId}})()"
    if (-not $peer.workspaceId -or -not $peer.projectId -or -not (Test-Path -LiteralPath $peerAssetRoot -PathType Container)) { throw '另一数据区合成项目或资产根创建失败。' }
    [IO.File]::WriteAllText($peerAsset, 'SYNTHETIC-PEER-AREA-UNCHANGED', [Text.Encoding]::UTF8)
    $peerAssetHash = (Get-FileHash -LiteralPath $peerAsset -Algorithm SHA256).Hash
    $peerWorkspaceJson = $peer.workspaceId | ConvertTo-Json -Compress
    [IO.File]::WriteAllText($asset, 'SYNTHETIC-AFTER-BACKUP-CHANGE', [Text.Encoding]::UTF8)
    $workspaceJson = $WorkspaceId | ConvertTo-Json -Compress
    $backupJson = $backupPath | ConvertTo-Json -Compress
    $areaJson = $Area | ConvertTo-Json -Compress
    $intentJson = ('synthetic-after-backup-' + [Guid]::NewGuid().ToString('N')) | ConvertTo-Json -Compress
    $changed = Invoke-Cdp $running.port "(async()=>{const h={headers:{'x-nanfeng-data-area':$areaJson}};const call=(n,a={})=>window.__TAURI__.core.invoke(n,a,h);const added=await call('mutate_desktop_domain',{args:{intentId:$intentJson,workspaceId:$workspaceJson,entity:'project',action:'create',fields:{title:'备份后合成项目',description:'应由恢复移除'}}});const preflight=await call('preflight_desktop_local_backup',{selectedPath:$backupJson});const restore=await call('restore_desktop_local_backup',{fingerprint:preflight.fingerprint,replaceLocal:true});return {addedId:added.objectId,checkpointId:restore.checkpointId,restartRequired:restore.restartRequired}})()"
    if (-not $changed.addedId -or -not $changed.checkpointId -or -not $changed.restartRequired) { throw '明确替换恢复未返回重启回执。' }
    $peerBeforeRestart = Invoke-Cdp $running.port "(async()=>{const h={headers:{'x-nanfeng-data-area':$peerAreaJson}};const w=await window.__TAURI__.core.invoke('read_desktop_workspace',{workspaceId:$peerWorkspaceJson},h);return {projects:w.exchange.projects.map(p=>p.id)}})()"
    if (-not ($peerBeforeRestart.projects -contains $peer.projectId) -or (Get-FileHash -LiteralPath $peerAsset -Algorithm SHA256).Hash -ne $peerAssetHash) { throw '目标区恢复后，另一数据区内容已变化。' }
    Stop-Candidate $launch
    $launch = $null
    $running = Start-Candidate
    $launch = $running.launch
    $pids += [int]$launch.pid
    $after = Invoke-Cdp $running.port "(async()=>{const h={headers:{'x-nanfeng-data-area':$areaJson}};const call=(n,a={})=>window.__TAURI__.core.invoke(n,a,h);const workspace=await call('read_desktop_workspace',{workspaceId:$workspaceJson});const status=await call('read_desktop_local_backup_status');return {projects:workspace.exchange.projects.map(p=>({id:p.id,title:p.title})),operation:status.operation}})()"
    if (-not ($after.projects | Where-Object id -eq $ProjectId) -or ($after.projects | Where-Object id -eq $changed.addedId) -or $after.operation -ne 'COMPLETED' -or [IO.File]::ReadAllText($asset) -ne 'SYNTHETIC-NORMAL-BACKUP-FIXTURE') { throw '同根冷启后备份内容未准确恢复。' }
    $peerAfterRestart = Invoke-Cdp $running.port "(async()=>{const h={headers:{'x-nanfeng-data-area':$peerAreaJson}};const w=await window.__TAURI__.core.invoke('read_desktop_workspace',{workspaceId:$peerWorkspaceJson},h);return {projects:w.exchange.projects.map(p=>p.id)}})()"
    if (-not ($peerAfterRestart.projects -contains $peer.projectId) -or (Get-FileHash -LiteralPath $peerAsset -Algorithm SHA256).Hash -ne $peerAssetHash) { throw '同根冷启后另一数据区内容已变化。' }
    [pscustomobject]@{ candidateSha256 = $receipt.sha256; root = $rootPath; area = $Area; originalProjectId = $ProjectId; removedProjectId = $changed.addedId; checkpointId = $changed.checkpointId; coldStatus = $after.operation; assetRestored = $true; peerArea = $peerArea; peerProjectId = $peer.projectId; peerProjectRetained = $true; peerAssetHashUnchanged = $true; pids = $pids } | ConvertTo-Json -Compress
} finally {
    Stop-Candidate $launch
}
