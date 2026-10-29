param([string]$RootParent = 'D:\CodexTemp')
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
if (-not $IsWindows -and $PSVersionTable.PSEdition -eq 'Core') { throw '仅支持 Windows 原生验收。' }
$desktopRoot = Split-Path -Parent $PSScriptRoot
$receipt = Get-Content -LiteralPath (Join-Path $desktopRoot 'output/windows-acceptance/current.json') -Raw -Encoding utf8 | ConvertFrom-Json
$sourceExecutable = [IO.Path]::GetFullPath((Join-Path $desktopRoot ("output/windows-acceptance/{0}/NanfengAI-Windows-Acceptance.exe" -f $receipt.sha256)))
$runExecutable = [IO.Path]::GetFullPath((Join-Path $desktopRoot ("output/windows-acceptance-binaries/{0}/nanfeng-ai-desktop-spike.exe" -f $receipt.sha256)))
if ($receipt.identifier -ne 'com.nanzhufeng.ai.desktop.windowsacceptance' -or $receipt.sha256 -notmatch '^[A-F0-9]{64}$' -or
    [IO.Path]::GetFullPath([string]$receipt.executable) -ne $sourceExecutable -or (Get-FileHash -LiteralPath $sourceExecutable -Algorithm SHA256).Hash -ne $receipt.sha256) { throw '隔离候选收据无效。' }
$launcher = Join-Path $PSScriptRoot 'start-windows-acceptance.ps1'
$cdpHelper = Join-Path $PSScriptRoot 'windows-acceptance-cdp-eval.mjs'
$fixture = [IO.Path]::GetFullPath((Join-Path $desktopRoot '../protocol/artifacts/nfai.exchange.v1.golden.nfai-exchange'))
if (-not (Test-Path -LiteralPath $fixture -PathType Leaf)) { throw '合成夹具缺失。' }
$owned = [System.Collections.Generic.List[int]]::new()

function New-Port {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $listener.Start()
    try { return $listener.LocalEndpoint.Port } finally { $listener.Stop() }
}
function Invoke-Cdp([int]$port, [string]$expression) {
    $output = $expression | & node $cdpHelper $port 2>$null
    if ($LASTEXITCODE -ne 0 -or -not $output) { throw '隔离 WebView2 IPC 调用失败。' }
    return ($output | ConvertFrom-Json)
}
function Assert-Owned([int]$processId) {
    $process = Get-CimInstance Win32_Process -Filter ("ProcessId={0}" -f $processId)
    if (-not $process -or [IO.Path]::GetFullPath([string]$process.ExecutablePath) -ne $runExecutable -or
        (Get-FileHash -LiteralPath $runExecutable -Algorithm SHA256).Hash -ne $receipt.sha256) { throw '隔离 PID 身份不符。' }
}
function Stop-Owned([int]$processId) {
    if (-not (Get-Process -Id $processId -ErrorAction SilentlyContinue)) { return }
    Assert-Owned $processId
    Stop-Process -Id $processId -Force
    $deadline = [DateTime]::UtcNow.AddSeconds(15)
    while ((Get-Process -Id $processId -ErrorAction SilentlyContinue) -and [DateTime]::UtcNow -lt $deadline) { Start-Sleep -Milliseconds 100 }
    if (Get-Process -Id $processId -ErrorAction SilentlyContinue) { throw '隔离 PID 未停止。' }
}
function Wait-Cdp([int]$port) {
    $deadline = [DateTime]::UtcNow.AddSeconds(30)
    while ([DateTime]::UtcNow -lt $deadline) {
        try { if ((Invoke-Cdp $port 'Boolean(window.__TAURI__?.core?.invoke)') -eq $true) { return } }
        catch { Start-Sleep -Milliseconds 250 }
    }
    throw '隔离 WebView2 未就绪。'
}

