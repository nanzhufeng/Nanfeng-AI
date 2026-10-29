param([string]$RootParent = 'D:\CodexTemp')
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
if (-not $IsWindows -and $PSVersionTable.PSEdition -eq 'Core') { throw '仅支持 Windows 原生验收。' }
$desktopRoot = Split-Path -Parent $PSScriptRoot
$receipt = Get-Content -LiteralPath (Join-Path $desktopRoot 'output/windows-acceptance/current.json') -Raw | ConvertFrom-Json
if ($receipt.identifier -ne 'com.nanzhufeng.ai.desktop.windowsacceptance' -or $receipt.sha256 -notmatch '^[A-F0-9]{64}$') { throw '验收构建收据身份无效。' }
$launcher = Join-Path $PSScriptRoot 'start-windows-acceptance.ps1'
$cdpHelper = Join-Path $PSScriptRoot 'windows-acceptance-cdp-eval.mjs'
$sourceExecutable = [IO.Path]::GetFullPath((Join-Path $desktopRoot ("output/windows-acceptance/{0}/NanfengAI-Windows-Acceptance.exe" -f $receipt.sha256)))
$expectedExecutable = [IO.Path]::GetFullPath((Join-Path $desktopRoot ("output/windows-acceptance-binaries/{0}/nanfeng-ai-desktop-spike.exe" -f $receipt.sha256)))
if ([IO.Path]::GetFullPath([string]$receipt.executable) -ne $sourceExecutable -or
    (Get-FileHash -LiteralPath $sourceExecutable -Algorithm SHA256).Hash -ne $receipt.sha256) {
    throw '验收候选路径或哈希与收据不符。'
}
$oldKey = 'nfai-synthetic-old-' + [Guid]::NewGuid().ToString('N')
$newKey = 'nfai-synthetic-new-' + [Guid]::NewGuid().ToString('N')
$running = [System.Collections.Generic.List[object]]::new()
$stops = [System.Collections.Generic.List[object]]::new()
$root = ''