$results = @()
try {
    foreach ($phase in @('candidate-published','after-commit')) {
        $port = New-Port
        $launch = & $launcher -RootParent $RootParent -StartupMode normal -Visible -DebugPort $port -ExternalProfileForFullDelete -FullDeletePause $phase | ConvertFrom-Json
        $root = [string]$launch.root
        $pidBefore = [int]$launch.pid
        $owned.Add($pidBefore)
        Assert-Owned $pidBefore
        Wait-Cdp $port
        $fixtureJson = $fixture | ConvertTo-Json -Compress
        $key = 'nfai-synthetic-delete-crash-' + [Guid]::NewGuid().ToString('N')
        $keyJson = $key | ConvertTo-Json -Compress
        $before = Invoke-Cdp $port @"
(async()=>{const invoke=window.__TAURI__.core.invoke,fixture=$fixtureJson,key=$keyJson,ids={};for(const area of ['CHAT','WORK']){const h={headers:{'x-nanfeng-data-area':area}},staged=await invoke('stage_preflight_selected_exchange',{selectedPath:fixture},h),w=await invoke('import_staged_exchange_as_new_workspace',{args:{stagingId:staged.stagingId,workspaceTitle:'合成中断 '+area}},h);ids[area]=w.summary.id}const h={headers:{'x-nanfeng-data-area':'CHAT'}},s=(await invoke('read_desktop_model_service_settings',{},h)).find(x=>x.providerId==='QWEN');await invoke('save_desktop_model_service_settings',{args:{providerId:'QWEN',enabled:true,presetId:'QWEN_3_7_PLUS',expectedRevision:s.revision,apiKey:key}},h);const p=await invoke('preview_desktop_privacy_deletion',{args:{scope:'ALL_LOCAL_BUSINESS_DATA',selectedTaskIds:[]}},h);return {ids,fingerprint:p.fingerprint,aggregateCount:p.aggregates.length}})()
"@
        if ($before.aggregateCount -lt 2) { throw '双区预览汇总为空。' }
        $fingerprintJson = $before.fingerprint | ConvertTo-Json -Compress
        $queued = Invoke-Cdp $port @"
(async()=>{const h={headers:{'x-nanfeng-data-area':'WORK'}};return await window.__TAURI__.core.invoke('delete_desktop_privacy_data',{args:{scope:'ALL_LOCAL_BUSINESS_DATA',previewFingerprint:$fingerprintJson,confirmationPhrase:'删除全部本地业务数据',selectedTaskIds:[]}},h)})()
"@
        if (-not $queued.restartRequired) { throw '全部删除未进入退出队列。' }
        $marker = Join-Path $root ("full-delete-{0}.reached" -f $phase)
        $deadline = [DateTime]::UtcNow.AddSeconds(30)
        while (-not (Test-Path -LiteralPath $marker -PathType Leaf) -and [DateTime]::UtcNow -lt $deadline) { Start-Sleep -Milliseconds 100 }
        if (-not (Test-Path -LiteralPath $marker -PathType Leaf)) { throw "$phase 原生停点未到达。" }
        Assert-Owned $pidBefore
        $beforeKillOutcome = Test-Path -LiteralPath (Join-Path $root '.full-local-delete-v1.outcome.json') -PathType Leaf
        Stop-Owned $pidBefore
        $journalBeforeRestart = Test-Path -LiteralPath (Join-Path $root '.full-local-delete-v1.json') -PathType Leaf
        if (-not $journalBeforeRestart -or $beforeKillOutcome) { throw "$phase 强杀边界状态不符。" }
        $restartPort = New-Port
        $reopened = & $launcher -RootParent $RootParent -Root $root -StartupMode normal -Visible -DebugPort $restartPort -ExternalProfileForFullDelete | ConvertFrom-Json
        $pidAfter = [int]$reopened.pid
        $owned.Add($pidAfter)
        Assert-Owned $pidAfter
        Wait-Cdp $restartPort
        $idsJson = $before.ids | ConvertTo-Json -Compress
        $readbackExpression = @"
(async()=>{const invoke=window.__TAURI__.core.invoke,ids=$idsJson,result={};for(const area of ['CHAT','WORK']){const h={headers:{'x-nanfeng-data-area':area}};let oldPresent=false;try{const w=await invoke('read_desktop_workspace',{workspaceId:ids[area]},h);oldPresent=w.summary.id===ids[area]}catch{}const s=(await invoke('read_desktop_model_service_settings',{},h)).find(x=>x.providerId==='QWEN');result[area]={oldPresent,credentialStored:s.credentialStored}}result.outcome=await invoke('read_desktop_full_delete_outcome');return result})()
"@
        $readback = $null
        $readbackDeadline = [DateTime]::UtcNow.AddSeconds(30)
        while ([DateTime]::UtcNow -lt $readbackDeadline) {
            try {
                $candidate = Invoke-Cdp $restartPort $readbackExpression
                if (-not $candidate.outcome) { $readback = $candidate; break }
            } catch { Start-Sleep -Milliseconds 250 }
        }
        if (-not $readback) { throw "$phase 同根重启回读未就绪。" }
        $expectOld = $phase -eq 'candidate-published'
        foreach ($area in @('CHAT','WORK')) {
            if ($readback.$area.oldPresent -ne $expectOld -or $readback.$area.credentialStored -ne $expectOld) { throw "$phase $area 旧数据或凭据配对错误。" }
        }
        Stop-Owned $pidAfter
        $journalAfter = Test-Path -LiteralPath (Join-Path $root '.full-local-delete-v1.json') -PathType Leaf
        $committedAfter = Test-Path -LiteralPath (Join-Path $root '.full-local-delete-v1.committed') -PathType Leaf
        $outcomeAfter = Test-Path -LiteralPath (Join-Path $root '.full-local-delete-v1.outcome.json') -PathType Leaf
        if ($journalAfter -or $committedAfter -or $outcomeAfter) { throw "$phase 同根恢复控制文件未清理。" }
        $plain = & python -c "import pathlib,sys; root=pathlib.Path(sys.argv[1]); key=sys.argv[2].encode(); print(sum(key in p.read_bytes() for p in root.rglob('*') if p.is_file()))" $root $key
        if ($LASTEXITCODE -ne 0 -or [int]$plain -ne 0) { throw "$phase 合成密钥明文隔离失败。" }
        $checks = @{}
        foreach ($area in @('CHAT','WORK')) {
            $database = Join-Path $root $(if ($area -eq 'CHAT') {'workspace.sqlite3'} else {'work-area/workspace.sqlite3'})
            $db = & python -c "import json,sqlite3,sys; c=sqlite3.connect('file:'+sys.argv[1]+'?mode=ro',uri=True); print(json.dumps({'version':c.execute('PRAGMA user_version').fetchone()[0],'integrity':c.execute('PRAGMA integrity_check').fetchone()[0]}))" $database | ConvertFrom-Json
            if ($LASTEXITCODE -ne 0 -or $db.version -ne 46 -or $db.integrity -ne 'ok') { throw "$phase $area SQLite 回读失败。" }
            $checks[$area] = $db
        }
        $results += [pscustomobject]@{phase=$phase;root=$root;beforePid=$pidBefore;afterPid=$pidAfter;oldPairPresent=$expectOld;journalAtKill=$journalBeforeRestart;readback=$readback;sqlite=$checks;plaintextHits=[int]$plain;markersCleared=$true}
    }
    [pscustomobject]@{candidateSha256=$receipt.sha256;results=$results} | ConvertTo-Json -Depth 8
} finally {
    foreach ($processId in @($owned)) {
        try { Stop-Owned $processId } catch { Write-Warning $_ }
        $children = @(Get-CimInstance Win32_Process -Filter ("ParentProcessId={0}" -f $processId) -ErrorAction SilentlyContinue |
            Where-Object { $_.ExecutablePath -and [IO.Path]::GetFullPath([string]$_.ExecutablePath) -eq $runExecutable -and $_.CommandLine -like '*--windows-isolated-normal-startup*' })
        foreach ($child in $children) {
            try { Stop-Owned ([int]$child.ProcessId) } catch { Write-Warning $_ }
        }
    }
}