function Assert-ProcessIdentity($launch) {
    if ($launch.sha256 -ne $receipt.sha256 -or [IO.Path]::GetFullPath([string]$launch.executable) -ne $expectedExecutable) { throw '运行副本与候选收据不匹配。' }
    $deadline = [DateTime]::UtcNow.AddSeconds(5)
    do {
        $process = Get-CimInstance Win32_Process -Filter "ProcessId = $($launch.pid)" -ErrorAction Stop
        if ($process -and $process.ExecutablePath -and [IO.Path]::GetFullPath([string]$process.ExecutablePath) -eq $expectedExecutable) { return }
        Start-Sleep -Milliseconds 100
    } while ([DateTime]::UtcNow -lt $deadline)
    throw '验收进程路径不匹配或未就绪。'
}
function Stop-Acceptance($launch) {
    $process = Get-CimInstance Win32_Process -Filter "ProcessId = $($launch.pid)" -ErrorAction Stop
    if (-not $process -or -not $process.ExecutablePath -or [IO.Path]::GetFullPath([string]$process.ExecutablePath) -ne $expectedExecutable -or
        (Get-FileHash -LiteralPath $expectedExecutable -Algorithm SHA256).Hash -ne $receipt.sha256) {
        throw '验收 PID 身份不匹配，拒绝停止。'
    }
    Stop-Process -Id $launch.pid -Force -ErrorAction Stop
    $deadline = [DateTime]::UtcNow.AddSeconds(15)
    while ((Get-Process -Id $launch.pid -ErrorAction SilentlyContinue) -and [DateTime]::UtcNow -lt $deadline) { Start-Sleep -Milliseconds 100 }
    if (Get-Process -Id $launch.pid -ErrorAction SilentlyContinue) { throw '验收进程未停止。' }
    $running.Remove($launch) | Out-Null
    $stops.Add([pscustomobject]@{pid=$launch.pid; reason='controlled-stop'})
}
function New-DebugPort {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $listener.Start()
    try { return $listener.LocalEndpoint.Port } finally { $listener.Stop() }
}
function Invoke-Cdp([int]$port, [string]$expression) {
    $output = $expression | & node $cdpHelper $port 2>$null
    if ($LASTEXITCODE -ne 0 -or -not $output) { throw '隔离 WebView2 IPC 调用失败。' }
    return ($output | ConvertFrom-Json)
}
function Start-Acceptance([string]$phase = '') {
    $port = New-DebugPort
    $params = @{ RootParent=$RootParent; StartupMode='normal'; Visible=$true; DebugPort=$port }
    if ($root) { $params.Root = $root }
    if ($phase) { $params.ModelPendingPause = $phase }
    $launch = & $launcher @params | ConvertFrom-Json
    if (-not $root) { $script:root = [string]$launch.root }
    if ($launch.root -ne $root) { throw '隔离数据根不一致。' }
    $running.Add($launch)
    Assert-ProcessIdentity $launch
    $deadline = [DateTime]::UtcNow.AddSeconds(25)
    while ([DateTime]::UtcNow -lt $deadline) {
        try {
            $ready = Invoke-Cdp $port "(async()=>{const h={headers:{'x-nanfeng-data-area':'CHAT'}};const s=await window.__TAURI__.core.invoke('read_desktop_model_service_settings',{},h);return Array.isArray(s)&&s.some(x=>x.providerId==='QWEN')})()"
            if ($ready -eq $true) { return $launch }
        } catch { Start-Sleep -Milliseconds 250 }
    }
    throw '隔离 WebView2 页面未就绪。'
}
function Save-Setting($launch, [string]$area, [int]$revision, [string]$preset, [string]$key, [bool]$waitForResult) {
    $argsJson = @{providerId='QWEN';enabled=$true;presetId=$preset;expectedRevision=$revision;apiKey=$key} | ConvertTo-Json -Compress
    $headerJson = @{headers=@{'x-nanfeng-data-area'=$area}} | ConvertTo-Json -Compress
    $call = "window.__TAURI__.core.invoke('save_desktop_model_service_settings',{args:$argsJson},$headerJson)"
    if ($waitForResult) { return Invoke-Cdp $launch.debugPort $call }
    $started = Invoke-Cdp $launch.debugPort "(()=>{void $call;return 'started'})()"
    if ($started -ne 'started') { throw '未能开始合成模型保存。' }
}
function Assert-Pair($launch, [int]$revision, [string]$preset, [string]$key, [string]$wrongKey) {
    foreach ($area in @('CHAT','WORK')) {
        $headerJson = @{headers=@{'x-nanfeng-data-area'=$area}} | ConvertTo-Json -Compress
        $expression = "(async()=>{const h=$headerJson;const s=(await window.__TAURI__.core.invoke('read_desktop_model_service_settings',{},h)).find(x=>x.providerId==='QWEN');const k=await window.__TAURI__.core.invoke('reveal_desktop_model_service_credential',{providerId:'QWEN'},h);return {revision:s.revision,presetId:s.presetId,stored:s.credentialStored,keyMatches:k===$($key | ConvertTo-Json -Compress),wrongKeyMatches:k===$($wrongKey | ConvertTo-Json -Compress)}})()"
        $pair = Invoke-Cdp $launch.debugPort $expression
        if ($pair.revision -ne $revision -or $pair.presetId -ne $preset -or -not $pair.stored -or -not $pair.keyMatches -or $pair.wrongKeyMatches) { throw "${area} 配置与凭据配对失败。" }
    }
}
function Read-SqliteSetting {
    $database = Join-Path $root 'app-configuration.sqlite3'
    $code = "import sqlite3,sys,json; c=sqlite3.connect('file:'+sys.argv[1]+'?mode=ro',uri=True); r=c.execute('SELECT preset_id,revision FROM desktop_provider_settings WHERE provider_id=?',('QWEN',)).fetchone(); print(json.dumps(r))"
    $row = & python -c $code $database | ConvertFrom-Json
    if ($LASTEXITCODE -ne 0 -or @($row).Count -ne 2) { throw '共享 SQLite 回读失败。' }
    return ,$row
}
function Assert-Pending([string]$phase, [int]$revision, [string]$preset) {
    $marker = Join-Path $root "model-pending-$phase.reached"
    $pending = Join-Path $root 'provider-credentials-v1/model-setting-pending-v1.json'
    $deadline = [DateTime]::UtcNow.AddSeconds(20)
    while (-not (Test-Path -LiteralPath $marker -PathType Leaf) -and [DateTime]::UtcNow -lt $deadline) { Start-Sleep -Milliseconds 100 }
    if (-not (Test-Path -LiteralPath $marker -PathType Leaf) -or -not (Test-Path -LiteralPath $pending -PathType Leaf)) { throw '待决停点未落盘。' }
    $bytes = [IO.File]::ReadAllBytes($pending)
    $body = [Text.Encoding]::UTF8.GetString($bytes)
    $value = $body | ConvertFrom-Json
    if ($value.version -ne 2 -or $body.Contains($oldKey) -or $body.Contains($newKey)) { throw '待决版本或明文隔离失败。' }
    $row = Read-SqliteSetting
    if ($row[0] -ne $preset -or $row[1] -ne $revision) { throw '停点 SQLite 状态不符合预期。' }
    return [pscustomobject]@{phase=$phase; pendingBytes=$bytes.Length; pendingSha256=(Get-FileHash -LiteralPath $pending -Algorithm SHA256).Hash; sqliteRevision=$row[1]; sqlitePreset=$row[0]}
}
function Assert-PendingCleared {
    if (Test-Path -LiteralPath (Join-Path $root 'provider-credentials-v1/model-setting-pending-v1.json')) { throw '同根恢复后待决未清理。' }
}

try {
    $baseline = Start-Acceptance
    $saved = Save-Setting $baseline 'CHAT' 0 'QWEN_3_7_PLUS' $oldKey $true
    if ($saved.revision -ne 1) { throw '初始合成设置 revision 错误。' }
    Assert-Pair $baseline 1 'QWEN_3_7_PLUS' $oldKey $newKey
    Stop-Acceptance $baseline

    $before = Start-Acceptance 'before-sqlite'
    Save-Setting $before 'WORK' 1 'QWEN_3_8_MAX' $newKey $false
    $beforeState = Assert-Pending 'before-sqlite' 1 'QWEN_3_7_PLUS'
    Stop-Acceptance $before
    $recoveredOld = Start-Acceptance
    Assert-Pair $recoveredOld 1 'QWEN_3_7_PLUS' $oldKey $newKey
    Assert-PendingCleared
    Stop-Acceptance $recoveredOld

    $after = Start-Acceptance 'after-sqlite'
    Save-Setting $after 'CHAT' 1 'QWEN_3_8_MAX' $newKey $false
    $afterState = Assert-Pending 'after-sqlite' 2 'QWEN_3_8_MAX'
    Stop-Acceptance $after
    $recoveredNew = Start-Acceptance
    Assert-Pair $recoveredNew 2 'QWEN_3_8_MAX' $newKey $oldKey
    Assert-PendingCleared
    Stop-Acceptance $recoveredNew

    $files = @(Get-ChildItem -LiteralPath $root -Recurse -File -Force | Where-Object { $_.FullName -notlike (Join-Path $root 'webview2\*') })
    foreach ($file in $files) {
        $content = [Text.Encoding]::UTF8.GetString([IO.File]::ReadAllBytes($file.FullName))
        if ($content.Contains($oldKey) -or $content.Contains($newKey)) { throw '合成根非 WebView 文件发现凭据明文。' }
    }
    $databases = @($files | Where-Object Extension -eq '.sqlite3')
    foreach ($database in $databases) {
        $code = "import sqlite3,sys; c=sqlite3.connect('file:'+sys.argv[1]+'?mode=ro',uri=True); print(c.execute('PRAGMA integrity_check').fetchone()[0])"
        $result = & python -c $code $database.FullName
        if ($LASTEXITCODE -ne 0 -or $result -ne 'ok') { throw 'SQLite 完整性检查失败。' }
    }
    [pscustomobject]@{candidateSha256=$receipt.sha256;root=$root;before=$beforeState;after=$afterState;chatWorkOldAndNewPairs='passed';pendingCleared=$true;plaintextFileCount=$files.Count;plaintextHits=0;sqliteIntegrityOk=$databases.Count;stoppedPids=@($stops | ForEach-Object pid)} | ConvertTo-Json -Depth 5
} finally {
    foreach ($launch in @($running)) {
        try { Stop-Acceptance $launch } catch { Write-Warning '无法核验并停止某个验收 PID；请人工核对隔离运行副本路径。' }
    }
}
